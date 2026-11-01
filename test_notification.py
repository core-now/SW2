import asyncio
import uuid
import struct
import time
import zlib
from winrt.windows.devices.bluetooth.rfcomm import RfcommServiceProvider, RfcommServiceId
from winrt.windows.networking.sockets import StreamSocketListener
from winrt.windows.storage.streams import DataReader, DataWriter, InputStreamOptions

CID_DYNAMIC_BASE = 0x00100000
CID_NOTIFICATION_EXTENSION = CID_DYNAMIC_BASE + 2
CID_NOTIFICATION_EXTENSION_NAME = CID_DYNAMIC_BASE + 3
CID_NOTIFICATION_EXTENSION_ICON = CID_DYNAMIC_BASE + 4

CID_NOTIFICATION_BASE = CID_DYNAMIC_BASE + 100
CID_NOTIFICATION_TITLE = CID_DYNAMIC_BASE + 200
CID_NOTIFICATION_MESSAGE = CID_DYNAMIC_BASE + 300
CID_NOTIFICATION_IMAGE = CID_DYNAMIC_BASE + 400
CID_NOTIFICATION_ICON_STATUS = CID_DYNAMIC_BASE + 500
CID_NOTIFICATION_ICON_RICH = CID_DYNAMIC_BASE + 600
CID_NOTIFICATION_DISPLAY_NAME = CID_DYNAMIC_BASE + 700
CID_NOTIFICATION_SCREEN = CID_DYNAMIC_BASE + 800

TYPE_NAMES = {
    1: "RESOURCE",
    4: "VERSION_REQ",
    5: "VERSION_RSP",
    6: "REQUEST_FOTA",
    7: "RESPONSE_FOTA",
    9: "ACK",
    10: "NACK",
    11: "FRAGMENT",
    16: "SET_LOCALE_REQ",
    18: "BATTERY_LEVEL_IND",
    19: "BATTERY_LEVEL_CNF",
    20: "FACTORY_RESET_REQ",
    21: "FACTORY_RESET_RSP",
    22: "SET_TIME_REQ",
    104: "EXTENSION_REQ",
    105: "EXTENSION_RSP",
    106: "CONTROL_REQ",
    107: "CONTROL_RSP",
    108: "CONTROL_IND",
    110: "REQUEST_NOTIFICATION",
    112: "INDICATION_NOTIFICATION",
    114: "INDICATION_KEY",
    115: "INDICATION_TOUCH",
    116: "INDICATION_SWIPE",
    124: "DEBUG_LOG",
    129: "REQUEST_VIBRATION",
    131: "INDICATION_OPEN_RESOURCE",
    134: "NOTIFICATION_READ_IND",
    135: "NOTIFICATION_LIST_REQ",
    136: "NOTIFICATION_LIST_RESP",
    137: "ERROR_IND",
    141: "NOTIFICATION_ALL_READ_IND",
}

def to_rgb565(r: int, g: int, b: int) -> int:
    return ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3)

def render_teal_icon(width: int, height: int) -> bytes:
    val = to_rgb565(0, 137, 123)
    buf = bytearray(width * height * 2)
    for i in range(0, len(buf), 2):
        struct.pack_into("<H", buf, i, val)
    return bytes(buf)

def render_white_card(width: int = 196, height: int = 100) -> bytes:
    buf = bytearray(width * height * 2)
    for i in range(0, len(buf), 2):
        struct.pack_into("<H", buf, i, 0xFFFF)
    return bytes(buf)

def pack_packet(msg_type: int, msg_id: int, payload: bytes) -> bytes:
    total_len = 12 + len(payload)
    return struct.pack("<III", total_len, msg_type, msg_id) + payload

