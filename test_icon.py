import asyncio
import uuid
import struct
import time
import zlib
from winrt.windows.devices.bluetooth.rfcomm import RfcommServiceProvider, RfcommServiceId
from winrt.windows.networking.sockets import StreamSocketListener
from winrt.windows.storage.streams import DataReader, DataWriter, InputStreamOptions

CID_DYNAMIC_BASE = 0x00100000

def to_rgb565(r: int, g: int, b: int) -> int:
    return ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3)

def render_teal(w: int, h: int) -> bytes:
    val = to_rgb565(0, 137, 123)
    buf = bytearray(w * h * 2)
    for i in range(0, len(buf), 2):
        struct.pack_into("<H", buf, i, val)
    return bytes(buf)

def pack_packet(msg_type: int, msg_id: int, payload: bytes) -> bytes:
    return struct.pack("<III", 12 + len(payload), msg_type, msg_id) + payload

# Variant 1: Uncompressed ResourceImage (subType=5, colorMode=3 at 0x34, 1 at 0x38, rawSize at 0x3c)
def pack_raw_image(msg_id: int, cid: int, w: int, h: int, raw_bytes: bytes) -> bytes:
    data_len = 32 + len(raw_bytes)
    buf = bytearray()
    buf += struct.pack("<II", cid, 0)
    buf += struct.pack("<HH", 1, 0)
    buf += struct.pack("<I", data_len)
    buf += struct.pack("<III", 0, 0, 0)
    buf += struct.pack("<HH", 5, 0)                  # subType = 5
    buf += struct.pack("<I", 0)                      # pad
    buf += struct.pack("<HH", w - 1, h - 1)          # 0x30, 0x32
    buf += struct.pack("<HH", 3, 0)                  # 0x34: colorMode = 3 (RGB565)
    buf += struct.pack("<HH", 1, 0)                  # 0x38: 1
    buf += struct.pack("<I", len(raw_bytes))         # 0x3c: raw size
    buf += raw_bytes                                 # 0x40
    return pack_packet(1, msg_id, bytes(buf))

# Variant 2: Compressed Image with standard ZLIB (compressionType=4 at 0x34)
def pack_comp_zlib(msg_id: int, cid: int, w: int, h: int, raw_bytes: bytes) -> bytes:
    comp = zlib.compress(raw_bytes)
    data_len = 32 + len(comp)
    buf = bytearray()
    buf += struct.pack("<II", cid, 0)
    buf += struct.pack("<HH", 1, 0)
    buf += struct.pack("<I", data_len)
    buf += struct.pack("<III", 0, 0, 0)
    buf += struct.pack("<HH", 5, 0)
    buf += struct.pack("<I", 0)
    buf += struct.pack("<HH", w - 1, h - 1)
    buf += struct.pack("<I", 4)                      # 0x34: compressionType = 4 (ZLIB)
    buf += struct.pack("<I", 0)                      # 0x38: colorType = 0 (BGR565)
    buf += struct.pack("<I", len(comp))              # 0x3c: comp size
    buf += comp
    return pack_packet(1, msg_id, bytes(buf))

# Variant 3: Compressed Image with raw DEFLATE (wbits=-15, no zlib header)
def pack_comp_raw_deflate(msg_id: int, cid: int, w: int, h: int, raw_bytes: bytes) -> bytes:
    compressor = zlib.compressobj(wbits=-15)
    comp = compressor.compress(raw_bytes) + compressor.flush()
    data_len = 32 + len(comp)
    buf = bytearray()
    buf += struct.pack("<II", cid, 0)
    buf += struct.pack("<HH", 1, 0)
    buf += struct.pack("<I", data_len)
    buf += struct.pack("<III", 0, 0, 0)
    buf += struct.pack("<HH", 5, 0)
    buf += struct.pack("<I", 0)
    buf += struct.pack("<HH", w - 1, h - 1)
    buf += struct.pack("<I", 4)                      # 0x34: compressionType = 4
    buf += struct.pack("<I", 0)                      # 0x38: colorType = 0
    buf += struct.pack("<I", len(comp))              # 0x3c: comp size
    buf += comp
    return pack_packet(1, msg_id, bytes(buf))

