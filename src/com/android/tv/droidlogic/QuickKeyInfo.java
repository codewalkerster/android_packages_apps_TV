/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tv.droidlogic;

import android.content.Context;
import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.widget.Toast;
import android.os.Message;
import android.util.Log;
import android.os.Messenger;
import android.os.RemoteException;
import android.media.tv.TvTrackInfo;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.media.tv.TvInputInfo;
import android.media.tv.TvContract;
import android.hardware.hdmi.HdmiDeviceInfo;
import android.media.tv.TvContract.Channels;
import android.content.UriMatcher;
import android.database.Cursor;
import android.media.tv.TvContentRating;
import android.content.ComponentName;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.os.Handler;
import android.net.Uri;

import java.util.HashMap;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.ChannelTuner;
import com.android.tv.MainActivity;
import com.android.tv.common.util.SystemProperties;
import com.android.tv.util.TvClock;
import com.android.tv.ui.sidepanel.ClosedCaptionFragment;
//import com.android.tv.data.Channel;
import com.android.tv.data.api.Channel;
import com.android.tv.data.ChannelImpl;
import com.android.tv.data.Program;
import com.android.tv.parental.ContentRatingsManager;
import com.android.tv.data.ChannelNumber;
import com.android.tv.util.Utils;
import com.android.tv.TvSingletons;
import com.android.tv.R;

import com.android.tv.droidlogic.channelui.ChannelSourceSettingFragment;

import com.droidlogic.app.tv.TvControlManager;
import com.droidlogic.app.tv.ChannelInfo;
import com.droidlogic.app.tv.TvDataBaseManager;
import com.droidlogic.app.tv.DroidLogicTvUtils;
import com.droidlogic.app.tv.TvInSignalInfo;
import com.droidlogic.app.tv.TvControlDataManager;

import com.droidlogic.app.DroidLogicKeyEvent;

public class QuickKeyInfo implements TvControlManager.RRT5SourceUpdateListener {
    private final static String TAG = "QuickKeyInfo";
    private final boolean DEBUG = false;
    private TvInputManagerHelper mTvInputManagerHelper;
    private Context mContext;
    private ChannelTuner mChannelTuner;
    private MainActivity mActivity;
    private String mBindComponentName = null;

    private TvControlManager mTvControlManager;
    protected TvDataBaseManager mTvDataBaseManager;
    private boolean mShowResolution = false;

    public static final String SEPARATOR = "/";
    public static final int ORDER = 2;
    public static final int DEVICE_LENGTH = 2;

    private static final int REQUEST_CODE_START_TV_SOURCE = 3;
    private static final int REQUEST_CODE_START_DROID_SETTINGS = 4;

    public QuickKeyInfo(MainActivity mainactivity, TvInputManagerHelper tvinputmanagerhelper, ChannelTuner channeltuner) {
        this.mChannelTuner = channeltuner;
        this.mTvInputManagerHelper = tvinputmanagerhelper;
        this.mActivity = mainactivity;
        this.mContext = (Context) mainactivity;
        //init droidlogic
        mTvControlManager = TvControlManager.getInstance();
        mTvDataBaseManager = new TvDataBaseManager(mContext);
    }

    //start droidtvsettings
    public void startDroidSettings(){
        try {
            Intent intent = new Intent();
            intent.setClassName("com.droidlogic.tv.settings", "com.droidlogic.tv.settings.MainSettings");
            intent.putExtra("from_live_tv", 1);
            intent.putExtra("current_channel_id", mChannelTuner.getCurrentChannelId());
            intent.putExtra("current_tvinputinfo_id", mChannelTuner.getCurrentInputInfo() != null ? mChannelTuner.getCurrentInputInfo().getId() : null);
            intent.putExtra("tv_current_device_id", getDeviceIdFromInfo(mChannelTuner.getCurrentInputInfo()));
            mActivity.startActivityForResult(intent, REQUEST_CODE_START_DROID_SETTINGS);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(mContext, mActivity.getString(R.string.droidsettings_not_found), Toast.LENGTH_SHORT).show();
            return;
        }
    }