def pack_resource_compressed_image(msg_id: int, cid: int, width: int, height: int, raw_rgb565: bytes) -> bytes:
    compressor = zlib.compressobj(wbits=-15)
    deflated = compressor.compress(raw_rgb565) + compressor.flush()
    payload_size = 52 + len(deflated)
    data_len = payload_size - 20
    
    buf = bytearray()
    buf += struct.pack("<II", cid, 0)
    buf += struct.pack("<HH", 1, 0)
    buf += struct.pack("<I", data_len)
    buf += struct.pack("<III", 0, 0, 0)
    buf += struct.pack("<HH", 5, 0)                  # subType = 5 (Image)
    buf += struct.pack("<I", 0)                      # pad
    buf += struct.pack("<HH", max(1, width - 1), max(1, height - 1))
    buf += struct.pack("<I", 4)                      # compressionType = 4 (ZLIB raw deflate)
    buf += struct.pack("<I", 0)                      # colorType = 0 (BGR565)
    buf += struct.pack("<I", len(deflated))          # compressed byte size
    buf += deflated
    return pack_packet(1, msg_id, bytes(buf))

def pack_resource_string(msg_id: int, cid: int, text: str, width: int = 220, height: int = 36, color: int = -1) -> bytes:
    chars = text.encode("utf-16le")
    payload_size = (0x52 - 0x0c) + len(chars) + 2
    data_len = payload_size - 20
    
    buf = bytearray()
    buf += struct.pack("<II", cid, 0)
    buf += struct.pack("<HH", 1, 0)
    buf += struct.pack("<I", data_len)
    buf += struct.pack("<III", 0, 0, 0)
    buf += struct.pack("<HH", 9, 0)                  # subType = 9 (String)
    buf += struct.pack("<I", 0)                      # pad
    buf += struct.pack("<HH", max(1, width - 1), max(1, height - 1))
    buf += struct.pack("<HH", 1, 0)
    buf += struct.pack("<HH", 1, 0)                  # hAlign = Left
    buf += struct.pack("<HH", 1, 0)                  # vAlign = Top
    buf += struct.pack("<HH", 4, 0)                  # font = Normal
    buf += struct.pack("<HH", 4, 0)
    buf += struct.pack("<i", color)                  # color
    buf += struct.pack("<H", 0)
    buf += struct.pack("<H", len(text))
    buf += struct.pack("<H", 0)
    buf += chars + b"\x00\x00"
    return pack_packet(1, msg_id, bytes(buf))

def pack_resource_screen(msg_id: int, cid: int, cids: list[int]) -> bytes:
    payload_size = 34 + (len(cids) * 4)
    data_len = payload_size - 20
    
    buf = bytearray()
    buf += struct.pack("<II", cid, 0)
    buf += struct.pack("<HH", 1, 0)
    buf += struct.pack("<I", data_len)
    buf += struct.pack("<III", 0, 0, 0)
    buf += struct.pack("<HH", 11, 0)                 # subType = 11 (Screen)
    buf += struct.pack("<H", len(cids))
    for sub_cid in cids:
        buf += struct.pack("<I", sub_cid)
    return pack_packet(1, msg_id, bytes(buf))

def pack_request_notification_list(msg_id: int, extension_cid: int) -> bytes:
    payload = struct.pack("<I", extension_cid)
    return pack_packet(135, msg_id, payload)

def pack_resource_extension(msg_id: int, cid: int, name_cid: int, icon_cid: int) -> bytes:
    data_len = 28
    buf = bytearray()
    buf += struct.pack("<II", cid, 0)                 # 0x0c: cid, 0x10: pad
    buf += struct.pack("<HH", 1, 0)                   # 0x14: hasCid = 1
    buf += struct.pack("<I", data_len)                # 0x18: dataLength = 28
    buf += struct.pack("<HH", 4, 0)                  # 0x1c: subType = 4 (Extension)
    buf += struct.pack("<III", name_cid, icon_cid, icon_cid)  # 0x20: name, 0x24: icon, 0x28: offline icon
    buf += struct.pack("<i", -16777216)               # 0x2c: color
    buf += struct.pack("<H", 4)                       # 0x30: priority = 4 (NOTIFICATION_MSG)
    buf += struct.pack("<B", 1)                       # 0x32: capabilities = 1 (NOTIFICATION)
    buf += struct.pack("<B", 0)                       # 0x33: supportedDrawingSpecs = 0
    buf += struct.pack("<I", 1)                       # 0x34: showOnHomeScreen = 1
    buf += struct.pack("<i", -1)                      # 0x38: relativeCid = -1
    return pack_packet(1, msg_id, bytes(buf))