# Variant 4: subType = 6 (CID_TYPE_RESOURCE_COMPRESSED_IMAGE)
def pack_subtype_6(msg_id: int, cid: int, w: int, h: int, raw_bytes: bytes) -> bytes:
    comp = zlib.compress(raw_bytes)
    data_len = 32 + len(comp)
    buf = bytearray()
    buf += struct.pack("<II", cid, 0)
    buf += struct.pack("<HH", 1, 0)
    buf += struct.pack("<I", data_len)
    buf += struct.pack("<III", 0, 0, 0)
    buf += struct.pack("<HH", 6, 0)                  # subType = 6
    buf += struct.pack("<I", 0)
    buf += struct.pack("<HH", w - 1, h - 1)
    buf += struct.pack("<I", 4)
    buf += struct.pack("<I", 0)
    buf += struct.pack("<I", len(comp))
    buf += comp
    return pack_packet(1, msg_id, bytes(buf))

async def main():
    loop = asyncio.get_running_loop()
    costanza_uuid = uuid.UUID("8E780522-3C51-11E1-8D8D-001CC4D601D8")
    svc_id = RfcommServiceId.from_uuid(costanza_uuid)
    provider = await RfcommServiceProvider.create_async(svc_id)
    listener = StreamSocketListener()
    sock = None
    connected = asyncio.Event()

    def on_conn(s, args):
        nonlocal sock
        sock = args.socket
        loop.call_soon_threadsafe(connected.set)

    listener.add_connection_received(on_conn)
    await listener.bind_service_name_async(provider.service_id.as_string())
    provider.start_advertising(listener, True)
    
    print("[*] Advertising... waiting for watch...")
    await connected.wait()
    print("[+] Watch connected!")
    
    writer = DataWriter(sock.output_stream)
    reader = DataReader(sock.input_stream)
    reader.input_stream_options = InputStreamOptions.PARTIAL
    
    async def read_loop():
        while True:
            try:
                count = await reader.load_async(1024)
                if count > 0:
                    raw = bytearray(count)
                    reader.read_bytes(raw)
                    buf = bytearray(raw)
                    while len(buf) >= 12:
                        p_len, p_type, p_id = struct.unpack("<III", buf[:12])
                        if len(buf) < p_len:
                            break
                        pkt = buf[:p_len]
                        buf = buf[p_len:]
                        if p_type == 9:
                            ack_id = struct.unpack("<I", pkt[12:16])[0]
                            print(f"  ===> [ACK] for MsgId #{ack_id}!")
                        elif p_type == 10:
                            nack_id = struct.unpack("<I", pkt[12:16])[0]
                            print(f"  ===> [NACK] for MsgId #{nack_id}!")
                        elif p_type == 5:
                            print(f"  [RX] Version response")
            except Exception:
                break

    read_task = asyncio.create_task(read_loop())
    
    msg_id = 1
    async def send(name, pkt):
        nonlocal msg_id
        print(f"[TX] #{msg_id} - {name} (len={len(pkt)})")
        writer.write_bytes(pkt)
        await writer.store_async()
        await writer.flush_async()
        msg_id += 1
        await asyncio.sleep(0.1)

    # Handshake
    await send("VERSION_REQ", struct.pack("<III", 12, 4, msg_id))

    raw_12 = render_teal(12, 12) # 288 bytes

    print("\n--- TESTING 4 IMAGE VARIANTS ---")
    # Test 1: Raw Image (subType=5, colorMode=3)
    await send("VAR1_RAW_IMAGE_12x12", pack_raw_image(msg_id, CID_DYNAMIC_BASE + 901, 12, 12, raw_12))

    # Test 2: Comp Image standard zlib (subType=5, compressType=4)
    await send("VAR2_COMP_ZLIB_12x12", pack_comp_zlib(msg_id, CID_DYNAMIC_BASE + 902, 12, 12, raw_12))

    # Test 3: Comp Image raw deflate (subType=5, wbits=-15)
    await send("VAR3_RAW_DEFLATE_12x12", pack_comp_raw_deflate(msg_id, CID_DYNAMIC_BASE + 903, 12, 12, raw_12))

    # Test 4: Comp Image subType=6
    await send("VAR4_SUBTYPE_6_12x12", pack_subtype_6(msg_id, CID_DYNAMIC_BASE + 904, 12, 12, raw_12))

    await asyncio.sleep(4.0)
    read_task.cancel()
    provider.stop_advertising()
    listener.close()
    sock.close()
    print("[*] Test done!")

if __name__ == "__main__":
    asyncio.run(main())
