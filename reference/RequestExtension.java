package com.sonymobile.smartconnect.hostapp.protocol;

import com.sonymobile.smartconnect.hostapp.protocol.shared.Types;

/* JADX INFO: loaded from: classes.dex */
public class RequestExtension extends CostanzaMessage {
    public static final int ACTION_INSTALL = 0;
    public static final int ACTION_UNINSTALL = 1;
    public static final int ACTION_UPDATE = 2;
    private int mAction;
    private int mValue;

    public RequestExtension(int newMessageId) {
        super(newMessageId);
        this.type = Types.TYPE_EXTENSION_REQ;
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