def pack_resource_notification(msg_id: int, cid: int, extension_cid: int, title_cid: int,
                               msg_cid: int, disp_name_cid: int, status_icon_cid: int,
                               rich_icon_cid: int, rich_image_cid: int,
                               overview_screen_cid: int, detail_screen_cid: int) -> bytes:
    fields = [
        (0, title_cid),
        (1, disp_name_cid),
        (2, msg_cid),
        (3, status_icon_cid),
        (4, rich_icon_cid),
        (14, rich_image_cid),
        (17, 0) # bitmapRendering = 0
    ]
    subscreens = [
        (0, 0, 219, 175, 2, 0, overview_screen_cid),
        (0, 0, 219, 175, 2, 0, detail_screen_cid)
    ]
    subscreen_count = len(subscreens)
    total_size = 306 + (subscreen_count * 16) # 338 bytes
    data_len = total_size - 32 # 306 bytes (exact per libprotocol.so)
    timestamp_ms = int(time.time() * 1000)

    buf = bytearray()
    buf += struct.pack("<II", cid, 0)
    buf += struct.pack("<HH", 1, 0)
    buf += struct.pack("<I", data_len)
    buf += struct.pack("<HH", 2, 0)                  # subType = 2 (Notification)
    buf += struct.pack("<II", 0, extension_cid)
    buf += struct.pack("<Q", timestamp_ms)
    buf += struct.pack("<BB", 0, 0)                  # personal=0, readStatus=0
    buf += struct.pack("<H", len(fields))

    buf += struct.pack("<iH", -1, 0) + (b"\x00" * 48)
    buf += struct.pack("<iH", -1, 0) + (b"\x00" * 48)

    for key, val in fields:
        buf += struct.pack("<HHi", key, 0, val)
    remaining = 18 - len(fields)
    buf += b"\x00" * (remaining * 8)

    buf += struct.pack("<H", subscreen_count)
    for l, t, r, b, op, pad, s_cid in subscreens:
        buf += struct.pack("<HHHHHHi", l, t, r, b, op, pad, s_cid)

    return pack_packet(1, msg_id, bytes(buf))

