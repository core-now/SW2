package com.sonymobile.smartconnect.hostapp.protocol;

/* JADX INFO: loaded from: classes.dex */
public class ResourceScreen extends CostanzaResource {
    private int[] mCids;

    public ResourceScreen(int cid, int messageId) {
        super(cid, messageId);
        this.cidType = 4;
    }

    public int[] getCids() {
        return this.mCids;
    }

    public void setCids(int[] cids) {
        this.mCids = cids;
    }

    @Override // com.sonymobile.smartconnect.hostapp.protocol.CostanzaMessage
    public String toString() {
        return StringUtil.format("%s [cid=0x%08x, cids=%s]", getClass().getSimpleName(), Integer.valueOf(getCid()), StringUtil.toHexString(this.mCids));
    }
}
