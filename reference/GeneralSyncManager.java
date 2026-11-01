package com.sonymobile.smartconnect.hostapp.costanza.db;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.support.v4.app.FragmentTransaction;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import com.sonymobile.smartconnect.hostapp.Dbg;
import com.sonymobile.smartconnect.hostapp.analytics.Analytics;
import com.sonymobile.smartconnect.hostapp.connection.CommunicationManager;
import com.sonymobile.smartconnect.hostapp.costanza.CostanzaHostApplication;
import com.sonymobile.smartconnect.hostapp.costanza.NotificationSource;
import com.sonymobile.smartconnect.hostapp.costanza.ResourceDeleter;
import com.sonymobile.smartconnect.hostapp.costanza.ResourceProviderCache;
import com.sonymobile.smartconnect.hostapp.costanza.res.CostanzaResourceProvider;
import com.sonymobile.smartconnect.hostapp.costanza.res.ImageResourceProvider;
import com.sonymobile.smartconnect.hostapp.costanza.uicontrol.ControlFactory;
import com.sonymobile.smartconnect.hostapp.costanza.uicontrol.MenuControl;
import com.sonymobile.smartconnect.hostapp.debugevents.LogDbHelper;
import com.sonymobile.smartconnect.hostapp.extensions.Extension;
import com.sonymobile.smartconnect.hostapp.extensions.ExtensionManager;
import com.sonymobile.smartconnect.hostapp.extensions.LayoutData;
import com.sonymobile.smartconnect.hostapp.extensions.MenuFactory;
import com.sonymobile.smartconnect.hostapp.extensions.control.ExtensionMenuLayout;
import com.sonymobile.smartconnect.hostapp.extensions.control.MenuItem;
import com.sonymobile.smartconnect.hostapp.layout.ExtensionLayoutInflater;
import com.sonymobile.smartconnect.hostapp.notification.Event;
import com.sonymobile.smartconnect.hostapp.notification.EventManager;
import com.sonymobile.smartconnect.hostapp.notification.Smiley;
import com.sonymobile.smartconnect.hostapp.notification.Smileyfier;
import com.sonymobile.smartconnect.hostapp.protocol.CostanzaMessage;
import com.sonymobile.smartconnect.hostapp.protocol.CostanzaResource;
import com.sonymobile.smartconnect.hostapp.protocol.RequestExtension;
import com.sonymobile.smartconnect.hostapp.protocol.RequestNotification;
import com.sonymobile.smartconnect.hostapp.protocol.ResourceExtension;
import com.sonymobile.smartconnect.hostapp.protocol.ResourceNotification;
import com.sonymobile.smartconnect.hostapp.protocol.ResourceNotificationDataMap;
import com.sonymobile.smartconnect.hostapp.protocol.ResourceScreen;
import com.sonymobile.smartconnect.hostapp.protocol.ResourceSubCid;
import com.sonymobile.smartconnect.hostapp.protocol.shared.Types;
import com.sonymobile.smartconnect.hostapp.util.ArrayUtils;
import com.sonymobile.smartconnect.smartwatch2.R;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public class GeneralSyncManager extends SyncManager implements ExtensionManager.ExtensionsChangeListener, ExtensionManager.NotificationSourcesChangeListener, EventManager.EventsChangeListener {
    private static final int ANDROID_TO_COSTANZA_BITMAP_TEXT_RTL_COMPENSATION = 2;
    private static final int ANDROID_TO_COSTANZA_BITMAP_TEXT_WIDTH_COMPENSATION = 3;
    private static final int ANDROID_TO_COSTANZA_TEXT_WIDTH_COMPENSATION = 5;
    private static final int BITMAP_RENDERING = 1;
    private static final int CAPABILITY_ERROR = -1;
    private static final int CHARS_PER_LINE = 12;
    private static final int CHARS_PER_LINE_DISPLAYNAME = 9;
    private static final int CHARS_PER_LINE_DISPLAYNAME_WHITE = 20;
    private static final int CHARS_PER_LINE_TITLE = 16;
    private static final String EXTENSION_AND_EVENT_RES_PROVIDER_NAME_PREFIX = "ext_events_";
    private static final int IMAGE_PADDING = 3;
    private static final int NOTIFICATION_MESSAGE_MAX_LENGTH = 160;
    public static final int NOTIFICATION_OVERVIEW_MAX_CHARS = 9000;
    private static final int PIXELS_PER_LINE_POPUP = 200;
    private static final int RICH_NOTIFICATION_BITMAP_LENGTH = 196;
    private boolean bitmapRendering;
    private boolean displayNameRTL;
    private final CidProvider mCidProvider;
    private final int mExtensionIconSize;
    private final ExtensionManager mExtensionManager;
    private final Handler mHandler;
    private final int mNotificationContactIconSize;
    private final int mNotificationMessageTextSize;
    private final int mNotificationMessageTextSizeLarge;
    private final int mNotificationMessageTextSizeNormal;
    private final int mNotificationOverviewHeight;
    private final int mNotificationRichNotificationIconSize;
    private final int mNotificationStatusBarIconSize;
    private final int mNotificationTextBackgroundColor;
    private final int mNotificationWidgetIconSize;
    private final HashMap<Long, MenuControl> mOfflineMenuCache;
    private final HashMap<Long, MenuControl> mOnlineMenuCache;
    private final ResourceProviderCache mResProviderCache;
    private final ResourceDeleter mResourceDeleter;
    private final int mSmileySize;
    private boolean titleRTL;

    public GeneralSyncManager(Context ctx, CommunicationManager communicationManager, CidProvider cidProvider, MessageIdProvider msgIdProvider, ExtensionManager extensionManager, Handler handler, ResourceProviderCache resProviderCache, ResourceDeleter resourceDeleter) {
        super(ctx, communicationManager, cidProvider, msgIdProvider);
        this.mOnlineMenuCache = new HashMap<>();
        this.mOfflineMenuCache = new HashMap<>();
        this.bitmapRendering = false;
        this.displayNameRTL = false;
        this.titleRTL = false;
        this.mHandler = handler;
        this.mCidProvider = cidProvider;
        this.mExtensionIconSize = this.mContext.getResources().getDimensionPixelOffset(R.dimen.extension_icon_size);
        this.mNotificationRichNotificationIconSize = this.mContext.getResources().getDimensionPixelOffset(R.dimen.notification_rich_notification_icon_size);
        this.mNotificationContactIconSize = this.mContext.getResources().getDimensionPixelOffset(R.dimen.notification_contact_icon_size);
        this.mNotificationStatusBarIconSize = this.mContext.getResources().getDimensionPixelOffset(R.dimen.notification_status_bar_icon_size);
        this.mNotificationWidgetIconSize = this.mContext.getResources().getDimensionPixelOffset(R.dimen.notification_widget_icon_size);
        this.mNotificationOverviewHeight = this.mContext.getResources().getDimensionPixelOffset(R.dimen.notification_overview_height);
        this.mNotificationMessageTextSize = this.mContext.getResources().getDimensionPixelOffset(R.dimen.text_size_notification_message);
        this.mNotificationMessageTextSizeNormal = this.mContext.getResources().getDimensionPixelOffset(R.dimen.text_size_normal);
        this.mNotificationMessageTextSizeLarge = this.mContext.getResources().getDimensionPixelOffset(R.dimen.text_size_large);
        this.mSmileySize = this.mContext.getResources().getDimensionPixelOffset(R.dimen.notification_smiley_size);
        this.mNotificationTextBackgroundColor = this.mContext.getResources().getColor(R.color.notification_overview_text_background_color);
        this.mExtensionManager = extensionManager;
        this.mResourceDeleter = resourceDeleter;
        this.mResProviderCache = resProviderCache;
    }

    @Override // com.sonymobile.smartconnect.hostapp.costanza.db.SyncManager
    public void reset() {
        super.reset();
        this.mOfflineMenuCache.clear();
        this.mOnlineMenuCache.clear();
        this.mContentResolver.delete(SyncData.Extension.URI, null, null);
        this.mContentResolver.delete(SyncData.NotificationSource.URI, null, null);
        this.mContentResolver.delete(SyncData.Event.URI, null, null);
        this.mHandler.removeCallbacksAndMessages(null);
    }

    @Override // com.sonymobile.smartconnect.hostapp.extensions.ExtensionManager.ExtensionsChangeListener
    public void onExtensionsInserted(List<Extension> insertedExtensions) {
        syncInsertedExtensions(insertedExtensions);
    }

    @Override // com.sonymobile.smartconnect.hostapp.extensions.ExtensionManager.ExtensionsChangeListener
    public void onExtensionsUpdated(List<Extension> updatedExtensionsNew, List<Extension> updatedExtensionsOld) {
        syncUpdatedExtensions(updatedExtensionsNew, updatedExtensionsOld);
    }

    @Override // com.sonymobile.smartconnect.hostapp.extensions.ExtensionManager.ExtensionsChangeListener
    public void onExtensionsDeleted(List<Extension> deletedExtensions) {
        syncDeletedExtensions(deletedExtensions);
    }

    @Override // com.sonymobile.smartconnect.hostapp.extensions.ExtensionManager.NotificationSourcesChangeListener
    public void onNotificationSourcesInserted(List<NotificationSource> insertedSources) {
        if (Dbg.d()) {
            Dbg.d("Inserting source");
        }
        syncInsertedNotificationSources(insertedSources);
    }

    @Override // com.sonymobile.smartconnect.hostapp.extensions.ExtensionManager.NotificationSourcesChangeListener
    public void onNotificationSourcesUpdated(List<NotificationSource> updatedSourcesNew, List<NotificationSource> updatedSourcesOldProperties) {
        syncUpdatedNotificationSources(updatedSourcesNew, updatedSourcesOldProperties);
    }

    @Override // com.sonymobile.smartconnect.hostapp.extensions.ExtensionManager.NotificationSourcesChangeListener
    public void onNotificationSourcesDeleted(List<NotificationSource> deletedSources) {
        syncDeletedNotificationSources(deletedSources);
    }

    @Override // com.sonymobile.smartconnect.hostapp.notification.EventManager.EventsChangeListener
    public void onEventsInserted(List<Event> newEvents, boolean startup) {
        syncInsertedEvents(newEvents, startup);
    }

    @Override // com.sonymobile.smartconnect.hostapp.notification.EventManager.EventsChangeListener
    public void onEventsUpdated(List<Event> updatedEvents) {
        syncUpdatedEvents(updatedEvents);
    }

    @Override // com.sonymobile.smartconnect.hostapp.notification.EventManager.EventsChangeListener
    public void onEventsDeleted(List<Event> deletedEvents) {
        syncDeletedEvents(deletedEvents);
    }

    public void syncInsertedEvents(List<Event> insertedEvents, boolean startup) {
        boolean notificationRequestSent = false;
        if (insertedEvents != null && !insertedEvents.isEmpty()) {
            ArrayList<Event> reversedInsertedEvents = new ArrayList<>(insertedEvents);
            Collections.reverse(reversedInsertedEvents);
            for (Event event : reversedInsertedEvents) {
                int cid = getNewCid();
                event.setCid(cid);
                boolean success = insertEvent(event);
                if (!notificationRequestSent && !event.isRead() && success && !startup && isConnectionReadyForSynchronization()) {
                    RequestNotification rn = new RequestNotification(getNewMessageId(), 0, cid);
                    sendMessages(rn);
                    notificationRequestSent = true;
                }
            }
        }
    }

    public void syncUpdatedEvents(List<Event> updatedEvents) {
        if (updatedEvents != null && !updatedEvents.isEmpty()) {
            for (Event event : updatedEvents) {
                Event syncEvent = getEvent(event.getSmartConnectEventId());
                if (syncEvent == null) {
                    if (Dbg.e()) {
                        Dbg.e("Event marked for update not found! id: " + event.getId());
                    }
                } else {
                    updateEvent(event, syncEvent);
                }
            }
        }
    }

    public void syncDeletedEvents(List<Event> deletedEvents) {
        if (deletedEvents != null && !deletedEvents.isEmpty()) {
            HashSet<Long> sources = new HashSet<>();
            for (Event event : deletedEvents) {
                Event syncEvent = getEvent(event.getSmartConnectEventId());
                if (syncEvent == null) {
                    if (Dbg.d()) {
                        Dbg.d("Event marked for delete not found! id: " + event.getId());
                    }
                } else {
                    sources.add(Long.valueOf(syncEvent.getSourceId()));
                    deleteEvent(syncEvent);
                }
            }
            if (!sources.isEmpty()) {
                for (Long sourceId : sources) {
                    if (Dbg.d()) {
                        Dbg.d("DeletedEvents sync, removing sources: " + sourceId);
                    }
                    clearSourceFromCaches(sourceId);
                }
            }
        }
    }

    public void syncInsertedExtensions(List<Extension> insertedExtensions) {
        Object[] objArr = new Object[1];
        objArr[0] = Integer.valueOf(insertedExtensions != null ? insertedExtensions.size() : 0);
        Dbg.d("Syncing %d inserted extensions.", objArr);
        if (insertedExtensions != null && !insertedExtensions.isEmpty()) {
            for (Extension extension : insertedExtensions) {
                Dbg.d("Installing extension, %s.", extension.getName());
                insertExtension(extension, extension.getCid());
            }
        }
    }

    public void syncUpdatedExtensions(List<Extension> updatedExtensions, List<Extension> oldExtensions) {
        if (updatedExtensions != null && !updatedExtensions.isEmpty() && oldExtensions != null && !oldExtensions.isEmpty() && updatedExtensions.size() == oldExtensions.size()) {
            int count = updatedExtensions.size();
            for (int i = 0; i < count; i++) {
                Extension updated = updatedExtensions.get(i);
                Extension old = oldExtensions.get(i);
                if (updated == null || old == null) {
                    if (Dbg.e()) {
                        Dbg.e("Extension marked for update not found! index: " + i);
                    }
                } else {
                    updateExtension(updated, old);
                }
            }
        }
    }

    public void syncDeletedExtensions(List<Extension> deletedExtensions) {
        if (deletedExtensions != null && !deletedExtensions.isEmpty()) {
            for (Extension extension : deletedExtensions) {
                if (extension != null) {
                    deleteExtension(extension);
                }
            }
        }
    }

    public void syncInsertedNotificationSources(List<NotificationSource> sources) {
        if (sources != null && !sources.isEmpty()) {
            for (NotificationSource source : sources) {
                int cid = getNewCid();
                source.setCid(cid);
                insertNotificationSource(source, cid);
            }
        }
    }

    public void syncUpdatedNotificationSources(List<NotificationSource> updatedSources, List<NotificationSource> oldSources) {
        if (updatedSources != null && !updatedSources.isEmpty() && oldSources != null && !oldSources.isEmpty() && updatedSources.size() == oldSources.size()) {
            int count = updatedSources.size();
            for (int i = 0; i < count; i++) {
                NotificationSource updated = updatedSources.get(i);
                NotificationSource old = oldSources.get(i);
                if (updated == null || old == null) {
                    if (Dbg.e()) {
                        Dbg.e("Notification source to be updated not found! index: " + i);
                    }
                } else {
                    updateNotificationSource(updated, old);
                }
            }
        }
    }

    public void syncDeletedNotificationSources(List<NotificationSource> deletedSources) {
        if (deletedSources != null && !deletedSources.isEmpty()) {
            for (NotificationSource source : deletedSources) {
                if (source != null) {
                    deleteNotificationSource(source);
                }
            }
        }
    }

    private ResourceSubCid[] insertOverviewEventScreen(CostanzaResourceProvider resProvider, Event event) {
        String message = event.getMessage();
        int imageCid = event.getImageCid();
        if (message == null && imageCid <= 0) {
            if (Dbg.d()) {
                Dbg.d("insertOverviewEventScreen: Ignoring empty message");
            }
            return null;
        }
        ArrayList<ResourceSubCid> subCids = new ArrayList<>();
        if (imageCid != -1 && imageCid != 0) {
            insertOverviewEventScreenImage(imageCid, subCids);
        }
        if (message != null) {
            insertOverviewEventScreenMessage(resProvider, message, subCids);
        }
        return (ResourceSubCid[]) subCids.toArray(new ResourceSubCid[subCids.size()]);
    }

    private String addNewLineBeforeHyperlink(String message) {
        int hyperlinkStart = message.indexOf("http", 0);
        int startPos = hyperlinkStart;
        while (hyperlinkStart != -1) {
            startPos += message.offsetByCodePoints(startPos, "http".codePointCount(0, "http".length()));
            message = message.substring(0, hyperlinkStart) + "\n" + message.substring(hyperlinkStart, message.length());
            hyperlinkStart = message.indexOf("http", startPos);
        }
        return message;
    }

    private void insertOverviewEventScreenMessage(CostanzaResourceProvider resProvider, String message, ArrayList<ResourceSubCid> subCids) {
        String line;
        Rect screenBox;
        int lastXPos;
        String textPart;
        int textPartWidth;
        int textCid;
        int textCid2;
        int messageCid;
        int boxMargin = this.mContext.getResources().getDimensionPixelOffset(R.dimen.event_screen_margin);
        boolean isMessageRTL = isRTL(message);
        int marginOffest = this.bitmapRendering ? 4 : 2;
        LayoutInflater inflater = (LayoutInflater) this.mContext.getSystemService("layout_inflater");
        TextView tv = (TextView) inflater.inflate(R.layout.notification_overview_message, (ViewGroup) null);
        if (message.length() > 9000) {
            message = message.substring(0, NOTIFICATION_OVERVIEW_MAX_CHARS);
        }
        String message2 = addNewLineBeforeHyperlink(message);
        CharSequence spannable = Smileyfier.embedSmileys(this.mContext, message2);
        if (spannable != null) {
            tv.setText(spannable);
        } else {
            tv.setText(message2);
        }
        int widthMS = View.MeasureSpec.makeMeasureSpec(this.mCostanzaWidth - (marginOffest * boxMargin), Integer.MIN_VALUE);
        int heightMS = View.MeasureSpec.makeMeasureSpec(this.mCostanzaHeight, 0);
        tv.measure(widthMS, heightMS);
        int lineCount = tv.getLineCount();
        int textSize = getWatchTextSize(this.mNotificationMessageTextSize);
        int lineHeight = Math.max(tv.getLineHeight(), textSize);
        if (Dbg.v()) {
            Dbg.v("SyncManager: Text: '" + message2 + "'");
        }
        if (Dbg.v()) {
            Dbg.v("SyncManager: Number of lines required: " + lineCount);
        }
        CharSequence text = tv.getText();
        int textLength = text.length();
        for (int i = 0; i < lineCount; i++) {
            int lineStart = tv.getLayout().getLineStart(i);
            int lineEnd = tv.getLayout().getLineEnd(i);
            if (textLength < lineStart || textLength < lineEnd || lineStart >= lineEnd || lineStart < 0 || lineEnd < 0) {
                line = "";
            } else {
                line = text.subSequence(lineStart, lineEnd).toString();
            }
            ResourceScreen screenMsg = new ResourceScreen(getNewCid(), getNewMessageId());
            List<Smiley> smileys = new ArrayList<>();
            if (spannable != null) {
                smileys = Smileyfier.getSmileyImages(line);
            }
            if (Dbg.v()) {
                Dbg.v("Found " + smileys.size() + " smileys in '" + line + "'");
            }
            if (smileys.isEmpty()) {
                screenBox = new Rect(0, 0, this.mCostanzaWidth - 1, lineHeight);
                if (Dbg.v()) {
                    Dbg.v("Box for item #" + i + ": " + screenBox.toShortString());
                }
                Rect box = new Rect(boxMargin, 0, this.mCostanzaWidth - boxMargin, lineHeight - 1);
                if (this.bitmapRendering) {
                    messageCid = insertTextImageCanvas(resProvider, line, this.mCostanzaWidth, lineHeight + 2, textSize - 2, -16777216, false, isMessageRTL);
                } else {
                    messageCid = insertString(resProvider, line, this.mCostanzaWidth, lineHeight, box, textSize, 3, -16777216, this.mNotificationTextBackgroundColor, true);
                }
                screenMsg.setCids(new int[]{messageCid});
            } else {
                screenBox = new Rect(0, 0, this.mCostanzaWidth - 1, this.mSmileySize - 1);
                if (Dbg.v()) {
                    Dbg.v("Box for item #" + i + ": " + screenBox.toShortString());
                }
                List<Integer> cidsList = new ArrayList<>();
                int nextCharacterPos = 0;
                if (!this.bitmapRendering) {
                    lastXPos = 0;
                } else if (isMessageRTL) {
                    lastXPos = 214;
                } else {
                    lastXPos = boxMargin - 1;
                }
                for (Smiley smiley : smileys) {
                    int smileyResId = smiley.resourceId;
                    int nextSmileyPos = smiley.position;
                    int smileyLength = smiley.characterLength;
                    if (nextSmileyPos != nextCharacterPos) {
                        String textPart2 = line.substring(nextCharacterPos, nextSmileyPos);
                        int textPartWidth2 = getTextWidth(tv, textPart2, isMessageRTL);
                        if (textPartWidth2 != 0) {
                            int textBoxMargin = lastXPos == 0 ? boxMargin : 0;
                            Rect textBox = new Rect(textBoxMargin, 0, (textBoxMargin + textPartWidth2) - 1, this.mSmileySize - 1);
                            if (isMessageRTL) {
                                lastXPos -= textPartWidth2;
                            }
                            if (!this.bitmapRendering) {
                                textCid2 = insertString(resProvider, textPart2, textPartWidth2, this.mSmileySize, textBox, textSize, 83, -16777216, this.mNotificationTextBackgroundColor, true);
                            } else {
                                textCid2 = insertTextImageCanvas(resProvider, textPart2, textPartWidth2, lineHeight + 2, textSize - 2, -16777216, true, isMessageRTL);
                            }
                            int shifterCid = insertShifter(resProvider, textCid2, lastXPos, 0);
                            cidsList.add(Integer.valueOf(shifterCid));
                            if (!isMessageRTL) {
                                lastXPos += textBox.right - 1;
                            } else {
                                lastXPos -= textBox.left + 1;
                            }
                            if (lastXPos >= this.mCostanzaWidth && Dbg.w()) {
                                Dbg.w("insertOverviewEventScreen: lastXPos is outside screen: " + lastXPos);
                            }
                        }
                    }
                    String smileyUri = "android.resource://" + this.mContext.getPackageName() + "/" + smileyResId;
                    int smileyCid = insertImage(resProvider, smileyUri, this.mSmileySize, this.mSmileySize, ImageResourceProvider.ImageFilter.NONE, true);
                    if (lastXPos == 0 && !isMessageRTL) {
                        lastXPos += boxMargin;
                    }
                    if (isMessageRTL) {
                        if (lastXPos == 214) {
                            lastXPos = (this.mCostanzaWidth - boxMargin) - this.mSmileySize;
                        } else {
                            lastXPos -= this.mSmileySize;
                        }
                    }
                    int shifterCid2 = insertShifter(resProvider, smileyCid, lastXPos, 0);
                    cidsList.add(Integer.valueOf(shifterCid2));
                    nextCharacterPos = nextSmileyPos + smileyLength;
                    if (!isMessageRTL) {
                        lastXPos += this.mSmileySize;
                    }
                    if (lastXPos >= this.mCostanzaWidth && Dbg.w()) {
                        Dbg.w("insertOverviewEventScreen: lastXPos is outside screen: " + lastXPos);
                    }
                }
                if (nextCharacterPos < line.length() && (textPartWidth = getTextWidth(tv, (textPart = line.substring(nextCharacterPos)), isMessageRTL)) != 0) {
                    Rect textBox2 = new Rect(0, 0, textPartWidth - 1, this.mSmileySize - 1);
                    if (isMessageRTL) {
                        lastXPos -= textPartWidth;
                    }
                    if (!this.bitmapRendering) {
                        textCid = insertString(resProvider, textPart, textPartWidth, this.mSmileySize, textBox2, textSize, 83, -16777216, this.mNotificationTextBackgroundColor, true);
                    } else {
                        textCid = insertTextImageCanvas(resProvider, textPart, textPartWidth, lineHeight + 2, textSize - 2, -16777216, true, isMessageRTL);
                    }
                    int shifterCid3 = insertShifter(resProvider, textCid, lastXPos, 0);
                    cidsList.add(Integer.valueOf(shifterCid3));
                }
                int[] cidsArray = new int[cidsList.size()];
                int j = 0;
                Iterator<Integer> it = cidsList.iterator();
                while (it.hasNext()) {
                    int cid = it.next().intValue();
                    cidsArray[j] = cid;
                    j++;
                }
                screenMsg.setCids(cidsArray);
            }
            resProvider.cidRefTracker().put(screenMsg.getCid(), ArrayUtils.toIntegerArray(screenMsg.getCids()));
            sendMessages(screenMsg);
            ResourceSubCid subCid = new ResourceSubCid();
            subCid.setResourceCid(screenMsg.getCid());
            subCid.setScreenBoxLeft(screenBox.left);
            subCid.setScreenBoxTop(screenBox.top);
            subCid.setScreenBoxRight(screenBox.right);
            subCid.setScreenBoxBottom(screenBox.bottom);
            subCids.add(subCid);
        }
    }

    private ExtensionManager getExtensionManager() {
        CostanzaHostApplication hostApp = (CostanzaHostApplication) this.mContext.getApplicationContext();
        return hostApp.getExtensionManager();
    }

    private int getTextWidth(TextView tv, String text, boolean isMessageRTL) {
        Paint textPaint = tv.getPaint();
        float textWidth = textPaint.measureText(text);
        if (textWidth > 0.0f) {
            if (!this.bitmapRendering) {
                textWidth += 5.0f;
            } else if (isMessageRTL) {
                textWidth += 2.0f;
            } else {
                textWidth += 3.0f;
            }
        }
        return (int) textWidth;
    }

    private int getWatchTextSize(int textSize) {
        if (textSize == this.mNotificationMessageTextSizeNormal) {
            return 18;
        }
        if (textSize == this.mNotificationMessageTextSizeLarge) {
            return 24;
        }
        return textSize;
    }

    private void insertOverviewEventScreenImage(int imageCid, ArrayList<ResourceSubCid> subCids) {
        Rect screenBox = new Rect(0, 0, this.mCostanzaWidth - 1, this.mNotificationOverviewHeight - 1);
        ResourceScreen screenMsg = new ResourceScreen(getNewCid(), getNewMessageId());
        screenMsg.setCids(new int[]{imageCid});
        sendMessages(screenMsg);
        ResourceSubCid subCid = new ResourceSubCid();
        subCid.setResourceCid(screenMsg.getCid());
        subCid.setScreenBoxLeft(screenBox.left);
        subCid.setScreenBoxTop(screenBox.top);
        subCid.setScreenBoxRight(screenBox.right);
        subCid.setScreenBoxBottom(screenBox.bottom);
        subCids.add(subCid);
    }

    private void updateEvent(Event event, Event syncEvent) {
        ContentValues values = new ContentValues();
        CostanzaResourceProvider resProvider = getResProvider(event.getExtensionCid());
        int messageId = getNewMessageId();
        int syncEventCid = event.getCid();
        int extensionCid = getExtensionCidBySourceId(event.getSourceId());
        if (extensionCid == -1) {
            if (Dbg.w()) {
                Dbg.w("Extension CID not found for event: %d.", Long.valueOf(event.getId()));
                return;
            }
            return;
        }
        event.setExtensionCid(extensionCid);
        ResourceNotification eventMessage = createEventMessage(resProvider, syncEvent, event, syncEventCid, messageId);
        if (eventMessage != null) {
            if (!TextUtils.equals(syncEvent.getTitle(), event.getTitle())) {
                values.put("title", event.getTitle());
            }
            String syncMessage = syncEvent.getMessage();
            if (syncMessage != null && syncMessage.length() > NOTIFICATION_MESSAGE_MAX_LENGTH) {
                syncMessage = syncMessage.substring(0, NOTIFICATION_MESSAGE_MAX_LENGTH);
            }
            String message = event.getMessage();
            if (message != null && message.length() > NOTIFICATION_MESSAGE_MAX_LENGTH) {
                message = message.substring(0, NOTIFICATION_MESSAGE_MAX_LENGTH);
            }
            if (!TextUtils.equals(syncMessage, message)) {
                values.put("message", event.getMessage());
            }
            if (!TextUtils.equals(syncEvent.getContactsReference(), event.getContactsReference())) {
                values.put("contacts_reference", event.getContactsReference());
            }
            if (!TextUtils.equals(syncEvent.getDisplayName(), event.getDisplayName())) {
                values.put("display_name", event.getDisplayName());
            }
            if (!TextUtils.equals(syncEvent.getEventImageUri(), event.getEventImageUri())) {
                values.put("imageUri", event.getEventImageUri());
            }
            if (!TextUtils.equals(syncEvent.getProfileImageUri(), event.getProfileImageUri())) {
                values.put("profile_image_uri", event.getProfileImageUri());
            }
            if (syncEvent.isRead() != event.isRead()) {
                values.put("readStatus", Boolean.valueOf(event.isRead()));
            }
            if (syncEvent.isPersonal() != event.isPersonal()) {
                values.put("personal", Boolean.valueOf(event.isPersonal()));
            }
            if (syncEvent.getPublishedTime() != event.getPublishedTime()) {
                values.put("publishedTime", Long.valueOf(event.getPublishedTime()));
            }
            if (values.size() == 0) {
                Dbg.d("Could not find any updated data in event with id: " + event.getId());
                return;
            }
            ArrayList<ContentProviderOperation> operations = new ArrayList<>();
            ContentProviderOperation.Builder builder = ContentProviderOperation.newUpdate(SyncData.Event.URI);
            builder.withValues(values);
            builder.withSelection("cid=?", new String[]{Integer.toString(syncEventCid)});
            operations.add(builder.build());
            addOperations(messageId, operations);
            sendMessages(eventMessage);
        }
    }

    private void addResourceLayoutData(List<ResourceNotificationDataMap> dataMap, int key, int value) {
        ResourceNotificationDataMap data = new ResourceNotificationDataMap();
        data.setKey(key);
        data.setValue(value);
        dataMap.add(data);
    }

    private void deleteEvent(Event event) {
        int eventCid = event.getCid();
        if (eventCid == -1) {
            if (Dbg.d()) {
                Dbg.d("Skipped deleting event with missing CID, syncDB id: %d", Long.valueOf(event.getId()));
            }
        } else {
            ArrayList<ContentProviderOperation> operations = new ArrayList<>();
            ContentProviderOperation.Builder builder = ContentProviderOperation.newDelete(SyncData.Event.URI);
            builder.withSelection("cid=?", new String[]{Integer.toString(eventCid)});
            operations.add(builder.build());
            getResProvider(event.getExtensionCid()).delete(this.mResourceDeleter, eventCid, operations);
        }
    }

    private CostanzaResourceProvider getResProvider(int extensionCid) {
        return this.mResProviderCache.getSynchronized(getExtensionAndEventResProviderName(extensionCid));
    }

    private String getExtensionAndEventResProviderName(int extensionCid) {
        Extension ext = this.mExtensionManager.getExtensionByCid(extensionCid);
        String pkgName = "";
        if (ext != null) {
            pkgName = ext.getPackageName();
        }
        return EXTENSION_AND_EVENT_RES_PROVIDER_NAME_PREFIX + pkgName;
    }

    private void insertExtension(Extension extension, int extensionCid) {
        int registrationMessageId = getNewMessageId();
        int actionMessageId = getNewMessageId();
        ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        if (extension != null) {
            CostanzaResourceProvider resProvider = getResProvider(extensionCid);
            addExtensionRootReference(resProvider, extensionCid);
            ResourceExtension extensionMessage = createExtensionMessage(resProvider, extension, extensionCid, registrationMessageId);
            if (extensionMessage == null) {
                if (Dbg.w()) {
                    Dbg.w("Extension message was null, returning");
                    return;
                }
                return;
            }
            List<NotificationSource> sources = extension.getNotificationSources();
            if (Dbg.d()) {
                Dbg.d("Inserting extension/source");
            }
            syncInsertedNotificationSources(sources);
            ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(SyncData.Extension.URI);
            ContentValues values = new ContentValues();
            values.put("cid", Integer.valueOf(extensionCid));
            values.put("extensionId", extension.getId());
            values.put("apiRegistrationId", Integer.valueOf(extension.getApiRegistrationId()));
            values.put("name", extension.getName());
            values.put("configurationActivity", extension.getConfigurationActivityName());
            values.put("configurationText", extension.getConfigurationText());
            values.put("iconLargeUri", extension.getHostAppIconUri());
            values.put("extensionIconUri", extension.getExtensionIconUri());
            values.put("extension48PxIconUri", extension.getExtension48PxIconUri());
            values.put("extensionIconUriBlackWhite", extension.getExtensionIconUriBlackWhite());
            values.put("extension_key", extension.getExtensionKey());
            values.put("notificationApiVersion", Integer.valueOf(extension.getNotificationApiVersion()));
            values.put("packageName", extension.getPackageName());
            values.put("launchMode", Integer.valueOf(extension.getExtensionLaunchMode()));
            values.put("hostAppPackageName", extension.getHostAppPackage());
            values.put("widgetApiVersion", Integer.valueOf(extension.getWidgetApiVersion()));
            values.put("controlApiVersion", Integer.valueOf(extension.getControlApiVersion()));
            values.put("sensorApiVersion", Integer.valueOf(extension.getSensorApiVersion()));
            values.put("controlBackIntercept", Boolean.valueOf(extension.supportsBackKey()));
            values.put("lowPowerSupport", Integer.valueOf(extension.supportsActiveLowPowerMode() ? 1 : 0));
            operations.add(builder.withValues(values).build());
            addOperations(registrationMessageId, operations);
            RequestExtension actionMessage = new RequestExtension(actionMessageId);
            actionMessage.setAction(0);
            actionMessage.setValue(extensionCid);
            sendMessages(extensionMessage, actionMessage);
            Dbg.d("Installed extension, %s.", extension.getName());
            ArrayList<Integer> referencedCids = new ArrayList<>();
            referencedCids.add(Integer.valueOf(extensionMessage.getIconCid()));
            referencedCids.add(Integer.valueOf(extensionMessage.getIconOfflineCid()));
            referencedCids.add(Integer.valueOf(extensionMessage.getNameCid()));
            resProvider.cidRefTracker().put(extensionCid, referencedCids);
        }
    }

    private ResourceExtension createExtensionMessage(CostanzaResourceProvider resProvider, Extension extension, int extensionCid, int messageId) {
        if (extension == null) {
            if (Dbg.w()) {
                Dbg.w("createExtensionMessage: extension is null");
            }
            return null;
        }
        ResourceExtension extensionMessage = new ResourceExtension(extensionCid, messageId);
        String extensionDisplayName = extension.getName();
        if (extensionDisplayName.endsWith(Extension.CHILD_SUFFIX)) {
            extensionDisplayName = extensionDisplayName.substring(0, extensionDisplayName.indexOf(Extension.CHILD_SUFFIX));
        }
        int nameCid = insertString(resProvider, extensionDisplayName);
        extension.setNameCid(nameCid);
        boolean desaturate = extension.getNotificationApiVersion() == 0;
        int iconCid = insertImage(resProvider, extension.getExtension48PxIconUri(), this.mExtensionIconSize, this.mExtensionIconSize, ImageResourceProvider.ImageFilter.NONE, false);
        int iconOfflineCid = insertImage(resProvider, extension.getExtension48PxIconUri(), this.mExtensionIconSize, this.mExtensionIconSize, desaturate ? ImageResourceProvider.ImageFilter.DESATURATION : ImageResourceProvider.ImageFilter.NONE, false);
        if (iconCid == -1 || iconOfflineCid == -1) {
            iconCid = insertImage(resProvider, extension.getExtensionIconUri(), this.mExtensionIconSize, this.mExtensionIconSize, ImageResourceProvider.ImageFilter.NONE, true);
            iconOfflineCid = insertImage(resProvider, extension.getExtensionIconUri(), this.mExtensionIconSize, this.mExtensionIconSize, desaturate ? ImageResourceProvider.ImageFilter.DESATURATION : ImageResourceProvider.ImageFilter.NONE, true);
        }
        extension.setExtensionIconCid(iconCid);
        extension.setExtensionIconOfflineCid(iconOfflineCid);
        List<NotificationSource> sources = extension.getNotificationSources();
        extensionMessage.setNameCid(nameCid);
        extensionMessage.setIconCid(iconCid);
        extensionMessage.setIconOfflineCid(iconOfflineCid);
        ResourceExtension.ExtensionPriority priority = getExtensionPriority(extension);
        extensionMessage.setPriority(priority);
        extensionMessage.setCapabilities(getCapabilities(extension));
        if (extensionMessage.getCapabilities() == -1) {
            return null;
        }
        extensionMessage.setSupportedDrawingSpecs(getSupportedDrawingSpecs(extension));
        setExtensionColor(extensionMessage, sources);
        extensionMessage.setShowOnHomeScreen(extension.getShowOnHomeScreen());
        extensionMessage.setRelativeCid(extension.getRelativeCid());
        if (Dbg.v()) {
            Dbg.v("Created message for extension: %s with %d sources, color: 0x%08x", extension.getName(), Integer.valueOf(extension.getNotificationSources().size()), Integer.valueOf(extensionMessage.getColor()));
            return extensionMessage;
        }
        return extensionMessage;
    }

    private boolean isInUnicodeRange(int start, int end) {
        if (start >= 32 && end <= 591) {
            return true;
        }
        if (start >= 880 && end <= 1023) {
            return true;
        }
        if (start >= 7936 && end <= 8191) {
            return true;
        }
        if (start >= 12288 && end <= 12351) {
            return true;
        }
        if (start >= 12352 && end <= 12447) {
            return true;
        }
        if (start < 12448 || end > 12543) {
            return start >= 1024 && end <= 1327;
        }
        return true;
    }

    private boolean isSpaceCharacter(int codepoint) {
        int[] unicodeSpaces = {32, NOTIFICATION_MESSAGE_MAX_LENGTH, 5760, 6158, 8192, 8193, FragmentTransaction.TRANSIT_FRAGMENT_CLOSE, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202, 8203, 8239, 8287, 12288, 65279};
        for (int i = 0; i < unicodeSpaces.length - 1; i++) {
            if (unicodeSpaces[i] == codepoint) {
                return true;
            }
        }
        return false;
    }

    private boolean isRTL(String text) {
        int length;
        int ltr_characters = 0;
        int rtl_characters = 0;
        if (text == null) {
            return false;
        }
        if (text.endsWith("...")) {
            length = text.length() - 3;
        } else {
            length = text.length();
        }
        int offset = 0;
        while (offset < length) {
            int codepoint = text.codePointAt(offset);
            if (isSpaceCharacter(codepoint)) {
                offset += Character.charCount(codepoint);
            } else {
                if (codepoint >= 1536 && codepoint <= 1791) {
                    rtl_characters++;
                } else if (codepoint >= 1425 && codepoint <= 1524) {
                    rtl_characters++;
                } else if (codepoint >= 66464 && codepoint <= 66527) {
                    rtl_characters++;
                } else {
                    ltr_characters++;
                }
                offset += Character.charCount(codepoint);
            }
        }
        return rtl_characters + ltr_characters != 0 && (rtl_characters * 100) / (rtl_characters + ltr_characters) >= 70;
    }

    private boolean isBitmapRendering(String text) {
        int min = CidProvider.CID_MAX_VALUE;
        int max = Integer.MIN_VALUE;
        if (text == null) {
            return false;
        }
        int offset = 0;
        while (offset < text.length()) {
            int codepoint = text.codePointAt(offset);
            if (codepoint > max) {
                max = codepoint;
            } else if (codepoint < min) {
                min = codepoint;
            }
            offset += Character.charCount(codepoint);
        }
        if (max == Integer.MIN_VALUE) {
            max = min;
        }
        if (min == Integer.MAX_VALUE) {
            min = max;
        }
        return !isInUnicodeRange(min, max);
    }

    private ResourceNotification createEventMessage(CostanzaResourceProvider resProvider, Event syncEvent, Event event, int eventCid, int messageId) {
        if (event == null) {
            if (Dbg.w()) {
                Dbg.w("createEventMessage: event is null");
            }
            return null;
        }
        NotificationSource source = this.mExtensionManager.getSource(event.getSourceId());
        if (source == null) {
            if (Dbg.w()) {
                Dbg.w("No source found for source ID %d", Long.valueOf(event.getSourceId()));
            }
            return null;
        }
        if (!source.isEnabled()) {
            if (Dbg.w()) {
                Dbg.w("Source ID: %d disabled, ignoring event", Long.valueOf(source.getSmartConnectSourceId()));
            }
            return null;
        }
        ExtensionManager extensionManager = getExtensionManager();
        Extension extension = extensionManager.getExtensionFromSource(event.getSourceId());
        if (extension == null) {
            if (Dbg.w()) {
                Dbg.w("Extension not found for source: " + event.getSourceId());
            }
            return null;
        }
        if (Dbg.d()) {
            Dbg.w("Extension from sourceid: " + event.getSourceId() + "  was : " + extension.getName() + " cid: " + extension.getCid());
        }
        if (Dbg.d()) {
            Dbg.d("Sending event, cid: 0x%08x, messageId: %d", Integer.valueOf(eventCid), Integer.valueOf(messageId));
        }
        ArrayList<ResourceNotificationDataMap> dataMap = new ArrayList<>();
        int richIconCid = insertImage(resProvider, extension.getBiggestExtensionIconUri(), this.mNotificationRichNotificationIconSize, this.mNotificationRichNotificationIconSize, ImageResourceProvider.ImageFilter.NONE, true);
        int statusBarIconCid = insertImage(resProvider, source.getIcon1(), this.mNotificationStatusBarIconSize, this.mNotificationStatusBarIconSize, ImageResourceProvider.ImageFilter.NONE, true);
        int widgetColorIconCid = insertImage(resProvider, source.getIcon1(), this.mNotificationWidgetIconSize, this.mNotificationWidgetIconSize, ImageResourceProvider.ImageFilter.NONE, true);
        int widgetGrayscaleIconCid = insertThreeBitImage(resProvider, source.getIcon1(), this.mNotificationWidgetIconSize, this.mNotificationWidgetIconSize);
        addResourceLayoutData(dataMap, 4, richIconCid);
        addResourceLayoutData(dataMap, 3, statusBarIconCid);
        addResourceLayoutData(dataMap, 7, widgetColorIconCid);
        addResourceLayoutData(dataMap, 8, widgetGrayscaleIconCid);
        String message = event.getMessage();
        this.bitmapRendering = isBitmapRendering(event.getTitle()) || isBitmapRendering(message) || isBitmapRendering(event.getDisplayNameWithContactFallback(this.mContentResolver));
        if (message != null && message.length() > NOTIFICATION_MESSAGE_MAX_LENGTH) {
            message = message.substring(0, NOTIFICATION_MESSAGE_MAX_LENGTH);
        }
        if (this.bitmapRendering) {
            String title = event.getTitle();
            if (title == null) {
                addResourceLayoutData(dataMap, 0, -1);
                addResourceLayoutData(dataMap, 9, -1);
                addResourceLayoutData(dataMap, 10, -1);
            } else {
                this.titleRTL = isRTL(title);
                int titleCid = insertTitleImage(resProvider, title, -16777216, false, event);
                addResourceLayoutData(dataMap, 0, titleCid);
                int titleCid2 = insertTitleImage(resProvider, title, -1, true, event);
                addResourceLayoutData(dataMap, 9, titleCid2);
                int titleCid3 = insertTitleImage(resProvider, title, -16777216, true, event);
                addResourceLayoutData(dataMap, 10, titleCid3);
            }
        } else {
            int titleCid4 = insertString(resProvider, event.getTitle());
            addResourceLayoutData(dataMap, 0, titleCid4);
            int messageCid = insertString(resProvider, message);
            addResourceLayoutData(dataMap, 2, messageCid);
        }
        if (this.bitmapRendering) {
            int messageCid2 = insertMessageImage(resProvider, message, -16777216, false);
            addResourceLayoutData(dataMap, 2, messageCid2);
            int messageCid3 = insertMessageImage(resProvider, message, -16777216, true);
            addResourceLayoutData(dataMap, 16, messageCid3);
            int messageCid4 = insertMessageImage(resProvider, message, -1, false);
            addResourceLayoutData(dataMap, 15, messageCid4);
            if (event.getTitle() == null) {
                this.titleRTL = isRTL(message);
            }
        }
        int richNotificationCid = insertRichNotification(resProvider, message, RICH_NOTIFICATION_BITMAP_LENGTH, 100, -1);
        addResourceLayoutData(dataMap, 14, richNotificationCid);
        if (this.bitmapRendering) {
            String displayName = event.getDisplayNameWithContactFallback(this.mContentResolver);
            this.displayNameRTL = isRTL(displayName);
            int displayNameCid = insertDisplayNameImage(resProvider, displayName, -16777216, false, event);
            addResourceLayoutData(dataMap, 1, displayNameCid);
            int displayNameCid2 = insertDisplayNameImage(resProvider, displayName, -16777216, true, event);
            addResourceLayoutData(dataMap, 12, displayNameCid2);
            int displayNameCid3 = insertDisplayNameDrawerImage(resProvider, displayName, -1, true);
            addResourceLayoutData(dataMap, 13, displayNameCid3);
            int displayNameCid4 = insertDisplayNameImage(resProvider, displayName, -1, false, event);
            addResourceLayoutData(dataMap, 11, displayNameCid4);
        } else {
            int displayNameCid5 = insertString(resProvider, event.getDisplayNameWithContactFallback(this.mContentResolver));
            addResourceLayoutData(dataMap, 1, displayNameCid5);
        }
        int renderingParameters = 0;
        if (this.bitmapRendering) {
            renderingParameters = 0 | 1;
            if (this.displayNameRTL) {
                renderingParameters |= 2;
            }
            if (this.titleRTL) {
                renderingParameters |= 4;
            }
        }
        addResourceLayoutData(dataMap, 17, renderingParameters);
        insertProfileImage(resProvider, dataMap, syncEvent, event);
        insertEventImage(resProvider, dataMap, syncEvent, event);
        ResourceNotification eventMessage = new ResourceNotification(eventCid, messageId);
        eventMessage.setLocalization(0);
        eventMessage.setRead(event.isRead());
        eventMessage.setPersonal(event.isPersonal());
        eventMessage.setTimeStamp(event.getPublishedTime());
        eventMessage.setNotificationExtensionCid(event.getExtensionCid());
        eventMessage.setFields((ResourceNotificationDataMap[]) dataMap.toArray(new ResourceNotificationDataMap[dataMap.size()]));
        MenuControl onlineMenu = getOnlineSourceMenu(resProvider, source);
        if (onlineMenu != null) {
            eventMessage.setOnlineMenuCid(onlineMenu.getCid());
            eventMessage.setOnlineMenuScreens(onlineMenu.getMenuSubCids());
        }
        MenuControl offlineMenu = getOfflineSourceMenu(resProvider, source);
        if (offlineMenu != null) {
            eventMessage.setOfflineMenuCid(offlineMenu.getCid());
            eventMessage.setOfflineMenuScreens(offlineMenu.getMenuSubCids());
        }
        eventMessage.setSubScreens(insertOverviewEventScreen(resProvider, event));
        ArrayList<Integer> referencedCids = new ArrayList<>();
        referencedCids.add(Integer.valueOf(eventMessage.getNotificationExtensionCid()));
        referencedCids.add(Integer.valueOf(eventMessage.getOfflineMenuCid()));
        referencedCids.add(Integer.valueOf(eventMessage.getOnlineMenuCid()));
        for (ResourceNotificationDataMap data : dataMap) {
            referencedCids.add(Integer.valueOf(data.getValue()));
        }
        addSubCidsToList(eventMessage.getOnlineMenuScreens(), referencedCids);
        addSubCidsToList(eventMessage.getOfflineMenuScreens(), referencedCids);
        addSubCidsToList(eventMessage.getSubScreens(), referencedCids);
        resProvider.cidRefTracker().put(eventCid, referencedCids);
        return eventMessage;
    }

    String cutString(String text, int destLength, int w, TextPaint tp) {
        if (text == null) {
            return "";
        }
        int length = text.length();
        int codepoints = text.codePointCount(0, length);
        int codepointCounter = 0;
        int[][] characterOffsetsWidths = (int[][]) Array.newInstance((Class<?>) Integer.TYPE, destLength, 2);
        boolean truncated = false;
        if (destLength < 1 || w < 1) {
            return "";
        }
        int dotsWidth = (int) tp.measureText("...");
        int offset = 0;
        while (offset < length) {
            int codepoint = text.codePointAt(offset);
            String tmpString = text.substring(0, offset);
            int tmpStringWidth = (int) tp.measureText(tmpString);
            characterOffsetsWidths[codepointCounter][0] = offset;
            characterOffsetsWidths[codepointCounter][1] = tmpStringWidth;
            if (tmpStringWidth > w && codepointCounter - 1 >= 0) {
                for (int i = codepointCounter - 1; i >= 0; i--) {
                    if (characterOffsetsWidths[i][1] < w - dotsWidth) {
                        offset = characterOffsetsWidths[i][0];
                        truncated = true;
                        break;
                    }
                }
            }
            codepointCounter++;
            if (codepointCounter == destLength && codepointCounter < codepoints) {
                truncated = true;
                break;
            }
            if (codepointCounter == destLength || truncated) {
                break;
            }
            offset += Character.charCount(codepoint);
        }
        if (truncated) {
            return text.substring(0, offset).concat("...");
        }
        return text.substring(0, offset);
    }

    private void addSubCidsToList(ResourceSubCid[] subCids, ArrayList<Integer> referencedCids) {
        if (subCids != null) {
            for (ResourceSubCid subCid : subCids) {
                referencedCids.add(Integer.valueOf(subCid.getResourceCid()));
            }
        }
    }

    private ResourceExtension.ExtensionPriority getExtensionPriority(Extension extension) {
        boolean isCallExtension = extension.getPackageName() != null && extension.getPackageName().equals("com.sonyericsson.extras.liveware.extension.call");
        if (isCallExtension) {
            return ResourceExtension.ExtensionPriority.APPLICATION_ALERT_PRIORITY_INCOMING_CALL;
        }
        return ResourceExtension.ExtensionPriority.APPLICATION_ALERT_PRIORITY_NONE;
    }

    private void setExtensionColor(ResourceExtension extensionMessage, List<NotificationSource> sources) {
        if (sources != null && sources.size() > 0) {
            NotificationSource source = sources.get(0);
            extensionMessage.setColor(source.getColor());
            if (Dbg.v()) {
                Dbg.v("Set extension color: 0x%08x from source: %s", Integer.valueOf(extensionMessage.getColor()), source.getName());
            }
        }
    }

    private void updateExtension(Extension extension, Extension syncExtension) {
        boolean sendToAccessory = false;
        int syncExtensionCid = syncExtension.getCid();
        ContentValues values = new ContentValues();
        List<NotificationSource> sources = extension.getNotificationSources();
        List<NotificationSource> syncSources = syncExtension.getNotificationSources();
        if (extension.getId() != null && !extension.getId().equals(syncExtension.getId())) {
            if (Dbg.v()) {
                Dbg.v("Extension %s changed id from %d to %d.", syncExtension.getPackageName(), syncExtension.getId(), extension.getId());
            }
            values.put("extensionId", extension.getId());
        }
        if (extension.getApiRegistrationId() != syncExtension.getApiRegistrationId()) {
            values.put("apiRegistrationId", Integer.valueOf(extension.getApiRegistrationId()));
        }
        if (!TextUtils.equals(syncExtension.getConfigurationActivityName(), extension.getConfigurationActivityName())) {
            values.put("configurationActivity", extension.getConfigurationActivityName());
        }
        if (!TextUtils.equals(syncExtension.getConfigurationText(), extension.getConfigurationText())) {
            values.put("configurationText", extension.getConfigurationText());
        }
        if (!TextUtils.equals(syncExtension.getExtension48PxIconUri(), extension.getExtension48PxIconUri())) {
            sendToAccessory = true;
            values.put("extension48PxIconUri", extension.getExtension48PxIconUri());
        }
        if (extension.getExtension48PxIconUri() == null && !TextUtils.equals(syncExtension.getExtensionIconUri(), extension.getExtensionIconUri())) {
            sendToAccessory = true;
            values.put("extensionIconUri", extension.getExtensionIconUri());
        }
        if (!TextUtils.equals(syncExtension.getExtensionIconUriBlackWhite(), extension.getExtensionIconUriBlackWhite())) {
            values.put("extensionIconUriBlackWhite", extension.getExtensionIconUriBlackWhite());
        }
        if (!TextUtils.equals(syncExtension.getExtensionKey(), extension.getExtensionKey())) {
            values.put("extension_key", extension.getExtensionKey());
        }
        if (!TextUtils.equals(syncExtension.getHostAppIconUri(), extension.getHostAppIconUri())) {
            values.put("iconLargeUri", extension.getHostAppIconUri());
        }
        if (!TextUtils.equals(syncExtension.getHostAppPackage(), extension.getHostAppPackage())) {
            values.put("hostAppPackageName", extension.getHostAppPackage());
        }
        if (!TextUtils.equals(syncExtension.getName(), extension.getName())) {
            sendToAccessory = true;
            values.put("name", extension.getName());
        }
        if (!TextUtils.equals(syncExtension.getPackageName(), extension.getPackageName())) {
            values.put("packageName", extension.getPackageName());
        }
        if (syncExtension.getControlApiVersion() != extension.getControlApiVersion()) {
            values.put("controlApiVersion", Integer.valueOf(extension.getControlApiVersion()));
        }
        if (syncExtension.getNotificationApiVersion() != extension.getNotificationApiVersion()) {
            values.put("notificationApiVersion", Integer.valueOf(extension.getNotificationApiVersion()));
        }
        if (syncExtension.getSensorApiVersion() != extension.getSensorApiVersion()) {
            values.put("sensorApiVersion", Integer.valueOf(extension.getSensorApiVersion()));
        }
        if (syncExtension.getWidgetApiVersion() != extension.getWidgetApiVersion()) {
            values.put("widgetApiVersion", Integer.valueOf(extension.getWidgetApiVersion()));
        }
        if (syncExtension.supportsBackKey() != extension.supportsBackKey()) {
            values.put("controlBackIntercept", Integer.valueOf(extension.supportsBackKey() ? 1 : 0));
        }
        if (syncExtension.supportsActiveLowPowerMode() != extension.supportsActiveLowPowerMode()) {
            sendToAccessory = true;
            values.put("lowPowerSupport", Integer.valueOf(extension.supportsActiveLowPowerMode() ? 1 : 0));
        }
        if (syncExtension.getShowOnHomeScreen() != extension.getShowOnHomeScreen()) {
            sendToAccessory = true;
        }
        if (!isSourceColorEqual(syncSources, sources)) {
            sendToAccessory = true;
        }
        if (values.size() == 0 && !sendToAccessory) {
            if (Dbg.d()) {
                Dbg.d("Could not find any updated data in extension with id: " + extension.getId());
                return;
            }
            return;
        }
        syncUpdatedNotificationSources(sources, syncSources);
        if (sendToAccessory) {
            int messageId = getNewMessageId();
            if (values.size() > 0) {
                ArrayList<ContentProviderOperation> operations = new ArrayList<>();
                ContentProviderOperation.Builder builder = ContentProviderOperation.newUpdate(SyncData.Extension.URI);
                builder.withValues(values);
                builder.withSelection("cid=?", new String[]{Integer.toString(syncExtensionCid)});
                operations.add(builder.build());
                addOperations(messageId, operations);
            }
            if (Dbg.v()) {
                Dbg.v("Updating extension %s with %d sources", extension.getName(), Integer.valueOf(sources.size()));
            }
            ResourceExtension extensionMessage = createExtensionMessage(getResProvider(syncExtensionCid), extension, syncExtensionCid, messageId);
            RequestExtension updateMessage = new RequestExtension(getNewMessageId());
            updateMessage.setAction(2);
            updateMessage.setValue(syncExtensionCid);
            sendMessages(extensionMessage, updateMessage);
            return;
        }
        this.mContentResolver.update(SyncData.Extension.URI, values, "cid=?", new String[]{Integer.toString(syncExtensionCid)});
    }

    private boolean isSourceColorEqual(List<NotificationSource> syncSources, List<NotificationSource> sources) {
        if (syncSources.isEmpty() || sources.isEmpty()) {
            return false;
        }
        if (syncSources.get(0) == null || sources.get(0) == null) {
            return false;
        }
        return syncSources.get(0).getColor() == sources.get(0).getColor();
    }

    private int getSupportedDrawingSpecs(Extension extension) {
        if (!extension.supportsClockWidgets() && !extension.hasNotificationsSources()) {
            return 1;
        }
        int drawingSpecs = 1 | 248;
        return drawingSpecs;
    }

    private int getCapabilities(Extension extension) {
        int capabilities;
        if (extension.getNotificationApiVersion() > 0) {
            capabilities = 0 | 1;
        } else if (extension.getControlApiVersion() > 0 || extension.getWidgetApiVersion() > 0) {
            capabilities = 0 | 2;
        } else {
            if (Dbg.w()) {
                Dbg.w("Invalid extension registered, capabilities error");
            }
            return -1;
        }
        if (extension.supportsActiveLowPowerMode()) {
            capabilities |= 4;
        }
        return capabilities;
    }

    private void deleteExtension(Extension extension) {
        int extensionCid = extension.getCid();
        if (extensionCid == -1) {
            if (Dbg.d()) {
                Dbg.d("Skipped deleting extension with missing CID, %s", extension.getName());
                return;
            }
            return;
        }
        ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        ContentProviderOperation.Builder builder = ContentProviderOperation.newDelete(SyncData.Extension.URI);
        builder.withSelection("cid=?", new String[]{Integer.toString(extensionCid)});
        operations.add(builder.build());
        RequestExtension actionMessage = new RequestExtension(getNewMessageId());
        actionMessage.setAction(1);
        actionMessage.setValue(extensionCid);
        sendMessages(actionMessage, actionMessage);
        getResProvider(extensionCid).delete(this.mResourceDeleter, extensionCid, operations);
    }

    private void addExtensionRootReference(CostanzaResourceProvider resProvider, int extensionCid) {
        resProvider.cidRefTracker().put(this.mCidProvider.getReserved(CidProvider.ReservedCid.HOST_APP_EXTENSION_ROOT), extensionCid);
    }

    private void insertNotificationSource(NotificationSource source, int sourceCid) {
        if (source != null) {
            ContentValues values = new ContentValues();
            values.put("source_id", Long.valueOf(source.getSmartConnectSourceId()));
            values.put("cid", Integer.valueOf(sourceCid));
            values.put("action_1", source.getActionText(0));
            values.put("action_2", source.getActionText(1));
            values.put("action_3", source.getActionText(2));
            values.put("enabled", Boolean.valueOf(source.isEnabled()));
            values.put("extension_specific_id", source.getExtensionSpecificSourceId());
            values.put("iconUri1", source.getIcon1());
            values.put("iconUri2", source.getIcon2());
            values.put("iconUriBlackWhite", source.getIconBlackWhite());
            values.put("name", source.getName());
            values.put("packageName", source.getPackageName());
            values.put("textToSpeech", source.getTextToSpeechText());
            values.put("updateTime", Long.valueOf(source.getUpdateTime()));
            values.put("color", Integer.valueOf(source.getColor()));
            this.mContentResolver.insert(SyncData.NotificationSource.URI, values);
            Extension extension = this.mExtensionManager.getExtensionFromSource(source.getSmartConnectSourceId());
            if (extension != null) {
                if (Dbg.v()) {
                    Dbg.v("Updating extension '%s' after inserting source with name: %s, color: 0x%08x", extension.getName(), source.getName(), Integer.valueOf(source.getColor()));
                }
                ResourceExtension extensionMessage = createExtensionMessage(getResProvider(extension.getCid()), extension, extension.getCid(), getNewMessageId());
                RequestExtension updateMessage = new RequestExtension(getNewMessageId());
                updateMessage.setAction(2);
                updateMessage.setValue(extension.getCid());
                sendMessages(extensionMessage, updateMessage);
            }
        }
    }

    private void updateNotificationSource(NotificationSource source, NotificationSource syncSource) {
        if (source != null && syncSource != null) {
            int syncSourceCid = syncSource.getCid();
            ContentValues values = new ContentValues();
            if (!TextUtils.equals(syncSource.getActionText(0), source.getActionText(0))) {
                values.put("action_1", source.getActionText(0));
            }
            if (!TextUtils.equals(syncSource.getActionText(1), source.getActionText(1))) {
                values.put("action_2", source.getActionText(1));
            }
            if (!TextUtils.equals(syncSource.getActionText(2), source.getActionText(2))) {
                values.put("action_3", source.getActionText(2));
            }
            if (!TextUtils.equals(syncSource.getActionIconUri(0), source.getActionIconUri(0))) {
                values.put("action_icon_1", source.getActionIconUri(0));
            }
            if (!TextUtils.equals(syncSource.getActionIconUri(1), source.getActionIconUri(1))) {
                values.put("action_icon_2", source.getActionIconUri(1));
            }
            if (!TextUtils.equals(syncSource.getActionIconUri(2), source.getActionIconUri(2))) {
                values.put("action_icon_3", source.getActionIconUri(2));
            }
            if (!TextUtils.equals(syncSource.getExtensionSpecificSourceId(), source.getExtensionSpecificSourceId())) {
                values.put("extension_specific_id", source.getExtensionSpecificSourceId());
            }
            if (!TextUtils.equals(syncSource.getIcon1(), source.getIcon1())) {
                values.put("iconUri1", source.getIcon1());
            }
            if (!TextUtils.equals(syncSource.getIcon2(), source.getIcon2())) {
                values.put("iconUri2", source.getIcon2());
            }
            if (!TextUtils.equals(syncSource.getIconBlackWhite(), source.getIconBlackWhite())) {
                values.put("iconUriBlackWhite", source.getIconBlackWhite());
            }
            if (!TextUtils.equals(syncSource.getName(), source.getName())) {
                values.put("name", source.getName());
            }
            if (!TextUtils.equals(syncSource.getPackageName(), source.getPackageName())) {
                values.put("packageName", source.getPackageName());
            }
            if (!TextUtils.equals(syncSource.getTextToSpeechText(), source.getTextToSpeechText())) {
                values.put("textToSpeech", source.getTextToSpeechText());
            }
            if (syncSource.getUpdateTime() != source.getUpdateTime()) {
                values.put("updateTime", Long.valueOf(source.getUpdateTime()));
            }
            if (syncSource.isEnabled() != source.isEnabled()) {
                values.put("enabled", Boolean.valueOf(source.isEnabled()));
            }
            if (syncSource.getColor() != source.getColor()) {
                values.put("color", Integer.valueOf(source.getColor()));
                Extension extension = this.mExtensionManager.getExtensionFromSource(syncSource.getSmartConnectSourceId());
                if (extension != null) {
                    ResourceExtension extensionMessage = createExtensionMessage(getResProvider(extension.getCid()), extension, extension.getCid(), getNewMessageId());
                    RequestExtension updateMessage = new RequestExtension(getNewMessageId());
                    updateMessage.setAction(2);
                    updateMessage.setValue(extension.getCid());
                    sendMessages(extensionMessage, updateMessage);
                    if (Dbg.v()) {
                        Dbg.v("Updating extension '%s' after source color changed, name: %s, color: 0x%08x", extension.getName(), source.getName(), Integer.valueOf(source.getColor()));
                    }
                }
            }
            if (values.size() == 0) {
                Dbg.w("No updates found in notification source with id: " + source.getSmartConnectSourceId());
            } else {
                this.mContentResolver.update(SyncData.NotificationSource.URI, values, "cid=?", new String[]{Integer.toString(syncSourceCid)});
                clearSourceFromCaches(Long.valueOf(source.getSmartConnectSourceId()));
            }
        }
    }

    private void deleteNotificationSource(NotificationSource source) {
        if (source != null) {
            int cid = source.getCid();
            this.mContentResolver.delete(SyncData.NotificationSource.URI, "cid=?", new String[]{Integer.toString(cid)});
            clearSourceFromCaches(Long.valueOf(source.getSmartConnectSourceId()));
        }
    }

    private void clearSourceFromCaches(Long sourceId) {
        if (Dbg.d()) {
            Dbg.d("Removing source from cache: " + sourceId);
        }
        this.mOnlineMenuCache.remove(sourceId);
        this.mOfflineMenuCache.remove(sourceId);
    }

    /* JADX WARN: Code duplicated, block: B:13:0x0045  */
    public Hashtable<Long, List<Event>> getAllEvents() {
        Hashtable<Long, List<Event>> hash = new Hashtable<>();
        Cursor c = this.mContentResolver.query(SyncData.Event.URI, null, null, null, null);
        if (c != null) {
            while (c.moveToNext()) {
                try {
                    try {
                        Event event = readEvent(c);
                        long extensionId = event.getExtensionId();
                        HashSet<Event> events = new HashSet<>(hash.get(Long.valueOf(extensionId)));
                        if (events != null) {
                            events.add(event);
                        } else {
                            HashSet<Event> events2 = new HashSet<>();
                            events2.add(event);
                            hash.put(Long.valueOf(extensionId), new ArrayList(events2));
                        }
                    } catch (Exception e) {
                        Analytics.getInstance().sendCaughtException(e);
                        Dbg.e("Failed while reading cursor.", e);
                        if (c != null) {
                            c.close();
                        }
                    }
                } catch (Throwable th) {
                    if (c != null) {
                        c.close();
                    }
                    throw th;
                }
            }
            if (c != null) {
                c.close();
            }
        } else if (c != null) {
            c.close();
        }
        return hash;
    }

    /* JADX WARN: Code duplicated, block: B:10:0x0029 A[Catch: all -> 0x007e, TRY_ENTER, TryCatch #0 {all -> 0x007e, blocks: (B:4:0x0019, B:6:0x001f, B:10:0x0029, B:11:0x0033, B:13:0x0039, B:14:0x0049, B:16:0x004f, B:18:0x0061, B:20:0x0069, B:22:0x0071, B:24:0x0079), top: B:30:0x0019 }] */
    /* JADX WARN: Code duplicated, block: B:13:0x0039 A[Catch: all -> 0x007e, TryCatch #0 {all -> 0x007e, blocks: (B:4:0x0019, B:6:0x001f, B:10:0x0029, B:11:0x0033, B:13:0x0039, B:14:0x0049, B:16:0x004f, B:18:0x0061, B:20:0x0069, B:22:0x0071, B:24:0x0079), top: B:30:0x0019 }] */
    /* JADX WARN: Code duplicated, block: B:16:0x004f A[Catch: all -> 0x007e, TryCatch #0 {all -> 0x007e, blocks: (B:4:0x0019, B:6:0x001f, B:10:0x0029, B:11:0x0033, B:13:0x0039, B:14:0x0049, B:16:0x004f, B:18:0x0061, B:20:0x0069, B:22:0x0071, B:24:0x0079), top: B:30:0x0019 }] */
    private Event getEvent(long smartConnectEventId) {
        ContentValues values;
        Long eventId;
        Event event = null;
        String[] selectionArgs = {Long.toString(smartConnectEventId)};
        Cursor c = this.mContentResolver.query(SyncData.Event.URI, null, "event_id=?", selectionArgs, null);
        if (c == null) {
            loop0: for (Map.Entry<Integer, ArrayList<ContentProviderOperation>> entry : this.mPendingOperations.entrySet()) {
                List<ContentProviderOperation> operations = entry.getValue();
                while (r11.hasNext()) {
                    if (!operation.getUri().equals(SyncData.Event.URI)) {
                    }
                }
            }
        } else {
            try {
                if (c.moveToFirst()) {
                    event = readEvent(c);
                } else {
                    loop0: while (r10.hasNext()) {
                        List<ContentProviderOperation> operations2 = entry.getValue();
                        for (ContentProviderOperation operation : operations2) {
                            if (!operation.getUri().equals(SyncData.Event.URI) && (values = operation.resolveValueBackReferences(null, 0)) != null && (eventId = values.getAsLong("event_id")) != null && eventId.longValue() == smartConnectEventId) {
                                event = readEvent(values);
                                break loop0;
                            }
                        }
                    }
                }
            } catch (Throwable th) {
                if (c != null) {
                    c.close();
                }
                throw th;
            }
        }
        if (c != null) {
            c.close();
        }
        return event;
    }

    private Event readEvent(Cursor c) {
        long id = c.getLong(c.getColumnIndexOrThrow(LogDbHelper.COL_ID));
        long smartConnectEventId = c.getLong(c.getColumnIndexOrThrow("event_id"));
        long extensionId = c.getLong(c.getColumnIndexOrThrow(SyncData.EventColumns.EXTENSION_ID));
        long sourceId = c.getLong(c.getColumnIndexOrThrow("sourceId"));
        int cid = c.getInt(c.getColumnIndexOrThrow("cid"));
        String title = c.getString(c.getColumnIndexOrThrow("title"));
        String message = c.getString(c.getColumnIndexOrThrow("message"));
        long publishedTime = c.getLong(c.getColumnIndexOrThrow("publishedTime"));
        int readStatus = c.getInt(c.getColumnIndexOrThrow("readStatus"));
        String imageUriString = c.getString(c.getColumnIndexOrThrow("imageUri"));
        String profileImageUriString = c.getString(c.getColumnIndexOrThrow("profile_image_uri"));
        String displayName = c.getString(c.getColumnIndexOrThrow("display_name"));
        String contactsReference = c.getString(c.getColumnIndexOrThrow("contacts_reference"));
        int personal = c.getInt(c.getColumnIndexOrThrow("personal"));
        int extensionCid = c.getInt(c.getColumnIndexOrThrow(SyncData.EventColumns.EXTENSION_CID));
        Event event = new Event(id, extensionId, sourceId, smartConnectEventId, cid, extensionCid, title, message, publishedTime, readStatus, imageUriString, profileImageUriString, displayName, contactsReference, personal);
        return event;
    }

    private Event readEvent(ContentValues values) {
        Long id = values.getAsLong(LogDbHelper.COL_ID);
        Long smartConnectEventId = values.getAsLong("event_id");
        Long extensionId = values.getAsLong(SyncData.EventColumns.EXTENSION_ID);
        Long sourceId = values.getAsLong("sourceId");
        Integer cid = values.getAsInteger("cid");
        String title = values.getAsString("title");
        String message = values.getAsString("message");
        Long publishedTime = values.getAsLong("publishedTime");
        Boolean readStatus = values.getAsBoolean("readStatus");
        String imageUriString = values.getAsString("imageUri");
        String profileImageUriString = values.getAsString("profile_image_uri");
        String displayName = values.getAsString("display_name");
        String contactsReference = values.getAsString("contacts_reference");
        Boolean personal = values.getAsBoolean("personal");
        Integer extensionCid = values.getAsInteger(SyncData.EventColumns.EXTENSION_CID);
        Event event = new Event(id != null ? id.longValue() : -1L, extensionId != null ? extensionId.longValue() : -1L, sourceId != null ? sourceId.longValue() : -1L, smartConnectEventId != null ? smartConnectEventId.longValue() : -1L, cid != null ? cid.intValue() : -1, extensionCid != null ? extensionCid.intValue() : -1, title, message, publishedTime.longValue(), readStatus.booleanValue() ? 1 : 0, imageUriString, profileImageUriString, displayName, contactsReference, personal.booleanValue() ? 1 : 0);
        return event;
    }

    private boolean insertEvent(Event event) {
        int eventCid = event.getCid();
        int messageId = getNewMessageId();
        int extensionCid = getExtensionCidBySourceId(event.getSourceId());
        if (extensionCid == -1) {
            if (Dbg.w()) {
                Dbg.w("Extension CID not found for event: " + event.getId());
            }
            return false;
        }
        event.setExtensionCid(extensionCid);
        if (Dbg.w()) {
            Dbg.w("Inserting event for extensions with CID: " + extensionCid);
        }
        ResourceNotification eventMessage = createEventMessage(getResProvider(event.getExtensionCid()), event, event, eventCid, messageId);
        if (eventMessage == null) {
            return false;
        }
        ArrayList<ContentProviderOperation> operations = new ArrayList<>();
        ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(SyncData.Event.URI);
        ContentValues values = new ContentValues();
        values.put("cid", Integer.valueOf(eventCid));
        values.put("event_id", Long.valueOf(event.getSmartConnectEventId()));
        values.put(SyncData.EventColumns.EXTENSION_ID, Long.valueOf(event.getExtensionId()));
        values.put("sourceId", Long.valueOf(event.getSourceId()));
        values.put("title", event.getTitle());
        values.put("message", event.getMessage());
        values.put("publishedTime", Long.valueOf(event.getPublishedTime()));
        values.put("readStatus", Boolean.valueOf(event.isRead()));
        values.put("imageUri", event.getEventImageUri());
        values.put("profile_image_uri", event.getProfileImageUri());
        values.put("display_name", event.getDisplayName());
        values.put("contacts_reference", event.getContactsReference());
        values.put("personal", Boolean.valueOf(event.isPersonal()));
        values.put(SyncData.EventColumns.EXTENSION_CID, Integer.valueOf(event.getExtensionCid()));
        operations.add(builder.withValues(values).build());
        addOperations(messageId, operations);
        sendMessages(eventMessage);
        return true;
    }

    private int getExtensionCidBySourceId(long sourceId) {
        NotificationSource source = this.mExtensionManager.getSource(sourceId);
        if (source == null) {
            if (!Dbg.w()) {
                return -1;
            }
            Dbg.w("No source found for source ID %d.", Long.valueOf(sourceId));
            return -1;
        }
        if (!source.isEnabled()) {
            if (!Dbg.w()) {
                return -1;
            }
            Dbg.w("Source ID: %d disabled, ignoring event", Long.valueOf(source.getSmartConnectSourceId()));
            return -1;
        }
        ExtensionManager extensionManager = getExtensionManager();
        Extension extension = extensionManager.getExtensionFromSource(sourceId);
        if (extension == null) {
            if (!Dbg.w()) {
                return -1;
            }
            Dbg.w("Extension not found for source: %s.", Long.valueOf(sourceId));
            return -1;
        }
        return extension.getCid();
    }

    private void insertEventImage(CostanzaResourceProvider resProvider, List<ResourceNotificationDataMap> dataMap, Event syncEvent, Event event) {
        String imageUri = event.getEventImageUri();
        if (imageUri != null) {
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                InputStream is = this.mContentResolver.openInputStream(Uri.parse(imageUri));
                if (is == null) {
                    if (Dbg.e()) {
                        Dbg.e("No image found at: " + imageUri);
                        return;
                    }
                    return;
                }
                BitmapFactory.decodeStream(is, null, options);
                is.close();
                int width = options.outWidth;
                int height = options.outHeight;
                int imgViewWidth = this.mCostanzaWidth - 6;
                int imgViewHeight = this.mNotificationOverviewHeight - 6;
                if (width > imgViewWidth || height > imgViewHeight) {
                    float ratio = width / height;
                    if (height >= width) {
                        width = (int) (imgViewHeight * ratio);
                        height = imgViewHeight;
                    } else {
                        width = imgViewWidth;
                        height = (int) (imgViewWidth / ratio);
                        if (height > imgViewHeight) {
                            width = (int) (imgViewHeight * ratio);
                            height = imgViewHeight;
                        }
                    }
                }
                if (Dbg.d()) {
                    Dbg.d("Scaled event image size, w: " + width + ", h: " + height);
                }
                LayoutData layoutData = new LayoutData();
                layoutData.setUri(Uri.parse(imageUri));
                layoutData.setTargetSize(width, height, LayoutData.ScaleOption.SCALE_TO_EXACT_SIZE);
                int imageCid = insertImage(resProvider, layoutData);
                int shifterCid = insertShifter(resProvider, event.getCid(), imageCid, (this.mCostanzaWidth - width) / 2, (this.mNotificationOverviewHeight - height) / 2);
                syncEvent.setImageCid(shifterCid);
                addResourceLayoutData(dataMap, 6, shifterCid);
            } catch (FileNotFoundException e) {
                Analytics.getInstance().sendCaughtException(e);
                if (Dbg.e()) {
                    Dbg.e(e);
                }
            } catch (IOException e2) {
                Analytics.getInstance().sendCaughtException(e2);
                if (Dbg.e()) {
                    Dbg.e(e2);
                }
            } catch (Exception e3) {
                if (Dbg.e()) {
                    Dbg.e(e3);
                }
            }
        }
    }

    private void insertProfileImage(CostanzaResourceProvider resProvider, List<ResourceNotificationDataMap> dataMap, Event syncEvent, Event event) {
        Bitmap bitmap;
        if (event.getProfileImageUri() != null) {
            LayoutData layoutData = new LayoutData();
            layoutData.setUri(Uri.parse(event.getProfileImageUri()));
            layoutData.setTargetSize(this.mNotificationContactIconSize, this.mNotificationContactIconSize, LayoutData.ScaleOption.SCALE_TO_EXACT_SIZE);
            syncEvent.setProfileImageCid(insertImage(resProvider, layoutData));
            addResourceLayoutData(dataMap, 5, syncEvent.getProfileImageCid());
            return;
        }
        if (event.getContactsReference() != null && (bitmap = ContactReader.readContactPhoto(this.mContentResolver, event.getContactsReference())) != null) {
            LayoutData layoutData2 = new LayoutData();
            layoutData2.setData(bitmap);
            layoutData2.setTargetSize(this.mNotificationContactIconSize, this.mNotificationContactIconSize, LayoutData.ScaleOption.SCALE_TO_EXACT_SIZE);
            syncEvent.setProfileImageCid(insertImage(resProvider, layoutData2));
            addResourceLayoutData(dataMap, 5, syncEvent.getProfileImageCid());
        }
    }

    protected MenuControl getOnlineSourceMenu(CostanzaResourceProvider resProvider, NotificationSource source) {
        return getSourceMenu(resProvider, source, false);
    }

    protected MenuControl getOfflineSourceMenu(CostanzaResourceProvider resProvider, NotificationSource source) {
        return getSourceMenu(resProvider, source, true);
    }

    private MenuControl getSourceMenu(CostanzaResourceProvider resProvider, NotificationSource source, boolean offline) {
        MenuControl menuControl = offline ? this.mOfflineMenuCache.get(Long.valueOf(source.getSmartConnectSourceId())) : this.mOnlineMenuCache.get(Long.valueOf(source.getSmartConnectSourceId()));
        if (menuControl == null) {
            MenuItem[] menuItems = generateMenuItemsFromSource(source);
            if (menuItems.length > 0) {
                ExtensionLayoutInflater inflater = new ExtensionLayoutInflater(source.getPackageName(), this.mContext);
                MenuFactory menuFactory = new MenuFactory();
                ExtensionMenuLayout menuView = menuFactory.create(this.mContext, inflater, menuItems, offline);
                ControlFactory controlFactory = new ControlFactory(resProvider, this.mContext);
                menuControl = controlFactory.createMenuControl(menuView, offline, source.getCid());
                generateAndSendResources(resProvider, menuControl);
                if (offline) {
                    this.mOfflineMenuCache.put(Long.valueOf(source.getSmartConnectSourceId()), menuControl);
                } else {
                    this.mOnlineMenuCache.put(Long.valueOf(source.getSmartConnectSourceId()), menuControl);
                }
            } else {
                return null;
            }
        }
        return menuControl;
    }

    private void generateAndSendResources(CostanzaResourceProvider resProvider, MenuControl menuControl) {
        List<CostanzaResource> newResources = new LinkedList<>();
        List<ResourceSubCid> subScreens = new LinkedList<>();
        List<Integer> cids = new LinkedList<>();
        menuControl.generateResources(resProvider, newResources, cids, subScreens);
        sendMessages((CostanzaMessage[]) newResources.toArray(new CostanzaResource[newResources.size()]));
    }

    private MenuItem[] generateMenuItemsFromSource(NotificationSource source) {
        ArrayList<MenuItem> menuItemList = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            String itemText = source.getActionText(i);
            String iconUri = source.getActionIconUri(i);
            MenuItem menuItem = MenuItem.Factory.create(i, iconUri, itemText);
            if (menuItem != null) {
                menuItemList.add(menuItem);
            }
            if (Dbg.v()) {
                Dbg.v("Checked source action %d. text=%s, icon=%s.", Integer.valueOf(i), itemText, iconUri);
            }
        }
        return (MenuItem[]) menuItemList.toArray(new MenuItem[menuItemList.size()]);
    }

    @Override // com.sonymobile.smartconnect.hostapp.extensions.ExtensionManager.ExtensionsChangeListener
    public void onSyncFinished() {
    }

    @Override // com.sonymobile.smartconnect.hostapp.costanza.db.SyncManager
    protected void postRunnable(Runnable runnable) {
        this.mHandler.post(runnable);
    }

    private int insertShifter(CostanzaResourceProvider resProvider, int eventCid, int imageCid, int x, int y) {
        List<CostanzaResource> newResources = new LinkedList<>();
        int shifterCid = resProvider.getShifter(newResources, eventCid, imageCid, x, y);
        sendMessagesWithMsgId((CostanzaMessage[]) newResources.toArray(new CostanzaMessage[0]));
        return shifterCid;
    }

    private int insertShifter(CostanzaResourceProvider resProvider, int imageCid, int x, int y) {
        return insertShifter(resProvider, -1, imageCid, x, y);
    }

    private int insertImage(CostanzaResourceProvider resProvider, LayoutData layoutData) {
        List<CostanzaResource> newResources = new LinkedList<>();
        int imageCid = resProvider.getImage(newResources, layoutData, ImageResourceProvider.TargetColorMode.Color16Bit);
        sendMessagesWithMsgId((CostanzaMessage[]) newResources.toArray(new CostanzaMessage[0]));
        return imageCid;
    }

    private int insertString(CostanzaResourceProvider resProvider, String text, int width, int height, Rect masterBox, int textSize, int gravity, int textColor, int backgroundColor, boolean useMaster) {
        List<CostanzaResource> newResources = new LinkedList<>();
        int textCid = resProvider.getText(newResources, text, width, height, masterBox, textSize, gravity, textColor, backgroundColor, useMaster);
        sendMessagesWithMsgId((CostanzaMessage[]) newResources.toArray(new CostanzaMessage[0]));
        return textCid;
    }

    private int insertImage(CostanzaResourceProvider resProvider, String imageUri, int width, int height, ImageResourceProvider.ImageFilter imageConversion, boolean useEmptyOnError) {
        List<CostanzaResource> newResources = new LinkedList<>();
        int imageCid = resProvider.getImage(newResources, imageUri, width, height, imageConversion, useEmptyOnError, ImageResourceProvider.TargetColorMode.Color16Bit);
        sendMessagesWithMsgId((CostanzaMessage[]) newResources.toArray(new CostanzaMessage[0]));
        return imageCid;
    }

    private int insertThreeBitImage(CostanzaResourceProvider resProvider, String imageUri, int width, int height) {
        List<CostanzaResource> newResources = new LinkedList<>();
        int imageCid = resProvider.getImage(newResources, imageUri, width, height, ImageResourceProvider.ImageFilter.NONE, false, ImageResourceProvider.TargetColorMode.Grayscale3Bit);
        sendMessagesWithMsgId((CostanzaMessage[]) newResources.toArray(new CostanzaMessage[0]));
        return imageCid;
    }

    private int insertString(CostanzaResourceProvider resProvider, String text) {
        List<CostanzaResource> newResources = new LinkedList<>();
        int textCid = resProvider.getText(newResources, text);
        sendMessagesWithMsgId((CostanzaMessage[]) newResources.toArray(new CostanzaMessage[0]));
        return textCid;
    }

    private int insertTitleImage(CostanzaResourceProvider resourceProvider, String text, int color, boolean bold, Event event) {
        if (color == -1) {
            return insertTextImage(resourceProvider, text, NOTIFICATION_MESSAGE_MAX_LENGTH, 20, 16, color, bold, 0, 16, NOTIFICATION_MESSAGE_MAX_LENGTH);
        }
        if (event.getProfileImageUri() != null || (event.getContactsReference() != null && ContactReader.readContactPhoto(this.mContentResolver, event.getContactsReference()) != null)) {
            return insertTextImage(resourceProvider, text, 168, 20, 16, color, bold, 0, 16, 168);
        }
        return insertTextImage(resourceProvider, text, 208, 20, 16, color, bold, 0, 16, 208);
    }

    private int insertDisplayNameImage(CostanzaResourceProvider resourceProvider, String text, int color, boolean bold, Event event) {
        if (color == -1) {
            return insertTextImage(resourceProvider, text, Types.TYPE_WALLPAPER_IND, 25, 15, color, bold, 3, 20, Types.TYPE_WALLPAPER_IND);
        }
        if (event.getProfileImageUri() != null || (event.getContactsReference() != null && ContactReader.readContactPhoto(this.mContentResolver, event.getContactsReference()) != null)) {
            return insertTextImage(resourceProvider, text, Types.TYPE_SHUTDOWN_IND, 25, 21, color, bold, 0, 9, Types.TYPE_SHUTDOWN_IND);
        }
        return insertTextImage(resourceProvider, text, 158, 25, 21, color, bold, 0, 9, 158);
    }

    private int insertDisplayNameDrawerImage(CostanzaResourceProvider resourceProvider, String text, int color, boolean bold) {
        return insertTextImage(resourceProvider, text, Types.TYPE_NOTIFICATION_IND, 25, 21, -1, true, 0, 9, Types.TYPE_NOTIFICATION_IND);
    }

    private int insertMessageImage(CostanzaResourceProvider resourceProvider, String text, int color, boolean bold) {
        return color == -1 ? insertTextImage(resourceProvider, text, NOTIFICATION_MESSAGE_MAX_LENGTH, 20, 16, color, bold, 0, 16, NOTIFICATION_MESSAGE_MAX_LENGTH) : insertTextImage(resourceProvider, text, 168, 20, 16, color, bold, 0, 16, 168);
    }

    private int insertTextImage(CostanzaResourceProvider resProvider, String text, int w, int h, int textSize, int color, boolean bold, int offsetY, int charsPerLine, int rtlOffset) {
        if (text == null) {
            return -1;
        }
        List<CostanzaResource> newResources = new LinkedList<>();
        int x = 0;
        int y = textSize + offsetY;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(NOTIFICATION_MESSAGE_MAX_LENGTH);
        Canvas c = new Canvas(bitmap);
        TextPaint textPaint = new TextPaint();
        textPaint.setColor(color);
        textPaint.setTextSize(textSize);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setAntiAlias(true);
        boolean rTL = isRTL(text);
        if (rTL) {
            x = rtlOffset;
            textPaint.setTextAlign(Paint.Align.RIGHT);
        }
        if (charsPerLine != 0) {
            text = cutString(text, charsPerLine, w, textPaint);
        }
        if (bold) {
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        }
        c.drawText(text, x, y, textPaint);
        int textLineImage = resProvider.getTextLineImage(newResources, bitmap, ImageResourceProvider.TargetColorMode.Color16Bit);
        sendMessagesWithMsgId((CostanzaMessage[]) newResources.toArray(new CostanzaMessage[0]));
        return textLineImage;
    }

    private int insertTextImageCanvas(CostanzaResourceProvider resProvider, String text, int w, int h, int textSize, int color, boolean smileys, boolean isMessageRTL) {
        int x;
        if (text == null) {
            return -1;
        }
        List<CostanzaResource> newResources = new LinkedList<>();
        String mText = text;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(NOTIFICATION_MESSAGE_MAX_LENGTH);
        Canvas c = new Canvas(bitmap);
        TextPaint textPaint = new TextPaint();
        textPaint.setColor(color);
        textPaint.setTextSize(textSize);
        textPaint.setAntiAlias(true);
        if (isMessageRTL) {
            mText = "\u200f" + mText;
            if (!smileys) {
                x = 215;
                textPaint.setTextAlign(Paint.Align.RIGHT);
            } else {
                x = w / 2;
                textPaint.setTextAlign(Paint.Align.CENTER);
            }
        } else if (!smileys) {
            textPaint.setTextAlign(Paint.Align.LEFT);
            x = 6;
        } else {
            x = w / 2;
            textPaint.setTextAlign(Paint.Align.CENTER);
        }
        c.drawText(mText, x, textSize, textPaint);
        int textLineImage = resProvider.getTextLineImage(newResources, bitmap, ImageResourceProvider.TargetColorMode.Color16Bit);
        sendMessagesWithMsgId((CostanzaMessage[]) newResources.toArray(new CostanzaMessage[0]));
        return textLineImage;
    }

    private float getTextSizeFromDisplayMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) this.mContext.getSystemService("window");
        windowManager.getDefaultDisplay().getMetrics(metrics);
        switch (metrics.densityDpi) {
            case Types.TYPE_SHUTDOWN_IND /* 120 */:
                if (Dbg.d()) {
                    Dbg.d("getTextSizeFromDisplayMetrics():  DENSITY_LOW");
                }
                return this.mContext.getResources().getDimensionPixelSize(R.dimen.bitmap_rendering_rich_notification_size_low);
            case NOTIFICATION_MESSAGE_MAX_LENGTH /* 160 */:
                if (Dbg.d()) {
                    Dbg.d("getTextSizeFromDisplayMetrics():  DENSITY_MEDIUM");
                }
                return this.mContext.getResources().getDimensionPixelSize(R.dimen.bitmap_rendering_rich_notification_size_medium);
            case 240:
                if (Dbg.d()) {
                    Dbg.d("getTextSizeFromDisplayMetrics():  DENSITY_HIGH");
                }
                return this.mContext.getResources().getDimensionPixelSize(R.dimen.bitmap_rendering_rich_notification_size_high);
            case 320:
                if (Dbg.d()) {
                    Dbg.d("getTextSizeFromDisplayMetrics():  DENSITY_XHIGH");
                }
                return this.mContext.getResources().getDimensionPixelSize(R.dimen.bitmap_rendering_rich_notification_size_xhigh);
            case 480:
                if (Dbg.d()) {
                    Dbg.d("getTextSizeFromDisplayMetrics():  DENSITY_XXHIGH");
                }
                return this.mContext.getResources().getDimensionPixelSize(R.dimen.bitmap_rendering_rich_notification_size_xxhigh);
            default:
                return -1.0f;
        }
    }

    private int insertRichNotification(CostanzaResourceProvider resProvider, String text, int width, int height, int color) {
        int lineCount;
        String line;
        int lastXPos;
        String textPart;
        int textPartWidth;
        if (text == null) {
            return -1;
        }
        List<CostanzaResource> newResources = new LinkedList<>();
        float textSize = getTextSizeFromDisplayMetrics();
        int y = this.mSmileySize - (((int) textSize) / 2);
        int boxMargin = this.mContext.getResources().getDimensionPixelOffset(R.dimen.event_screen_margin);
        boolean isTextRtl = isRTL(text);
        Bitmap bitmap = Bitmap.createBitmap(this.mCostanzaWidth - boxMargin, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        TextPaint textPaint = new TextPaint();
        textPaint.setColor(color);
        textPaint.setTextSize(textSize);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setAntiAlias(true);
        if (isTextRtl) {
            textPaint.setTextAlign(Paint.Align.RIGHT);
        }
        LayoutInflater inflater = (LayoutInflater) this.mContext.getSystemService("layout_inflater");
        TextView tv = (TextView) inflater.inflate(R.layout.notification_overview_message, (ViewGroup) null);
        String text2 = addNewLineBeforeHyperlink(text);
        Spannable spannable = Smileyfier.embedSmileys(this.mContext, text2);
        if (spannable != null) {
            tv.setText(spannable);
        } else {
            tv.setText(text2);
        }
        int widthMS = View.MeasureSpec.makeMeasureSpec(width, Integer.MIN_VALUE);
        int heightMS = View.MeasureSpec.makeMeasureSpec(height, Integer.MIN_VALUE);
        tv.measure(widthMS, heightMS);
        if (tv.getLineCount() > 4) {
            lineCount = 4;
        } else {
            lineCount = tv.getLineCount();
        }
        CharSequence ctext = tv.getText();
        int textLength = ctext.length();
        boolean nextLine = false;
        int oldLastXPos = 0;
        int oldSmileyY = 0;
        for (int i = 0; i < lineCount; i++) {
            int lineStart = tv.getLayout().getLineStart(i);
            int lineEnd = tv.getLayout().getLineEnd(i);
            if (textLength < lineStart || textLength < lineEnd || lineStart >= lineEnd || lineStart < 0 || lineEnd < 0) {
                line = "";
            } else {
                line = ctext.subSequence(lineStart, lineEnd).toString();
            }
            new ArrayList();
            List<Smiley> smileys = Smileyfier.getSmileyImages(line);
            int nextCharacterPos = 0;
            int smileyY = this.mSmileySize * i;
            if (isTextRtl) {
                if (!nextLine) {
                    lastXPos = this.mCostanzaWidth - boxMargin;
                } else {
                    nextLine = false;
                    lastXPos = oldLastXPos;
                    smileyY = oldSmileyY;
                }
            } else if (!nextLine) {
                lastXPos = 0;
            } else {
                nextLine = false;
                lastXPos = oldLastXPos;
                smileyY = oldSmileyY;
            }
            if (smileys.isEmpty()) {
                c.drawText(line, lastXPos, y, textPaint);
            } else {
                for (Smiley smiley : smileys) {
                    int smileyResId = smiley.resourceId;
                    int nextSmileyPos = smiley.position;
                    int smileyLength = smiley.characterLength;
                    if (nextSmileyPos != nextCharacterPos) {
                        String textPart2 = line.substring(nextCharacterPos, nextSmileyPos);
                        int textPartWidth2 = getTextWidth(tv, textPart2, isTextRtl);
                        if (textPartWidth2 != 0) {
                            Rect textBox = new Rect(0, 0, textPartWidth2 - 3, this.mSmileySize - 1);
                            if (isTextRtl && lastXPos - textPartWidth2 < 0) {
                                nextLine = true;
                                lastXPos = this.mCostanzaWidth - boxMargin;
                                y = (this.mSmileySize * (i + 2)) - (((int) textSize) / 2);
                                smileyY += this.mSmileySize;
                            } else if (!isTextRtl && lastXPos + textPartWidth2 > this.mCostanzaWidth) {
                                nextLine = true;
                                lastXPos = 0;
                                y = (this.mSmileySize * (i + 2)) - (((int) textSize) / 2);
                                smileyY += this.mSmileySize;
                            }
                            c.drawText(textPart2, lastXPos, y, textPaint);
                            if (!isTextRtl) {
                                lastXPos += textBox.right - 1;
                                if (lastXPos > this.mCostanzaWidth || this.mSmileySize + lastXPos > this.mCostanzaWidth) {
                                    nextLine = true;
                                    lastXPos = 0;
                                    y = (this.mSmileySize * (i + 2)) - (((int) textSize) / 2);
                                    smileyY += this.mSmileySize;
                                }
                            } else {
                                lastXPos -= this.mSmileySize + textPartWidth2;
                                if (lastXPos < 0) {
                                    nextLine = true;
                                    lastXPos = this.mCostanzaWidth - this.mSmileySize;
                                    y = (this.mSmileySize * (i + 2)) - (((int) textSize) / 2);
                                    smileyY += this.mSmileySize;
                                }
                            }
                        }
                    } else if (isTextRtl) {
                        lastXPos -= this.mSmileySize;
                        if (lastXPos < 0) {
                            nextLine = true;
                            lastXPos = this.mCostanzaWidth - this.mSmileySize;
                            y = (this.mSmileySize * (i + 2)) - (((int) textSize) / 2);
                            smileyY += this.mSmileySize;
                        }
                    } else if (this.mSmileySize + lastXPos > this.mCostanzaWidth) {
                        nextLine = true;
                        lastXPos = 0;
                        y = (this.mSmileySize * (i + 2)) - (((int) textSize) / 2);
                        smileyY += this.mSmileySize;
                    }
                    Bitmap s = BitmapFactory.decodeResource(this.mContext.getResources(), smileyResId);
                    Matrix m = new Matrix();
                    m.postTranslate(lastXPos, smileyY);
                    c.drawBitmap(s, m, null);
                    nextCharacterPos = nextSmileyPos + smileyLength;
                    if (!isTextRtl) {
                        if (this.mSmileySize + lastXPos > this.mCostanzaWidth) {
                            nextLine = true;
                            lastXPos = 0;
                            y = (this.mSmileySize * (i + 2)) - (((int) textSize) / 2);
                            smileyY += this.mSmileySize;
                        }
                        lastXPos += this.mSmileySize;
                    }
                }
                if (nextCharacterPos < line.length() && (textPartWidth = getTextWidth(tv, (textPart = line.substring(nextCharacterPos)), isTextRtl)) != 0) {
                    if (isTextRtl && lastXPos - textPartWidth < 0 && lastXPos < this.mCostanzaWidth) {
                        nextLine = true;
                        lastXPos = this.mCostanzaWidth - boxMargin;
                        y = (this.mSmileySize * (i + 2)) - (((int) textSize) / 2);
                        smileyY += this.mSmileySize;
                    } else if (!isTextRtl && lastXPos + textPartWidth > this.mCostanzaWidth && lastXPos > 0) {
                        nextLine = true;
                        lastXPos = 0;
                        y = (this.mSmileySize * (i + 2)) - (((int) textSize) / 2);
                        smileyY += this.mSmileySize;
                    }
                    c.drawText(textPart, lastXPos, y, textPaint);
                    if (isTextRtl && nextLine && lastXPos == this.mCostanzaWidth) {
                        lastXPos -= textPartWidth;
                    } else if (!isTextRtl && nextLine) {
                        lastXPos += textPartWidth;
                    }
                }
            }
            if (!nextLine) {
                y = (this.mSmileySize * (i + 2)) - (((int) textSize) / 2);
            } else {
                oldLastXPos = lastXPos;
                oldSmileyY = smileyY;
            }
        }
        int textLineImage = resProvider.getTextLineImage(newResources, bitmap, ImageResourceProvider.TargetColorMode.Color16Bit);
        sendMessagesWithMsgId((CostanzaMessage[]) newResources.toArray(new CostanzaMessage[0]));
        return textLineImage;
    }

    protected void sendMessagesWithMsgId(CostanzaMessage... messages) {
        for (CostanzaMessage msg : messages) {
            msg.setMessageId(getNewMessageId());
        }
        this.mCostanzaMessageSender.sendPrioritized(messages);
    }
}
