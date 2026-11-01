package com.sonymobile.smartconnect.hostapp.protocol;

/* JADX INFO: loaded from: classes.dex */
public class RequestNotification extends CostanzaMessage {
    public static final int ACTION_NOTIFICATION_ADDED = 0;
    private int mAction;
    private int mValue;

    public RequestNotification(int messageId, int action, int value) {
        super(messageId);
        this.type = 110;
        setAction(action);
        setValue(value);
    }

    public int getAction() {
        return this.mAction;
    }

    public void setAction(int action) {
        this.mAction = action;
    }

    public int getValue() {
        return this.mValue;
    }

    public void setValue(int value) {
        this.mValue = value;
    }
}