    public void startDroidlogicTvSource(){
        try {
            Intent intent = new Intent();
            intent.setClassName("com.droidlogic.tv.settings", "com.droidlogic.tv.settings.TvSourceActivity");
            intent.putExtra("start_mode", 1);
            mActivity.startActivityForResult(intent, REQUEST_CODE_START_TV_SOURCE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(mContext, mActivity.getString(R.string.options_item_not_found), Toast.LENGTH_SHORT).show();
            return;
        }
    }

    public int getDeviceIdFromInfo(TvInputInfo inputinfo) {
        int id = -1;
        if (inputinfo != null) {
            //refer to function generateInputId() in TvInputInfo.java to look for how inputid was generated
            HdmiDeviceInfo hdmidevinfo = inputinfo.getHdmiDeviceInfo();
            String inputid = inputinfo.getId();
            if (hdmidevinfo != null && hdmidevinfo.isCecDevice()) {
                inputid = inputinfo.getParentId();
            }
            String[] temp = inputid.split(SEPARATOR);
            if (temp.length > ORDER) {
                id = Integer.parseInt(temp[ORDER].substring(DEVICE_LENGTH));
            }
        }
        return id;
    }

    public TvInputInfo getCurrentTvInputInfo() {
        return mChannelTuner.getCurrentInputInfo();
    }

    public ChannelTuner getChannelTuner() {
        return mChannelTuner;
    }

    public boolean isAtvSource() {
        boolean isatv = false;
        if (mChannelTuner != null) {
            final Channel channel = mChannelTuner.getCurrentChannel();
            isatv =  channel != null && channel.isAnalogChannel();
        }
        Log.d(TAG, "isAtvSource = " + isatv);
        return isatv;
    }

    public boolean isAvCurrentSourceInputType() {
        int currentsource = mTvControlManager.GetCurrentSourceInput();
        return currentsource == DroidLogicTvUtils.DEVICE_ID_AV1 || currentsource == DroidLogicTvUtils.DEVICE_ID_AV2;
    }

    public int getCurrentSourceInputType() {
        return mTvControlManager.GetCurrentSourceInput();
    }

    /********start: hand droidsetting request to lunch fragment********/
    //add flag to judge whether started by droidsetting
    private boolean othersStartTvFragment = false;
    private String currentcommand = null;
    public static final String COMMAND_FROM_TV_SOURCE =  "from_tv_source";
    public static final String COMMAND_SEARCH_CHANNEL =  "tv_search_channel";
    public static final String COMMAND_PARENT_CONTROL =  "parental_controls";
    public static final String COMMAND_PIP =  "pip";
    public static final String COMMAND_CLOSED_CAPTION =  "tv_closed_captions";
    public static final String COMMAND_MENU_TIME =  "menu_time";
    public static final String COMMAND_CHANNEL =  "channel";

    private final String COMMANDACTION = "action.startlivetv.settingui";

    private final BroadcastReceiver mUiCommandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "intent = " + intent);
            if (SystemProperties.USE_CUSTOMIZATION.getValue() && intent != null) {
                if (COMMANDACTION.equals(intent.getAction()) || COMMAND_EPG_APPOINT.equals(intent.getAction())) {
                    handleUiCommand(intent);
                } else if (COMMAND_EPG_SWITCH_CHANNEL.equals(intent.getAction())) {
                    handleEpgCommand(intent);
                }
            }
        }
    };

    public void dealOnResume() {
        mFactoryKeyCount = 0;
    }

    public void dealOnPause() {
        mFactoryKeyCount = 0;
    }

    public void registerCommandReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(COMMANDACTION);
        intentFilter.addAction(DroidLogicTvUtils.ACTION_SWITCH_CHANNEL);
        intentFilter.addAction(DroidLogicTvUtils.ACTION_PROGRAM_APPOINTED);
        mActivity.registerReceiver(mUiCommandReceiver, intentFilter);
        mTvControlManager.SetRRT5SourceUpdateListener(this);
    }

    public void unregisterCommandReceiver() {
        mActivity.unregisterReceiver(mUiCommandReceiver);
        cancelAllTimeout();
        mTvControlManager.SetRRT5SourceUpdateListener(null);
    }

    public void setStartTvFragment(boolean value) {
        othersStartTvFragment = value;
    }

    public boolean getStartTvFragment() {
        return othersStartTvFragment;
    }

    public String getCurrentCommand() {
        return currentcommand;
    }

    public void resetCurrentCommand() {
        currentcommand = null;
    }

    public boolean handleUiCommand(Intent intent) {
        if (intent == null) {
            return false;
        }
        setStartTvFragment(false);
        currentcommand = null;
        if (intent.getBooleanExtra(COMMAND_FROM_TV_SOURCE, false)) {
            currentcommand = COMMAND_FROM_TV_SOURCE;
            List<TvInputInfo> inputList = mTvInputManagerHelper.getTvInputList();
            for (TvInputInfo input : inputList) {
                if (intent.getStringExtra(TvInputInfo.EXTRA_INPUT_ID).equals(input.getId())) {
                    if (input.getType() == TvInputInfo.TYPE_TUNER) {
                        Channel currentChannel = mChannelTuner.getCurrentChannel();
                        Log.d(TAG,"onTunerInputSelected:" + currentChannel);
                        if (intent.getBooleanExtra(DroidLogicTvUtils.KEY_LIVETV_PROGRAM_APPOINTED, false)) {
                            //tune to appoint channel
                            Channel appointone = mChannelTuner.getChannelById(intent.getLongExtra(COMMAND_EPG_CHANNEL_ID, -1));
                            if (currentChannel != null) {
                                if (appointone != null && currentChannel.getId() != appointone.getId()) {
                                    Log.d(TAG,"handleUiCommand tune to appointone = " + appointone.getDisplayName());
                                    mActivity.tuneToChannel(appointone);
                                }
                            } else {
                                if (appointone != null) {
                                    Log.d(TAG,"handleUiCommand start tune appointone = " + appointone.getDisplayName());
                                    Uri channelUri = TvContract.buildChannelUri(appointone.getId());
                                    Utils.setLastWatchedChannelUri(mContext, channelUri.toString());
                                    mActivity.tuneToLastWatchedChannelForTunerInput();
                                }
                            }
                        } else if (currentChannel != null && !currentChannel.isPassthrough()) {
                            //hideOverlays();
                        } else {
                            mActivity.tuneToLastWatchedChannelForTunerInput();
                        }
                    } else if (input.isPassthroughInput()) {
                        Channel currentChannel = mChannelTuner.getCurrentChannel();
                        Log.d(TAG,"onPassthroughInputSelected:" + currentChannel);
                        String currentInputId = currentChannel == null ? null : currentChannel.getInputId();
                        if (TextUtils.equals(input.getId(), currentInputId)) {
                            //hideOverlays();
                        } else {
                            canShowResolution(false);
                            if (input.getHdmiDeviceInfo() != null && input.getHdmiDeviceInfo().isCecDevice()) {
                                mActivity.tuneToChannel(ChannelImpl.createPassthroughChannel(input.getParentId()));
                            } else {
                                mActivity.tuneToChannel(ChannelImpl.createPassthroughChannel(input.getId()));
                            }
                        }
                    }
                }
            }
            return true;
        } else if (intent.getBooleanExtra(COMMAND_CHANNEL, false)) {
            currentcommand = COMMAND_CHANNEL;
            mActivity.getOverlayManager().getSideFragmentManager().showByDroid(
                        new ChannelSourceSettingFragment(), true);
            othersStartTvFragment = true;
            Log.d(TAG, "[handleUiCommand] " + COMMAND_CHANNEL);
            return true;
        } else if (intent.getBooleanExtra(COMMAND_PIP, false)) {
            currentcommand = COMMAND_PIP;
            //mActivity.enterPictureInPictureMode();
            othersStartTvFragment = true;
            Log.d(TAG, "[handleUiCommand] " + COMMAND_PIP);
            return true;
        } else if (intent.getBooleanExtra(COMMAND_CLOSED_CAPTION, false)) {
            currentcommand = COMMAND_CLOSED_CAPTION;
            mActivity.getOverlayManager().getSideFragmentManager().showByDroid(new ClosedCaptionFragment(), true);
            othersStartTvFragment = true;
            Log.d(TAG, "[handleUiCommand] " + COMMAND_CLOSED_CAPTION);
            return true;
        }

        return false;
    }

    public TvInputInfo getTunerInput() {
        TvInputInfo tunerinputinfo = null;
        for (TvInputInfo input : mTvInputManagerHelper.getTvInputInfos(true, true)) {
            if (!input.isPassthroughInput()) {
                tunerinputinfo = input;
                break;
            }
        }
        return tunerinputinfo;
    }
    /********end: hand droidsetting request to lunch fragment********/

