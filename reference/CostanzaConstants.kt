package com.sonymobile.smartwatch2.companion.protocol

import java.util.UUID

object CostanzaConstants {
    // Bluetooth RFCOMM SDP Service Name & UUIDs
    const val COSTANZA_SERVER_NAME: String = "Constanza"
    val COSTANZA_SERVER_UUID: UUID = UUID.fromString("8E780522-3C51-11E1-8D8D-001CC4D601D8")
    val COSTANZA_CLIENT_UUID: UUID = UUID.fromString("8E780523-3C51-11E1-8D8D-001CC4D601D8")

    const val HEADER_SIZE = 12

    // Protocol Message Types
    const val TYPE_RESOURCE = 1
    const val TYPE_VERSION_REQ = 4
    const val TYPE_VERSION_RSP = 5
    const val TYPE_REQUEST_FOTA = 6
    const val TYPE_RESPONSE_FOTA = 7
    const val TYPE_ACK = 9
    const val TYPE_NACK = 10
    const val TYPE_FRAGMENT = 11
    const val TYPE_SET_LOCALE_REQ = 16
    const val TYPE_BATTERY_LEVEL_IND = 18
    const val TYPE_BATTERY_LEVEL_CNF = 19
    const val TYPE_FACTORY_RESET_REQ = 20
    const val TYPE_FACTORY_RESET_RSP = 21
    const val TYPE_SET_TIME_REQ = 22
    const val TYPE_EXTENSION_REQ = 104
    const val TYPE_EXTENSION_RSP = 105
    const val TYPE_CONTROL_REQ = 106
    const val TYPE_CONTROL_RSP = 107
    const val TYPE_CONTROL_IND = 108
    const val TYPE_REQUEST_NOTIFICATION = 110
    const val TYPE_INDICATION_NOTIFICATION = 112
    const val TYPE_INDICATION_KEY = 114
    const val TYPE_INDICATION_TOUCH = 115
    const val TYPE_INDICATION_SWIPE = 116
    const val TYPE_SENSOR_DATA_CONFIG_REQ = 125
    const val TYPE_SENSOR_DATA_CONFIG_RSP = 126
    const val TYPE_SENSOR_DATA_IND = 127
    const val TYPE_SENSOR_DATA_CNF = 128
    const val TYPE_REQUEST_VIBRATION = 129
    const val TYPE_REQUEST_SYNC_TIME = 130
    const val TYPE_SET_TIME_ZONE_REQ = 133
    const val TYPE_NOTIFICATION_READ_IND = 134
    const val TYPE_NOTIFICATION_LIST_REQ = 135
    const val TYPE_NOTIFICATION_LIST_RESP = 136
    const val TYPE_ERROR_IND = 137
    const val TYPE_NOTIFICATION_ALL_READ_IND = 141
    const val TYPE_REQUEST_WALLPAPER = 150
    const val TYPE_RESPONSE_WALLPAPER = 151
    const val TYPE_INDICATION_WALLPAPER = 152

    // Native Wallpaper CIDs (Sony Wallpapers 0..6)
    val NATIVE_WALLPAPER_CIDS = intArrayOf(
        900, // WALLPAPER_00 (Default)
        901, // WALLPAPER_01
        902, // WALLPAPER_02
        903, // WALLPAPER_03
        904, // WALLPAPER_04
        905, // WALLPAPER_05
        906  // WALLPAPER_06
    )

    // Wallpaper Actions
    const val WALLPAPER_ACTION_INSTALL = 0
    const val WALLPAPER_ACTION_UNINSTALL = 1
    const val WALLPAPER_ACTION_SET = 2
    const val WALLPAPER_ACTION_GET_SELECTED = 3
    const val WALLPAPER_ACTION_GET_ALL = 4

    // Dynamic CID base (Sony SW2 requires dynamic heap resources to have CID >= 0x00100000 = 1048576)
    const val CID_DYNAMIC_BASE = 0x00100000

