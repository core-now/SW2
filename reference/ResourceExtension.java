package com.sonymobile.smartconnect.hostapp.protocol;

/* JADX INFO: loaded from: classes.dex */
public class ResourceExtension extends CostanzaResource {
    private int mCapabilities;
    private int mColor;
    private int mIconCid;
    private int mIconOfflineCid;
    private int mNameCid;
    private int mPriority;
    private int mRelativeCid;
    private boolean mShowOnHomeScreen;
    private int mSupportedDrawingSpecs;

    public enum ExtensionPriority {
        APPLICATION_ALERT_PRIORITY_HIGHEST,
        APPLICATION_ALERT_PRIORITY_INCOMING_CALL,
        APPLICATION_ALERT_PRIORITY_BATTERY_WARNING,
        APPLICATION_ALERT_PRIORITY_ALARM,
        APPLICATION_ALERT_PRIORITY_TIMER,
        APPLICATION_ALERT_PRIORITY_NOTIFICATION_MSG,
        APPLICATION_ALERT_PRIORITY_NONE
    }

    public ResourceExtension(int newcid, int messageId) {
        super(newcid, messageId);
        this.mColor = -16777216;
        this.mShowOnHomeScreen = true;
        this.mRelativeCid = -1;
        this.cidType = 11;
    }

    public int getNameCid() {
        return this.mNameCid;
    }

    public void setNameCid(int nameCid) {
        this.mNameCid = nameCid;
    }

    public int getIconCid() {
        return this.mIconCid;
    }

    public void setIconCid(int iconCid) {
        this.mIconCid = iconCid;
    }

    public int getIconOfflineCid() {
        return this.mIconOfflineCid;
    }

    public void setIconOfflineCid(int iconOfflineCid) {
        this.mIconOfflineCid = iconOfflineCid;
    }

    public ExtensionPriority getPriority() {
        return ExtensionPriority.values()[this.mPriority];
    }

    public void setPriority(ExtensionPriority priority) {
        this.mPriority = priority.ordinal();
    }

    public int getCapabilities() {
        return this.mCapabilities;
    }

    public void setCapabilities(int capabilites) {
        this.mCapabilities = capabilites;
    }

    public int getSupportedDrawingSpecs() {
        return this.mSupportedDrawingSpecs;
    }

    public void setSupportedDrawingSpecs(int supportedDrawingSpecs) {
        this.mSupportedDrawingSpecs = supportedDrawingSpecs;
    }

    public int getColor() {
        return this.mColor;
    }

    public void setColor(int color) {
        this.mColor = color;
    }

    public boolean getShowOnHomeScreen() {
        return this.mShowOnHomeScreen;
    }

    public void setShowOnHomeScreen(boolean show) {
        this.mShowOnHomeScreen = show;
    }

    public void setRelativeCid(int relativeCid) {
        this.mRelativeCid = relativeCid;
    }
}
