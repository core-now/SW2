package com.sonymobile.smartconnect.hostapp.connection;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import com.sonyericsson.extras.liveware.aef.notification.Notification;
import com.sonymobile.smartconnect.hostapp.Dbg;
import com.sonymobile.smartconnect.hostapp.analytics.Analytics;
import com.sonymobile.smartconnect.hostapp.costanza.CostanzaHostApplication;
import com.sonymobile.smartconnect.hostapp.costanza.FactoryResetter;
import com.sonymobile.smartconnect.hostapp.costanza.db.MessageIdProvider;
import com.sonymobile.smartconnect.hostapp.costanza.db.SyncData;
import com.sonymobile.smartconnect.hostapp.extensions.Extension;
import com.sonymobile.smartconnect.hostapp.extensions.ExtensionManager;
import com.sonymobile.smartconnect.hostapp.fota.FotaController;
import com.sonymobile.smartconnect.hostapp.notification.Event;
import com.sonymobile.smartconnect.hostapp.notification.EventManager;
import com.sonymobile.smartconnect.hostapp.protocol.CostanzaMessage;
import com.sonymobile.smartconnect.hostapp.protocol.CostanzaResource;
import com.sonymobile.smartconnect.hostapp.protocol.IndicationBatteryLevel;
import com.sonymobile.smartconnect.hostapp.protocol.IndicationNotificationRead;
import com.sonymobile.smartconnect.hostapp.protocol.Proto;
import com.sonymobile.smartconnect.hostapp.protocol.RequestFactoryReset;
import com.sonymobile.smartconnect.hostapp.protocol.RequestForceCrash;
import com.sonymobile.smartconnect.hostapp.protocol.RequestNotificationList;
import com.sonymobile.smartconnect.hostapp.protocol.RequestVersion;
import com.sonymobile.smartconnect.hostapp.protocol.ResponseExtension;
import com.sonymobile.smartconnect.hostapp.protocol.ResponseFactoryReset;
import com.sonymobile.smartconnect.hostapp.protocol.ResponseFota;
import com.sonymobile.smartconnect.hostapp.protocol.ResponseNotificationList;
import com.sonymobile.smartconnect.hostapp.protocol.ResponseVersion;
import com.sonymobile.smartconnect.hostapp.protocol.shared.Types;
import com.sonymobile.smartconnect.hostapp.service.HostAppService;
import com.sonymobile.smartconnect.hostapp.service.TimeAndLocaleSender;
import com.sonymobile.smartconnect.hostapp.util.WakeLockManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/* JADX INFO: loaded from: classes.dex */
public class CommunicationManager extends Proto implements ConnectionListener, CostanzaMessageSender {
    private static final int APPROX_MAX_MESSAGE_SIZE = 1024;
    public static final String INTENT_ACTION_APP_MANAGER_RESULT = "com.sonymobile.smartconnect.smartwatch2.action.service.INTENT_ACTION_APP_MANAGER_RESULT";
    private static final int OUTPUT_BUFFER_SIZE = 131072;
    private static final int RECEIVE_COSTANZA_MESSAGE = 1;
    private static final int SEND_COSTANZA_MESSAGE = 0;
    private static final int SIZE_LENGTH = 4;
    private static final String SYNCED_DEVICE_ADDRESS_KEY = "synced_device";
    private final Context ctxt;
    private ConnectionManager mConnectionManager;
    private final FactoryResetter mFactoryResetter;
    private final FotaResponseListener mFotaResponseListener;
    private InputStream mInputStream;
    private final HandlerThread mMessageReceiverHandlerThread;
    private final MessageIdProvider mMsgIdProvider;
    private final String mOurFirmwareVersion;
    private final ReceiveMessageHandler mReceiveMessageHandler;
    private int mRemainingListMessages;
    private final ResourceSendTracker mResourceTracker;
    private final SendMessageHandler mSendMessageHandler;
    private String mSyncedDeviceAddress;
    private String mTheirFirmwareVersion;
    private final TimeAndLocaleSender mTimeAndLocaleSender;
    private final Hashtable<Integer, List<CostanzaMessageListener>> mCostanzaMessageListeners = new Hashtable<>();
    private final int mOurProtoVersion = Types.TYPE_PROTOCOL_VERSION_NUMBER;
    private int mTheirProtoVersion = 1;
    private ConnectionState mState = ConnectionState.DISCONNECTED;
    byte[] mOutBuffer = new byte[131072];
    private final SendQueue normalQ = new SendQueue(SendQueue.Category.NORMAL);
    private final SendQueue prioQ = new SendQueue(SendQueue.Category.PRIORITIZED);
    private final SendQueue fotaQ = new SendQueue(SendQueue.Category.FOTA);
    private final SendQueue fotaProgressQ = new SendQueue(SendQueue.Category.FOTA_PROGRESS);
    private final CopyOnWriteArrayList<CommunicationListener> mCommunicationListeners = new CopyOnWriteArrayList<>();
    private final Stack<CostanzaMessageSender.SendPriority> mSendPriority = new Stack<>();
    private final byte[] mReadBuf = new byte[1024];
    int mReadOffset = 0;
    private final HandlerThread mMessageSenderHandlerThread = new HandlerThread("MessageSender");