/********start: channelbannerview related********/
    public TvContentRating[] getContentRatingsOfCurrentProgram() {
        /*
         * Gets atv ratings from Channel, not Program.
         */
        final Channel channel = mActivity.getCurrentChannel();
        final TvInputInfo tvinputinfo = getCurrentTvInputInfo();
        if (isAtvSource()) {
            return channel == null ? null : Program.stringToContentRatings(channel.getContentRatings());
        } else if (isAvCurrentSourceInputType()) {
            return mActivity.getTvView().getCurrentTvContentRating();
        } else {
            final Program currentprogram = mActivity.getCurrentProgram();
            TvContentRating[] programratings = currentprogram == null ? null : currentprogram.getContentRatings();
            TvContentRating[] channelratings = null;
            if (channel != null) {
                String ratingString = null;
                ratingString = channel.getContentRatings();
                channelratings = Program.stringToContentRatings(ratingString);
            }
            //if program donnot have ratings, relplace it with channel ratings
            return programratings != null ? programratings : channelratings;
        }
    }

    public String getCurrentRatingsString() {
        TvContentRating[] current = getContentRatingsOfCurrentProgram();
        String result = null;
        if (current != null && current.length > 0) {
            for (TvContentRating rating : current) {
                String display = mActivity.getContentRatingsManager().getDisplayNameForRating(rating);
                if (display == null) {
                    continue;
                }
                if (result == null) {
                    result = display;
                } else {
                    result = result + "  " + display;
                }
            }
        } else {
            result = ContentRatingsManager.RATING_NO;
        }
        return result;
    }

    //channel data may not update on time, so get it drom db directly
    public Map<String, String> getLatestChannelInternalDataFromDb(Channel channel) {
        Map<String, String> latestinternaldata = new HashMap<String, String>();
        if (channel == null || (channel != null && channel.getId() < 0)) {
            return null;
        }
        String[] projection = {TvContract.Channels._ID, TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA};
        Cursor cursor = null;
        Channel latestchannel = null;
        try {
            cursor = mContext.getContentResolver().query(channel.getUri(), ChannelImpl.PROJECTION, TvContract.Channels._ID + "=?", new String[]{String.valueOf(channel.getId())}, null);
            if (cursor != null) {
                cursor.moveToNext();
                int index = cursor.getColumnIndex(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA);
                if (index >= 0)
                    latestinternaldata = ChannelImpl.jsonToMap(cursor.getString(index));
                }
        } catch (Exception e) {
            Log.e(TAG, "getLatestChannelInternalDataFromDb failed from TvProvider.", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return latestinternaldata;
    }

    public void updateChnnelInfoToDb(ChannelInfo value) {
        if (value != null) {
            mTvDataBaseManager.updateChannelInfo(value);
        }
    }

    public ChannelInfo getChannelInfoFromDbByChannel(Channel channel) {
        if (channel == null || (channel != null && channel.getId() < 0)) {
            return null;
        }
        String[] projection = {TvContract.Channels._ID, TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA};
        Cursor cursor = null;
        ChannelInfo channelinfo = null;
        try {
            cursor = mContext.getContentResolver().query(channel.getUri(), ChannelImpl.PROJECTION, TvContract.Channels._ID + "=?", new String[]{String.valueOf(channel.getId())}, null);
            if (cursor != null) {
                cursor.moveToNext();
                channelinfo = ChannelInfo.fromCommonCursor(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "getChannelInfoFromDbByChannel failed from TvProvider.", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return channelinfo;
    }

    public ChannelInfo getCurrentChannelInfo() {
        final Channel channel = mActivity.getCurrentChannel();
        final TvInputInfo tvinputinfo = getCurrentTvInputInfo();
        ChannelInfo currentchannelinfo = null;
        if (tvinputinfo != null) {
            if (tvinputinfo.isPassthroughInput()) {
                currentchannelinfo = ChannelInfo.createPassthroughChannel(tvinputinfo.getId());
            } else {
                currentchannelinfo =  getChannelInfoFromDbByChannel(mChannelTuner.getCurrentChannel());
            }
        }
        return currentchannelinfo;
    }

    public String getDtvTime(boolean isdtv) {
        String currentTime = null;
        if (isdtv) {
            TvClock tvtime = new TvClock(mContext);
            SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            sDateFormat.setTimeZone(TimeZone.getDefault());
            String[] dateAndTime = sDateFormat.format(new Date(tvtime.currentTimeMillis() + 0)).split("\\/| |:");
            currentTime = dateAndTime[0] + "." + dateAndTime[1] + "." + dateAndTime[2] + "   " + dateAndTime[3] + ":"
                  + dateAndTime[4];
            Log.d(TAG, "getDtvTime = " + currentTime);
        } else {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd   HH:mm:ss");
            formatter.setTimeZone(TimeZone.getDefault());
            Date curDate = new Date();
            currentTime = formatter.format(curDate);
            Log.d(TAG, "getAtvTime = " + currentTime);
        }
        return currentTime;
    }

    public String getResolution() {
        final String EMPTY = "";
        final String SPACE = "  ";
        TvInSignalInfo tvinsignalinfo = mTvControlManager.GetCurrentSignalInfo();
        String[] strings = tvinsignalinfo.sigFmt.toString().split("_");
        String mode = "";
        Boolean isHdmi = false;
        if (mChannelTuner.getCurrentInputInfo() != null && mChannelTuner.getCurrentInputInfo().getType() == TvInputInfo.TYPE_HDMI) {
            TvInSignalInfo.SignalFmt fmt = tvinsignalinfo.sigFmt;
            isHdmi = true;
            if (fmt == TvInSignalInfo.SignalFmt.TVIN_SIG_FMT_HDMI_1440X480I_60HZ
                    || fmt == TvInSignalInfo.SignalFmt.TVIN_SIG_FMT_HDMI_1440X480I_120HZ
                    || fmt == TvInSignalInfo.SignalFmt.TVIN_SIG_FMT_HDMI_1440X480I_240HZ
                    || fmt == TvInSignalInfo.SignalFmt.TVIN_SIG_FMT_HDMI_2880X480I_60HZ
                    || fmt == TvInSignalInfo.SignalFmt.TVIN_SIG_FMT_HDMI_2880X480I_60HZ) {
                strings[4] = "480I";
            } else if (fmt == TvInSignalInfo.SignalFmt.TVIN_SIG_FMT_HDMI_1440X576I_50HZ
                    || fmt == TvInSignalInfo.SignalFmt.TVIN_SIG_FMT_HDMI_1440X576I_100HZ
                    || fmt == TvInSignalInfo.SignalFmt.TVIN_SIG_FMT_HDMI_1440X576I_200HZ) {
                strings[4] = "576I";
            }
        }
        if (strings != null) {
            Log.d(TAG, "strings = " + Arrays.toString(strings) + ", isHdmi = " + isHdmi);
        }
        if (strings != null && strings.length > 4) {
            mode = strings[4];
        }
        if (strings != null && strings.length > 5 && isHdmi) {
            mode =  mode + "_" + tvinsignalinfo.reserved + "HZ";
            if (mTvControlManager.IsDviSignal()) {
                mode = mode + " DVI";
            }
            Log.d(TAG, "mode = " + mode);
        }
        if (mShowResolution && mode != null && !mode.trim().isEmpty()) {
            return SPACE + mode;
        } else {
            return EMPTY;
        }
    }

    public void canShowResolution(boolean status) {
        mShowResolution = status;
    }

    //atv sound mode related
    public String getAtvAudioOutmodestring(int mode) {
        String name = "";

        switch ((mode >> 8) & 0xFF) {
        case TvControlManager.AUDIO_STANDARD_BTSC:
            switch (mode & 0xFF) {
                case TvControlManager.AUDIO_OUTMODE_MONO:
                    name = mActivity.getResources().getString(R.string.channel_audio_outmode_mono);
                    break;
                case TvControlManager.AUDIO_OUTMODE_STEREO:
                    name = mActivity.getResources().getString(R.string.channel_audio_outmode_stereo);
                    break;
                case TvControlManager.AUDIO_OUTMODE_SAP:
                    name = mActivity.getResources().getString(R.string.channel_audio_outmode_sap);
                    break;
                default:
                    name = mActivity.getResources().getString(R.string.channel_audio_outmode_stereo);
                    break;
            }
            break;

        case TvControlManager.AUDIO_STANDARD_A2_K:
        case TvControlManager.AUDIO_STANDARD_A2_BG:
        case TvControlManager.AUDIO_STANDARD_A2_DK1:
        case TvControlManager.AUDIO_STANDARD_A2_DK2:
        case TvControlManager.AUDIO_STANDARD_A2_DK3:
            switch (mode & 0xFF) {
                case TvControlManager.AUDIO_OUTMODE_A2_MONO:
                    name = mActivity.getResources().getString(R.string.channel_audio_outmode_mono);
                    break;
                case TvControlManager.AUDIO_OUTMODE_A2_STEREO:
                    name = mActivity.getResources().getString(R.string.channel_audio_outmode_stereo);
                    break;
                case TvControlManager.AUDIO_OUTMODE_A2_DUAL_A:
                    name = mActivity.getResources().getString(R.string.channel_audio_outmode_dualI);
                    break;
                case TvControlManager.AUDIO_OUTMODE_A2_DUAL_B:
                    name = mActivity.getResources().getString(R.string.channel_audio_outmode_dualII);
                    break;
                case TvControlManager.AUDIO_OUTMODE_A2_DUAL_AB:
                    name = mActivity.getResources().getString(R.string.channel_audio_outmode_dualI_II);
                    break;
                default:
                    name = mActivity.getResources().getString(R.string.channel_audio_outmode_stereo);
                    break;
            }
            break;

        case TvControlManager.AUDIO_STANDARD_NICAM_I:
        case TvControlManager.AUDIO_STANDARD_NICAM_BG:
        case TvControlManager.AUDIO_STANDARD_NICAM_L:
        case TvControlManager.AUDIO_STANDARD_NICAM_DK:
            switch (mode & 0xFF) {
            case TvControlManager.AUDIO_OUTMODE_NICAM_MONO:
                name = mActivity.getResources().getString(R.string.channel_audio_outmode_mono);
                break;
            case TvControlManager.AUDIO_OUTMODE_NICAM_MONO1:
                name = mActivity.getResources().getString(R.string.channel_audio_outmode_nicam_mono);
                break;
            case TvControlManager.AUDIO_OUTMODE_NICAM_STEREO:
                name = mActivity.getResources().getString(R.string.channel_audio_outmode_stereo);
                break;
            case TvControlManager.AUDIO_OUTMODE_NICAM_DUAL_A:
                name = mActivity.getResources().getString(R.string.channel_audio_outmode_dualI);
                break;
            case TvControlManager.AUDIO_OUTMODE_NICAM_DUAL_B:
                name = mActivity.getResources().getString(R.string.channel_audio_outmode_dualII);
                break;
            case TvControlManager.AUDIO_OUTMODE_NICAM_DUAL_AB:
                name = mActivity.getResources().getString(R.string.channel_audio_outmode_dualI_II);
                break;
            default:
                name = mActivity.getResources().getString(R.string.channel_audio_outmode_stereo);
                break;
            }
            break;

        default:
            name = "";
            break;
        }

        return name;
    }

    private final int AUDIO_OUTMODE_MONO_SAP = 2;
    private final int AUDIO_OUTMODE_STEREO_SAP = 3;

    //display priority from high to low is stereo mono sap
    public String getAtvAudioStreamOutmodestring(){
        String displayinfo = "";
        int inputmode = getAtvAudioStreamOutmode();
        int outmode = getAtvAudioOutMode();

        displayinfo = getAtvAudioOutmodestring((inputmode & 0xff00) | outmode);

        return displayinfo;
    }

    //parse realtime mode to priority display string mode
    private String getAtvAudioRealTimeOutmodestring(int mode){
        switch (mode) {
            case TvControlManager.AUDIO_OUTMODE_MONO:
                return mActivity.getResources().getString(R.string.audio_outmode_mono);
            case TvControlManager.AUDIO_OUTMODE_STEREO:
                return mActivity.getResources().getString(R.string.audio_outmode_stereo);
            case AUDIO_OUTMODE_MONO_SAP:
                return mActivity.getResources().getString(R.string.audio_outmode_sap);
            case AUDIO_OUTMODE_STEREO_SAP:
                return mActivity.getResources().getString(R.string.audio_outmode_stereo);
            default:
                return mActivity.getResources().getString(R.string.audio_outmode_stereo);
        }
    }

    //parse realtime mode to priority display int mode
    private int getAtvAudioRealTimeOutmodeint(int mode){
        switch (mode) {
            case TvControlManager.AUDIO_OUTMODE_MONO:
                return TvControlManager.AUDIO_OUTMODE_MONO;
            case TvControlManager.AUDIO_OUTMODE_STEREO:
                return TvControlManager.AUDIO_OUTMODE_STEREO;
            case AUDIO_OUTMODE_MONO_SAP:
                return TvControlManager.AUDIO_OUTMODE_SAP;
            case AUDIO_OUTMODE_STEREO_SAP:
                return TvControlManager.AUDIO_OUTMODE_STEREO;
            default:
                return TvControlManager.AUDIO_OUTMODE_STEREO;
        }
    }

    //get mode according to priority if not set outputmode
    public int getAtvAudioPriorityMode(){
        int mode = getAtvAudioOutmodeFromDb();
        if (mode == -1) {
            //int realtimemode = getAtvAudioStreamOutmode();
            mode = getAtvAudioOutMode();
        }
        return mode;
    }

    //this value take effect
    public int getAtvAudioOutMode() {
        int mode = mTvControlManager.GetAudioOutmode();
        if (DEBUG) {
            Log.d(TAG, "getAtvAudioOutEffectMode"+" mode = " + mode);
        }
        return mode;
    }

    // get save value from db
    public int getAtvAudioOutmodeFromDb(){
        int outputmode = -1;
        ChannelInfo channelinfo = getCurrentChannelInfo();
        if (channelinfo != null) {
            outputmode = channelinfo.getAudioOutPutMode();//-1:not set
        }
        if (DEBUG) {
            Log.d(TAG, "getAtvAudioOutmodeFromDb outputmode = " + outputmode);
        }
        return outputmode;
    }

    //driver decode mode from realtime stream
    public int getAtvAudioStreamOutmode(){
        int mode = mTvControlManager.GetAudioStreamOutmode();
        if (DEBUG) {
            Log.d(TAG, "get Atv Audio realtime Stream mode = " + mode);
        }
        return mode;
    }

    //this value take effect
    public void setAtvAudioOutmode(int mode) {
        if (DEBUG) {
            Log.d(TAG, "setAtvAudioOutmode"+" mode = " + mode);
        }
        mTvControlManager.SetAudioOutmode(mode);
    }

    //get video and audio format
    public String getVideoFormat() {
        final ChannelInfo channelinfo = getCurrentChannelInfo();
        if (channelinfo != null && channelinfo.isDigitalChannel()) {
            return getVideoMap().get(channelinfo.getVfmt());
        } else {
            return "";
        }
    }

    public String getAudioFormat() {
        final ChannelInfo channelinfo = getCurrentChannelInfo();
        if (channelinfo != null && channelinfo.isDigitalChannel()) {
            int audiopids[] = channelinfo.getAudioPids();
            String audioTrackId = mActivity.getTvView().getSelectedTrack(TvTrackInfo.TYPE_AUDIO);
            int index = 0;
            if (audioTrackId != null && audiopids != null) {
                String[] item = audioTrackId.split("\\&");
                String[] audioTrack = item[0].split("=");
                int audioTrackPid = Integer.parseInt(audioTrack[1]);
                for (int i = 0; i < audiopids.length; i++) {
                    if (audioTrackPid == (audiopids[i])) {
                        index = i;
                        break;
                    }
                }
                int audioformats[] = channelinfo.getAudioFormats();
                String audioformatstring = null;
                if (audioformats != null) {
                    audioformatstring = getAudioMap().get(audioformats[index]);
                }
                if (TextUtils.isEmpty(audioformatstring)) {
                    return "";
                } else {
                    return audioformatstring;
                }
            } else {
                return "";
            }
        } else {
            return "";
        }
    }

    public boolean isAudioFormatAC3() {
        final ChannelInfo channelinfo = getCurrentChannelInfo();
        if (channelinfo != null && channelinfo.isDigitalChannel()) {
            final String audioformat = getAudioFormat();
            if (!TextUtils.isEmpty(audioformat) && (audioformat.equals("AC3") || audioformat.equals("EAC3"))) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public static final Map<Integer, String> getVideoMap(){
        Map<Integer, String> videoformat = new HashMap<Integer, String>();
        videoformat.put(-1, "UNKNOWN");
        videoformat.put(0, "MPEG12");
        videoformat.put(1, "MPEG4");
        videoformat.put(2, "H264");
        videoformat.put(3, "MJPEG");
        videoformat.put(4, "REAL");
        videoformat.put(5, "JPEG");
        videoformat.put(6, "VC1");
        videoformat.put(7, "AVS");
        videoformat.put(8, "SW");
        videoformat.put(9, "H264MVC");
        videoformat.put(10, "H264_4K2K");
        videoformat.put(11, "HEVC");
        videoformat.put(12, "H264_ENC");
        videoformat.put(13, "JPEG_ENC");
        videoformat.put(14, "VP9");
        return videoformat;
    }

    public static final Map<Integer, String> getAudioMap(){
        Map<Integer, String> audioformat = new HashMap<Integer, String>();
        audioformat.put(-1, "UNKNOWN");
        audioformat.put(0, "MPEG");
        audioformat.put(1, "PCM_S16LE");
        audioformat.put(2, "AAC");
        audioformat.put(3, "AC3");
        audioformat.put(4, "ALAW");
        audioformat.put(5, "MULAW");
        audioformat.put(6, "DTS");
        audioformat.put(7, "PCM_S16BE");
        audioformat.put(8, "FLAC");
        audioformat.put(9, "COOK");
        audioformat.put(10, "PCM_U8");
        audioformat.put(11, "ADPCM");
        audioformat.put(12, "AMR");
        audioformat.put(13, "RAAC");
        audioformat.put(14, "WMA");
        audioformat.put(15, "WMAPRO");
        audioformat.put(16, "PCM_BLURAY");
        audioformat.put(17, "ALAC");
        audioformat.put(18, "VORBIS");
        audioformat.put(19, "AAC_LATM");
        audioformat.put(20, "APE");
        audioformat.put(21, "EAC3");
        audioformat.put(22, "PCM_WIFIDISPLAY");
        audioformat.put(23, "DRA");
        audioformat.put(24, "SIPR");
        audioformat.put(25, "TRUEHD");
        audioformat.put(26, "MPEG1");
        audioformat.put(27, "MPEG2");
        audioformat.put(28, "WMAVOI");
        audioformat.put(29, "WMALOSSLESS");
        audioformat.put(30, "UNSUPPORT");
        audioformat.put(31, "MAX");
        return audioformat;
    }
/********start: channelbannerview related********/

/********start: start:add for search channel********/
    private boolean mStartSearch = false;
    private Channel mFirstFoundChannel = null;
    private int mFirstSearchedFrequency = 0;
    private int mFirstSearchedDtvFrequency = 0;
    private int mFirstSearchedAtvFrequency = 0;
    private int mFirstSearchedRadioFrequency = 0;
    private int mSearchedChannelNumber = 0;
    private int mSearchedDtvChannelNumber = 0;
    private int mSearchedAtvChannelNumber = 0;
    private int mSearchedRadioChannelNumber = 0;
    private int mFirstAutoSearchedFrequency = 0;

    public boolean setSearchedChannelData(Intent data) {
        if (data != null) {
            if (data.getIntExtra(DroidLogicTvUtils.DTVPROGRAM, 0) != 0) {
                mFirstSearchedDtvFrequency = data.getIntExtra(DroidLogicTvUtils.DTVPROGRAM, 0);
                mFirstSearchedFrequency = mFirstSearchedDtvFrequency;
            } else if (data.getIntExtra(DroidLogicTvUtils.ATVPROGRAM, 0) != 0) {
                mFirstSearchedAtvFrequency = data.getIntExtra(DroidLogicTvUtils.ATVPROGRAM, 0);
                mFirstSearchedFrequency = mFirstSearchedAtvFrequency;
            } else if (data.getIntExtra(DroidLogicTvUtils.RADIOPROGRAM, 0) != 0) {
                mFirstSearchedRadioFrequency = data.getIntExtra(DroidLogicTvUtils.RADIOPROGRAM, 0);
                mFirstSearchedFrequency = mFirstSearchedRadioFrequency;
            }

            mSearchedDtvChannelNumber = data.getIntExtra(DroidLogicTvUtils.DTVNUMBER, 0);
            mSearchedAtvChannelNumber = data.getIntExtra(DroidLogicTvUtils.ATVNUMBER, 0);
            mSearchedRadioChannelNumber = data.getIntExtra(DroidLogicTvUtils.RADIONUMBER, 0);
            mSearchedChannelNumber = mSearchedDtvChannelNumber + mSearchedAtvChannelNumber + mSearchedRadioChannelNumber;
            if (mSearchedRadioChannelNumber > 0 && mFirstSearchedRadioFrequency == 0) {
                mFirstSearchedRadioFrequency = mFirstSearchedDtvFrequency;//usually radio exist in dtv
            }
            if (data.getIntExtra(DroidLogicTvUtils.AUTO_SEARCH_MODE, 0) != 0) {
                mFirstAutoSearchedFrequency = data.getIntExtra(DroidLogicTvUtils.FIRSTAUTOFOUNDFREQUENCY, 0);
                mFirstSearchedRadioFrequency = mFirstAutoSearchedFrequency;
            }
            Log.d(TAG, "number searched frequency = " + mFirstSearchedFrequency + ", dtvnum = " + mSearchedDtvChannelNumber
                    + ", atvnum = " + mSearchedAtvChannelNumber + ", radionum = " + mSearchedRadioChannelNumber
                    + ", dtvfre = " + mFirstSearchedDtvFrequency + ", atvfre = " + mFirstSearchedAtvFrequency + ", radiofre = " + mFirstSearchedRadioFrequency);
            return hasSearchedChannel();
        } else {
            return false;
        }
    }

    public void setSearchedChannelFlag(boolean value) {
        mStartSearch = value;
        //reset number search parameters
        mFirstFoundChannel = null;
        mFirstSearchedFrequency = 0;
        mFirstSearchedDtvFrequency = 0;
        mFirstSearchedAtvFrequency = 0;
        mFirstSearchedRadioFrequency = 0;
        mSearchedChannelNumber = 0;
        mSearchedDtvChannelNumber = 0;
        mSearchedAtvChannelNumber = 0;
        mSearchedRadioChannelNumber = 0;
        mFirstAutoSearchedFrequency = 0;
    }

    public boolean hasSearchedChannel() {
        return mStartSearch && mFirstSearchedFrequency > 0;
    }

    public Channel getFirstSearchedChannel() {
        if (!hasSearchedChannel()) {
            return null;
        }
        ArrayList<Channel> channellist = new ArrayList<Channel>();
        channellist.addAll(mChannelTuner.getBrowsableChannelList());
        if (channellist == null || channellist.size() == 0) {
            return null;
        }
        Collections.sort(channellist, new ChannelStringComparator());
        ArrayList<Channel> newdtvchannels = new ArrayList<Channel>();
        ArrayList<Channel> newatvchannels = new ArrayList<Channel>();
        ArrayList<Channel> newradiochannels = new ArrayList<Channel>();
        Channel foundchannel = null;
        if (channellist != null && channellist.size() > 0) {
            for (Channel channel : channellist) {
                if (mFirstAutoSearchedFrequency > 0 && channel.getFrequency() == mFirstAutoSearchedFrequency) {
                    Log.d(TAG, "first auto searched channel");
                    return channel;
                } else  if (channel != null && mFirstSearchedDtvFrequency > 0 && channel.getFrequency() == mFirstSearchedDtvFrequency && !channel.isRadioChannel()) {
                    Log.d(TAG, "dtv fre = " + mFirstSearchedDtvFrequency + ", name = " + channel.getDisplayNumber());
                    newdtvchannels.add(channel);
                } else if (channel != null && mFirstSearchedAtvFrequency > 0 && channel.getFrequency() == mFirstSearchedAtvFrequency) {
                    Log.d(TAG, "atv fre = " + mFirstSearchedAtvFrequency + ", name = " + channel.getDisplayNumber());
                    newatvchannels.add(channel);
                } else if (channel != null && channel.getFrequency() == mFirstSearchedRadioFrequency && channel.isRadioChannel()) {
                    Log.d(TAG, "radio fre = " + mFirstSearchedRadioFrequency + ", name = " + channel.getDisplayNumber());
                    newradiochannels.add(channel);
                }
            }
            //ensure most channels have stored
            if (newdtvchannels.size() > 0 && (newdtvchannels.size() >= mSearchedDtvChannelNumber - 1 || mSearchedDtvChannelNumber >= newdtvchannels.size() || newdtvchannels.size() > 10)) {
                foundchannel = newdtvchannels.get(0);
                Log.d(TAG, "found dtv channel = " + foundchannel);
                return foundchannel;
            } else if (newatvchannels.size() > 0 && (newatvchannels.size() >= mSearchedAtvChannelNumber - 1 || mSearchedAtvChannelNumber >= newatvchannels.size() || newatvchannels.size() > 10)) {
                foundchannel = newatvchannels.get(0);
                Log.d(TAG, "found atv channel = " + foundchannel);
                return foundchannel;
            } else if (newradiochannels.size() > 0 && (newradiochannels.size() >= mSearchedRadioChannelNumber - 1 || mSearchedRadioChannelNumber >= newradiochannels.size() || newradiochannels.size() > 10)) {
                foundchannel = newatvchannels.get(0);
                Log.d(TAG, "found radio channel = " + foundchannel);
                return foundchannel;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public class ChannelStringComparator implements Comparator<Channel> {
        public int compare(Channel object1, Channel object2) {
            String p1 = object1.getDisplayNumber();
            String p2 = object2.getDisplayNumber();
            return ChannelNumber.compare(p1, p2);
        }
    }

    /********start: add for quick key********/
    public static final int KEYCODE_TV_SHORTCUTKEY_VIEWMODE = DroidLogicKeyEvent.KEYCODE_TV_SHORTCUTKEY_VIEWMODE;
    public static final int KEYCODE_TV_SHORTCUTKEY_VOICEMODE = DroidLogicKeyEvent.KEYCODE_TV_SHORTCUTKEY_VOICEMODE;
    public static final int KEYCODE_TV_SHORTCUTKEY_DISPAYMODE = DroidLogicKeyEvent.KEYCODE_TV_SHORTCUTKEY_DISPAYMODE;
    public static final int KEYCODE_TV_SLEEP = DroidLogicKeyEvent.KEYCODE_TV_SLEEP;
    public static final int KEYCODE_FAV = DroidLogicKeyEvent.KEYCODE_FAV;
    public static final int KEYCODE_LIST = DroidLogicKeyEvent.KEYCODE_LIST;

    public static final int SCANCODE_TV_SHORTCUTKEY_VIEWMODE = 469;
    public static final int SCANCODE_TV_SHORTCUTKEY_VOICEMODE = 470;
    public static final int SCANCODE_TV_SHORTCUTKEY_DISPAYMODE = 471;
    public static final int SCANCODE_TV_SLEEP = 219;
    public static final int SCANCODE_FAV = 237;
    public static final int SCANCODE_LIST = 238;

    public void QuickKeyAction (int eventkey) {
        String droidlivetv = "com.droidlogic.droidlivetv/com.droidlogic.droidlivetv.DroidLiveTvActivity";
        String shortcut = "com.droidlogic.droidlivetv/com.droidlogic.droidlivetv.shortcut.ShortCutActivity";
        if (eventkey == KeyEvent.KEYCODE_GUIDE) {
            droidlivetv = shortcut;
        }
        Bundle bundle = new Bundle();
        bundle.putInt("eventkey", eventkey);
        bundle.putInt("deviceid", getCurrentDeviceId());
        bundle.putLong("channelid", getCurrentChannelId());
        bundle.putString("inputid", getCurrentInputId());
        Intent intent = new Intent();
        intent.setComponent(ComponentName.unflattenFromString(droidlivetv));
        intent.putExtras(bundle);
        try {
            mContext.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(mContext, mActivity.getString(R.string.shortcutactivity_not_found), Toast.LENGTH_SHORT).show();
            return;
        }
    }

    public int getCurrentDeviceId() {
        int deviceid = -1;
        final TvInputInfo currentinfo = mChannelTuner.getCurrentInputInfo();
        if (currentinfo != null ) {
            deviceid = getDeviceIdFromInfo(currentinfo);
        }
        return deviceid;
    }

    public long getCurrentChannelId() {
        long id = -1;
        final Channel channel = mChannelTuner.getCurrentChannel();
        if (channel != null) {
            id = channel.getId();
        }
        return id;
    }

    public String getCurrentInputId() {
        String inputid = null;
        final Channel channel = mChannelTuner.getCurrentChannel();
        if (channel != null) {
            inputid = channel.getInputId();
        }
        return inputid;
    }

    public int getKeyCode(int scancode) {
        switch (scancode) {
            case SCANCODE_TV_SHORTCUTKEY_VIEWMODE:
                return KEYCODE_TV_SHORTCUTKEY_VIEWMODE;
            case SCANCODE_TV_SHORTCUTKEY_VOICEMODE:
                return KEYCODE_TV_SHORTCUTKEY_VOICEMODE;
            case SCANCODE_TV_SHORTCUTKEY_DISPAYMODE:
                return KEYCODE_TV_SHORTCUTKEY_DISPAYMODE;
            case SCANCODE_FAV:
                return KEYCODE_FAV;
            case SCANCODE_LIST:
                return KEYCODE_LIST;
            case SCANCODE_TV_SLEEP:
                return KEYCODE_TV_SLEEP;
            default:
                return -1;
        }
    }

    public void tuneToRecentChannel() {
        long recentChannelIndex = Utils.getRecentWatchedChannelId(mContext);
        long currentchannelindex = getCurrentChannelId();

        if (recentChannelIndex != -1 && recentChannelIndex != currentchannelindex) {
            final Channel recentChannel = mChannelTuner.getChannelById(recentChannelIndex);
            if (recentChannel != null) {
                mActivity.tuneToChannel(recentChannel);
            }
        }
    }
    /********end:add for quick key********/

    /********start:add set country for quick key********/

    public final String COUNTRY_CHINA = "CN";//dtmb mode

    public String getCountry() {
        String country = TvSingletons.getSingletons(mContext).getTvControlDataManager().getString(mContext.getContentResolver(), DroidLogicTvUtils.KEY_SEARCH_COUNTRY);
        if (TextUtils.isEmpty(country)) {
            country = getSupportCountry().get(0);
        }
        Log.d(TAG, "getCountry = " + country);
        return country;
    }

    public ArrayList<String> getSupportCountry() {
        String config = mTvControlManager.GetTVSupportCountries();//"US,IN,ID,MX,DE,CN";
        Log.d(TAG, "getCountry = " + config);
        String[] supportcountry = {"US", "IN", "ID", "MX", "DE", "CN"};//default
        ArrayList<String> getsupportlist = new ArrayList<String>();
        if (!TextUtils.isEmpty(config)) {
            supportcountry = config.split(",");
            for (String temp : supportcountry) {
                getsupportlist.add(temp);
            }
        } else {
            for (String temp : supportcountry) {
                getsupportlist.add(temp);
            }
        }
        return getsupportlist;
    }

    public boolean isDtmbModeCountry() {
        if (COUNTRY_CHINA.equals(getCountry())) {
            return true;
        }
        return false;
    }
    /********end:add set country for quick key********/

    /********start:add to deal epg command for quick key********/
    public static final String COMMAND_EPG_SWITCH_CHANNEL =  DroidLogicTvUtils.ACTION_SWITCH_CHANNEL;
    public static final String COMMAND_EPG_CHANNEL_ID =  DroidLogicTvUtils.EXTRA_CHANNEL_ID;
    public static final String COMMAND_EPG_APPOINT =  DroidLogicTvUtils.ACTION_LIVETV_PROGRAM_APPOINTED;
    public static final String COMMAND_EPG_PROGRAM_ID =  DroidLogicTvUtils.EXTRA_PROGRAM_ID;

    private boolean handleEpgCommand(Intent intent) {
        if (COMMAND_EPG_SWITCH_CHANNEL.equals(intent.getAction())) {
            long id = intent.getLongExtra(COMMAND_EPG_CHANNEL_ID, -1);
            if (id != -1) {
                Channel channel = mChannelTuner.getChannelById(id);
                if (channel != null && channel.getId() != mChannelTuner.getCurrentChannelId()) {
                    mActivity.tuneToChannel(channel);
                }
            }
            Log.d(TAG, "[handleEpgCommand] " + COMMAND_EPG_SWITCH_CHANNEL);
            return true;
        }
        return false;
    }
    /********end:add to deal epg command for quick key********/

    /********start:add to deal no signal suspend for quick key********/
    private boolean mSetNoSignalTimeout = false;

    public void setNoSingalTimeout() {
        Log.d(TAG, "setNoSingalTimeout");
        enableNosignalTimeout(true);
    }

    public void cancelNoSingalTimeout() {
        Log.d(TAG, "cancelNoSingalTimeout");
        if (mSetNoSignalTimeout) {
            enableNosignalTimeout(false);
        }
    }

    private void enableNosignalTimeout(boolean value) {
        Intent intent = new Intent("droidlogic.intent.action.TIMER_SUSPEND");
        intent.putExtra(DroidLogicTvUtils.KEY_ENABLE_NOSIGNAL_TIMEOUT, value);
        mContext.sendBroadcast(intent);
        mSetNoSignalTimeout = value;
    }

    private void cancelAllTimeout() {
        cancelNoSingalTimeout();
    }

    public boolean hasSetNosignalTimeout() {
        return mSetNoSignalTimeout;
    }
    /********end:add to deal no signal suspend for quick key********/

    /********start:add factory menu response for quick key********/
    private int mFactoryKeyCount = 0;

    public boolean checkFactoryMenuStatus(int keyCode) {
        if (keyCode != KeyEvent.KEYCODE_9) {
            mFactoryKeyCount = 0;
        } else {
            mFactoryKeyCount++;
        }
        if (mFactoryKeyCount == 4) {
            mFactoryKeyCount = 0;
            startFactoryMenu();
            return true;
        } else {
            return false;
        }
    }

    private void startFactoryMenu() {
        Intent factory = new Intent("droidlogic.intent.action.FactoryMainActivity");
        mContext.startActivity(factory);
    }
    /********end:add factory menu response for quick key********/

    /********start:save setting data for quick key********/
    //public static final String TABLE_SCAN_NAME_URI = TvControlDataManager.CONTENT_URI + TvControlDataManager.TABLE_SCAN_NAME;
    public static final Uri TABLE_SCAN_NAME_URI = Uri.parse(TvControlDataManager.CONTENT_URI + TvControlDataManager.TABLE_SCAN_NAME);

    public static Uri getUriForKey(Uri uri, String key) {
        if (uri == null || key == null) {
            return null;
        }
        return Uri.withAppendedPath(uri, key);
    }
    /********end:save setting data for quick key********/


    /********start: add for rrt5********/
    private static final int UPDATE_MANUAL = 1;

    public int updateRRT5XmlResource() {
        int freq;
        int module;
        int result= -1;
        TvControlManager.FEParas fe = null;
        ChannelInfo info = getCurrentChannelInfo();;
        if (mActivity.getContentRatingsManager().isRRT5UpdateFinish()) {
            if (info != null && !info.isPassthrough()) {
                Log.d(TAG, "[updateRRT5XmlResource] getFEParas" + info.getFEParas());
                fe = new TvControlManager.FEParas(info.getFEParas());
                result = mTvControlManager.updateRRTRes(fe.getFrequency(), fe.getModulation(), UPDATE_MANUAL);
            }
            Log.d(TAG,"result:"+result);
        }
        return result;
    }

    @Override
    public void onRRT5InfoUpdated(int result) {
        Log.d(TAG,"onRRT5InfoUpdated:"+result);
        mActivity.getContentRatingsManager().setRRT5updateResult(result);
    }
    /********end: add for rrt5********/
}