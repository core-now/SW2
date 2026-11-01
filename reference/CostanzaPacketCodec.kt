package com.sonymobile.smartwatch2.companion.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary packet codec for the Sony SmartWatch 2 Costanza protocol.
 */
object CostanzaPacketCodec {

    fun argbToAbgr(color: Int): Int {
        val a = (color ushr 24) and 0xFF
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        return (a shl 24) or (b shl 16) or (g shl 8) or r
    }

    fun pack(message: CostanzaMessage): ByteArray {
        val payload: ByteArray = when (message) {
            is RequestVersion -> ByteArray(0)
            is ResponseVersion -> {
                val fwBytes = message.firmwareVersion.toByteArray(Charsets.UTF_8)
                val buf = ByteBuffer.allocate(4 + fwBytes.size + 1).order(ByteOrder.LITTLE_ENDIAN)
                buf.putInt(message.protocolVersion)
                buf.put(fwBytes)
                buf.put(0.toByte()) // Null terminator
                buf.array()
            }
            is RequestFota -> {
                ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(message.action)
                    .putInt(message.value)
                    .array()
            }
            is ResponseFota -> {
                ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(message.action)
                    .putInt(message.value)
                    .array()
            }
            is RequestFactoryReset -> {
                ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(message.action.toShort())
                    .putInt(message.status)
                    .array()
            }
            is ResponseFactoryReset -> {
                ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(message.status.toShort())
                    .array()
            }
            is ControlMessageAck -> {
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(message.ackMessageId)
                    .array()
            }
            is ControlMessageNack -> {
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(message.nackMessageId)
                    .array()
            }
            is ControlMessageSetLocaleReq -> {
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(message.localeCode)
                    .array()
            }
            is IndicationBatteryLevel -> {
                ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(message.percentage.toShort())
                    .array()
            }
            is ControlMessageSetTimeReq -> {
                ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    .putLong(message.time)
                    .array()
            }
            is RequestNotification -> {
                ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(message.action.toShort())
                    .putInt(message.value)
                    .array()
            }
            is IndicationNotification -> {
                ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(message.action.toShort())
                    .putInt(message.eventId)
                    .array()
            }
            is IndicationKey -> {
                ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(message.action.toShort())
                    .putShort(message.code.toShort())
                    .putLong(message.timestamp)
                    .array()
            }
            is IndicationTouch -> {
                ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(message.action.toShort())
                    .putShort(message.x.toShort())
                    .putShort(message.y.toShort())
                    .putInt(message.key)
                    .putLong(message.timestamp)
                    .array()
            }
            is IndicationSwipe -> {
                ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(message.direction.toShort())
                    .putShort(message.x.toShort())
                    .putShort(message.y.toShort())
                    .putInt(message.key)
                    .array()
            }
            is RequestSensorData -> {
                ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(message.sensorType.toShort())
                    .putShort(message.mode.toShort())
                    .putInt(message.rate)
                    .putInt(0)
                    .array()
            }
            is ResponseSensorData -> {
                ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(message.status.toShort())
                    .array()
            }
            is IndicationSensorData -> {
                val dataSize = message.data.size
                val buf = ByteBuffer.allocate(14 + (dataSize * 4)).order(ByteOrder.LITTLE_ENDIAN)
                buf.putShort(message.sensorType.toShort())
                buf.putShort(message.accuracy.toShort())
                buf.putLong(message.timestamp)
                buf.putShort(dataSize.toShort())
                for (v in message.data) {
                    buf.putInt(v)
                }
                buf.array()
            }
            is RequestVibration -> {
                ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(message.onDurationMs)
                    .putInt(message.offDurationMs)
                    .putInt(message.repeats)
                    .array()
            }
            is RequestSyncTime -> ByteArray(0)
            is ControlMessageSetTimeZoneReq -> {
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(message.offsetMs)
                    .array()
            }
            is RequestWallpaper -> {
                ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(message.action.toShort())
                    .putInt(message.wallpaperCid)
                    .putInt(message.thumbnailCidOrTimestamp)
                    .array()
            }
            is ResponseWallpaper -> {
                val buf = ByteBuffer.allocate(8 + (message.values.size * 4)).order(ByteOrder.LITTLE_ENDIAN)
                buf.putShort(message.action.toShort())
                buf.putInt(message.result)
                buf.putShort(message.values.size.toShort())
                for (v in message.values) {
                    buf.putInt(v)
                }
                buf.array()
            }
            is IndicationWallpaper -> {
                ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(message.action.toShort())
                    .putInt(message.wallpaperCid)
                    .array()
            }
            is RequestExtension -> {
                ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(message.action.toShort())
                    .putInt(message.value)
                    .array()
            }
            is ResponseExtension -> {
                ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(message.action.toShort())
                    .putInt(message.value)
                    .putInt(message.extensionCid)
                    .array()
            }
            is RequestCtrl -> {
                ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(message.action.toShort())
                    .putInt(message.cid)
                    .array()
            }
            is ResponseCtrl -> {
                ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(message.action.toShort())
                    .putInt(message.result)
                    .array()
            }
            is IndicationCtrl -> {
                ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(message.action.toShort())
                    .putInt(message.cid)
                    .array()
            }
            is ResourceExtension -> {
                val buf = ByteBuffer.allocate(60).order(ByteOrder.LITTLE_ENDIAN)
                buf.putInt(message.cid)                                          // 0x0c: cid
                buf.putInt(0)                                                    // 0x10: padding
                buf.putShort(1.toShort())                                        // 0x14: hasCid = 1
                buf.putShort(0.toShort())                                        // 0x16: padding
                buf.putInt(40)                                                   // 0x18: dataLength = 40
                buf.putInt(0)                                                    // 0x1c: padding
                buf.putInt(0)                                                    // 0x20: padding
                buf.putInt(0)                                                    // 0x24: padding
                buf.putShort(message.cidType.toShort())                          // 0x28: cidType
                buf.putShort(0.toShort())                                        // 0x2a: padding
                buf.putInt(message.nameCid)                                      // 0x2c: nameCid
                buf.putInt(message.iconCid)                                      // 0x30: iconCid
                buf.putInt(message.iconOfflineCid)                               // 0x34: iconOfflineCid
                buf.putInt(message.color)                                        // 0x38: color
                buf.putShort(5.toShort())                                        // 0x3c: priority = NOTIFICATION_MSG
                buf.put(if (message.showOnHomeScreen) 1.toByte() else 0.toByte())// 0x3e: show
                buf.put(0.toByte())                                              // 0x3f: launchMode
                buf.putShort(message.capabilities.toShort())                     // 0x40: capabilities
                buf.putShort(0.toShort())                                        // 0x42: padding
                buf.putInt(-1)                                                   // 0x44: relativeCid = -1
                buf.array()
            }
            is ResourceString -> {
                val chars = message.text.toByteArray(Charsets.UTF_16LE)
                val totalHeaderAndPad = 0x52 - 0x0c // 70 bytes
                val buf = ByteBuffer.allocate(totalHeaderAndPad + chars.size + 2).order(ByteOrder.LITTLE_ENDIAN)
                buf.putInt(message.cid)                                          // 0x0c: cid
                buf.putInt(0)                                                    // 0x10: padding
                buf.putShort(1.toShort())                                        // 0x14: hasCid = 1
                buf.putShort(0.toShort())                                        // 0x16: padding
                buf.putInt(buf.capacity() - 20)                                  // 0x18: dataLength
                buf.putInt(0)                                                    // 0x1c: padding
                buf.putInt(0)                                                    // 0x20: padding
                buf.putInt(0)                                                    // 0x24: padding
                buf.putShort(9.toShort())                                        // 0x28: cidType = 9
                buf.putShort(0.toShort())                                        // 0x2a: padding
                buf.putInt(0)                                                    // 0x2c: padding
                val w = (message.width - 1).coerceAtLeast(1)
                buf.putShort(w.toShort())                                        // 0x30: width - 1
                val h = (message.height - 1).coerceAtLeast(1)
                buf.putShort(h.toShort())                                        // 0x32: height - 1
                buf.putShort(1.toShort())                                        // 0x34: 1
                buf.putShort(0.toShort())                                        // 0x36: 0

                val hVal = when (message.hAlign) {
                    1 -> 2
                    2 -> 3
                    else -> 1
                }
                buf.putShort(hVal.toShort())                                     // 0x38: hAlign ({1,0})
                buf.putShort(0.toShort())                                        // 0x3a: 0

                val vVal = when (message.vAlign) {
                    1 -> 2
                    2 -> 3
                    else -> 1
                }
                buf.putShort(vVal.toShort())                                     // 0x3c: vAlign ({1,0})
                buf.putShort(0.toShort())                                        // 0x3e: 0

                val fontVal = when (message.fontSize) {
                    0 -> 0
                    1 -> 1
                    3 -> 5
                    4 -> 8
                    else -> 4 // 2 = Normal regular ({4,0})
                }
                buf.putShort(fontVal.toShort())                                  // 0x40: font
                buf.putShort(0.toShort())                                        // 0x42: 0
                buf.putShort(fontVal.toShort())                                  // 0x44: font
                buf.putShort(0.toShort())                                        // 0x46: 0
                buf.putInt(argbToAbgr(message.color))                            // 0x48: color (ABGR)
                buf.putShort(0.toShort())                                        // 0x4c: padding
                buf.putShort(message.text.length.toShort())                      // 0x4e: strLen
                buf.putShort(0.toShort())                                        // 0x50: padding
                buf.put(chars)                                                   // 0x52: UTF-16LE characters
                buf.putShort(0.toShort())                                        // null terminator
                buf.array()
            }
            is ResourceScreen -> {
                val payloadSize = 34 + (message.cids.size * 4)
                val buf = ByteBuffer.allocate(payloadSize).order(ByteOrder.LITTLE_ENDIAN)
                buf.putInt(message.cid)                                          // 0x0c: cid
                buf.putInt(0)                                                    // 0x10: padding
                buf.putShort(1.toShort())                                        // 0x14: hasCid = 1
                buf.putShort(0.toShort())                                        // 0x16: padding
                buf.putInt(payloadSize - 20)                                     // 0x18: dataLength
                buf.putInt(0)                                                    // 0x1c: padding
                buf.putInt(0)                                                    // 0x20: padding
                buf.putInt(0)                                                    // 0x24: padding
                buf.putShort(11.toShort())                                       // 0x28: subType = 11 (0x0b)
                buf.putShort(0.toShort())                                        // 0x2a: padding
                buf.putShort(message.cids.size.toShort())                        // 0x2c: count
                for (subCid in message.cids) {
                    buf.putInt(subCid)                                           // 0x2e..: subCid
                }
                buf.array()
            }
            is ResourceImage -> {
                val totalHeader = 0x40 - 0x0c                                    // 52 bytes header
                val payloadSize = totalHeader + message.data.size
                val buf = ByteBuffer.allocate(payloadSize).order(ByteOrder.LITTLE_ENDIAN)
                buf.putInt(message.cid)                                          // 0x0c: cid
                buf.putInt(0)                                                    // 0x10: padding
                buf.putShort(1.toShort())                                        // 0x14: hasCid = 1
                buf.putShort(0.toShort())                                        // 0x16: padding
                buf.putInt(payloadSize - 20)                                     // 0x18: dataLength
                buf.putInt(0)                                                    // 0x1c: padding
                buf.putInt(0)                                                    // 0x20: padding
                buf.putInt(0)                                                    // 0x24: padding
                buf.putShort(5.toShort())                                        // 0x28: subType = 5
                buf.putShort(0.toShort())                                        // 0x2a: padding
                buf.putInt(0)                                                    // 0x2c: padding
                buf.putShort((message.width - 1).coerceAtLeast(1).toShort())     // 0x30: width - 1
                buf.putShort((message.height - 1).coerceAtLeast(1).toShort())    // 0x32: height - 1
                buf.putShort(3.toShort())                                        // 0x34: colorMode = 3 (RGB565)
                buf.putShort(0.toShort())                                        // 0x36: padding
                buf.putShort(1.toShort())                                        // 0x38: 1
                buf.putShort(0.toShort())                                        // 0x3a: padding
                buf.putInt(message.data.size)                                    // 0x3c: rawImageBytes.size
                buf.put(message.data)                                            // 0x40: raw pixels (RGB565 LE)
                buf.array()
            }
            is ResourceCompressedImage -> {
                // Total packet is 64 + data.size bytes.
                // 12-byte header + 52-byte payload descriptor + compressed image bytes.
                val payloadSize = 52 + message.data.size
                val buf = ByteBuffer.allocate(payloadSize).order(ByteOrder.LITTLE_ENDIAN)
                buf.putInt(message.cid)                                          // 0x0c: cid
                buf.putInt(0)                                                    // 0x10: padding
                buf.putShort(1.toShort())                                        // 0x14: hasCid = 1
                buf.putShort(0.toShort())                                        // 0x16: padding
                buf.putInt(payloadSize - 20)                                     // 0x18: dataLength = 32 + data.size
                buf.putInt(0)                                                    // 0x1c: padding
                buf.putInt(0)                                                    // 0x20: padding
                buf.putInt(0)                                                    // 0x24: padding
                buf.putShort(5.toShort())                                        // 0x28: subType = 5
                buf.putShort(0.toShort())                                        // 0x2a: padding
                buf.putInt(0)                                                    // 0x2c: padding
                buf.putShort((message.width - 1).coerceAtLeast(1).toShort())     // 0x30: width - 1
                buf.putShort((message.height - 1).coerceAtLeast(1).toShort())    // 0x32: height - 1
                buf.putInt(message.compressionType)                              // 0x34: compressionType (4 = ZLIB)
                buf.putInt(message.colorType)                                    // 0x38: colorType (0 = BGR565)
                buf.putInt(message.data.size)                                    // 0x3c: compressed byte size
                buf.put(message.data)                                            // 0x40: deflated data bytes
                buf.array()
            }
            is CostanzaFragment -> {
                val buf = ByteBuffer.allocate(8 + message.data.size).order(ByteOrder.LITTLE_ENDIAN)
                buf.putShort(message.fragIdx.toShort())                          // 0x0c: fragIdx
                buf.putShort(message.fragTotal.toShort())                        // 0x0e: fragTotal
                buf.putInt(message.data.size)                                    // 0x10: fragDataLen
                buf.put(message.data)                                            // 0x14: data
                buf.array()
            }
            is ResourceNotification -> {
                val fields = mutableListOf<Pair<Int, Int>>()
                // Native String Keys
                if (message.titleCid != -1) {
                    fields.add(CostanzaConstants.NOTIFICATION_KEY_TITLE to message.titleCid)
                }
                if (message.displayNameCid != -1) {
                    fields.add(CostanzaConstants.NOTIFICATION_KEY_DISPLAY_NAME to message.displayNameCid)
                }
                if (message.messageCid != -1) {
                    fields.add(CostanzaConstants.NOTIFICATION_KEY_MESSAGE to message.messageCid)
                }
                // Icon Keys (Image CIDs)
                if (message.statusBarIconCid != -1) {
                    fields.add(CostanzaConstants.NOTIFICATION_KEY_STATUS_BAR_ICON to message.statusBarIconCid)
                }
                if (message.richIconCid != -1) {
                    fields.add(CostanzaConstants.NOTIFICATION_KEY_RICH_ICON to message.richIconCid)
                }
                // Rich Notification Popup Card Image Key (Key 14, Image CID)
                if (message.richNotificationCid != -1) {
                    fields.add(CostanzaConstants.NOTIFICATION_KEY_MESSAGE_WHITE_IMAGE to message.richNotificationCid)
                }
                fields.add(CostanzaConstants.NOTIFICATION_KEY_BITMAP_RENDERING to message.bitmapRendering)

                val fieldCount = fields.size
                val subScreenCount = if (message.screenCid != -1) 1 else 0
                val totalSize = 306 + (subScreenCount * 16) // 322 bytes when subScreenCount == 1, 306 when 0
                val payloadSize = totalSize - CostanzaConstants.HEADER_SIZE // 310 or 294 bytes
                val dataLength = totalSize - 20 // 302 or 286 bytes

                val buf = ByteBuffer.allocate(payloadSize).order(ByteOrder.LITTLE_ENDIAN)
                buf.putInt(message.cid)                                          // 0x0c: cid
                buf.putInt(0)                                                    // 0x10: padding
                buf.putShort(1.toShort())                                        // 0x14: hasCid = 1
                buf.putShort(0.toShort())                                        // 0x16: padding
                buf.putInt(dataLength)                                           // 0x18: dataLength (totalSize - 20)
                buf.putShort(2.toShort())                                        // 0x1c: subType = 2
                buf.putShort(0.toShort())                                        // 0x1e: padding
                buf.putInt(0)                                                    // 0x20: localization = 0
                buf.putInt(message.notificationExtensionCid)                     // 0x24: notificationExtensionCid
                buf.putLong(message.timeStamp)                                   // 0x28: timeStamp
                buf.put(0.toByte())                                              // 0x30: personal = 0
                buf.put(0.toByte())                                              // 0x31: readStatus = 0
                buf.putShort(fieldCount.toShort())                               // 0x32: fieldCount

                // Offset 0x34..0x69 (54 bytes): mOnlineMenu
                buf.putInt(-1)                                                   // 0x34: menuCid = -1
                buf.putShort(0.toShort())                                        // 0x38: count = 0
                buf.put(ByteArray(48))                                           // 0x3a..0x69: 48 bytes zeroes

                // Offset 0x6a..0x9f (54 bytes): mOfflineMenu
                buf.putInt(-1)                                                   // 0x6a: menuCid = -1
                buf.putShort(0.toShort())                                        // 0x6e: count = 0
                buf.put(ByteArray(48))                                           // 0x70..0x9f: 48 bytes zeroes

                // Offset 0xa0..0x12f (144 bytes = 18 slots of 8 bytes): mFields
                val writeFields = fields.take(18)
                for ((key, value) in writeFields) {
                    buf.putShort(key.toShort())                                  // key
                    buf.putShort(0.toShort())                                    // padding
                    buf.putInt(value)                                            // value
                }
                val remainingSlots = 18 - writeFields.size
                if (remainingSlots > 0) {
                    buf.put(ByteArray(remainingSlots * 8))
                }

                // Offset 0x130..0x131 (2 bytes): subScreenCount
                buf.putShort(subScreenCount.toShort())

                // Offset 0x132..0x141 (16 bytes if subScreenCount == 1): mSubScreens
                if (subScreenCount == 1) {
                    buf.putShort(0.toShort())                                    // 0x132: left = 0
                    buf.putShort(0.toShort())                                    // 0x134: top = 0
                    buf.putShort(219.toShort())                                  // 0x136: right = 219 (SCREEN_WIDTH - 1)
                    buf.putShort(175.toShort())                                  // 0x138: bottom = 175 (SCREEN_HEIGHT - 1)
                    buf.putShort(2.toShort())                                    // 0x13a: opaque = 2
                    buf.putShort(0.toShort())                                    // 0x13c: pad = 0
                    buf.putInt(message.screenCid)                                // 0x13e: resourceCid = screenCid
                }
                buf.array()
            }
            is RawCostanzaMessage -> message.payload
            else -> ByteArray(0)
        }

        val totalLength = CostanzaConstants.HEADER_SIZE + payload.size
        val packet = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN)
        packet.putInt(totalLength)
        packet.putInt(message.type)
        packet.putInt(message.messageId)
        if (payload.isNotEmpty()) {
            packet.put(payload)
        }
        return packet.array()
    }

    fun unpack(bytes: ByteArray): CostanzaMessage {
        require(bytes.size >= CostanzaConstants.HEADER_SIZE) {
            "Packet is smaller than 12-byte header: ${bytes.size}"
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.int // length (header already validated)
        val type = buf.int
        val messageId = buf.int

        val payloadSize = bytes.size - CostanzaConstants.HEADER_SIZE
        return when (type) {
            CostanzaConstants.TYPE_VERSION_REQ -> {
                RequestVersion(messageId)
            }
            CostanzaConstants.TYPE_VERSION_RSP -> {
                val protoVersion = if (payloadSize >= 4) buf.int else 0
                val remainingStrBytes = ByteArray(payloadSize - 4)
                if (remainingStrBytes.isNotEmpty()) {
                    buf.get(remainingStrBytes)
                }
                val nullIndex = remainingStrBytes.indexOf(0)
                val fwStr = if (nullIndex >= 0) {
                    String(remainingStrBytes, 0, nullIndex, Charsets.UTF_8)
                } else {
                    String(remainingStrBytes, Charsets.UTF_8)
                }
                ResponseVersion(messageId, protoVersion, fwStr)
            }
            CostanzaConstants.TYPE_REQUEST_FOTA -> {
                val action = if (payloadSize >= 4) buf.int else 0
                val value = if (payloadSize >= 8) buf.int else 0
                RequestFota(messageId, action, value)
            }
            CostanzaConstants.TYPE_RESPONSE_FOTA -> {
                val action = if (payloadSize >= 4) buf.int else 0
                val value = if (payloadSize >= 8) buf.int else 0
                ResponseFota(messageId, action, value)
            }
            CostanzaConstants.TYPE_FACTORY_RESET_REQ -> {
                val action = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                val status = if (payloadSize >= 6) buf.int else 0
                RequestFactoryReset(messageId, action, status)
            }
            CostanzaConstants.TYPE_FACTORY_RESET_RSP -> {
                val status = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                ResponseFactoryReset(messageId, status)
            }
            CostanzaConstants.TYPE_ACK -> {
                val ackMsgId = if (payloadSize >= 4) buf.int else 0
                ControlMessageAck(messageId, ackMsgId)
            }
            CostanzaConstants.TYPE_NACK -> {
                val nackMsgId = if (payloadSize >= 4) buf.int else 0
                ControlMessageNack(messageId, nackMsgId)
            }
            CostanzaConstants.TYPE_FRAGMENT -> {
                val fragIdx = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                val fragTotal = if (payloadSize >= 4) buf.short.toInt() and 0xFFFF else 0
                val fragDataLen = if (payloadSize >= 8) buf.int else 0
                val fragData = ByteArray(minOf(fragDataLen, buf.remaining()))
                if (fragData.isNotEmpty()) {
                    buf.get(fragData)
                }
                CostanzaFragment(messageId, fragIdx, fragTotal, fragData)
            }
            CostanzaConstants.TYPE_SET_LOCALE_REQ -> {
                val locale = if (payloadSize >= 4) buf.int else 0
                ControlMessageSetLocaleReq(messageId, locale)
            }
            CostanzaConstants.TYPE_BATTERY_LEVEL_IND,
            CostanzaConstants.TYPE_BATTERY_LEVEL_CNF -> {
                val percentage = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                IndicationBatteryLevel(messageId, percentage)
            }
            CostanzaConstants.TYPE_SET_TIME_REQ -> {
                val time = if (payloadSize >= 8) buf.long else 0L
                ControlMessageSetTimeReq(messageId, time)
            }
            CostanzaConstants.TYPE_REQUEST_NOTIFICATION -> {
                val action = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                val value = if (payloadSize >= 6) buf.int else 0
                RequestNotification(messageId, action, value)
            }
            CostanzaConstants.TYPE_INDICATION_NOTIFICATION -> {
                val action = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                val eventId = if (payloadSize >= 6) buf.int else 0
                IndicationNotification(messageId, action, eventId)
            }
            CostanzaConstants.TYPE_INDICATION_KEY -> {
                val action = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                val code = if (payloadSize >= 4) buf.short.toInt() and 0xFFFF else 0
                val timestamp = if (payloadSize >= 12) buf.long else 0L
                IndicationKey(messageId, action, code, timestamp)
            }
            CostanzaConstants.TYPE_INDICATION_TOUCH -> {
                val action = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                val x = if (payloadSize >= 4) buf.short.toInt() and 0xFFFF else 0
                val y = if (payloadSize >= 6) buf.short.toInt() and 0xFFFF else 0
                val key = if (payloadSize >= 10) buf.int else 0
                val timestamp = if (payloadSize >= 18) buf.long else 0L
                IndicationTouch(messageId, action, x, y, key, timestamp)
            }
            CostanzaConstants.TYPE_INDICATION_SWIPE -> {
                val direction = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                val x = if (payloadSize >= 4) buf.short.toInt() and 0xFFFF else 0
                val y = if (payloadSize >= 6) buf.short.toInt() and 0xFFFF else 0
                val key = if (payloadSize >= 10) buf.int else 0
                IndicationSwipe(messageId, direction, x, y, key)
            }
            CostanzaConstants.TYPE_REQUEST_VIBRATION -> {
                val on = if (payloadSize >= 4) buf.int else 0
                val off = if (payloadSize >= 8) buf.int else 0
                val repeats = if (payloadSize >= 12) buf.int else 0
                RequestVibration(messageId, on, off, repeats)
            }
            CostanzaConstants.TYPE_REQUEST_SYNC_TIME -> {
                RequestSyncTime(messageId)
            }
            CostanzaConstants.TYPE_SET_TIME_ZONE_REQ -> {
                val offset = if (payloadSize >= 4) buf.int else 0
                ControlMessageSetTimeZoneReq(messageId, offset)
            }
            CostanzaConstants.TYPE_REQUEST_WALLPAPER -> {
                val action = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                val cid = if (payloadSize >= 6) buf.int else 0
                val extra = if (payloadSize >= 10) buf.int else 0
                RequestWallpaper(messageId, action, cid, extra)
            }
            CostanzaConstants.TYPE_RESPONSE_WALLPAPER -> {
                val action = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                val result = if (payloadSize >= 6) buf.int else 0
                val count = if (payloadSize >= 8) buf.short.toInt() and 0xFFFF else 0
                val values = IntArray(count)
                for (i in 0 until count) {
                    if (buf.remaining() >= 4) {
                        values[i] = buf.int
                    }
                }
                ResponseWallpaper(messageId, action, result, values)
            }
            CostanzaConstants.TYPE_INDICATION_WALLPAPER -> {
                val action = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                val cid = if (payloadSize >= 6) buf.int else 0
                IndicationWallpaper(messageId, action, cid)
            }
            CostanzaConstants.TYPE_RESOURCE -> {
                if (payloadSize >= 20) {
                    val cid = buf.int
                    buf.int // padding
                    buf.short // hasCid
                    buf.short // padding
                    buf.int // dataLength
                    val subTypeOrPad = buf.short.toInt() and 0xFFFF
                    buf.short // padding

                    if (subTypeOrPad == 2 && payloadSize >= 56) {
                        // ResourceNotification
                        buf.int // localization
                        val extCid = buf.int
                        val timeStamp = buf.long
                        buf.get() // personal
                        buf.get() // readStatus
                        val fieldCount = buf.short.toInt() and 0xFFFF
                        var screenCid = -1
                        var titleCid = -1
                        var msgCid = -1
                        var dispCid = -1
                        var statusBarIconCid = -1
                        var richIconCid = -1
                        var richImgCid = -1
                        var bitmapRendering = 0

                        if (payloadSize >= 294 && buf.remaining() >= 254) {
                            // Skip mOnlineMenu (54 bytes) and mOfflineMenu (54 bytes) -> reaches 0xa0
                            buf.position(buf.position() + 108)

                            // Read up to 18 fields from 144-byte table
                            val readCount = minOf(fieldCount, 18)
                            for (i in 0 until readCount) {
                                val key = buf.short.toInt() and 0xFFFF
                                buf.short // padding
                                val value = buf.int
                                when (key) {
                                    CostanzaConstants.NOTIFICATION_KEY_TITLE -> titleCid = value
                                    CostanzaConstants.NOTIFICATION_KEY_DISPLAY_NAME -> dispCid = value
                                    CostanzaConstants.NOTIFICATION_KEY_MESSAGE -> msgCid = value
                                    CostanzaConstants.NOTIFICATION_KEY_STATUS_BAR_ICON -> statusBarIconCid = value
                                    CostanzaConstants.NOTIFICATION_KEY_RICH_ICON -> richIconCid = value
                                    CostanzaConstants.NOTIFICATION_KEY_MESSAGE_WHITE_IMAGE -> richImgCid = value
                                    CostanzaConstants.NOTIFICATION_KEY_BITMAP_RENDERING -> bitmapRendering = value
                                }
                            }
                            // Skip remaining unused field slots to reach offset 0x130
                            val remainingFieldSlots = 18 - readCount
                            if (remainingFieldSlots > 0) {
                                buf.position(buf.position() + (remainingFieldSlots * 8))
                            }

                            // Offset 0x130..0x131: subScreenCount
                            if (buf.remaining() >= 2) {
                                val subCount = buf.short.toInt() and 0xFFFF
                                if (subCount > 0 && buf.remaining() >= 16) {
                                    buf.short // left
                                    buf.short // top
                                    buf.short // right
                                    buf.short // bottom
                                    buf.short // opaque
                                    buf.short // pad
                                    screenCid = buf.int // resourceCid
                                }
                            }
                        }
                        ResourceNotification(
                            messageId = messageId,
                            cid = cid,
                            notificationExtensionCid = extCid,
                            titleCid = titleCid,
                            messageCid = msgCid,
                            displayNameCid = if (dispCid != -1) dispCid else titleCid,
                            statusBarIconCid = statusBarIconCid,
                            richIconCid = richIconCid,
                            richNotificationCid = richImgCid,
                            screenCid = screenCid,
                            bitmapRendering = bitmapRendering,
                            timeStamp = timeStamp
                        )
                    } else if (payloadSize >= 34) {
                        buf.int // 0x20: padding
                        buf.int // 0x24: padding
                        val cidType = buf.short.toInt() and 0xFFFF
                        buf.short // padding

                        if (cidType == 11 && payloadSize != 60) {
                            // ResourceScreen (subType 11)
                            val count = buf.short.toInt() and 0xFFFF // 0x2c
                            val subCids = IntArray(count)
                            for (i in 0 until count) {
                                if (buf.remaining() >= 4) {
                                    subCids[i] = buf.int
                                }
                            }
                            ResourceScreen(messageId, cid, subCids)
                        } else if (cidType == CostanzaConstants.CID_TYPE_RESOURCE_EXTENSION && payloadSize >= 48) {
                            val nameCid = buf.int
                            val iconCid = buf.int
                            val iconOfflineCid = buf.int
                            val color = buf.int
                            buf.short // priority
                            val show = buf.get().toInt() != 0
                            buf.get() // launchMode
                            val caps = buf.short.toInt() and 0xFFFF
                            buf.short // padding
                            buf.int // relCid
                            ResourceExtension(
                                messageId = messageId,
                                cid = cid,
                                nameCid = nameCid,
                                iconCid = iconCid,
                                iconOfflineCid = iconOfflineCid,
                                capabilities = caps,
                                color = color,
                                showOnHomeScreen = show
                            )
                        } else if ((cidType == 5 || cidType == 8 || cidType == CostanzaConstants.CID_TYPE_RESOURCE_COMPRESSED_IMAGE) && payloadSize >= 52) {
                            buf.int // 0x2c: padding
                            val w = (buf.short.toInt() and 0xFFFF) + 1 // 0x30
                            val h = (buf.short.toInt() and 0xFFFF) + 1 // 0x32
                            val modeOrCompress = buf.int // 0x34
                            if (modeOrCompress == 2 || modeOrCompress == 3) {
                                // Raw ResourceImage
                                buf.short // 0x38: 1
                                buf.short // 0x3a: padding
                                val rawSize = buf.int // 0x3c
                                val imgBytes = ByteArray(rawSize.coerceAtMost(buf.remaining()))
                                if (imgBytes.isNotEmpty()) {
                                    buf.get(imgBytes)
                                }
                                ResourceImage(messageId, cid, w, h, imgBytes)
                            } else {
                                // ResourceCompressedImage
                                val colorType = buf.int // 0x38
                                val compSize = buf.int // 0x3c
                                val imgBytes = ByteArray(compSize.coerceAtMost(buf.remaining()))
                                if (imgBytes.isNotEmpty()) {
                                    buf.get(imgBytes)
                                }
                                ResourceCompressedImage(messageId, cid, w, h, modeOrCompress, colorType, imgBytes)
                            }
                        } else if (cidType == 9 && payloadSize >= 70) {
                            buf.int // 0x2c
                            val w = (buf.short.toInt() and 0xFFFF) + 1 // 0x30
                            val h = (buf.short.toInt() and 0xFFFF) + 1 // 0x32
                            for (k in 0 until 10) buf.short // 0x34 - 0x47 (20 bytes: align and font)
                            val colorAbgr = buf.int // 0x48
                            val color = argbToAbgr(colorAbgr)
                            buf.short // 0x4c
                            val strLen = buf.short.toInt() and 0xFFFF // 0x4e
                            buf.short // 0x50
                            val charBytes = ByteArray(strLen * 2)
                            if (buf.remaining() >= charBytes.size) {
                                buf.get(charBytes)
                            }
                            val text = String(charBytes, Charsets.UTF_16LE)
                            ResourceString(messageId, cid, text, w, h, color)
                        } else {
                            val remaining = ByteArray(buf.remaining())
                            buf.get(remaining)
                            RawCostanzaMessage(type, messageId, remaining)
                        }
                    } else {
                        val remaining = ByteArray(buf.remaining())
                        buf.get(remaining)
                        RawCostanzaMessage(type, messageId, remaining)
                    }
                } else {
                    val remaining = ByteArray(payloadSize)
                    buf.get(remaining)
                    RawCostanzaMessage(type, messageId, remaining)
                }
            }
            CostanzaConstants.TYPE_EXTENSION_REQ -> {
                val action = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                val value = if (payloadSize >= 6) buf.int else 0
                RequestExtension(messageId, action, value)
            }
            CostanzaConstants.TYPE_EXTENSION_RSP -> {
                val action = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                val value = if (payloadSize >= 6) buf.int else 0
                val extCid = if (payloadSize >= 10) buf.int else 0
                ResponseExtension(messageId, action, value, extCid)
            }
            CostanzaConstants.TYPE_CONTROL_REQ -> {
                val action = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                val cid = if (payloadSize >= 6) buf.int else 0
                RequestCtrl(messageId, action, cid)
            }
            CostanzaConstants.TYPE_CONTROL_RSP -> {
                val action = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                val result = if (payloadSize >= 6) buf.int else 0
                ResponseCtrl(messageId, action, result)
            }
            CostanzaConstants.TYPE_CONTROL_IND -> {
                val action = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                val cid = if (payloadSize >= 6) buf.int else 0
                IndicationCtrl(messageId, action, cid)
            }
            CostanzaConstants.TYPE_SENSOR_DATA_CONFIG_REQ -> {
                val sensorType = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                val mode = if (payloadSize >= 4) buf.short.toInt() and 0xFFFF else 0
                val rate = if (payloadSize >= 8) buf.int else 0
                RequestSensorData(messageId, sensorType, mode, rate)
            }
            CostanzaConstants.TYPE_SENSOR_DATA_CONFIG_RSP -> {
                val status = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                ResponseSensorData(messageId, status)
            }
            CostanzaConstants.TYPE_SENSOR_DATA_IND -> {
                val sensorType = if (payloadSize >= 2) buf.short.toInt() and 0xFFFF else 0
                val accuracy = if (payloadSize >= 4) buf.short.toInt() and 0xFFFF else 0
                val timestamp = if (payloadSize >= 12) buf.long else 0L
                val dataCount = if (payloadSize >= 14) buf.short.toInt() and 0xFFFF else 0
                val data = IntArray(dataCount)
                for (i in 0 until dataCount) {
                    if (buf.remaining() >= 4) {
                        data[i] = buf.int
                    }
                }
                IndicationSensorData(messageId, sensorType, accuracy, timestamp, data)
            }
            else -> {
                val payload = ByteArray(payloadSize)
                if (payloadSize > 0) {
                    buf.get(payload)
                }
                RawCostanzaMessage(type, messageId, payload)
            }
        }
    }

    fun parseHeader(headerBytes: ByteArray): Triple<Int, Int, Int> {
        require(headerBytes.size >= CostanzaConstants.HEADER_SIZE) {
            "Header must be at least 12 bytes"
        }
        val buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        val length = buf.int
        val type = buf.int
        val messageId = buf.int
        return Triple(length, type, messageId)
    }

    /**
     * Splits an assembled/packed Costanza packet into fragments if size > 512 bytes,
     * matching libprotocol.so's splitPackedIntoFragments logic.
     * Each fragment has max payload of 492 bytes with a 20-byte TYPE_FRAGMENT (11) header.
     */
    fun splitIntoFragments(packetBytes: ByteArray, origMsgId: Int): List<ByteArray> {
        if (packetBytes.size <= CostanzaConstants.MAX_PACKET_SIZE) {
            return listOf(packetBytes)
        }

        val totalPayload = packetBytes.size
        val maxChunk = CostanzaConstants.MAX_FRAGMENT_PAYLOAD_SIZE
        val fragTotal = (totalPayload + maxChunk - 1) / maxChunk
        val fragments = ArrayList<ByteArray>(fragTotal)

        var offset = 0
        for (fragIdx in 0 until fragTotal) {
            val chunkLen = minOf(maxChunk, totalPayload - offset)
            val fragPacketSize = CostanzaConstants.FRAGMENT_HEADER_SIZE + chunkLen
            val buf = ByteBuffer.allocate(fragPacketSize).order(ByteOrder.LITTLE_ENDIAN)

            buf.putInt(fragPacketSize)                       // 0x00: length
            buf.putInt(CostanzaConstants.TYPE_FRAGMENT)      // 0x04: type = 11
            buf.putInt(origMsgId)                            // 0x08: origMsgId
            buf.putShort(fragIdx.toShort())                  // 0x0c: fragIdx
            buf.putShort(fragTotal.toShort())                // 0x0e: fragTotal
            buf.putInt(chunkLen)                             // 0x10: fragDataLen
            buf.put(packetBytes, offset, chunkLen)           // 0x14: data

            fragments.add(buf.array())
            offset += chunkLen
        }
        return fragments
    }

    /**
     * Packs a CostanzaMessage and automatically fragments it into 512-byte fragments if necessary.
     */
    fun packWithFragments(message: CostanzaMessage): List<ByteArray> {
        val packed = pack(message)
        return splitIntoFragments(packed, message.messageId)
    }

    /**
     * Assembles incoming CostanzaFragment packets into a complete packet,
     * matching libprotocol.so's getFragmentAssembler / getAssembledPacket logic.
     */
    class FragmentAssembler {
        private val fragmentsMap = mutableMapOf<Int, Array<ByteArray?>>()

        /**
         * Adds a fragment and returns the reassembled complete packet bytes if all fragments have arrived, or null otherwise.
         */
        fun addFragment(fragment: CostanzaFragment): ByteArray? {
            val list = fragmentsMap.getOrPut(fragment.origMsgId) {
                arrayOfNulls(fragment.fragTotal)
            }
            if (fragment.fragIdx in list.indices) {
                list[fragment.fragIdx] = fragment.data
            }
            if (list.all { it != null }) {
                fragmentsMap.remove(fragment.origMsgId)
                val totalSize = list.sumOf { it!!.size }
                val assembled = ByteBuffer.allocate(totalSize)
                for (chunk in list) {
                    assembled.put(chunk!!)
                }
                return assembled.array()
            }
            return null
        }

        fun clear() {
            fragmentsMap.clear()
        }
    }
}
