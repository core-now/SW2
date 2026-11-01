<div align="center">
  <img src="splash_image.svg" alt="Sony SmartWatch 2" width="600"/>
</div>

# Sony SmartWatch 2 (SW2) Reverse Engineering & Protocol Lab (Windows 11)

This repository (`SWRE`) provides a standalone environment for direct Bluetooth communication, packet capture, analysis, and reverse engineering of the Sony Costanza protocol on Windows 11 — **completely bypassing Android build chains and bridges**.

---

## 🚀 Quick Start on Windows 11

### 1. Pair SmartWatch 2
1. Power on or reset the SmartWatch 2 (long press power button) until the pairing screen ("Waiting for phone / Smart Connect") is displayed.
2. In Windows 11, open: **Settings -> Bluetooth & devices -> Add device -> Bluetooth**.
3. Select SmartWatch 2 and pair (PIN is typically `0000` or confirmed automatically).

### 2. Choose Connection Mode

Windows 11 supports three distinct modes to communicate with the watch:

#### Option 1: Native Windows 11 RFCOMM Server (Zero-Config, Recommended) ⭐
The Sony SmartWatch 2 functions as a Bluetooth client and actively searches for the Sony Costanza SDP Service UUID (`8E780522-3C51-11E1-8D8D-001CC4D601D8`). The controller spins up this service advertisement directly via native Windows 11 WinRT APIs:
```cmd
python sw2_controller.py
```
*No manual COM port or MAC address configuration required. As soon as the watch is within range, it connects automatically in 1–2 seconds.*

#### Option 2: Virtual COM Port (Classic)
Connect via a paired Bluetooth Serial Port (COM) configured in Windows:
```cmd
python sw2_controller.py --com COM3
```

#### Option 3: Native Windows Bluetooth RFCOMM Client Socket
Connect directly to the device's Bluetooth MAC address:
```cmd
python sw2_controller.py --mac B4:52:7D:F3:E8:81 --channel 1
```

---

## 📦 Setup & Dependencies

The scripts run on standard Python 3. For serial COM support and optional bitmap processing:
```cmd
pip install -r requirements.txt
```
*(Note: Both `sw2_controller.py` and `telegram_helper.py` include pure-Python fallbacks and run out-of-the-box without third-party dependencies).*

---

## 🎮 Interactive Console (`sw2_controller.py`)

Upon connection, the controller automatically completes the full Sony initialization sequence (version query, FOTA reset, real-time clock sync, locale sync, and extension registration).

An interactive terminal menu is then presented:
```text
======================================================
  Sony SmartWatch 2 Reverse Engineering Controller
======================================================
  [1] Synchronize time
  [2] Test vibration pattern
  [3] Send test notification (title & body)
  [4] Re-register Notification Extension
  [5] Query status (FOTA & version)
  [q] Quit
------------------------------------------------------
```

All incoming watch packets (ACKs, NACKs, touch events, swipe gestures, hardware buttons, and sensor reports) are **received, decoded, and logged in real-time as plaintext and hex dumps via a dedicated background thread**.

---

## 🔬 Protocol Layout & Byte-Offset Reference (Step 24)

Notifications (`ResourceNotification`) follow the exact **322-byte binary layout** reconstructed from `libprotocol.so`:

```text
Offset       Length   Description
-----------------------------------------------------------------------------------
0x00..0x03    4 B     Total packet length (Little-Endian: 322 or 0x0142)
0x04..0x07    4 B     Packet type (TYPE_RESOURCE = 1)
0x08..0x0B    4 B     Message sequence ID
0x0C..0x0F    4 B     Resource CID (e.g. 0x00100064)
0x10..0x13    4 B     Padding (Zeroes)
0x14..0x15    2 B     hasCid = 1
0x16..0x17    2 B     Padding
0x18..0x1B    4 B     dataLength (totalSize - 20 = 302 or 0x012E)
0x1C..0x1D    2 B     subType = 2 (Notification)
0x1E..0x1F    2 B     Padding
0x20..0x23    4 B     localization = 0
0x24..0x27    4 B     notificationExtensionCid (CID_NOTIFICATION_EXTENSION)
0x28..0x2F    8 B     timeStamp (Unix timestamp in milliseconds)
0x30          1 B     personal = 0
0x31          1 B     readStatus = 0
0x32..0x33    2 B     fieldCount (e.g. 7 or 8)
0x34..0x69   54 B     mOnlineMenu (menuCid = -1, count = 0, 48B zeroes)
0x6A..0x9F   54 B     mOfflineMenu (menuCid = -1, count = 0, 48B zeroes)
0xA0..0x12F 144 B     mFields table (18 slots à 8 bytes: short key, short pad, int value)
0x130..0x131  2 B     subScreenCount (1 if screenCid present, otherwise 0)
0x132..0x141 16 B     mSubScreens (left=0, top=0, right=219, bottom=175, opaque=2, pad=0, screenCid)
-----------------------------------------------------------------------------------
Total Size:           322 Bytes (when subScreenCount = 1)
```

