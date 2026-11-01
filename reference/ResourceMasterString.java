package com.sonymobile.smartconnect.hostapp.protocol;

/* JADX INFO: loaded from: classes.dex */
public class ResourceMasterString extends CostanzaResource {
    private int mBackgroundColor;
    private int[] mStringCids;
    private int mViewWindowBottom;
    private int mViewWindowLeft;
    private int mViewWindowRight;
    private int mViewWindowTop;
    private int mVirtualWindowBottom;
    private int mVirtualWindowLeft;
    private int mVirtualWindowRight;
    private int mVirtualWindowTop;

    public ResourceMasterString(int cid, int newMessageId) {
        super(cid, newMessageId);
        this.cidType = 2;
    }

    public int getViewWindowLeft() {
        return this.mViewWindowLeft;
    }

    public void setViewWindowLeft(int viewWindowLeft) {
        this.mViewWindowLeft = viewWindowLeft;
    }

    public int getViewWindowTop() {
        return this.mViewWindowTop;
    }

    public void setViewWindowTop(int viewWindowTop) {
        this.mViewWindowTop = viewWindowTop;
    }

    public int getViewWindowRight() {
        return this.mViewWindowRight;
    }

    public void setViewWindowRight(int viewWindowRight) {
        this.mViewWindowRight = viewWindowRight;
    }

    public int getViewWindowBottom() {
        return this.mViewWindowBottom;
    }

    public void setViewWindowBottom(int viewWindowBottom) {
        this.mViewWindowBottom = viewWindowBottom;
    }

    public int getVirtualWindowLeft() {
        return this.mVirtualWindowLeft;
    }

    public void setVirtualWindowLeft(int virtualWindowLeft) {
        this.mVirtualWindowLeft = virtualWindowLeft;
    }

    public int getVirtualWindowTop() {
        return this.mVirtualWindowTop;
    }

    public void setVirtualWindowTop(int virtualWindowTop) {
        this.mVirtualWindowTop = virtualWindowTop;
    }

    public int getVirtualWindowRight() {
        return this.mVirtualWindowRight;
    }

    public void setVirtualWindowRight(int virtualWindowRight) {
        this.mVirtualWindowRight = virtualWindowRight;
    }

    public int getVirtualWindowBottom() {
        return this.mVirtualWindowBottom;
    }

    public void setVirtualWindowBottom(int virtualWindowBottom) {
        this.mVirtualWindowBottom = virtualWindowBottom;
    }

    public int getBackgroundColor() {
        return this.mBackgroundColor;
    }

    public void setBackgroundColor(int backgroundColor) {
        this.mBackgroundColor = backgroundColor;
    }

    public int[] getStringCids() {
        return this.mStringCids;
    }

    public void setStringCids(int[] stringCids) {
        this.mStringCids = stringCids;
    }
}