    public interface CommunicationListener {
        void onConnect();

        void onDisconnect();

        void onNewFirmwareAccessoryConnected();

        void onOldFirmwareAccessoryConnected();

        void onReady();
    }

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTED,
        READY,
        CONNECTED_TO_OLDER_FIRMWARE,
        CONNECTED_TO_NEWER_FIRMWARE
    }

    public interface CostanzaMessageListener {
        int getMessageTypeToListenFor();

        void onMessage(CostanzaMessage costanzaMessage);
    }

    public interface FotaResponseListener {
        void onFotaBatteryLevelIndication(IndicationBatteryLevel indicationBatteryLevel);

        void onFotaResponse(ResponseFota responseFota);
    }

    static /* synthetic */ int access$2206(CommunicationManager x0) {
        int i = x0.mRemainingListMessages - 1;
        x0.mRemainingListMessages = i;
        return i;
    }

    public CommunicationManager(Context c, String embeddedfirmwareVersion, FotaResponseListener fotaResponseListener, MessageIdProvider msgIdProvider, FactoryResetter factoryResetter) {
        this.ctxt = c;
        this.mMessageSenderHandlerThread.setPriority(5);
        this.mMessageSenderHandlerThread.start();
        this.mSendMessageHandler = new SendMessageHandler(this.mMessageSenderHandlerThread.getLooper());
        this.mMessageReceiverHandlerThread = new HandlerThread("MessageReceiver");
        this.mMessageReceiverHandlerThread.setPriority(5);
        this.mMessageReceiverHandlerThread.start();
        this.mReceiveMessageHandler = new ReceiveMessageHandler(this.mMessageReceiverHandlerThread.getLooper());
        this.mOurFirmwareVersion = embeddedfirmwareVersion;
        this.mFotaResponseListener = fotaResponseListener;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.ctxt);
        this.mSyncedDeviceAddress = prefs.getString(SYNCED_DEVICE_ADDRESS_KEY, null);
        this.mMsgIdProvider = msgIdProvider;
        this.mTimeAndLocaleSender = new TimeAndLocaleSender(msgIdProvider, this);
        this.mFactoryResetter = factoryResetter;
        this.mResourceTracker = new ResourceSendTracker(this);
    }

    public void closeConnections() {
        if (this.mConnectionManager != null && this.mConnectionManager.isConnected()) {
            this.mConnectionManager.closeActiveConnection();
        }
    }

    public synchronized void setConnectionManager(ConnectionManager connectionManager) {
        if (this.mConnectionManager == connectionManager) {
            if (Dbg.d()) {
                Dbg.d("ConnectionManager.setConnectionManager(), same manager supplied. Ignoring.");
            }
        } else {
            if (this.mConnectionManager != null) {
                this.mConnectionManager.closeActiveConnection();
                this.mConnectionManager.stopAcceptingConnections();
                this.mConnectionManager.removeConnectionListener(this);
                if (Dbg.d()) {
                    Dbg.d("ConnectionManager.setConnectionManager(), ConnectionManager overriden!");
                }
            } else if (Dbg.d()) {
                Dbg.d("ConnectionManager.setConnectionManager() first set!");
            }
            this.mConnectionManager = connectionManager;
            this.mConnectionManager.addConnectionListener(this);
            this.mConnectionManager.startAcceptingConnections();
        }
    }

    public ConnectionManager getConnectionManager() {
        return this.mConnectionManager;
    }

    public synchronized String getAccessoryFirmwareVersion() {
        return isConnected() ? this.mTheirFirmwareVersion : null;
    }

    public synchronized ConnectionState getConnectionState() {
        return this.mState;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public synchronized void setConnectionState(ConnectionState state) {
        switch (state) {
            case CONNECTED:
                notifyOnConnect();
                this.mState = state;
                break;
            case CONNECTED_TO_NEWER_FIRMWARE:
                notifyNewFirmwareAccessoryConnected();
                this.mState = state;
                break;
            case CONNECTED_TO_OLDER_FIRMWARE:
                notifyOldFirmwareAccessoryConnected();
                this.mState = state;
                break;
            case READY:
                notifyOnReady();
                this.mState = state;
                break;
            case DISCONNECTED:
                notifyOnDisconnect();
                this.mState = state;
                break;
            default:
                if (Dbg.e()) {
                    Dbg.e("setConnectionState() isn't checking state: " + state);
                }
                break;
        }
    }

    public void connect(String address) {
        if (this.mConnectionManager != null) {
            this.mConnectionManager.connect(address);
        }
    }

    public boolean isConnected() {
        ConnectionState state = getConnectionState();
        switch (state) {
            case CONNECTED:
            case CONNECTED_TO_NEWER_FIRMWARE:
            case CONNECTED_TO_OLDER_FIRMWARE:
            case READY:
                return true;
            case DISCONNECTED:
                return false;
            default:
                if (!Dbg.e()) {
                    return false;
                }
                Dbg.e("isConnected() isn't checking all states!");
                return false;
        }
    }

    @Override // com.sonymobile.smartconnect.hostapp.connection.CostanzaMessageSender
    public boolean isReady() {
        ConnectionState state = getConnectionState();
        switch (state) {
            case CONNECTED:
            case CONNECTED_TO_NEWER_FIRMWARE:
            case CONNECTED_TO_OLDER_FIRMWARE:
            case DISCONNECTED:
                return false;
            case READY:
                return true;
            default:
                if (!Dbg.e()) {
                    return false;
                }
                Dbg.e("isReady() isn't checking all states!");
                return false;
        }
    }

    public synchronized void addCommunicationListener(CommunicationListener listener) {
        if (!this.mCommunicationListeners.contains(listener)) {
            this.mCommunicationListeners.add(listener);
        }
    }

    public synchronized void removeCommunicationListener(CommunicationListener listener) {
        if (this.mCommunicationListeners != null) {
            this.mCommunicationListeners.remove(listener);
        }
    }

    private synchronized void notifyOnConnect() {
        for (CommunicationListener listener : this.mCommunicationListeners) {
            listener.onConnect();
        }
    }

    private synchronized void notifyOldFirmwareAccessoryConnected() {
        for (CommunicationListener listener : this.mCommunicationListeners) {
            listener.onOldFirmwareAccessoryConnected();
        }
    }

    private synchronized void notifyNewFirmwareAccessoryConnected() {
        for (CommunicationListener listener : this.mCommunicationListeners) {
            listener.onNewFirmwareAccessoryConnected();
        }
    }

    private synchronized void notifyOnReady() {
        this.mResourceTracker.resendTrackedMessages();
        for (CommunicationListener listener : this.mCommunicationListeners) {
            listener.onReady();
        }
    }

    private synchronized void notifyOnDisconnect() {
        for (CommunicationListener listener : this.mCommunicationListeners) {
            listener.onDisconnect();
        }
    }

    private void send(Iterable<CostanzaMessage> messages, ConcurrentLinkedQueue<CostanzaMessage> queue) {
        for (CostanzaMessage message : messages) {
            if (message.getMessageId() == -1) {
                message.setMessageId(this.mMsgIdProvider.getNewMessageId());
            }
            queue.add(message);
        }
        if (isConnected()) {
            this.mSendMessageHandler.sendMessage(Message.obtain(this.mSendMessageHandler, 0));
        }
    }

    private void send(ConcurrentLinkedQueue<CostanzaMessage> queue, CostanzaMessage... messages) {
        if (messages != null && messages.length > 0) {
            send(Arrays.asList(messages), queue);
        }
    }

    @Override // com.sonymobile.smartconnect.hostapp.connection.CostanzaMessageSender
    public void send(SendQueue.Category category, CostanzaMessage... messages) {
        send(getQueue(category), messages);
    }

    private synchronized ConcurrentLinkedQueue<CostanzaMessage> getQueue(SendQueue.Category category) {
        SendQueue sendQueue;
        switch (category) {
            case FOTA:
                sendQueue = this.fotaQ;
                break;
            case FOTA_PROGRESS:
                sendQueue = this.fotaProgressQ;
                break;
            case PRIORITIZED:
                sendQueue = this.prioQ;
                break;
            default:
                sendQueue = this.normalQ;
                break;
        }
        return sendQueue;
    }

    @Override // com.sonymobile.smartconnect.hostapp.connection.CostanzaMessageSender
    public void send(Iterable<CostanzaMessage> messages) {
        send(messages, getCurrentQueue());
    }

    @Override // com.sonymobile.smartconnect.hostapp.connection.CostanzaMessageSender
    public void send(CostanzaMessage... messages) {
        send(getCurrentQueue(), messages);
    }

    private ConcurrentLinkedQueue<CostanzaMessage> getCurrentQueue() {
        CostanzaMessageSender.SendPriority prio = this.mSendPriority.isEmpty() ? CostanzaMessageSender.SendPriority.NORMAL : this.mSendPriority.peek();
        switch (prio) {
            case HIGHEST:
                return this.fotaQ;
            case HIGH:
                return this.prioQ;
            default:
                return this.normalQ;
        }
    }

    @Override // com.sonymobile.smartconnect.hostapp.connection.CostanzaMessageSender
    public void sendPrioritized(Iterable<CostanzaMessage> messages) {
        send(messages, this.prioQ);
    }

    @Override // com.sonymobile.smartconnect.hostapp.connection.CostanzaMessageSender
    public void sendPrioritized(CostanzaMessage... messages) {
        send(this.prioQ, messages);
    }

    public void sendFotaMessages(Iterable<CostanzaMessage> messages) {
        send(messages, this.fotaQ);
    }

    @Override // com.sonymobile.smartconnect.hostapp.connection.CostanzaMessageSender
    public void sendFotaMessages(CostanzaMessage... messages) {
        send(this.fotaQ, messages);
    }

    @Override // com.sonymobile.smartconnect.hostapp.connection.CostanzaMessageSender
    public void sendFotaProgress(CostanzaMessage message) {
        if (message != null) {
            send(this.fotaProgressQ, message);
        }
    }

    public void sendFactoryResetMessage() {
        if (Dbg.d()) {
            Dbg.d("Executing factory reset");
        }
        RequestFactoryReset resetMsg = new RequestFactoryReset(this.mMsgIdProvider.getNewMessageId());
        resetMsg.setAction(1);
        sendFotaMessages(resetMsg);
    }

    public void sendForceCrashMessage() {
        if (Dbg.d()) {
            Dbg.d("Executing force crash");
        }
        RequestForceCrash forceCrashMsg = new RequestForceCrash(this.mMsgIdProvider.getNewMessageId());
        send(forceCrashMsg);
    }

    public void receive(CostanzaMessage message) {
        this.mReceiveMessageHandler.sendMessage(Message.obtain(this.mReceiveMessageHandler, 1, message));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public EventManager getEventManager(Context ctxt) {
        CostanzaHostApplication hostApp = (CostanzaHostApplication) ctxt.getApplicationContext();
        return hostApp.getEventManager();
    }

    private ExtensionManager getExtensionManager(Context ctxt) {
        CostanzaHostApplication hostApp = (CostanzaHostApplication) ctxt.getApplicationContext();
        return hostApp.getExtensionManager();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public FotaController getFotaController(Context ctxt) {
        CostanzaHostApplication hostApp = (CostanzaHostApplication) ctxt.getApplicationContext();
        return hostApp.getFotaController();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void performHandshake() {
        sendFotaMessages(new RequestVersion(this.mMsgIdProvider.getNewMessageId()));
        this.mTimeAndLocaleSender.sendTimeAndTimeZone();
        this.mTimeAndLocaleSender.sendLocale();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void requestNotificationList() {
        ExtensionManager extensionManager = getExtensionManager(this.ctxt);
        ArrayList<Extension> notificationExtensions = extensionManager.getNotificationExtensions();
        this.mRemainingListMessages = notificationExtensions.size();
        if (this.mRemainingListMessages == 0) {
            setConnectionState(ConnectionState.READY);
            return;
        }
        for (Extension extension : notificationExtensions) {
            RequestNotificationList msg = new RequestNotificationList(this.mMsgIdProvider.getNewMessageId());
            msg.setExtensionCid(extension.getCid());
            sendFotaMessages(msg);
        }
    }

    @Override // com.sonymobile.smartconnect.hostapp.connection.ConnectionListener
    public void onConnected() {
        setConnectionState(ConnectionState.CONNECTED);
        RequestFactoryReset msg = new RequestFactoryReset(this.mMsgIdProvider.getNewMessageId());
        msg.setAction(0);
        sendFotaMessages(msg);
        if (this.mConnectionManager.isConnected()) {
            this.mSendMessageHandler.sendMessage(Message.obtain(this.mSendMessageHandler, 0));
        }
        if (Dbg.d()) {
            Dbg.d("CommunicationManager - onConnected - start receiving messages...");
        }
        this.mReadOffset = 0;
        startReader();
    }

    private void startReader() {
        Runnable r = new Runnable() { // from class: com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.1
            @Override // java.lang.Runnable
            public void run() {
                int readEx = 0;
                CommunicationManager.this.mInputStream = CommunicationManager.this.mConnectionManager.getInputStream();
                while (CommunicationManager.this.mInputStream != null && readEx < 10) {
                    try {
                        CostanzaMessage msg = CommunicationManager.this.read();
                        if (msg == null) {
                            throw new IOException();
                        }
                        CommunicationManager.this.receive(msg);
                        readEx = 0;
                    } catch (IOException e) {
                        if (Dbg.e()) {
                            Dbg.e("Communications manager got IOException during read in onConnected.", e);
                        }
                        try {
                            Thread.sleep(100L);
                        } catch (InterruptedException e2) {
                            if (Dbg.e()) {
                                Dbg.e(e2);
                            }
                        }
                        CommunicationManager.this.mInputStream = CommunicationManager.this.mConnectionManager.getInputStream();
                        readEx++;
                    }
                }
                if (Dbg.d()) {
                    Dbg.d("CommunicationManager reader exiting to wait for reconnect.");
                }
            }
        };
        new Thread(r).start();
    }

    @Override // com.sonymobile.smartconnect.hostapp.connection.ConnectionListener
    public void onDisconnected() {
        if (Dbg.d()) {
            Dbg.d("CommunicationManager - onDisconnected!");
        }
        if (isConnected()) {
            setConnectionState(ConnectionState.DISCONNECTED);
        }
    }

    @Override // com.sonymobile.smartconnect.hostapp.protocol.Proto
    protected CostanzaMessage read() {
        CostanzaMessage cmsg = null;
        while (cmsg == null) {
            int sizeBytes = 4 - this.mReadOffset;
            while (this.mReadOffset < 4) {
                try {
                    int readBytes = this.mInputStream.read(this.mReadBuf, this.mReadOffset, sizeBytes);
                    if (readBytes > 0) {
                        sizeBytes -= readBytes;
                        this.mReadOffset += readBytes;
                    }
                } catch (IOException e) {
                    if (Dbg.e()) {
                        Dbg.e("Communications manager got an IOException during buffer read and will return null.", e);
                    }
                    return null;
                } catch (IndexOutOfBoundsException e2) {
                    if (Dbg.e()) {
                        Dbg.e("Communications manager got an IndexOutOfBoundsException during buffer read. We can not recover from this, reconnecting! ", e2);
                    }
                    this.mConnectionManager.reconnect();
                    return null;
                }
            }
            int reslen = bytesToInt(this.mReadBuf, 4);
            int bytesLeft = reslen - this.mReadOffset;
            while (bytesLeft > 0) {
                int readBytes2 = this.mInputStream.read(this.mReadBuf, this.mReadOffset, bytesLeft);
                if (readBytes2 > 0) {
                    this.mReadOffset += readBytes2;
                    bytesLeft -= readBytes2;
                }
            }
            this.mReadOffset = 0;
            cmsg = unpack(this.mReadBuf);
        }
        return cmsg;
    }

    private int bytesToInt(byte[] bArr, int size) {
        int result = 0;
        for (int i = 0; i < size; i++) {
            result += (bArr[i] < 0 ? bArr[i] + 256 : bArr[i]) << (i * 8);
        }
        return result;
    }

    @Override // com.sonymobile.smartconnect.hostapp.protocol.Proto
    protected void write(CostanzaMessage message) throws IOException {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void write(SendQueue queue) throws IOException {
        OutputStream out = this.mConnectionManager.getOutputStream();
        if (out == null) {
            throw new IOException("Output stream is null.");
        }
        ByteBuffer byteBuffer = ByteBuffer.wrap(this.mOutBuffer);
        int messagesWritten = 0;
        for (CostanzaMessage message : queue) {
            byte[][] parts = message.getPacked(this);
            if (parts != null) {
                int totalLength = 0;
                for (byte[] bArr : parts) {
                    totalLength += bArr.length;
                }
                if (byteBuffer.remaining() <= totalLength) {
                    break;
                }
                int partIndex = 0;
                for (byte[] part : parts) {
                    byteBuffer.put(part);
                    if (Dbg.v()) {
                        Dbg.v("Wrote %s part %d/%d (%d bytes) to buffer from %s.", message, Integer.valueOf(partIndex + 1), Integer.valueOf(parts.length), Integer.valueOf(part.length), queue.category());
                    }
                    partIndex++;
                }
                messagesWritten++;
            }
        }
        for (int i = 0; i < messagesWritten; i++) {
            CostanzaMessage message2 = queue.poll();
            if (message2 == null) {
                break;
            }
            if (message2 instanceof CostanzaResource) {
                this.mResourceTracker.track((CostanzaResource) message2, queue.category());
            }
        }
        out.write(this.mOutBuffer, 0, byteBuffer.position());
        if (Dbg.v()) {
            Dbg.v("Wrote %d msgs (total %d bytes) from %s to output stream.", Integer.valueOf(messagesWritten), Integer.valueOf(byteBuffer.position()), queue.category());
        }
    }

    @Override // com.sonymobile.smartconnect.hostapp.protocol.Proto
    protected void write(byte[] messagePart) throws IOException {
        OutputStream out = this.mConnectionManager.getOutputStream();
        if (out == null) {
            throw new IOException("Output stream is null.");
        }
        out.write(messagePart);
    }

    private class SendMessageHandler extends Handler {
        final int MAX_RETRIES;
        WakeLockManager mWakeLockManager;

        SendMessageHandler(Looper looper) {
            super(looper);
            this.mWakeLockManager = WakeLockManager.getInstance(CommunicationManager.this.ctxt);
            this.MAX_RETRIES = 5;
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            int retries = 0;
            this.mWakeLockManager.acquire(WakeLockManager.PowerUser.OUTGOING_MESSAGE);
            if (msg.what == 0) {
                while (retries < 5) {
                    try {
                        if (!CommunicationManager.this.fotaProgressQ.isEmpty()) {
                            CommunicationManager.this.write(CommunicationManager.this.fotaProgressQ);
                        } else if (!CommunicationManager.this.fotaQ.isEmpty()) {
                            CommunicationManager.this.write(CommunicationManager.this.fotaQ);
                        } else {
                            if (!CommunicationManager.this.isReady()) {
                                break;
                            }
                            if (!CommunicationManager.this.prioQ.isEmpty()) {
                                CommunicationManager.this.write(CommunicationManager.this.prioQ);
                            } else if (CommunicationManager.this.normalQ.isEmpty()) {
                                break;
                            } else {
                                CommunicationManager.this.write(CommunicationManager.this.normalQ);
                            }
                        }
                    } catch (IOException e) {
                        retries = retry(retries, e);
                    }
                }
            }
            this.mWakeLockManager.release(WakeLockManager.PowerUser.OUTGOING_MESSAGE);
        }

        private int retry(int retries, IOException e) {
            if (retries < 5) {
                if (Dbg.e()) {
                    Dbg.e("Communications manager got IOException during message write. Retrying.", e);
                }
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException e2) {
                }
            } else if (Dbg.e()) {
                Dbg.e("Communications manager got IOException during message write. Waiting for accessory to reconnect.", e);
            }
            return retries + 1;
        }
    }

    private class ReceiveMessageHandler extends Handler {
        private final WakeLockManager mWakeLockManager;

        ReceiveMessageHandler(Looper looper) {
            super(looper);
            this.mWakeLockManager = WakeLockManager.getInstance(CommunicationManager.this.ctxt);
        }

        @Override // android.os.Handler
        public void handleMessage(Message msg) {
            this.mWakeLockManager.poke();
            this.mWakeLockManager.acquire(WakeLockManager.PowerUser.INCOMING_MESSAGE);
            if (msg.what == 1) {
                CostanzaMessage cmsg = (CostanzaMessage) msg.obj;
                CommunicationManager.this.notifyListeners(cmsg);
            }
            this.mWakeLockManager.release(WakeLockManager.PowerUser.INCOMING_MESSAGE);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void notifyListeners(CostanzaMessage cmsg) {
        if (Dbg.v()) {
            Dbg.v("Received msg: %s.", cmsg);
        }
        List<CostanzaMessageListener> listeners = getListeners(cmsg.getType());
        if (listeners != null) {
            for (CostanzaMessageListener listener : listeners) {
                listener.onMessage(cmsg);
            }
            return;
        }
        if (Dbg.w()) {
            Dbg.w("No listener registered for received message, type: %d", Integer.valueOf(cmsg.getType()));
        }
    }

    public void registerMessageListener(CostanzaMessageListener listener) {
        int msgType = listener.getMessageTypeToListenFor();
        List<CostanzaMessageListener> listeners = getListeners(msgType);
        if (listeners == null) {
            listeners = new LinkedList<>();
            this.mCostanzaMessageListeners.put(Integer.valueOf(msgType), listeners);
        }
        listeners.add(listener);
    }

    private List<CostanzaMessageListener> getListeners(int msgType) {
        return this.mCostanzaMessageListeners.get(Integer.valueOf(msgType));
    }

    public void registerDefaultMessageListeners() {
        registerMessageListener(new CostanzaMessageListener() { // from class: com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.1ResponseVersionListener
            @Override // com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.CostanzaMessageListener
            public int getMessageTypeToListenFor() {
                return 5;
            }

            @Override // com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.CostanzaMessageListener
            public void onMessage(CostanzaMessage msg) {
                ResponseVersion protoVersionMsg = (ResponseVersion) msg;
                CommunicationManager.this.mTheirProtoVersion = protoVersionMsg.getProtocolVersion();
                CommunicationManager.this.mTheirFirmwareVersion = protoVersionMsg.getFirmwareVersion();
                if (1048679 <= CommunicationManager.this.mTheirProtoVersion && FotaController.compareFirmware(CommunicationManager.this.mOurFirmwareVersion, CommunicationManager.this.mTheirFirmwareVersion) <= 0) {
                    if (1048679 < CommunicationManager.this.mTheirProtoVersion || FotaController.compareFirmware(CommunicationManager.this.mOurFirmwareVersion, CommunicationManager.this.mTheirFirmwareVersion) < 0) {
                        if (Dbg.d()) {
                            Dbg.d("Communications manager: Accessory is using newer protocol or firmware!(HostApp: proto=1048679, fw=" + CommunicationManager.this.mOurFirmwareVersion + ". Accessory: proto=" + CommunicationManager.this.mTheirProtoVersion + ", fw=" + CommunicationManager.this.mTheirFirmwareVersion + ".)");
                        }
                        CommunicationManager.this.setConnectionState(ConnectionState.CONNECTED_TO_NEWER_FIRMWARE);
                        return;
                    }
                    if (Dbg.d()) {
                        Dbg.d("Communications manager: Accessory using matching protocol and firmware!(HostApp: proto=1048679, fw=" + CommunicationManager.this.mOurFirmwareVersion + ". Accessory: proto=" + CommunicationManager.this.mTheirProtoVersion + ", fw=" + CommunicationManager.this.mTheirFirmwareVersion + ".)");
                    }
                    if (Dbg.d()) {
                        Dbg.d("Checking shared prefs for reset");
                    }
                    boolean reset = CommunicationManager.this.getResetFlag();
                    if (!reset) {
                        CommunicationManager.this.requestNotificationList();
                        return;
                    }
                    if (Dbg.d()) {
                        Dbg.d("Forced factory reset from prefs");
                    }
                    CommunicationManager.this.clearResetFlag();
                    CommunicationManager.this.sendFactoryResetMessage();
                    return;
                }
                if (Dbg.d()) {
                    Dbg.d("Communications manager: Accessory is using old protocol or firmware!(HostApp: proto=1048679, fw=" + CommunicationManager.this.mOurFirmwareVersion + ". Accessory: proto=" + CommunicationManager.this.mTheirProtoVersion + ", fw=" + CommunicationManager.this.mTheirFirmwareVersion + ".)");
                }
                CommunicationManager.this.setConnectionState(ConnectionState.CONNECTED_TO_OLDER_FIRMWARE);
            }
        });
        registerMessageListener(new CostanzaMessageListener() { // from class: com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.1ResponseFotaListener
            @Override // com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.CostanzaMessageListener
            public int getMessageTypeToListenFor() {
                return 7;
            }

            @Override // com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.CostanzaMessageListener
            public void onMessage(CostanzaMessage msg) {
                ResponseFota fotaResponse = (ResponseFota) msg;
                if (Dbg.d()) {
                    Dbg.d("Communications manager: Got ResponseFota action: " + fotaResponse.getAction() + ", value: " + fotaResponse.getValue());
                }
                CommunicationManager.this.mFotaResponseListener.onFotaResponse(fotaResponse);
            }
        });
        registerMessageListener(new CostanzaMessageListener() { // from class: com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.1IndicationFotaBatteryLevelListener
            @Override // com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.CostanzaMessageListener
            public int getMessageTypeToListenFor() {
                return 18;
            }

            @Override // com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.CostanzaMessageListener
            public void onMessage(CostanzaMessage msg) {
                IndicationBatteryLevel indication = (IndicationBatteryLevel) msg;
                if (Dbg.d()) {
                    Dbg.d("Communications manager: Got IndicationFotaBatteryLevelListener percentage: " + indication.getPercentage());
                }
                CommunicationManager.this.mFotaResponseListener.onFotaBatteryLevelIndication(indication);
            }
        });
        registerMessageListener(new CostanzaMessageListener() { // from class: com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.1CostanzaResourceListener
            @Override // com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.CostanzaMessageListener
            public int getMessageTypeToListenFor() {
                return 1;
            }

            @Override // com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.CostanzaMessageListener
            public void onMessage(CostanzaMessage msg) {
                int cid = ((CostanzaResource) msg).getCid();
                if (Dbg.d()) {
                    Dbg.d("Communications manager: got a message with CID = " + cid);
                }
            }
        });
        registerMessageListener(new CostanzaMessageListener() { // from class: com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.1CostanzaExtensionResponseListener
            @Override // com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.CostanzaMessageListener
            public int getMessageTypeToListenFor() {
                return Types.TYPE_EXTENSION_RSP;
            }

            @Override // com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.CostanzaMessageListener
            public void onMessage(CostanzaMessage msg) {
                ResponseExtension extensionResponse = (ResponseExtension) msg;
                if (Dbg.d()) {
                    Dbg.w("Communications manager: Got ResponseExtension action: " + extensionResponse.getAction() + ", value: " + extensionResponse.getValue() + ", extension: " + extensionResponse.getExtensionCid());
                }
                if (extensionResponse.getAction() == 0) {
                    ContentValues values = new ContentValues();
                    values.put(SyncData.ExtensionColumns.IS_INSTALLED, (Boolean) true);
                    int added = CommunicationManager.this.ctxt.getContentResolver().update(SyncData.Extension.URI, values, "cid=?", new String[]{Integer.toString(extensionResponse.getExtensionCid())});
                    if (Dbg.d()) {
                        Dbg.w("ResponseExtension added: " + added);
                    }
                }
            }
        });
        registerMessageListener(new CostanzaMessageListener() { // from class: com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.1FactoryResetListener
            @Override // com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.CostanzaMessageListener
            public int getMessageTypeToListenFor() {
                return 21;
            }

            @Override // com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.CostanzaMessageListener
            public void onMessage(CostanzaMessage msg) {
                ResponseFactoryReset rspFactoryReset = (ResponseFactoryReset) msg;
                if (Dbg.v()) {
                    Dbg.v("Got ResponseFactoryReset status: " + rspFactoryReset.getStatus());
                }
                String oldAddress = CommunicationManager.this.mSyncedDeviceAddress;
                String newAddress = CommunicationManager.this.mConnectionManager.getRemoteAddress();
                if (Dbg.d()) {
                    Dbg.d("Connected to address: %s, last known address: %s", newAddress, oldAddress);
                }
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(CommunicationManager.this.ctxt);
                SharedPreferences.Editor edit = prefs.edit();
                edit.putString(CommunicationManager.SYNCED_DEVICE_ADDRESS_KEY, newAddress);
                edit.apply();
                CommunicationManager.this.mSyncedDeviceAddress = newAddress;
                if (rspFactoryReset.getStatus() == 0) {
                    CommunicationManager.this.getFotaController(CommunicationManager.this.ctxt).abort();
                    if (CommunicationManager.this.isReady()) {
                        HostAppService.setSyncStarted(true);
                    }
                    clearSendQueuesAndTracking();
                    CommunicationManager.this.mFactoryResetter.performReset();
                } else if (newAddress != null && !TextUtils.equals(newAddress, oldAddress)) {
                    if (Dbg.d()) {
                        Dbg.d("This is a new device, will ask it to factory reset before continuing");
                    }
                    CommunicationManager.this.sendFactoryResetMessage();
                    CommunicationManager.this.clearResetFlag();
                    return;
                }
                CommunicationManager.this.performHandshake();
            }

            private void clearSendQueuesAndTracking() {
                CommunicationManager.this.normalQ.clear();
                CommunicationManager.this.prioQ.clear();
                CommunicationManager.this.fotaProgressQ.clear();
                CommunicationManager.this.fotaQ.clear();
                CommunicationManager.this.mResourceTracker.clear();
            }
        });
        registerMessageListener(new CostanzaMessageListener() { // from class: com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.1ResponseNotificationListListener
            @Override // com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.CostanzaMessageListener
            public int getMessageTypeToListenFor() {
                return Types.TYPE_NOTIFICATION_LIST_RESP;
            }

            @Override // com.sonymobile.smartconnect.hostapp.connection.CommunicationManager.CostanzaMessageListener
            public void onMessage(CostanzaMessage msg) {
                ResponseNotificationList notificationListMsg = (ResponseNotificationList) msg;
                int extensionCid = notificationListMsg.getExtensionCid();
                IndicationNotificationRead[] notificationList = notificationListMsg.getNotificationList();
                if (Dbg.v()) {
                    Dbg.v("Handling ResponseNotificationList, extension cid: 0x%08x, notification count: %d", Integer.valueOf(extensionCid), Integer.valueOf(notificationList.length));
                }
                ArrayList<ContentProviderOperation> operations = new ArrayList<>();
                for (IndicationNotificationRead notification : notificationList) {
                    if (notification.isRead()) {
                        int eventCid = notification.getNotificationCid();
                        Event event = CommunicationManager.this.getEventManager(CommunicationManager.this.ctxt).getEventFromCid(eventCid);
                        if (event == null) {
                            if (Dbg.w()) {
                                Dbg.w("ResponseNotificationListListener: Could not find event with cid: 0x%08x", Integer.valueOf(eventCid));
                            }
                        } else if (notification.isRead() == event.isRead()) {
                            if (Dbg.v()) {
                                Dbg.v("Event with cid: 0x%08x, read status not changed, not updating in SmartConnect", Integer.valueOf(eventCid));
                            }
                        } else {
                            if (Dbg.v()) {
                                Dbg.v("Setting read status for event with cid: 0x%08x to true", Integer.valueOf(event.getCid()));
                            }
                            CommunicationManager.this.getEventManager(CommunicationManager.this.ctxt).setEventRead(event, true);
                            ContentProviderOperation.Builder operation = ContentProviderOperation.newUpdate(Notification.Event.URI);
                            operation.withSelection("_id=?", new String[]{Long.toString(event.getSmartConnectEventId())});
                            operation.withValue("readStatus", true);
                            operations.add(operation.build());
                        }
                    }
                }
                if (operations.size() > 0) {
                    try {
                        CommunicationManager.this.ctxt.getContentResolver().applyBatch(Notification.AUTHORITY, operations);
                    } catch (OperationApplicationException e) {
                        Analytics.getInstance().sendCaughtException(e);
                        if (Dbg.e()) {
                            Dbg.e(e);
                        }
                    } catch (RemoteException e2) {
                        Analytics.getInstance().sendCaughtException(e2);
                        if (Dbg.e()) {
                            Dbg.e(e2);
                        }
                    }
                }
                if (CommunicationManager.access$2206(CommunicationManager.this) == 0) {
                    CommunicationManager.this.setConnectionState(ConnectionState.READY);
                }
            }
        });
    }

    public boolean getResetFlag() {
        return this.mFactoryResetter.getResetFlag();
    }

    public void onMessageAck(int messageId) {
        this.mResourceTracker.onAck(messageId);
    }

    public void onMessageNack(int messageId) {
        this.mResourceTracker.onNack(messageId);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void clearResetFlag() {
        this.mFactoryResetter.clearResetFlag();
    }
}
