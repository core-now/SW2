#!/usr/bin/env python3
"""
Sony SmartWatch 2 (SW2) Protocol Controller & Reverse Engineering Tool
=====================================================================
Direct PC-to-SmartWatch 2 communication for rapid prototyping, protocol analysis,
and notification layout testing on Windows 11 / Linux.

Features:
- Dual Transport: Windows Native Bluetooth RFCOMM Sockets (AF_BLUETOOTH) OR Virtual COM Ports (pyserial)
- Exact Sony Costanza Binary Protocol Implementation:
  * 12-Byte Little-Endian Packet Headers
  * Automatic Fragmentation (Type 11, max 492 bytes payload for packets > 512 bytes)
  * Full Handshake Sequence (Version, Locale, Time-Sync, Extension Registration)
  * Step 24 Notification Layout (322-Byte Frame, 18-Slot Field Table, 0x130 SubScreens)
  * Real-time packet capture, hex-dumping, and symbolic decoding
- Interactive Console CLI for live testing without recompiling an Android APK
"""

import argparse
import datetime
import io
import os
import struct
import sys
import threading
import time
import zlib

# Try importing pyserial and PIL (optional but recommended)
try:
    import serial
    HAVE_SERIAL = True
except ImportError:
    HAVE_SERIAL = False

try:
    from PIL import Image, ImageDraw, ImageFont
    HAVE_PIL = True
except ImportError:
    HAVE_PIL = False

try:
    import asyncio
    import uuid as _uuid
    from winrt.windows.devices.bluetooth.rfcomm import RfcommServiceProvider, RfcommServiceId
    from winrt.windows.networking.sockets import StreamSocketListener
    from winrt.windows.storage.streams import DataReader, DataWriter, InputStreamOptions
    HAVE_WINRT = True
except ImportError:
    HAVE_WINRT = False

# ==============================================================================
# Constants & Protocol Definitions
# ==============================================================================

# SDP RFCOMM Service UUIDs
COSTANZA_SERVER_NAME = "Constanza"
COSTANZA_SERVER_UUID = "8E780522-3C51-11E1-8D8D-001CC4D601D8"
COSTANZA_CLIENT_UUID = "8E780523-3C51-11E1-8D8D-001CC4D601D8"

HEADER_SIZE = 12

# Packet Types
TYPE_RESOURCE = 1
TYPE_VERSION_REQ = 4
TYPE_VERSION_RSP = 5
TYPE_REQUEST_FOTA = 6
TYPE_RESPONSE_FOTA = 7
TYPE_ACK = 9
TYPE_NACK = 10
TYPE_FRAGMENT = 11
TYPE_SET_LOCALE_REQ = 16
TYPE_BATTERY_LEVEL_IND = 18
TYPE_BATTERY_LEVEL_CNF = 19
TYPE_FACTORY_RESET_REQ = 20
TYPE_FACTORY_RESET_RSP = 21
TYPE_SET_TIME_REQ = 22
TYPE_EXTENSION_REQ = 104
TYPE_EXTENSION_RSP = 105
TYPE_CONTROL_REQ = 106
TYPE_CONTROL_RSP = 107
TYPE_CONTROL_IND = 108
TYPE_REQUEST_NOTIFICATION = 110
TYPE_INDICATION_NOTIFICATION = 112
TYPE_INDICATION_KEY = 114
TYPE_INDICATION_TOUCH = 115
TYPE_INDICATION_SWIPE = 116
TYPE_SENSOR_DATA_CONFIG_REQ = 125
TYPE_SENSOR_DATA_CONFIG_RSP = 126
TYPE_SENSOR_DATA_IND = 127
TYPE_SENSOR_DATA_CNF = 128
TYPE_REQUEST_VIBRATION = 129
TYPE_REQUEST_SYNC_TIME = 130
TYPE_SET_TIME_ZONE_REQ = 133
TYPE_NOTIFICATION_READ_IND = 134
TYPE_NOTIFICATION_LIST_REQ = 135
TYPE_NOTIFICATION_LIST_RESP = 136
TYPE_ERROR_IND = 137
TYPE_NOTIFICATION_ALL_READ_IND = 141
TYPE_REQUEST_WALLPAPER = 150
TYPE_RESPONSE_WALLPAPER = 151
TYPE_INDICATION_WALLPAPER = 152

TYPE_NAMES = {
    TYPE_RESOURCE: "RESOURCE",
    TYPE_VERSION_REQ: "VERSION_REQ",
    TYPE_VERSION_RSP: "VERSION_RSP",
    TYPE_REQUEST_FOTA: "REQUEST_FOTA",
    TYPE_RESPONSE_FOTA: "RESPONSE_FOTA",
    TYPE_ACK: "ACK",
    TYPE_NACK: "NACK",
    TYPE_FRAGMENT: "FRAGMENT",
    TYPE_SET_LOCALE_REQ: "SET_LOCALE_REQ",
    TYPE_BATTERY_LEVEL_IND: "BATTERY_LEVEL_IND",
    TYPE_BATTERY_LEVEL_CNF: "BATTERY_LEVEL_CNF",
    TYPE_FACTORY_RESET_REQ: "FACTORY_RESET_REQ",
    TYPE_FACTORY_RESET_RSP: "FACTORY_RESET_RSP",
    TYPE_SET_TIME_REQ: "SET_TIME_REQ",
    TYPE_EXTENSION_REQ: "EXTENSION_REQ",
    TYPE_EXTENSION_RSP: "EXTENSION_RSP",
    TYPE_CONTROL_REQ: "CONTROL_REQ",
    TYPE_CONTROL_RSP: "CONTROL_RSP",
    TYPE_CONTROL_IND: "CONTROL_IND",
    TYPE_REQUEST_NOTIFICATION: "REQUEST_NOTIFICATION",
    TYPE_INDICATION_NOTIFICATION: "INDICATION_NOTIFICATION",
    TYPE_INDICATION_KEY: "INDICATION_KEY",
    TYPE_INDICATION_TOUCH: "INDICATION_TOUCH",
    TYPE_INDICATION_SWIPE: "INDICATION_SWIPE",
    TYPE_SENSOR_DATA_CONFIG_REQ: "SENSOR_DATA_CONFIG_REQ",
    TYPE_SENSOR_DATA_CONFIG_RSP: "SENSOR_DATA_CONFIG_RSP",
    TYPE_SENSOR_DATA_IND: "SENSOR_DATA_IND",
    TYPE_SENSOR_DATA_CNF: "SENSOR_DATA_CNF",
    TYPE_REQUEST_VIBRATION: "REQUEST_VIBRATION",
    TYPE_REQUEST_SYNC_TIME: "REQUEST_SYNC_TIME",
    TYPE_SET_TIME_ZONE_REQ: "SET_TIME_ZONE_REQ",
    TYPE_NOTIFICATION_READ_IND: "NOTIFICATION_READ_IND",
    TYPE_NOTIFICATION_LIST_REQ: "NOTIFICATION_LIST_REQ",
    TYPE_NOTIFICATION_LIST_RESP: "NOTIFICATION_LIST_RESP",
    TYPE_ERROR_IND: "ERROR_IND",
    TYPE_NOTIFICATION_ALL_READ_IND: "NOTIFICATION_ALL_READ_IND",
    TYPE_REQUEST_WALLPAPER: "REQUEST_WALLPAPER",
    TYPE_RESPONSE_WALLPAPER: "RESPONSE_WALLPAPER",
    TYPE_INDICATION_WALLPAPER: "INDICATION_WALLPAPER",
}

