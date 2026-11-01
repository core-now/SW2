package com.sonymobile.smartconnect.hostapp.protocol;

/* JADX INFO: loaded from: classes.dex */
public class ResourceNotification extends CostanzaResource {
    public static final int NOTIFICATION_KEY_BITMAP_RENDERING = 17;
    public static final int NOTIFICATION_KEY_CONTACT_ICON = 5;
    public static final int NOTIFICATION_KEY_DISPLAY_NAME = 1;
    public static final int NOTIFICATION_KEY_DISPLAY_NAME_BOLD_IMAGE = 12;
    public static final int NOTIFICATION_KEY_DISPLAY_NAME_DRAWER = 13;
    public static final int NOTIFICATION_KEY_DISPLAY_NAME_WHITE_IMAGE = 11;
    public static final int NOTIFICATION_KEY_IMAGE = 6;
    public static final int NOTIFICATION_KEY_MESSAGE = 2;
    public static final int NOTIFICATION_KEY_MESSAGE_BOLD_IMAGE = 16;
    public static final int NOTIFICATION_KEY_MESSAGE_DRAWER_WHITE_IMAGE = 15;
    public static final int NOTIFICATION_KEY_MESSAGE_WHITE_IMAGE = 14;
    public static final int NOTIFICATION_KEY_RICH_ICON = 4;
    public static final int NOTIFICATION_KEY_STATUS_BAR_ICON = 3;
    public static final int NOTIFICATION_KEY_TITLE = 0;
    public static final int NOTIFICATION_KEY_TITLE_BOLD_IMAGE = 10;
    public static final int NOTIFICATION_KEY_TITLE_WHITE_IMAGE = 9;
    public static final int NOTIFICATION_KEY_WIDGET_COLOR_ICON = 7;
    public static final int NOTIFICATION_KEY_WIDGET_GRAYSCALE_ICON = 8;
    public static final int NO_CID = -1;
    private ResourceNotificationDataMap[] mFields;
    private int mLocalization;
    private int mNotificationExtensionCid;
    private int mOfflineMenuCid;
    private ResourceSubCid[] mOfflineMenuScreens;
    private int mOnlineMenuCid;
    private ResourceSubCid[] mOnlineMenuScreens;
    private boolean mPersonal;
    private boolean mReadStatus;
    private ResourceSubCid[] mSubScreens;
    private long mTimeStamp;

    public ResourceNotification(int newcid, int newMessageId) {
        super(newcid, newMessageId);
        this.mOnlineMenuCid = -1;
        this.mOfflineMenuCid = -1;
        this.cidType = 7;
    }

    public boolean isRead() {
        return this.mReadStatus;
    }

    public void setRead(boolean read) {
        this.mReadStatus = read;
    }

    public boolean isPersonal() {
        return this.mPersonal;
    }

    public void setPersonal(boolean personal) {
        this.mPersonal = personal;
    }

    public long getTimeStamp() {
        return this.mTimeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.mTimeStamp = timeStamp;
    }

    public ResourceNotificationDataMap[] getFields() {
        return this.mFields;
    }

    public void setFields(ResourceNotificationDataMap[] fields) {
        this.mFields = fields;
    }

    public int getLocalization() {
        return this.mLocalization;
    }

    public void setLocalization(int localization) {
        this.mLocalization = localization;
    }

    public int getNotificationExtensionCid() {
        return this.mNotificationExtensionCid;
    }

    public void setNotificationExtensionCid(int notificationExtensionCid) {
        this.mNotificationExtensionCid = notificationExtensionCid;
    }

    public ResourceSubCid[] getSubScreens() {
        return this.mSubScreens;
    }

    public void setSubScreens(ResourceSubCid[] subScreens) {
        this.mSubScreens = subScreens;
    }

    public int getOnlineMenuCid() {
        return this.mOnlineMenuCid;
    }

    public void setOnlineMenuCid(int onlineMenuCid) {
        this.mOnlineMenuCid = onlineMenuCid;
    }

    public ResourceSubCid[] getOnlineMenuScreens() {
        return this.mOnlineMenuScreens;
    }

    public void setOnlineMenuScreens(ResourceSubCid[] onlineMenuScreens) {
        this.mOnlineMenuScreens = onlineMenuScreens;
    }

    public int getOfflineMenuCid() {
        return this.mOfflineMenuCid;
    }

    public void setOfflineMenuCid(int offlineMenuCid) {
        this.mOfflineMenuCid = offlineMenuCid;
    }

    public ResourceSubCid[] getOfflineMenuScreens() {
        return this.mOfflineMenuScreens;
    }

    public void setOfflineMenuScreens(ResourceSubCid[] offlineMenuScreens) {
        this.mOfflineMenuScreens = offlineMenuScreens;
    }
}
