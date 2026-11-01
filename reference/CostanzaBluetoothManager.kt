package com.sonymobile.smartwatch2.companion.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import com.sonymobile.smartwatch2.companion.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data class Listening(val message: String = "Warte auf SmartWatch 2 Verbindung…") : ConnectionState()
    data class Connecting(val deviceName: String, val address: String) : ConnectionState()
    data class Connected(val deviceName: String, val address: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class CostanzaBluetoothManager(
    private val bluetoothAdapter: BluetoothAdapter?
) {
    companion object {
        private const val TAG = "CostanzaBtManager"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val msgIdGenerator = AtomicInteger(1)

    private var serverSocket: BluetoothServerSocket? = null
    private var serverJob: Job? = null

    private var activeSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private var readJob: Job? = null
    @Volatile
    private var isConnected = false
    private val fragmentAssembler = CostanzaPacketCodec.FragmentAssembler()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<CostanzaMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<CostanzaMessage> = _incomingMessages.asSharedFlow()

    private val _activeWallpaperCid = MutableStateFlow(900)
    val activeWallpaperCid: StateFlow<Int> = _activeWallpaperCid.asStateFlow()

    private val _firmwareVersion = MutableStateFlow<String?>(null)
    val firmwareVersion: StateFlow<String?> = _firmwareVersion.asStateFlow()

    fun nextMessageId(): Int = msgIdGenerator.getAndIncrement()

    @SuppressLint("MissingPermission")
    fun startServerListener() {
        if (isConnected || serverJob?.isActive == true) return
        val adapter = bluetoothAdapter ?: run {
            Log.w(TAG, "BluetoothAdapter is null, cannot start server listener")
            return
        }

        serverJob = scope.launch {
            while (isActive && !isConnected) {
                try {
                    Log.i(TAG, "Starting RFCOMM server on '${CostanzaConstants.COSTANZA_SERVER_NAME}' (UUID: ${CostanzaConstants.COSTANZA_SERVER_UUID})")
                    val sSocket = createServerSocket(adapter)
                    serverSocket = sSocket
                    _connectionState.value = ConnectionState.Listening()

                    val client = sSocket.accept() // Blocks until watch connects
                    Log.i(TAG, "Incoming connection accepted from: ${client.remoteDevice.name} (${client.remoteDevice.address})")

                    // Close server socket while handling active connection
                    try { sSocket.close() } catch (_: Exception) {}
                    serverSocket = null

                    onSocketConnected(client, client.remoteDevice)
                    break
                } catch (e: Exception) {
                    if (!isActive || isConnected) break
                    Log.d(TAG, "Server socket accept interrupted or failed: ${e.message}, retrying in 2s...")
                    delay(2000)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createServerSocket(adapter: BluetoothAdapter): BluetoothServerSocket {
        return try {
            adapter.listenUsingInsecureRfcommWithServiceRecord(
                CostanzaConstants.COSTANZA_SERVER_NAME,
                CostanzaConstants.COSTANZA_SERVER_UUID
            )
        } catch (e: Exception) {
            Log.w(TAG, "Insecure server socket failed: ${e.message}, fallback to secure server socket...")
            adapter.listenUsingRfcommWithServiceRecord(
                CostanzaConstants.COSTANZA_SERVER_NAME,
                CostanzaConstants.COSTANZA_SERVER_UUID
            )
        }
    }

    fun stopServerListener() {
        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    @SuppressLint("MissingPermission")
    fun findSmartWatchDevice(targetAddress: String? = null): BluetoothDevice? {
        val adapter = bluetoothAdapter ?: return null
        val bondedDevices = adapter.bondedDevices ?: return null

        if (!targetAddress.isNullOrBlank()) {
            val dev = bondedDevices.find { it.address.equals(targetAddress, ignoreCase = true) }
            if (dev != null) return dev
        }

        // Auto-detect paired Sony SmartWatch 2
        return bondedDevices.find {
            val name = it.name?.lowercase(Locale.ROOT) ?: ""
            name.contains("smartwatch 2") || name.contains("sw2") || name.contains("smartwatch")
        } ?: bondedDevices.firstOrNull()
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        disconnectInternal()
        _connectionState.value = ConnectionState.Connecting(device.name ?: "SW2", device.address)

        scope.launch {
            try {
                Log.d(TAG, "Attempting active client RFCOMM connection to ${device.name} (${device.address})")
                val socket = createClientSocket(device)
                bluetoothAdapter?.cancelDiscovery()

                socket.connect()
                Log.i(TAG, "Active client RFCOMM socket connected!")
                onSocketConnected(socket, device)
            } catch (e: Exception) {
                Log.e(TAG, "Active connection failed: ${e.message}", e)
                _connectionState.value = ConnectionState.Error("Verbindung fehlgeschlagen: ${e.localizedMessage}")
                disconnectInternal()
                startServerListener()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createClientSocket(device: BluetoothDevice): BluetoothSocket {
        // Priority 1: Force Insecure RFCOMM directly on Channel 1 (Bypasses Android SDP timeout & avoids encryption negotiation)
        try {
            Log.i(TAG, "Creating forced Insecure RFCOMM socket on Channel 1 via reflection...")
            val m = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
            return m.invoke(device, 1) as BluetoothSocket
        } catch (e: Exception) {
            Log.w(TAG, "Reflection createInsecureRfcommSocket(1) failed: ${e.message}, trying SDP UUID...")
        }

        // Priority 2: Insecure RFCOMM via COSTANZA_CLIENT_UUID
        try {
            Log.i(TAG, "Creating Insecure RFCOMM socket via COSTANZA_CLIENT_UUID...")
            return device.createInsecureRfcommSocketToServiceRecord(CostanzaConstants.COSTANZA_CLIENT_UUID)
        } catch (e: Exception) {
            Log.w(TAG, "Insecure client socket via UUID failed: ${e.message}, trying fallback...")
        }

        // Priority 3: Secure reflection port 1 fallback
        val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        return m.invoke(device, 1) as BluetoothSocket
    }

    @SuppressLint("MissingPermission")
    private suspend fun onSocketConnected(socket: BluetoothSocket, device: BluetoothDevice) {
        activeSocket = socket
        inputStream = socket.inputStream
        outputStream = socket.outputStream
        isConnected = true

        _connectionState.value = ConnectionState.Connected(
            device.name ?: "Sony SmartWatch 2",
            device.address
        )

        // Start reader coroutine
        readJob = scope.launch {
            readLoop()
        }

        // Execute official Sony Handshake sequence
        performHandshake()
    }

    private val _lastLogMessage = MutableStateFlow("Bereit")
    val lastLogMessage: StateFlow<String> = _lastLogMessage.asStateFlow()

    private var handshakeStarted = false

    /**
     * Official SmartWatch 2 Runtime Handshake sequence matching Sony CommunicationManager:
     * 1. Send RequestFactoryReset (Action=0 INFO)
     * 2. Wait for ResponseFactoryReset (or fallback timeout)
     * 3. Send RequestVersion (Type 4)
     * 4. Send SetTimeReq (Type 22) + SetTimeZoneReq (Type 133)
     * 5. Send SetLocaleReq (Type 16)
     * 6. DO NOT send ControlMessageAck back to watch for status/version packets!
     */
    private suspend fun performHandshake() {
        Log.i(TAG, "=== Starting Official Sony SW2 Sequential Handshake ===")
        handshakeStarted = false
        _lastLogMessage.value = "--> Sende RequestFactoryReset (INFO)"
        delay(100)

        val resetMsg = RequestFactoryReset(messageId = nextMessageId(), action = RequestFactoryReset.ACTION_INFO, status = 0)
        sendMessage(resetMsg)

        // Fallback: If watch is already initialized and doesn't answer with ResponseFactoryReset, proceed after 800ms
        scope.launch {
            delay(800)
            if (!handshakeStarted && isConnected) {
                Log.i(TAG, "Handshake fallback timeout: Dispatching version & time sync")
                performVersionAndTimeSync()
            }
        }
    }

    private suspend fun performVersionAndTimeSync() {
        if (handshakeStarted) return
        handshakeStarted = true

        Log.i(TAG, "Handshake: Sending RequestVersion (Type 4)")
        _lastLogMessage.value = "--> Sende RequestVersion"
        val verMsg = RequestVersion(messageId = nextMessageId())
        sendMessage(verMsg)
        delay(80)

        Log.i(TAG, "Handshake: Sending Time & TimeZone")
        _lastLogMessage.value = "--> Sende Time & TimeZone"
        syncTime()
        delay(80)

        Log.i(TAG, "Handshake: Sending Locale")
        _lastLogMessage.value = "--> Sende Locale"
        val localeMsg = ControlMessageSetLocaleReq.fromLocale(Locale.getDefault(), nextMessageId())
        sendMessage(localeMsg)
        delay(80)

        // Reset any accidental FOTA update mode (ACTION_RESET = 3) to clear any firmware loading bar
        val fotaReset = RequestFota(messageId = nextMessageId(), action = RequestFota.ACTION_RESET, value = 0)
        sendMessage(fotaReset)
        delay(80)

        // Request battery status via Battery Sensor streaming
        queryBattery()
        delay(80)

        // Clean up any previously registered dummy extensions to purge any white bar placeholders from watch face
        val cidsToPurge = listOf(
            CostanzaConstants.CID_MEDIA_EXTENSION,
            5001,
            5002,
            0x00100001
        )
        for (cid in cidsToPurge) {
            sendMessage(RequestExtension(
                messageId = nextMessageId(),
                action = RequestExtension.ACTION_UNINSTALL,
                value = cid
            ))
            delay(30)
        }

        // Register Notification Extension (CID_NOTIFICATION_EXTENSION) with metadata (Name & 48x48 App Icon)
        Log.i(TAG, "Registering Notification Extension (CID 0x${CostanzaConstants.CID_NOTIFICATION_EXTENSION.toString(16)}) with metadata")
        _lastLogMessage.value = "--> Registriere Benachrichtigungs-Extension"

        val extNameRes = ResourceString(
            messageId = nextMessageId(),
            cid = CostanzaConstants.CID_NOTIFICATION_EXTENSION_NAME,
            text = "SmartWatch 2",
            width = CostanzaConstants.SCREEN_WIDTH,
            height = 28,
            color = -1
        )
        sendMessage(extNameRes)
        delay(40)

        val extIconBytes = renderIcon(48, 48)
        val extIconRes = ResourceCompressedImage.createDeflatedRgb565(
            cid = CostanzaConstants.CID_NOTIFICATION_EXTENSION_ICON,
            width = 48,
            height = 48,
            rawRgb565 = extIconBytes,
            messageId = nextMessageId()
        )
        sendMessage(extIconRes)
        delay(40)

        val notifRes = ResourceExtension(
            messageId = nextMessageId(),
            cid = CostanzaConstants.CID_NOTIFICATION_EXTENSION,
            nameCid = CostanzaConstants.CID_NOTIFICATION_EXTENSION_NAME,
            iconCid = CostanzaConstants.CID_NOTIFICATION_EXTENSION_ICON,
            iconOfflineCid = CostanzaConstants.CID_NOTIFICATION_EXTENSION_ICON,
            capabilities = 1,
            showOnHomeScreen = true
        )
        sendMessage(notifRes)
        delay(40)
        val notifReq = RequestExtension(
            messageId = nextMessageId(),
            action = RequestExtension.ACTION_INSTALL,
            value = CostanzaConstants.CID_NOTIFICATION_EXTENSION
        )
        sendMessage(notifReq)
        delay(40)

        // RequestNotificationList (Typ 135) - Synchronize watch notification database
        val listReq = RawCostanzaMessage(
            type = CostanzaConstants.TYPE_NOTIFICATION_LIST_REQ,
            messageId = nextMessageId(),
            payload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(CostanzaConstants.CID_NOTIFICATION_EXTENSION).array()
        )
        sendMessage(listReq)
        delay(40)

        _lastLogMessage.value = "Verbindung & Benachrichtigungen bereit"
        Log.i(TAG, "=== Clean Handshake completed (Notification Extension registered, showOnHomeScreen=true, list synced) ===")
    }

    fun syncTime() {
        val now = System.currentTimeMillis()
        val offset = TimeZone.getDefault().getOffset(now)
        val localTime = now + offset

        val timeReq = ControlMessageSetTimeReq(nextMessageId(), localTime)
        sendMessage(timeReq)

        val timeZoneReq = ControlMessageSetTimeZoneReq(nextMessageId(), offset)
        sendMessage(timeZoneReq)

        Log.i(TAG, "Sent Time ($localTime) & TimeZone ($offset ms) to watch")
    }

    fun setWallpaper(wallpaperCid: Int) {
        val timestamp = (System.currentTimeMillis() / 1000L).toInt()
        val req = RequestWallpaper(
            messageId = nextMessageId(),
            action = CostanzaConstants.WALLPAPER_ACTION_SET,
            wallpaperCid = wallpaperCid,
            thumbnailCidOrTimestamp = timestamp
        )
        sendMessage(req)
        _activeWallpaperCid.value = wallpaperCid
        Log.i(TAG, "Sent SetWallpaper request for CID $wallpaperCid (0x${wallpaperCid.toString(16)})")
    }

    fun sendVibration(onDurationMs: Int = 200, offDurationMs: Int = 100, repeats: Int = 1) {
        val vib = RequestVibration(
            messageId = nextMessageId(),
            onDurationMs = onDurationMs,
            offDurationMs = offDurationMs,
            repeats = repeats
        )
        sendMessage(vib)
        Log.i(TAG, "Sent Vibration: on=${onDurationMs}ms, off=${offDurationMs}ms, repeats=$repeats")
    }

    fun queryBattery() {
        Log.i(TAG, "Querying battery status (Sensor rate=0)...")
        _lastLogMessage.value = "--> Frage Akkustand ab..."
        // Battery sensor interrupt streaming (sensor 3, mode 1, rate 0 = RATE_USE_INTERRUPT)
        val batterySensorReq = RequestSensorData(
            messageId = nextMessageId(),
            sensorType = CostanzaConstants.SENSOR_TYPE_BATTERY,
            mode = CostanzaConstants.SENSOR_MODE_ON,
            rate = CostanzaConstants.SENSOR_RATE_INTERRUPT
        )
        sendMessage(batterySensorReq)
    }

    private var notificationSequence = 0

    /**
     * Renders a rich notification card (RGB565 raw pixels) containing title and text excerpt.
     * SW2 firmware requires Key 14 (NOTIFICATION_KEY_MESSAGE_WHITE_IMAGE) pointing to a ResourceImage
     * in order to display the notification popup / excerpt card.
     */
    private fun renderNotificationImage(title: String, message: String, width: Int = 196, height: Int = 100): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 14f
            isAntiAlias = true
        }

        // Draw title at top
        canvas.drawText(title.take(22), 6f, 22f, titlePaint)

        // Draw message lines (word wrap)
        val words = message.split(Regex("\\s+"))
        var line = ""
        var y = 44f
        for (w in words) {
            val test = if (line.isEmpty()) w else "$line $w"
            if (textPaint.measureText(test) < (width - 12)) {
                line = test
            } else {
                canvas.drawText(line, 6f, y, textPaint)
                y += 18f
                line = w
                if (y > height - 6) break
            }
        }
        if (line.isNotEmpty() && y <= height - 6) {
            canvas.drawText(line, 6f, y, textPaint)
        }

        // Convert Bitmap pixels to RGB565 Little-Endian ByteArray
        val buffer = ByteBuffer.allocate(width * height * 2).order(ByteOrder.LITTLE_ENDIAN)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (pixel in pixels) {
            val r = (pixel shr 19) and 0x1F
            val g = (pixel shr 10) and 0x3F
            val b = (pixel shr 3) and 0x1F
            buffer.putShort(((r shl 11) or (g shl 5) or b).toShort())
        }
        bitmap.recycle()
        return buffer.array()
    }

    private fun renderIcon(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val bgPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(0, 137, 123) // #00897B Sony Teal
            style = Paint.Style.FILL
        }
        val radius = width * 0.22f
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, bgPaint)

        val iconPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        // Draw speech bubble / notification symbol
        val margin = width * 0.2f
        val bubbleRect = RectF(margin, margin, width - margin, height - margin * 1.15f)
        canvas.drawRoundRect(bubbleRect, width * 0.12f, width * 0.12f, iconPaint)

        val path = Path().apply {
            moveTo(margin + (width - 2 * margin) * 0.25f, height - margin * 1.15f)
            lineTo(margin + (width - 2 * margin) * 0.12f, height - margin * 0.35f)
            lineTo(margin + (width - 2 * margin) * 0.55f, height - margin * 1.15f)
            close()
        }
        canvas.drawPath(path, iconPaint)

        val buffer = ByteBuffer.allocate(width * height * 2).order(ByteOrder.LITTLE_ENDIAN)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (pixel in pixels) {
            val r = (pixel shr 19) and 0x1F
            val g = (pixel shr 10) and 0x3F
            val b = (pixel shr 3) and 0x1F
            buffer.putShort(((r shl 11) or (g shl 5) or b).toShort())
        }
        bitmap.recycle()
        return buffer.array()
    }

    fun sendWatchNotification(title: String, message: String) {
        val seq = (notificationSequence++) % 30
        val notifCid = CostanzaConstants.CID_NOTIFICATION_BASE + seq
        val titleCid = CostanzaConstants.CID_NOTIFICATION_TITLE + seq
        val msgCid = CostanzaConstants.CID_NOTIFICATION_MESSAGE + seq
        val dispCid = CostanzaConstants.CID_NOTIFICATION_DISPLAY_NAME + seq
        val screenCid = CostanzaConstants.CID_NOTIFICATION_SCREEN + seq
        val statusIconCid = CostanzaConstants.CID_NOTIFICATION_ICON_STATUS + seq
        val richIconCid = CostanzaConstants.CID_NOTIFICATION_ICON_RICH + seq
        val imgCid = CostanzaConstants.CID_NOTIFICATION_IMAGE + seq

        Log.i(TAG, "Sending watch notification #$seq: '$title' - '$message' (notifCid=0x${notifCid.toString(16)}, screenCid=0x${screenCid.toString(16)})")
        _lastLogMessage.value = "--> Benachrichtigung #$seq senden"

        // 1. Send Title string resource (White text: w=220, h=36, color=-1)
        val titleRes = ResourceString(
            messageId = nextMessageId(),
            cid = titleCid,
            text = title,
            width = CostanzaConstants.SCREEN_WIDTH,
            height = 36,
            color = -1 // 0xFFFFFFFF = White
        )
        sendMessage(titleRes)

        // 2. Send Display Name string resource (White text: w=220, h=28, color=-1)
        val dispRes = ResourceString(
            messageId = nextMessageId(),
            cid = dispCid,
            text = "SmartWatch 2",
            width = CostanzaConstants.SCREEN_WIDTH,
            height = 28,
            color = -1 // 0xFFFFFFFF = White
        )
        sendMessage(dispRes)

        // 3. Send Message string resource (White text: w=220, h=120, color=-1)
        val msgRes = ResourceString(
            messageId = nextMessageId(),
            cid = msgCid,
            text = message,
            width = CostanzaConstants.SCREEN_WIDTH,
            height = 120,
            color = -1 // 0xFFFFFFFF = White
        )
        sendMessage(msgRes)

        // 4. Send ResourceScreen (CidType 4) referencing message and title CIDs
        val screenRes = ResourceScreen(
            messageId = nextMessageId(),
            cid = screenCid,
            cids = intArrayOf(msgCid, titleCid)
        )
        sendMessage(screenRes)

        // 5. Send Status Bar Icon (20x20) and Rich Notification Icon (40x40)
        val statusIconBytes = renderIcon(20, 20)
        val statusIconRes = ResourceCompressedImage.createDeflatedRgb565(
            cid = statusIconCid,
            width = 20,
            height = 20,
            rawRgb565 = statusIconBytes,
            messageId = nextMessageId()
        )
        sendMessage(statusIconRes)

        val richIconBytes = renderIcon(40, 40)
        val richIconRes = ResourceCompressedImage.createDeflatedRgb565(
            cid = richIconCid,
            width = 40,
            height = 40,
            rawRgb565 = richIconBytes,
            messageId = nextMessageId()
        )
        sendMessage(richIconRes)

        // 6. Render and send Rich Notification Card Image (196x100 white card preview with ZLIB compression)
        val imgWidth = 196
        val imgHeight = 100
        val imgBytes = renderNotificationImage(title, message, imgWidth, imgHeight)
        val imgRes = ResourceCompressedImage.createDeflatedRgb565(
            cid = imgCid,
            width = imgWidth,
            height = imgHeight,
            rawRgb565 = imgBytes,
            messageId = nextMessageId()
        )
        sendMessage(imgRes)

        // 7. Send Notification resource (CidType 7 with SubScreen & Drawer White Image Mappings)
        val notifRes = ResourceNotification(
            messageId = nextMessageId(),
            cid = notifCid,
            notificationExtensionCid = CostanzaConstants.CID_NOTIFICATION_EXTENSION,
            titleCid = titleCid,
            displayNameCid = dispCid,
            messageCid = msgCid,
            statusBarIconCid = statusIconCid,
            richIconCid = richIconCid,
            richNotificationCid = imgCid,
            screenCid = screenCid,
            bitmapRendering = 0, // 0 = native string rendering for drawer & detail view!
            timeStamp = System.currentTimeMillis()
        )
        sendMessage(notifRes)

        // 8. Send RequestNotification (Action = 0 ADDED, value = notifCid)
        val notifReq = RequestNotification(
            messageId = nextMessageId(),
            action = RequestNotification.ACTION_NOTIFICATION_ADDED,
            value = notifCid
        )
        sendMessage(notifReq)

        // 9. Trigger vibration alert
        sendVibration(onDurationMs = 300, offDurationMs = 150, repeats = 2)
    }

    fun sendNotification(action: Int = RequestNotification.ACTION_NOTIFICATION_ADDED, value: Int = 1) {
        val notif = RequestNotification(
            messageId = nextMessageId(),
            action = action,
            value = value
        )
        sendMessage(notif)
        Log.i(TAG, "Sent RequestNotification: action=$action, value=$value")
    }

    @Synchronized
    fun sendMessage(msg: CostanzaMessage): Boolean {
        return try {
            val out = outputStream ?: return false
            val packets = CostanzaPacketCodec.packWithFragments(msg)
            for ((idx, packet) in packets.withIndex()) {
                out.write(packet)
                if (packets.size > 1 && idx < packets.size - 1) {
                    Thread.sleep(5) // Pacing to prevent buffer overflow on SW2 Bluetooth controller
                }
            }
            out.flush()
            if (packets.size > 1) {
                Log.d(TAG, "--> Sent [${msg.javaClass.simpleName}] (fragmented into ${packets.size} packets, total ${packets.sumOf { it.size }} bytes)")
            } else {
                Log.d(TAG, "--> Sent [${msg.javaClass.simpleName}] (${packets[0].size} bytes)")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message [${msg.javaClass.simpleName}]: ${e.message}")
            false
        }
    }

    private suspend fun readLoop() = withContext(Dispatchers.IO) {
        val inStream = inputStream ?: return@withContext
        val headerBuf = ByteArray(CostanzaConstants.HEADER_SIZE)

        while (isConnected && isActive) {
            try {
                // Read 12-byte header
                readFully(inStream, headerBuf, 0, CostanzaConstants.HEADER_SIZE)

                val (totalLength, type, messageId) = CostanzaPacketCodec.parseHeader(headerBuf)
                if (totalLength < CostanzaConstants.HEADER_SIZE || totalLength > 65536) {
                    Log.e(TAG, "Invalid packet length: $totalLength, disconnecting")
                    break
                }

                val payloadSize = totalLength - CostanzaConstants.HEADER_SIZE
                val fullPacket = ByteArray(totalLength)
                System.arraycopy(headerBuf, 0, fullPacket, 0, CostanzaConstants.HEADER_SIZE)

                if (payloadSize > 0) {
                    readFully(inStream, fullPacket, CostanzaConstants.HEADER_SIZE, payloadSize)
                }

                // Decode message
                val msg = CostanzaPacketCodec.unpack(fullPacket)
                if (msg is CostanzaFragment) {
                    Log.d(TAG, "<-- Fragment received: ${msg.fragIdx + 1}/${msg.fragTotal} (origMsgId=${msg.origMsgId}, len=${msg.data.size})")
                    val assembled = fragmentAssembler.addFragment(msg)
                    if (assembled != null) {
                        val completeMsg = CostanzaPacketCodec.unpack(assembled)
                        Log.i(TAG, "<-- Reassembled complete message: [${completeMsg.javaClass.simpleName}] (type=${completeMsg.type}, msgId=${completeMsg.messageId})")
                        handleIncomingMessage(completeMsg)
                    }
                } else {
                    Log.d(TAG, "<-- Received [${msg.javaClass.simpleName}] (type=$type, msgId=$messageId, len=$totalLength)")
                    handleIncomingMessage(msg)
                }
            } catch (e: Exception) {
                if (isConnected) {
                    Log.w(TAG, "Read loop terminated: ${e.message}")
                    _lastLogMessage.value = "Abbruch: ${e.message}"
                    _connectionState.value = ConnectionState.Error("Verbindung unterbrochen: ${e.localizedMessage}")
                }
                break
            }
        }
        disconnectInternal()
        startServerListener()
    }

    private suspend fun handleIncomingMessage(msg: CostanzaMessage) {
        _lastLogMessage.value = "<-- [${msg.javaClass.simpleName}] Typ=${msg.type} ID=${msg.messageId}"

        // Note: As verified from original Sony CommunicationManager, phone NEVER sends ControlMessageAck to the watch for incoming indications!

        when (msg) {
            is ResponseFactoryReset -> {
                Log.i(TAG, "Watch ResponseFactoryReset: status=${msg.status}")
                scope.launch { performVersionAndTimeSync() }
            }
            is ResponseVersion -> {
                Log.i(TAG, "Watch ResponseVersion: protocolVersion=0x${msg.protocolVersion.toString(16)}, fw=${msg.firmwareVersion}")
                _firmwareVersion.value = msg.firmwareVersion
                _lastLogMessage.value = "FW: ${msg.firmwareVersion} (0x${msg.protocolVersion.toString(16)})"
            }
            is ResponseFota -> {
                Log.i(TAG, "Watch ResponseFota: action=${msg.action}, val=${msg.value}")
            }
            is IndicationBatteryLevel -> {
                Log.i(TAG, "Watch battery level: ${msg.percentage}%")
                _batteryLevel.value = msg.percentage
                _lastLogMessage.value = "Akku: ${msg.percentage}%"
            }
            is IndicationSensorData -> {
                Log.i(TAG, "Watch IndicationSensorData: type=${msg.sensorType}, acc=${msg.accuracy}, data=${msg.data.contentToString()}")
                if (msg.sensorType == CostanzaConstants.SENSOR_TYPE_BATTERY && msg.data.isNotEmpty()) {
                    val battery = msg.data[0]
                    Log.i(TAG, "Watch battery level reported via sensor: $battery%")
                    _batteryLevel.value = battery
                    _lastLogMessage.value = "Akku: $battery%"
                }
            }
            is IndicationCtrl -> {
                Log.i(TAG, "Watch IndicationCtrl: action=${msg.action}, cid=${msg.cid}")
                _lastLogMessage.value = "Ctrl: CID=${msg.cid} Act=${msg.action}"
            }
            is ResponseExtension -> {
                Log.i(TAG, "Watch ResponseExtension: action=${msg.action}, val=${msg.value}, extCid=${msg.extensionCid}")
            }
            is ResponseSensorData -> {
                Log.i(TAG, "Watch ResponseSensorData: status=${msg.status}")
            }
            is RequestSyncTime -> {
                Log.i(TAG, "Watch requested sync time, sending time...")
                syncTime()
            }
            is ControlMessageAck -> {
                Log.d(TAG, "Watch ACKed message ID ${msg.ackMessageId}")
            }
            is ControlMessageNack -> {
                Log.w(TAG, "Watch NACKed message ID ${msg.nackMessageId}")
            }
            is ResponseWallpaper -> {
                Log.i(TAG, "Wallpaper response: action=${msg.action}, result=${msg.result}, values=${msg.values.contentToString()}")
                if (msg.values.isNotEmpty()) {
                    _activeWallpaperCid.value = msg.values[0]
                }
            }
            is IndicationWallpaper -> {
                Log.i(TAG, "Watch wallpaper changed by user to CID 0x${msg.wallpaperCid.toString(16)}")
                _activeWallpaperCid.value = msg.wallpaperCid
            }
            is IndicationTouch -> {
                Log.d(TAG, "Touch event: ${msg.x}, ${msg.y}")
            }
            is IndicationSwipe -> {
                Log.d(TAG, "Swipe event: direction=${msg.direction}")
            }
            is IndicationKey -> {
                Log.d(TAG, "Key event: code=${msg.code}, action=${msg.action}")
            }
            else -> {}
        }
        _incomingMessages.emit(msg)
    }

    private fun readFully(stream: InputStream, buffer: ByteArray, offset: Int, length: Int) {
        var bytesRead = 0
        while (bytesRead < length) {
            val count = stream.read(buffer, offset + bytesRead, length - bytesRead)
            if (count < 0) {
                throw java.io.EOFException("End of stream reached after $bytesRead of $length bytes")
            }
            bytesRead += count
        }
    }

    fun disconnect() {
        disconnectInternal()
        startServerListener()
    }

    private fun disconnectInternal() {
        isConnected = false
        readJob?.cancel()
        readJob = null

        try { inputStream?.close() } catch (_: Exception) {}
        try { outputStream?.close() } catch (_: Exception) {}
        try { activeSocket?.close() } catch (_: Exception) {}

        inputStream = null
        outputStream = null
        activeSocket = null
        fragmentAssembler.clear()

        if (_connectionState.value !is ConnectionState.Error) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    fun release() {
        disconnectInternal()
        stopServerListener()
        scope.cancel()
    }
}