    // Extension CIDs
    const val CID_EXTENSION_NAME = CID_DYNAMIC_BASE + 0
    const val CID_MEDIA_EXTENSION = CID_DYNAMIC_BASE + 1
    const val CID_NOTIFICATION_EXTENSION = CID_DYNAMIC_BASE + 2
    const val CID_NOTIFICATION_EXTENSION_NAME = CID_DYNAMIC_BASE + 3
    const val CID_NOTIFICATION_EXTENSION_ICON = CID_DYNAMIC_BASE + 4
    const val CID_NOTIFICATION_BASE = CID_DYNAMIC_BASE + 100
    const val CID_NOTIFICATION_TITLE = CID_DYNAMIC_BASE + 200
    const val CID_NOTIFICATION_MESSAGE = CID_DYNAMIC_BASE + 300
    const val CID_NOTIFICATION_IMAGE = CID_DYNAMIC_BASE + 400
    const val CID_NOTIFICATION_ICON_STATUS = CID_DYNAMIC_BASE + 500
    const val CID_NOTIFICATION_ICON_RICH = CID_DYNAMIC_BASE + 600
    const val CID_NOTIFICATION_DISPLAY_NAME = CID_DYNAMIC_BASE + 700
    const val CID_NOTIFICATION_SCREEN = CID_DYNAMIC_BASE + 800

    // Notification Layout Keys (from Sony ResourceNotification.java)
    const val NOTIFICATION_KEY_TITLE = 0
    const val NOTIFICATION_KEY_DISPLAY_NAME = 1
    const val NOTIFICATION_KEY_MESSAGE = 2
    const val NOTIFICATION_KEY_STATUS_BAR_ICON = 3
    const val NOTIFICATION_KEY_RICH_ICON = 4
    const val NOTIFICATION_KEY_CONTACT_ICON = 5
    const val NOTIFICATION_KEY_IMAGE = 6
    const val NOTIFICATION_KEY_WIDGET_COLOR_ICON = 7
    const val NOTIFICATION_KEY_WIDGET_GRAYSCALE_ICON = 8
    const val NOTIFICATION_KEY_TITLE_WHITE_IMAGE = 9
    const val NOTIFICATION_KEY_TITLE_BOLD_IMAGE = 10
    const val NOTIFICATION_KEY_DISPLAY_NAME_WHITE_IMAGE = 11
    const val NOTIFICATION_KEY_DISPLAY_NAME_BOLD_IMAGE = 12
    const val NOTIFICATION_KEY_DISPLAY_NAME_DRAWER = 13
    const val NOTIFICATION_KEY_MESSAGE_WHITE_IMAGE = 14
    const val NOTIFICATION_KEY_MESSAGE_DRAWER_WHITE_IMAGE = 15
    const val NOTIFICATION_KEY_MESSAGE_BOLD_IMAGE = 16
    const val NOTIFICATION_KEY_BITMAP_RENDERING = 17

    // Resource CID Types
    const val CID_TYPE_RESOURCE_STRING = 1
    const val CID_TYPE_RESOURCE_SCREEN = 4
    const val CID_TYPE_RESOURCE_COMPRESSED_IMAGE = 6
    const val CID_TYPE_RESOURCE_NOTIFICATION = 7
    const val CID_TYPE_RESOURCE_IMAGE = 8
    const val CID_TYPE_RESOURCE_EXTENSION = 11

    // Fragmentation Limits (Sony libprotocol.so threshold = 512 bytes, max payload = 492 bytes)
    const val MAX_PACKET_SIZE = 512
    const val MAX_FRAGMENT_PAYLOAD_SIZE = 492
    const val FRAGMENT_HEADER_SIZE = 20

    // Sensor Types & Modes
    const val SENSOR_TYPE_ACCELEROMETER = 1
    const val SENSOR_TYPE_LIGHT = 2
    const val SENSOR_TYPE_BATTERY = 3

    const val SENSOR_MODE_OFF = 0
    const val SENSOR_MODE_ON = 1

    const val SENSOR_RATE_INTERRUPT = 0
    const val SENSOR_RATE_FASTEST = 100
    const val SENSOR_RATE_GAME = 100
    const val SENSOR_RATE_NORMAL = 500
    const val SENSOR_RATE_SLOW = 1000

    // Screen Dimensions
    const val SCREEN_WIDTH = 220
    const val SCREEN_HEIGHT = 176
}