# Resource CID Types
CID_TYPE_RESOURCE_STRING = 1
CID_TYPE_RESOURCE_SCREEN = 4
CID_TYPE_RESOURCE_COMPRESSED_IMAGE = 6
CID_TYPE_RESOURCE_NOTIFICATION = 7
CID_TYPE_RESOURCE_IMAGE = 8
CID_TYPE_RESOURCE_EXTENSION = 11

# Notification Layout Keys (Sony ResourceNotification.java)
NOTIFICATION_KEY_TITLE = 0
NOTIFICATION_KEY_DISPLAY_NAME = 1
NOTIFICATION_KEY_MESSAGE = 2
NOTIFICATION_KEY_STATUS_BAR_ICON = 3
NOTIFICATION_KEY_RICH_ICON = 4
NOTIFICATION_KEY_CONTACT_ICON = 5
NOTIFICATION_KEY_IMAGE = 6
NOTIFICATION_KEY_WIDGET_COLOR_ICON = 7
NOTIFICATION_KEY_WIDGET_GRAYSCALE_ICON = 8
NOTIFICATION_KEY_TITLE_WHITE_IMAGE = 9
NOTIFICATION_KEY_TITLE_BOLD_IMAGE = 10
NOTIFICATION_KEY_DISPLAY_NAME_WHITE_IMAGE = 11
NOTIFICATION_KEY_DISPLAY_NAME_BOLD_IMAGE = 12
NOTIFICATION_KEY_DISPLAY_NAME_DRAWER = 13
NOTIFICATION_KEY_MESSAGE_WHITE_IMAGE = 14
NOTIFICATION_KEY_MESSAGE_DRAWER_WHITE_IMAGE = 15
NOTIFICATION_KEY_MESSAGE_BOLD_IMAGE = 16
NOTIFICATION_KEY_BITMAP_RENDERING = 17

# Dynamic CIDs (>= 0x00100000)
CID_DYNAMIC_BASE = 0x00100000
CID_MEDIA_EXTENSION = CID_DYNAMIC_BASE + 1
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

SCREEN_WIDTH = 220
SCREEN_HEIGHT = 176

MAX_PACKET_SIZE = 512
MAX_FRAGMENT_PAYLOAD_SIZE = 492


# ==============================================================================
# Helper Functions: RGB565 & Bitmap Generation
# ==============================================================================

def to_rgb565(r: int, g: int, b: int) -> int:
    """Convert 8-bit RGB components to 16-bit RGB565 integer."""
    r5 = (r >> 3) & 0x1F
    g6 = (g >> 2) & 0x3F
    b5 = (b >> 3) & 0x1F
    return (r5 << 11) | (g6 << 5) | b5


