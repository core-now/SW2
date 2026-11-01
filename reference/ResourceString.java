package com.sonymobile.smartconnect.hostapp.protocol;

/* JADX INFO: loaded from: classes.dex */
public class ResourceString extends CostanzaResource {
    public static final int CID_FONT_LARGE_BOLD = 5;
    public static final int CID_FONT_LARGE_REGULAR = 4;
    public static final int CID_FONT_NORMAL_BOLD = 3;
    public static final int CID_FONT_NORMAL_REGULAR = 2;
    public static final int CID_FONT_SMALL_BOLD = 1;
    public static final int CID_FONT_SMALL_REGULAR = 0;
    public static final int HORIZONTAL_ALIGN_CENTER_INDEX = 1;
    public static final int HORIZONTAL_ALIGN_LEFT_INDEX = 0;
    public static final int HORIZONTAL_ALIGN_RIGHT_INDEX = 2;
    public static final int TEXT_SIZE_LARGE = 24;
    public static final int TEXT_SIZE_NORMAL = 18;
    public static final int VERTICAL_ALIGN_BOTTOM_INDEX = 2;
    public static final int VERTICAL_ALIGN_CENTER_INDEX = 1;
    public static final int VERTICAL_ALIGN_TOP_INDEX = 0;
    private int mFontCid;
    private int mHeight;
    private String mText;
    private int mTextColor;
    private int mTextHAlignment;
    private byte mTextSize;
    private int mTextVAlignment;
    private int mWidth;

    public ResourceString(int newcid, int newMessageId) {
        super(newcid, newMessageId);
        this.cidType = 1;
    }

    public int getWidth() {
        return this.mWidth;
    }

    public void setWidth(int width) {
        this.mWidth = width;
    }

    public int getHeight() {
        return this.mHeight;
    }

    public void setHeight(int height) {
        this.mHeight = height;
    }

    public String getText() {
        return this.mText;
    }

    public void setText(String text) {
        this.mText = text;
    }

    public int getTextColor() {
        return this.mTextColor;
    }

    public void setTextColor(int textColor) {
        this.mTextColor = textColor;
    }

    public int getTextSize() {
        return this.mTextSize;
    }

    public void setTextSize(int textSize) {
        this.mTextSize = (byte) textSize;
        if (textSize < 18) {
            this.mFontCid = 0;
        } else if (textSize < 24) {
            this.mFontCid = 2;
        } else {
            this.mFontCid = 4;
        }
    }

    public int getFontCid() {
        return this.mFontCid;
    }

    public int getHorizontalAlignment() {
        return this.mTextHAlignment;
    }

    public void setHorizontalAlignment(int hAlignment) {
        this.mTextHAlignment = hAlignment;
    }

    public int getVerticalAlignment() {
        return this.mTextVAlignment;
    }

    public void setVerticalAlignment(int vAlignment) {
        this.mTextVAlignment = vAlignment;
    }
}