### ResourceExtension Layout (60 Bytes)
To register an application or notification extension, the SmartWatch 2 firmware expects this precise 60-byte payload:
```text
Offset       Length   Description
-----------------------------------------------------------------------------------
0x00..0x03    4 B     Total packet length (60 or 0x003C)
0x04..0x07    4 B     Packet type (TYPE_RESOURCE = 1)
0x08..0x0B    4 B     Message sequence ID
0x0C..0x0F    4 B     Extension CID (e.g. 0x00100002)
0x10..0x13    4 B     Padding (Zeroes)
0x14..0x15    2 B     hasCid = 1
0x16..0x17    2 B     Padding
0x18..0x1B    4 B     dataLength (totalSize - 32 = 28 or 0x001C)
0x1C..0x1D    2 B     subType = 4 (ResourceExtension on the wire)
0x1E..0x1F    2 B     Padding
0x20..0x23    4 B     nameCid (String Resource CID)
0x24..0x27    4 B     iconCid (48x48 Image CID)
0x28..0x2B    4 B     iconOfflineCid (48x48 Image CID)
0x2C..0x2F    4 B     color (e.g. 0xFF000000 or -16777216)
0x30..0x31    2 B     priority (4 = APPLICATION_ALERT_PRIORITY_NOTIFICATION_MSG)
0x32          1 B     capabilities = 1 (Bit 0: Notification API enabled)
0x33          1 B     supportedDrawingSpecs = 0
0x34..0x37    4 B     showOnHomeScreen (1 = True, 0 = False)
0x38..0x3B    4 B     relativeCid = -1
-----------------------------------------------------------------------------------
Total Size:           60 Bytes
```

### Image & Icon Compression (Raw DEFLATE)
> [!IMPORTANT]
> The SmartWatch 2 firmware requires compressed images (`subType = 5`, `compressionType = 4`) in **pure Raw-DEFLATE** (RFC 1951) **without** zlib headers or Adler32 checksum footers (`wbits=-15`).
> Standard Python `zlib.compress()` prepends the RFC 1950 header (`0x78 0x9C`), which causes the watch to immediately return a **NACK**. Always compress using:
> ```python
> compressor = zlib.compressobj(wbits=-15)
> deflated = compressor.compress(raw_rgb565) + compressor.flush()
> ```

### Packet Fragmentation
Packets exceeding 512 bytes (such as full-screen bitmaps) are automatically fragmented by `CostanzaCodec.fragment_packet()` into **Type 11 (TYPE_FRAGMENT)** frames with a maximum payload of 492 bytes each.

---

## 📂 Reference Implementations (`reference/`)

The `reference/` directory contains disassembled and decompiled reference files for protocol verification:
- `GeneralSyncManager.java`: Sony's original database and notification synchronization logic
- `CommunicationManager.java`: Original connection management, RFCOMM socket lifecycle, and handshake flow
- `ResourceNotification.java`, `ResourceExtension.java`, `ResourceString.java`, `ResourceScreen.java`
- `RequestNotification.java`, `RequestExtension.java`, `Types.java`, `costanza.xml`
- `libprotocol.so`: Original ARM shared library extracted from the SmartWatch 2 firmware
- `CostanzaPacketCodec.kt`, `CostanzaBluetoothManager.kt`, `CostanzaConstants.kt`: Kotlin sources from the Android companion service

---

## 🤖 Telegram Bot Helper (`telegram_helper.py`)

For remote notification forwarding, automated test alerts, or dispatching log captures directly to a mobile device:
- Refer to [TELEGRAM_BOT.md](TELEGRAM_BOT.md)
- Quick command example:
  ```cmd
  python telegram_helper.py --text "Notification test successful!"
  ```