def render_teal_icon(width: int, height: int) -> bytes:
    """
    Generate an RGB565 byte buffer for a rounded square Sony Teal (#00897B) icon.
    Works with or without PIL installed.
    """
    if HAVE_PIL:
        img = Image.new("RGB", (width, height), (0, 0, 0))
        draw = ImageDraw.Draw(img)
        corner_r = int(width * 0.22)
        # Sony Teal rounded rect
        draw.rounded_rectangle([0, 0, width - 1, height - 1], radius=corner_r, fill=(0, 137, 123))
        # Draw small white inner symbol (bell/envelope shape)
        cx, cy = width // 2, height // 2
        r = int(width * 0.22)
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(255, 255, 255), width=max(1, width // 20))
        
        buf = bytearray(width * height * 2)
        idx = 0
        pixels = img.load()
        for y in range(height):
            for x in range(width):
                pr, pg, pb = pixels[x, y]
                val = to_rgb565(pr, pg, pb)
                struct.pack_into("<H", buf, idx, val)
                idx += 2
        return bytes(buf)
    else:
        # Fallback pure-python: simple filled box in #00897B
        teal_565 = to_rgb565(0, 137, 123)
        buf = bytearray(width * height * 2)
        for i in range(0, len(buf), 2):
            struct.pack_into("<H", buf, i, teal_565)
        return bytes(buf)


def render_preview_card(title: str, text: str, width: int = 196, height: int = 100) -> bytes:
    """
    Render a 196x100 white card notification popup image in RGB565.
    """
    if HAVE_PIL:
        img = Image.new("RGB", (width, height), (255, 255, 255))
        draw = ImageDraw.Draw(img)
        
        # Header bar (Sony Teal)
        draw.rectangle([0, 0, width - 1, 24], fill=(0, 137, 123))
        # Title in white
        draw.text((6, 4), title[:24], fill=(255, 255, 255))
        # Message body in black
        draw.text((6, 32), text[:80], fill=(0, 0, 0))
        
        buf = bytearray(width * height * 2)
        idx = 0
        pixels = img.load()
        for y in range(height):
            for x in range(width):
                pr, pg, pb = pixels[x, y]
                val = to_rgb565(pr, pg, pb)
                struct.pack_into("<H", buf, idx, val)
                idx += 2
        return bytes(buf)
    else:
        # Pure python fallback: White card
        white_565 = 0xFFFF
        buf = bytearray(width * height * 2)
        for i in range(0, len(buf), 2):
            struct.pack_into("<H", buf, i, white_565)
        return bytes(buf)


# ==============================================================================
# Protocol Codec & Packet Builders
# ==============================================================================

class CostanzaCodec:
    @staticmethod
    def pack_packet(msg_type: int, msg_id: int, payload: bytes) -> bytes:
        total_len = HEADER_SIZE + len(payload)
        header = struct.pack("<III", total_len, msg_type, msg_id)
        return header + payload

    @staticmethod
    def fragment_packet(packet: bytes) -> list[bytes]:
        """
        Fragments packets > 512 bytes using Type 11 (TYPE_FRAGMENT) according to libprotocol.so.
        """
        if len(packet) <= MAX_PACKET_SIZE:
            return [packet]
        
        orig_len, orig_type, orig_msg_id = struct.unpack("<III", packet[:12])
        fragments = []
        offset = 0
        total_frags = (len(packet) + MAX_FRAGMENT_PAYLOAD_SIZE - 1) // MAX_FRAGMENT_PAYLOAD_SIZE

        for frag_idx in range(total_frags):
            chunk = packet[offset : offset + MAX_FRAGMENT_PAYLOAD_SIZE]
            offset += len(chunk)
            
            # Fragment Header: 20 bytes total (12 bytes standard header + 8 bytes frag info)
            frag_payload_len = 8 + len(chunk)
            frag_total_len = HEADER_SIZE + frag_payload_len
            
            # 0x00: length, 0x04: type = 11, 0x08: origMsgId
            header = struct.pack("<III", frag_total_len, TYPE_FRAGMENT, orig_msg_id)
            # 0x0c: fragIdx (short), 0x0e: fragTotal (short), 0x10: fragDataLen (int)
            frag_info = struct.pack("<HHI", frag_idx, total_frags, len(chunk))
            
            fragments.append(header + frag_info + chunk)

        return fragments

    @staticmethod
    def pack_request_version(msg_id: int) -> bytes:
        return CostanzaCodec.pack_packet(TYPE_VERSION_REQ, msg_id, b"")

    @staticmethod
    def pack_factory_reset(msg_id: int, action: int = 0, status: int = 0) -> bytes:
        # action (short), status (int)
        payload = struct.pack("<HI", action, status)
        return CostanzaCodec.pack_packet(TYPE_FACTORY_RESET_REQ, msg_id, payload)

    @staticmethod
    def pack_set_locale(msg_id: int, locale_id: int = 0) -> bytes:
        # English / Default = 0
        return CostanzaCodec.pack_packet(TYPE_SET_LOCALE_REQ, msg_id, struct.pack("<I", locale_id))

    @staticmethod
    def pack_set_time(msg_id: int, epoch_ms: int = None) -> bytes:
        if epoch_ms is None:
            now = datetime.datetime.now()
            # Local time in milliseconds since epoch
            epoch_ms = int(now.timestamp() * 1000)
        return CostanzaCodec.pack_packet(TYPE_SET_TIME_REQ, msg_id, struct.pack("<Q", epoch_ms))

    @staticmethod
    def pack_fota(msg_id: int, action: int = 3, value: int = 0) -> bytes:
        # ACTION_RESET = 3 (clears any accidental flash/loading mode)
        return CostanzaCodec.pack_packet(TYPE_REQUEST_FOTA, msg_id, struct.pack("<II", action, value))

    @staticmethod
    def pack_request_vibration(msg_id: int, delay_ms: int = 0, on_ms: int = 500, off_ms: int = 0, count: int = 1) -> bytes:
        # delay (short), on (short), off (short), count (short), pattern (short = 0)
        payload = struct.pack("<HHHHH", delay_ms, on_ms, off_ms, count, 0)
        return CostanzaCodec.pack_packet(TYPE_REQUEST_VIBRATION, msg_id, payload)

    @staticmethod
    def pack_request_extension(msg_id: int, action: int, extension_cid: int) -> bytes:
        # action (short: 0=INSTALL, 1=UNINSTALL), value (int: CID)
        payload = struct.pack("<HI", action, extension_cid)
        return CostanzaCodec.pack_packet(TYPE_EXTENSION_REQ, msg_id, payload)

    @staticmethod
    def pack_request_notification_list(msg_id: int, extension_cid: int) -> bytes:
        """
        RequestNotificationList (Type 135)
        Wire format:
        0x00..0x03: 16 (Total Length)
        0x04..0x07: 135 (TYPE_NOTIFICATION_LIST_REQ)
        0x08..0x0B: msg_id
        0x0C..0x0F: extension_cid
        """
        payload = struct.pack("<I", extension_cid)
        return CostanzaCodec.pack_packet(TYPE_NOTIFICATION_LIST_REQ, msg_id, payload)

    @staticmethod
    def pack_request_notification(msg_id: int, action: int, notification_cid: int) -> bytes:
        # action (short: 0=ADDED, 1=DELETED), value (int: CID)
        payload = struct.pack("<HI", action, notification_cid)
        return CostanzaCodec.pack_packet(TYPE_REQUEST_NOTIFICATION, msg_id, payload)

    @staticmethod
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
        return CostanzaCodec.pack_packet(TYPE_RESOURCE, msg_id, bytes(buf))

    @staticmethod
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
        return CostanzaCodec.pack_packet(TYPE_RESOURCE, msg_id, bytes(buf))

    @staticmethod
    def pack_resource_screen(msg_id: int, cid: int, cids: list[int]) -> bytes:
        payload_size = 34 + (len(cids) * 4)
        data_len = payload_size - 20
        
        buf = bytearray()
        buf += struct.pack("<II", cid, 0)
        buf += struct.pack("<HH", 1, 0)
        buf += struct.pack("<I", data_len)
        buf += struct.pack("<III", 0, 0, 0)
        buf += struct.pack("<HH", 7, 0)                  # subType = 7 (Screen)
        buf += struct.pack("<HH", 0, 0)
        buf += struct.pack("<HH", 219, 175)
        buf += struct.pack("<H", len(cids))
        for c in cids:
            buf += struct.pack("<I", c)
        return CostanzaCodec.pack_packet(TYPE_RESOURCE, msg_id, bytes(buf))

    @staticmethod
    def pack_resource_extension(msg_id: int, cid: int, name_cid: int = -1, icon_cid: int = -1,
                                icon_offline_cid: int = -1, capabilities: int = 1, show: bool = True) -> bytes:
        data_len = 28
        buf = bytearray()
        buf += struct.pack("<II", cid, 0)                 # 0x0c: cid, 0x10: pad
        buf += struct.pack("<HH", 1, 0)                   # 0x14: hasCid = 1
        buf += struct.pack("<I", data_len)                # 0x18: dataLength = 28
        buf += struct.pack("<HH", 4, 0)                   # 0x1c: subType = 4 (Extension)
        buf += struct.pack("<III", name_cid, icon_cid, icon_offline_cid if icon_offline_cid != -1 else icon_cid)
        buf += struct.pack("<i", -16777216)               # 0x2c: color
        buf += struct.pack("<H", 4)                       # 0x30: priority = 4 (NOTIFICATION_MSG)
        buf += struct.pack("<B", capabilities)            # 0x32: capabilities = 1 (NOTIFICATION)
        buf += struct.pack("<B", 0)                       # 0x33: supportedDrawingSpecs = 0
        buf += struct.pack("<I", 1 if show else 0)        # 0x34: showOnHomeScreen = 1
        buf += struct.pack("<i", -1)                      # 0x38: relativeCid = -1
        return CostanzaCodec.pack_packet(TYPE_RESOURCE, msg_id, bytes(buf))


    @staticmethod
    def pack_resource_notification(msg_id: int, cid: int, extension_cid: int, title_cid: int,
                                   msg_cid: int, disp_name_cid: int = -1, status_icon_cid: int = -1,
                                   rich_icon_cid: int = -1, rich_image_cid: int = -1,
                                   screen_cid: int = -1, overview_screen_cid: int = -1,
                                   detail_screen_cid: int = -1, subscreens: list = None,
                                   bitmap_rendering: int = 0,
                                   timestamp_ms: int = None) -> bytes:
        """
        Exact Disassembly Specification for libprotocol.so:
        - 0x18: dataLength = total_packet_size - 32 (e.g. 290 for 1 subscreen, 306 for 2 subscreens)
        - 0x32: fieldCount (Short)
        - 0x34..0x69 (54 bytes): mOnlineMenu (menuCid=-1, count=0, 48B pad)
        - 0x6a..0x9f (54 bytes): mOfflineMenu (menuCid=-1, count=0, 48B pad)
        - 0xa0..0x12f (144 bytes): 18 slots of (short key, short pad, int value)
        - 0x130..0x131 (2 bytes): subScreenCount
        - 0x132.. (16 bytes per subscreen): SubScreens (left, top, right, bottom, opaque=2, pad=0, screen_cid)
        """
        if timestamp_ms is None:
            timestamp_ms = int(time.time() * 1000)
            
        fields = []
        if title_cid != -1:
            fields.append((NOTIFICATION_KEY_TITLE, title_cid))
        if disp_name_cid != -1:
            fields.append((NOTIFICATION_KEY_DISPLAY_NAME, disp_name_cid))
        elif title_cid != -1:
            fields.append((NOTIFICATION_KEY_DISPLAY_NAME, title_cid))
        if msg_cid != -1:
            fields.append((NOTIFICATION_KEY_MESSAGE, msg_cid))
        if status_icon_cid != -1:
            fields.append((NOTIFICATION_KEY_STATUS_BAR_ICON, status_icon_cid))
        if rich_icon_cid != -1:
            fields.append((NOTIFICATION_KEY_RICH_ICON, rich_icon_cid))
        if rich_image_cid != -1:
            fields.append((NOTIFICATION_KEY_MESSAGE_WHITE_IMAGE, rich_image_cid))
        fields.append((NOTIFICATION_KEY_BITMAP_RENDERING, bitmap_rendering))

        if subscreens is not None:
            subscreen_list = list(subscreens)
        elif overview_screen_cid != -1 and detail_screen_cid != -1:
            subscreen_list = [
                (0, 0, 219, 175, 2, 0, overview_screen_cid),
                (0, 0, 219, 175, 2, 0, detail_screen_cid)
            ]
        elif screen_cid != -1:
            subscreen_list = [(0, 0, 219, 175, 2, 0, screen_cid)]
        else:
            subscreen_list = []

        subscreen_count = len(subscreen_list)
        total_packet_size = 306 + (subscreen_count * 16)
        data_len = total_packet_size - 32

        buf = bytearray()
        buf += struct.pack("<II", cid, 0)                # 0x0c: cid, pad
        buf += struct.pack("<HH", 1, 0)                  # 0x14: hasCid=1, pad
        buf += struct.pack("<I", data_len)               # 0x18: dataLength = total_packet_size - 32
        buf += struct.pack("<HH", 2, 0)                  # 0x1c: subType=2, pad
        buf += struct.pack("<II", 0, extension_cid)      # 0x20: localization=0, 0x24: extensionCid
        buf += struct.pack("<Q", timestamp_ms)           # 0x28: timestamp
        buf += struct.pack("<BB", 0, 0)                  # 0x30: personal=0, 0x31: readStatus=0
        buf += struct.pack("<H", len(fields))            # 0x32: fieldCount

        # 0x34..0x69 (54 bytes): mOnlineMenu
        buf += struct.pack("<iH", -1, 0)                 # menuCid = -1, count = 0
        buf += b"\x00" * 48

        # 0x6a..0x9f (54 bytes): mOfflineMenu
        buf += struct.pack("<iH", -1, 0)                 # menuCid = -1, count = 0
        buf += b"\x00" * 48

        # 0xa0..0x12f (144 bytes = 18 slots of 8 bytes): mFields
        for key, val in fields[:18]:
            buf += struct.pack("<HHi", key, 0, val)
        remaining_slots = 18 - len(fields[:18])
        if remaining_slots > 0:
            buf += b"\x00" * (remaining_slots * 8)

        # 0x130..0x131 (2 bytes): subScreenCount
        buf += struct.pack("<H", subscreen_count)

        # 0x132.. (16 bytes per subscreen): mSubScreens
        for sub in subscreen_list:
            if len(sub) == 7:
                l, t, r, b, op, pad, s_cid = sub
            elif len(sub) == 5:
                l, t, r, b, s_cid = sub
                op, pad = 2, 0
            else:
                l, t, r, b, op, pad, s_cid = 0, 0, 219, 175, 2, 0, sub[0]
            buf += struct.pack("<HHHHHHi", l, t, r, b, op, pad, s_cid)

        return CostanzaCodec.pack_packet(TYPE_RESOURCE, msg_id, bytes(buf))


# ==============================================================================
# Transports: Bluetooth RFCOMM Socket vs Virtual Serial COM Port
# ==============================================================================

class BaseTransport:
    def send(self, data: bytes):
        raise NotImplementedError
    def recv(self, max_bytes: int) -> bytes:
        raise NotImplementedError
    def close(self):
        raise NotImplementedError


class BluetoothSocketTransport(BaseTransport):
    def __init__(self, mac: str, channel: int = 1):
        import socket
        if not hasattr(socket, "AF_BLUETOOTH"):
            raise RuntimeError("Python on this system does not have AF_BLUETOOTH built-in.")
        
        print(f"[*] Connecting via native Bluetooth RFCOMM to {mac} on Channel {channel}...")
        self.sock = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
        self.sock.settimeout(10.0)
        self.sock.connect((mac, channel))
        self.sock.settimeout(None) # blocking for reader
        print("[+] Bluetooth RFCOMM socket connected!")

    def send(self, data: bytes):
        self.sock.sendall(data)

    def recv(self, max_bytes: int) -> bytes:
        return self.sock.recv(max_bytes)

    def close(self):
        try:
            self.sock.close()
        except Exception:
            pass


class SerialTransport(BaseTransport):
    def __init__(self, port: str, baudrate: int = 115200):
        if not HAVE_SERIAL:
            raise RuntimeError("pyserial is not installed! Run 'pip install pyserial'")
        
        print(f"[*] Opening Serial COM port {port} at {baudrate} baud...")
        self.ser = serial.Serial(port=port, baudrate=baudrate, timeout=1.0)
        print(f"[+] Serial port {port} opened successfully!")

    def send(self, data: bytes):
        self.ser.write(data)
        self.ser.flush()

    def recv(self, max_bytes: int) -> bytes:
        chunk = self.ser.read(max_bytes)
        return chunk

    def close(self):
        try:
            self.ser.close()
        except Exception:
            pass


class WinRtServerTransport(BaseTransport):
    """
    Native Windows 11 RFCOMM Server Transport using WinRT.
    Advertises the Sony Costanza SDP service UUID (8E780522-3C51-11E1-8D8D-001CC4D601D8),
    allowing the Sony SmartWatch 2 to automatically connect to this PC as a client.
    """
    def __init__(self, service_uuid: str = COSTANZA_SERVER_UUID):
        if not HAVE_WINRT:
            raise RuntimeError("WinRT modules not installed! Run 'pip install -r requirements.txt'")

        self.service_uuid = _uuid.UUID(service_uuid)
        self.loop = asyncio.new_event_loop()
        self.ready_event = threading.Event()
        self.connected_event = threading.Event()
        self.provider = None
        self.listener = None
        self.sock = None
        self.writer = None
        self.reader = None
        self.running = True

        self.thread = threading.Thread(target=self._run_loop, daemon=True)
        self.thread.start()
        self.ready_event.wait(5.0)

    def _run_loop(self):
        asyncio.set_event_loop(self.loop)
        self.loop.run_until_complete(self._start_server())
        self.loop.run_forever()

    async def _start_server(self):
        svc_id = RfcommServiceId.from_uuid(self.service_uuid)
        self.provider = await RfcommServiceProvider.create_async(svc_id)
        self.listener = StreamSocketListener()

        def on_conn(sender, args):
            self.sock = args.socket
            self.writer = DataWriter(self.sock.output_stream)
            self.reader = DataReader(self.sock.input_stream)
            self.reader.input_stream_options = InputStreamOptions.PARTIAL
            remote = self.sock.information.remote_host_name.display_name
            print(f"\n[+] SmartWatch 2 CONNECTED via WinRT RFCOMM ({remote})!\n", flush=True)
            self.connected_event.set()

        self.listener.add_connection_received(on_conn)
        await self.listener.bind_service_name_async(self.provider.service_id.as_string())
        self.provider.start_advertising(self.listener, True)
        print(f"[*] Native RFCOMM Server advertising '{COSTANZA_SERVER_NAME}' ({self.service_uuid})...", flush=True)
        print("[*] Waiting for SmartWatch 2 to connect...", flush=True)
        self.ready_event.set()

    def wait_for_connection(self, timeout: float = 30.0) -> bool:
        return self.connected_event.wait(timeout)

    def send(self, data: bytes):
        if not self.sock or not self.running:
            return
        fut = asyncio.run_coroutine_threadsafe(self._async_send(data), self.loop)
        try:
            fut.result(timeout=5.0)
        except Exception as e:
            if self.running:
                print(f"[!] WinRT send error: {e}")

    async def _async_send(self, data: bytes):
        self.writer.write_bytes(data)
        await self.writer.store_async()
        await self.writer.flush_async()

    def recv(self, max_bytes: int = 1024) -> bytes:
        if not self.sock or not self.running:
            time.sleep(0.05)
            return b""
        fut = asyncio.run_coroutine_threadsafe(self._async_recv(max_bytes), self.loop)
        try:
            return fut.result(timeout=2.0)
        except Exception:
            return b""

    async def _async_recv(self, max_bytes: int) -> bytes:
        count = await self.reader.load_async(max_bytes)
        if count > 0:
            raw = bytearray(count)
            self.reader.read_bytes(raw)
            return bytes(raw)
        return b""

    def close(self):
        self.running = False
        try:
            if self.provider:
                self.provider.stop_advertising()
            if self.listener:
                self.listener.close()
            if self.sock:
                self.sock.close()
            self.loop.call_soon_threadsafe(self.loop.stop)
        except Exception:
            pass


# ==============================================================================
# Controller Class
# ==============================================================================

class SmartWatch2Controller:
    def __init__(self, transport: BaseTransport):
        self.transport = transport
        self.msg_id = 1
        self.running = True
        self.rx_thread = threading.Thread(target=self._reader_loop, daemon=True)
        self.rx_thread.start()

    def next_msg_id(self) -> int:
        mid = self.msg_id
        self.msg_id += 1
        return mid

    def send_message(self, packet: bytes):
        frags = CostanzaCodec.fragment_packet(packet)
        for frag in frags:
            hex_head = frag[:12].hex()
            t_len, t_type, t_id = struct.unpack("<III", frag[:12])
            type_name = TYPE_NAMES.get(t_type, f"TYPE_{t_type}")
            print(f"[TX] {type_name:<20} (len={t_len:3d}, id={t_id}) [head: {hex_head}]")
            self.transport.send(frag)
            time.sleep(0.03)

    def _reader_loop(self):
        buffer = bytearray()
        while self.running:
            try:
                data = self.transport.recv(1024)
                if not data:
                    time.sleep(0.01)
                    continue
                buffer.extend(data)
                
                while len(buffer) >= HEADER_SIZE:
                    pkt_len, pkt_type, pkt_msg_id = struct.unpack("<III", buffer[:12])
                    
                    if pkt_len < HEADER_SIZE or pkt_len > 65536:
                        # Corrupted or out of sync - advance 1 byte
                        buffer.pop(0)
                        continue
                        
                    if len(buffer) < pkt_len:
                        # Need more data for complete packet
                        break
                        
                    packet = bytes(buffer[:pkt_len])
                    buffer = buffer[pkt_len:]
                    self._handle_packet(packet, pkt_len, pkt_type, pkt_msg_id)
            except Exception as e:
                if self.running:
                    print(f"\n[!] Reader thread exception: {e}")
                    time.sleep(0.5)

    def _handle_packet(self, packet: bytes, pkt_len: int, pkt_type: int, pkt_msg_id: int):
        type_str = TYPE_NAMES.get(pkt_type, f"TYPE_{pkt_type}")
        payload = packet[12:]
        print(f"\n[RX] {type_str:<22} (len={pkt_len:3d}, id={pkt_msg_id}) -> hex: {packet[:24].hex()}...")
        
        if pkt_type == TYPE_ACK:
            ack_id = struct.unpack("<I", payload[:4])[0] if len(payload) >= 4 else -1
            print(f"     => ACK for MsgId: {ack_id}")
        elif pkt_type == TYPE_NACK:
            nack_id = struct.unpack("<I", payload[:4])[0] if len(payload) >= 4 else -1
            print(f"     => [!] NACK for MsgId: {nack_id}")
        elif pkt_type == TYPE_VERSION_RSP:
            proto = struct.unpack("<I", payload[:4])[0] if len(payload) >= 4 else 0
            fw_str = payload[4:].decode("utf-8", errors="ignore").strip("\x00")
            print(f"     => Watch Firmware: '{fw_str}', Protocol Version: {proto}")
        elif pkt_type == TYPE_FACTORY_RESET_RSP:
            status = struct.unpack("<H", payload[:2])[0] if len(payload) >= 2 else -1
            print(f"     => FactoryReset status: {status}")
        elif pkt_type == TYPE_SENSOR_DATA_IND:
            if len(payload) >= 8:
                sensor_type, val_len = struct.unpack("<II", payload[:8])
                vals = [struct.unpack("<f", payload[8+i*4 : 12+i*4])[0] for i in range((len(payload)-8)//4)]
                if sensor_type == 3: # Battery
                    print(f"     => Battery Sensor: Level={vals[0] if vals else '?'}")
                else:
                    print(f"     => Sensor #{sensor_type} data: {vals}")
        elif pkt_type == TYPE_INDICATION_TOUCH:
            if len(payload) >= 8:
                action, x, y = struct.unpack("<HHI", payload[:8])
                print(f"     => Touch event: action={action}, x={x}, y={y}")
        elif pkt_type == TYPE_INDICATION_SWIPE:
            if len(payload) >= 2:
                direction = struct.unpack("<H", payload[:2])[0]
                print(f"     => Swipe event: direction={direction}")
        elif pkt_type == TYPE_INDICATION_KEY:
            if len(payload) >= 4:
                code, action = struct.unpack("<HH", payload[:4])
                print(f"     => Key event: code={code}, action={action}")
        elif pkt_type == 137:
            print(f"     => [!] ERROR_IND (137) Payload ({len(payload)}B): {payload.hex()}")
            if len(payload) >= 6:
                err_code, err_val = struct.unpack("<HI", payload[:6])
                print(f"        -> Decoded: ErrCode={err_code}, ErrVal=0x{err_val:08x} ({err_val})")
        elif pkt_type == 136:
            if len(payload) >= 8:
                ext_cid, notif_count = struct.unpack("<II", payload[:8])
                print(f"     => NOTIFICATION_LIST_RESP (136): ExtCid=0x{ext_cid:08x}, Count={notif_count}")
                offset = 8
                for idx in range(notif_count):
                    if offset + 6 <= len(payload):
                        notif_cid, read_st = struct.unpack("<IBx", payload[offset:offset+6])
                        print(f"        -> Notification Item #{idx+1}/{notif_count}: CID=0x{notif_cid:08x}, read={read_st}")
                        offset += 6
        elif pkt_type == 134:
            if len(payload) >= 5:
                notif_cid, read_st = struct.unpack("<IB", payload[:5])
                print(f"     => NOTIFICATION_READ_IND (134): notifCid=0x{notif_cid:08x}, read={read_st}")
        elif pkt_type == 141:
            if len(payload) >= 4:
                ext_cid = struct.unpack("<I", payload[:4])[0]
                print(f"     => NOTIFICATION_ALL_READ_IND (141): ExtCid=0x{ext_cid:08x}")
        elif pkt_type == 112:
            if len(payload) >= 6:
                act, ev_id = struct.unpack("<HI", payload[:6])
                print(f"     => INDICATION_NOTIFICATION (112): action={act}, eventId=0x{ev_id:08x}")
        elif pkt_type == 131:
            if len(payload) >= 4:
                res_cid = struct.unpack("<I", payload[:4])[0]
                print(f"     => INDICATION_OPEN_RESOURCE (131): ResourceCid=0x{res_cid:08x}")

    # ==========================================================================
    # High-Level Actions
    # ==========================================================================

    def perform_handshake(self):
        print("\n[*] Starting Sony SmartWatch 2 Handshake Sequence...")
        # 1. Version Request
        self.send_message(CostanzaCodec.pack_request_version(self.next_msg_id()))
        time.sleep(0.08)

        # 2. Factory Reset Info
        self.send_message(CostanzaCodec.pack_factory_reset(self.next_msg_id(), action=0, status=0))
        time.sleep(0.08)

        # 3. Clear accidental FOTA flash mode
        self.send_message(CostanzaCodec.pack_fota(self.next_msg_id(), action=3, value=0))
        time.sleep(0.08)

        # 4. Set Time
        self.sync_time()
        time.sleep(0.08)

        # 5. Set Locale (English/Default)
        self.send_message(CostanzaCodec.pack_set_locale(self.next_msg_id(), 0))
        time.sleep(0.08)

        # 6. Register Notification Extension
        self.register_notification_extension()
        print("[+] Handshake and Extension setup complete!\n")

    def sync_time(self):
        print("[*] Synchronizing Time...")
        pkt = CostanzaCodec.pack_set_time(self.next_msg_id())
        self.send_message(pkt)

    def test_vibration(self, count: int = 2, on_ms: int = 400):
        print(f"[*] Sending Test Vibration (count={count}, on={on_ms}ms)...")
        pkt = CostanzaCodec.pack_request_vibration(self.next_msg_id(), delay_ms=0, on_ms=on_ms, off_ms=200, count=count)
        self.send_message(pkt)

    def register_notification_extension(self):
        print("[*] Registering Notification Extension metadata & resource...")
        # Clean old registration
        self.send_message(CostanzaCodec.pack_request_extension(self.next_msg_id(), action=1, extension_cid=CID_NOTIFICATION_EXTENSION))
        time.sleep(0.05)

        # Extension Name string
        name_res = CostanzaCodec.pack_resource_string(self.next_msg_id(), CID_NOTIFICATION_EXTENSION_NAME, "SmartWatch 2", width=220, height=28)
        self.send_message(name_res)
        time.sleep(0.04)

        # 48x48 Extension App Icon
        icon_bytes = render_teal_icon(48, 48)
        icon_res = CostanzaCodec.pack_resource_compressed_image(self.next_msg_id(), CID_NOTIFICATION_EXTENSION_ICON, 48, 48, icon_bytes)
        self.send_message(icon_res)
        time.sleep(0.04)

        # ResourceExtension
        ext_res = CostanzaCodec.pack_resource_extension(
            msg_id=self.next_msg_id(),
            cid=CID_NOTIFICATION_EXTENSION,
            name_cid=CID_NOTIFICATION_EXTENSION_NAME,
            icon_cid=CID_NOTIFICATION_EXTENSION_ICON,
            icon_offline_cid=CID_NOTIFICATION_EXTENSION_ICON,
            capabilities=1,
            show=True # showOnHomeScreen = True (fuer Event-Historie)
        )
        self.send_message(ext_res)
        time.sleep(0.04)

        # RequestExtension INSTALL
        ext_req = CostanzaCodec.pack_request_extension(self.next_msg_id(), action=0, extension_cid=CID_NOTIFICATION_EXTENSION)
        self.send_message(ext_req)
        time.sleep(0.04)

        # RequestNotificationList (Typ 135) - Sync Watch Notification DB
        print("[*] Requesting Notification List (Type 135) from watch...")
        list_req = CostanzaCodec.pack_request_notification_list(self.next_msg_id(), extension_cid=CID_NOTIFICATION_EXTENSION)
        self.send_message(list_req)
        time.sleep(0.04)
        print("[+] Notification Extension registered and list sync requested.")

    def send_notification(self, title: str, message: str):
        print(f"\n[*] Sending Notification: '{title}' - '{message}'")
        notif_cid = CID_NOTIFICATION_BASE
        title_cid = CID_NOTIFICATION_TITLE
        msg_cid = CID_NOTIFICATION_MESSAGE
        disp_cid = CID_NOTIFICATION_DISPLAY_NAME
        overview_screen_cid = CID_NOTIFICATION_SCREEN
        detail_screen_cid = CID_DYNAMIC_BASE + 801
        status_icon_cid = CID_NOTIFICATION_ICON_STATUS
        rich_icon_cid = CID_NOTIFICATION_ICON_RICH
        img_cid = CID_NOTIFICATION_IMAGE

        # 1. Title String
        self.send_message(CostanzaCodec.pack_resource_string(self.next_msg_id(), title_cid, title, width=220, height=36, color=-1))
        # 2. Display Name String
        self.send_message(CostanzaCodec.pack_resource_string(self.next_msg_id(), disp_cid, "SmartWatch 2", width=220, height=28, color=-1))
        # 3. Message Body String
        self.send_message(CostanzaCodec.pack_resource_string(self.next_msg_id(), msg_cid, message, width=220, height=120, color=-1))
        # 4. ResourceScreens: Subscreen 1 (Overview / Card Preview) & Subscreen 2 (Detail View)
        self.send_message(CostanzaCodec.pack_resource_screen(self.next_msg_id(), overview_screen_cid, [img_cid, title_cid]))
        self.send_message(CostanzaCodec.pack_resource_screen(self.next_msg_id(), detail_screen_cid, [msg_cid, title_cid]))
        
        # 5. Icons: 20x20 Status bar & 40x40 Rich icon
        icon20 = render_teal_icon(20, 20)
        self.send_message(CostanzaCodec.pack_resource_compressed_image(self.next_msg_id(), status_icon_cid, 20, 20, icon20))
        icon40 = render_teal_icon(40, 40)
        self.send_message(CostanzaCodec.pack_resource_compressed_image(self.next_msg_id(), rich_icon_cid, 40, 40, icon40))

        # 6. Rich Notification Popup Card Image (196x100)
        preview_bytes = render_preview_card(title, message, 196, 100)
        self.send_message(CostanzaCodec.pack_resource_compressed_image(self.next_msg_id(), img_cid, 196, 100, preview_bytes))

        # 7. ResourceNotification (Multi-Subscreen Layout: Overview Screen & Detail Screen)
        notif_res = CostanzaCodec.pack_resource_notification(
            msg_id=self.next_msg_id(),
            cid=notif_cid,
            extension_cid=CID_NOTIFICATION_EXTENSION,
            title_cid=title_cid,
            msg_cid=msg_cid,
            disp_name_cid=disp_cid,
            status_icon_cid=status_icon_cid,
            rich_icon_cid=rich_icon_cid,
            rich_image_cid=img_cid,
            overview_screen_cid=overview_screen_cid,
            detail_screen_cid=detail_screen_cid,
            bitmap_rendering=0
        )
        self.send_message(notif_res)

        # 8. RequestNotification (Action 0 = ADDED)
        self.send_message(CostanzaCodec.pack_request_notification(self.next_msg_id(), action=0, notification_cid=notif_cid))
        # 9. Trigger Watch Vibration
        self.test_vibration(count=1, on_ms=300)
        print("[+] Notification sent successfully!")

    def close(self):
        self.running = False
        self.transport.close()


# ==============================================================================
# CLI Entry Point & Menu
# ==============================================================================

def main():
    parser = argparse.ArgumentParser(description="Sony SmartWatch 2 (SW2) Controller & Protocol Tester")
    parser.add_argument("--server", action="store_true", help="Native Windows 11 RFCOMM Server (Default & recommended)")
    parser.add_argument("--mac", help="Watch Bluetooth MAC Address (e.g. 00:24:EF:xx:xx:xx) for AF_BLUETOOTH")
    parser.add_argument("--channel", type=int, default=1, help="RFCOMM channel (default: 1)")
    parser.add_argument("--com", help="Virtual Serial COM Port (e.g. COM3 or /dev/rfcomm0) for pyserial")
    parser.add_argument("--baud", type=int, default=115200, help="COM port baudrate (default: 115200)")

    args = parser.parse_args()

    transport = None
    if args.com:
        transport = SerialTransport(args.com, args.baud)
    elif args.mac:
        transport = BluetoothSocketTransport(args.mac, args.channel)
    elif args.server or HAVE_WINRT:
        transport = WinRtServerTransport()
        if not transport.wait_for_connection(30.0):
            print("[!] Timeout: SmartWatch 2 hat sich nicht innerhalb von 30 Sekunden verbunden.")
            transport.close()
            sys.exit(1)
    else:
        print("[!] No connection target specified and WinRT is not available.")
        print("    Usage examples:")
        print("      python sw2_controller.py             (Zero-Config Windows 11 RFCOMM Server)")
        print("      python sw2_controller.py --com COM3")
        print("      python sw2_controller.py --mac 00:24:EF:12:34:56")
        sys.exit(1)

    controller = SmartWatch2Controller(transport)
    
    # Run automatic initial handshake
    controller.perform_handshake()

    menu = """
======================================================
  Sony SmartWatch 2 Reverse Engineering Controller
======================================================
  [1] Zeit synchronisieren
  [2] Vibration testen
  [3] Test-Benachrichtigung senden (Titel & Text)
  [4] Notification Extension neu registrieren
  [5] Status abfragen (FOTA & Version)
  [q] Beenden
------------------------------------------------------
Wähle eine Option: """

    try:
        while True:
            choice = input(menu).strip().lower()
            if choice == "1":
                controller.sync_time()
            elif choice == "2":
                controller.test_vibration()
            elif choice == "3":
                title = input("Titel der Benachrichtigung [Test]: ").strip() or "Test"
                msg = input("Nachrichtentext [Hallo SmartWatch 2]: ").strip() or "Hallo SmartWatch 2"
                controller.send_notification(title, msg)
            elif choice == "4":
                controller.register_notification_extension()
            elif choice == "5":
                controller.send_message(CostanzaCodec.pack_request_version(controller.next_msg_id()))
            elif choice == "q":
                print("[*] Beende Controller...")
                break
            else:
                print("[!] Ungültige Option.")
    except KeyboardInterrupt:
        print("\n[*] Abbruch durch Benutzer...")
    finally:
        controller.close()
        print("[*] Verbindung geschlossen.")


if __name__ == "__main__":
    main()