def parse_packets(raw_bytes: bytes):
    buf = bytearray(raw_bytes)
    while len(buf) >= 12:
        pkt_len, pkt_type, pkt_id = struct.unpack("<III", buf[:12])
        if pkt_len < 12 or pkt_len > 65536 or len(buf) < pkt_len:
            break
        pkt = buf[:pkt_len]
        buf = buf[pkt_len:]
        name = TYPE_NAMES.get(pkt_type, f"TYPE_{pkt_type}")
        payload = pkt[12:]
        info = ""
        if pkt_type == 9: # ACK
            ack_id = struct.unpack("<I", payload[:4])[0] if len(payload) >= 4 else -1
            info = f"ACK for Msg #{ack_id}"
        elif pkt_type == 10: # NACK
            nack_id = struct.unpack("<I", payload[:4])[0] if len(payload) >= 4 else -1
            info = f"[!] NACK for Msg #{nack_id}"
        elif pkt_type == 5:
            fw = payload[4:].decode("utf-8", errors="ignore").strip("\x00")
            info = f"Firmware: {fw}"
        elif pkt_type == 124: # DEBUG_LOG
            log_msg = payload[12:].decode("latin1", errors="ignore").strip("\x00")
            info = f"DEBUG LOG: {log_msg}"
        elif pkt_type == 137: # ERROR_IND
            if len(payload) >= 6:
                err_code, err_val = struct.unpack("<HI", payload[:6])
                info = f"[!] ERROR_IND: ErrCode={err_code}, ErrVal=0x{err_val:08x} ({err_val}) (raw: {payload.hex()})"
            else:
                info = f"[!] ERROR_IND: {payload.hex()}"
        elif pkt_type == 136: # NOTIFICATION_LIST_RESP
            if len(payload) >= 8:
                ext_cid, notif_count = struct.unpack("<II", payload[:8])
                info = f"NOTIFICATION_LIST_RESP: ExtCid=0x{ext_cid:08x}, Count={notif_count}"
            else:
                info = f"NOTIFICATION_LIST_RESP: {payload.hex()}"
        elif pkt_type == 131: # INDICATION_OPEN_RESOURCE
            if len(payload) >= 4:
                res_cid = struct.unpack("<I", payload[:4])[0]
                info = f"INDICATION_OPEN_RESOURCE: ResourceCid=0x{res_cid:08x}"
            else:
                info = f"INDICATION_OPEN_RESOURCE: {payload.hex()}"
        elif pkt_type == 112: # INDICATION_NOTIFICATION
            action, ev_id = struct.unpack("<HI", payload[:6]) if len(payload) >= 6 else (-1, -1)
            info = f"IndicationNotification: action={action}, eventId={ev_id}"
        else:
            info = f"Payload: {payload.hex()}"
        print(f"  [RX] {name:<22} (len={pkt_len:3d}, id={pkt_id}) => {info}")

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
        print(f"\n[+] WATCH CONNECTED from {sock.information.remote_host_name.display_name}!", flush=True)
        loop.call_soon_threadsafe(connected.set)

    listener.add_connection_received(on_conn)
    await listener.bind_service_name_async(provider.service_id.as_string())
    provider.start_advertising(listener, True)
    
    print("[*] Advertising Costanza SDP service...", flush=True)
    await connected.wait()
    
    writer = DataWriter(sock.output_stream)
    reader = DataReader(sock.input_stream)
    reader.input_stream_options = InputStreamOptions.PARTIAL
    
    # Continuous reader task
    async def read_loop():
        while True:
            try:
                count = await reader.load_async(1024)
                if count > 0:
                    raw = bytearray(count)
                    reader.read_bytes(raw)
                    parse_packets(raw)
            except Exception as e:
                break

    read_task = asyncio.create_task(read_loop())
    
    msg_id = 1
    
    async def send_pkt(name, pkt):
        nonlocal msg_id
        t_len, t_type, t_id = struct.unpack("<III", pkt[:12])
        type_name = TYPE_NAMES.get(t_type, f"TYPE_{t_type}")
        print(f"[TX] {type_name:<20} MsgId #{t_id} (len={t_len}) - {name}")
        writer.write_bytes(pkt)
        await writer.store_async()
        await writer.flush_async()
        msg_id += 1
        await asyncio.sleep(0.06)

    # 1. Version Request
    await send_pkt("VERSION_REQ", struct.pack("<III", 12, 4, msg_id))
    # 2. Set Time
    time_payload = struct.pack("<Q", int(time.time() * 1000))
    await send_pkt("SET_TIME", pack_packet(22, msg_id, time_payload))
    # 3. Set Locale (English)
    await send_pkt("SET_LOCALE", pack_packet(16, msg_id, struct.pack("<I", 0)))

    # Extension Setup
    await send_pkt("EXT_NAME", pack_resource_string(msg_id, CID_NOTIFICATION_EXTENSION_NAME, "Rubi SW2", 220, 28))
    icon48 = render_teal_icon(48, 48)
    await send_pkt("EXT_ICON_48", pack_resource_compressed_image(msg_id, CID_NOTIFICATION_EXTENSION_ICON, 48, 48, icon48))
    await send_pkt("RES_EXT", pack_resource_extension(msg_id, CID_NOTIFICATION_EXTENSION, CID_NOTIFICATION_EXTENSION_NAME, CID_NOTIFICATION_EXTENSION_ICON))
    await send_pkt("REQ_EXT_INSTALL", pack_packet(104, msg_id, struct.pack("<HI", 0, CID_NOTIFICATION_EXTENSION)))

    # RequestNotificationList (Type 135) - Sync Watch DB!
    await send_pkt("REQ_NOTIF_LIST_135", pack_request_notification_list(msg_id, CID_NOTIFICATION_EXTENSION))

    # Notification Resources
    overview_screen_cid = CID_NOTIFICATION_SCREEN
    detail_screen_cid = CID_DYNAMIC_BASE + 801
    await send_pkt("NOTIF_TITLE", pack_resource_string(msg_id, CID_NOTIFICATION_TITLE, "Test Alarm", 220, 36))
    await send_pkt("NOTIF_DISP_NAME", pack_resource_string(msg_id, CID_NOTIFICATION_DISPLAY_NAME, "Rubi SW2", 220, 28))
    await send_pkt("NOTIF_MESSAGE", pack_resource_string(msg_id, CID_NOTIFICATION_MESSAGE, "Hallo von Windows 11!", 220, 120))
    
    # Overview Subscreen (Card Preview + Title) & Detail Subscreen (Message + Title)
    await send_pkt("NOTIF_OVERVIEW_SCREEN", pack_resource_screen(msg_id, overview_screen_cid, [CID_NOTIFICATION_IMAGE, CID_NOTIFICATION_TITLE]))
    await send_pkt("NOTIF_DETAIL_SCREEN", pack_resource_screen(msg_id, detail_screen_cid, [CID_NOTIFICATION_MESSAGE, CID_NOTIFICATION_TITLE]))
    
    icon20 = render_teal_icon(20, 20)
    await send_pkt("ICON_STATUS_20", pack_resource_compressed_image(msg_id, CID_NOTIFICATION_ICON_STATUS, 20, 20, icon20))
    icon40 = render_teal_icon(40, 40)
    await send_pkt("ICON_RICH_40", pack_resource_compressed_image(msg_id, CID_NOTIFICATION_ICON_RICH, 40, 40, icon40))
    card = render_white_card(196, 100)
    await send_pkt("CARD_PREVIEW", pack_resource_compressed_image(msg_id, CID_NOTIFICATION_IMAGE, 196, 100, card))

    notif_res = pack_resource_notification(
        msg_id=msg_id,
        cid=CID_NOTIFICATION_BASE,
        extension_cid=CID_NOTIFICATION_EXTENSION,
        title_cid=CID_NOTIFICATION_TITLE,
        msg_cid=CID_NOTIFICATION_MESSAGE,
        disp_name_cid=CID_NOTIFICATION_DISPLAY_NAME,
        status_icon_cid=CID_NOTIFICATION_ICON_STATUS,
        rich_icon_cid=CID_NOTIFICATION_ICON_RICH,
        rich_image_cid=CID_NOTIFICATION_IMAGE,
        overview_screen_cid=overview_screen_cid,
        detail_screen_cid=detail_screen_cid
    )
    await send_pkt("RES_NOTIFICATION_DUAL_SUBSCREENS", notif_res)
    await send_pkt("REQ_NOTIFICATION_ADD", pack_packet(110, msg_id, struct.pack("<HI", 0, CID_NOTIFICATION_BASE)))

    # Vibration
    vib = struct.pack("<HHHH", 0, 400, 200, 2)
    await send_pkt("VIBRATION", pack_packet(129, msg_id, vib))

    print("\n[*] All packets sent! Monitoring responses for 15 seconds...", flush=True)
    await asyncio.sleep(15.0)

    read_task.cancel()
    provider.stop_advertising()
    listener.close()
    sock.close()
    print("[*] Done!")

if __name__ == "__main__":
    asyncio.run(main())
