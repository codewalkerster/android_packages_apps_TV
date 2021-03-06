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

package com.android.tv;

import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.util.ArraySet;
import android.util.Log;

import android.content.Context;

import com.android.tv.common.SoftPreconditions;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.api.Channel;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;
import com.android.tv.common.util.SystemProperties;
import com.android.tv.data.ChannelNumber;
import com.droidlogic.app.tv.DroidLogicTvUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;

import android.provider.Settings;

/**
 * It manages the current tuned channel among browsable channels. And it determines the next channel
 * by channel up/down. But, it doesn't actually tune through TvView.
 */
@MainThread
public class ChannelTuner {
    private static final String TAG = "ChannelTuner";
    private static final String BROADCAST_SKIP_ALL_CHANNEL = "android.action.skip.all.channels";

    private boolean mStarted;
    private boolean mChannelDataManagerLoaded;
    private final List<Channel> mChannels = new ArrayList<>();
    private final List<Channel> mBrowsableChannels = new ArrayList<>();
    private final List<Channel> mVideoChannels = new ArrayList<>();
    private final List<Channel> mRadioChannels = new ArrayList<>();
    private final Map<Long, Channel> mChannelMap = new HashMap<>();
    // TODO: need to check that mChannelIndexMap can be removed, once mCurrentChannelIndex
    // is changed to mCurrentChannel(Id).
    private final Map<Long, Integer> mChannelIndexMap = new HashMap<>();

    private final Handler mHandler = new Handler();
    private final ChannelDataManager mChannelDataManager;
    private final Set<Listener> mListeners = new ArraySet<>();
    @Nullable private Channel mCurrentChannel;
    private final TvInputManagerHelper mInputManager;
    @Nullable private TvInputInfo mCurrentChannelInputInfo;

    private Context mContext;

    private final ChannelDataManager.Listener mChannelDataManagerListener =
            new ChannelDataManager.Listener() {
                @Override
                public void onLoadFinished() {
                    mChannelDataManagerLoaded = true;
                    updateChannelData(mChannelDataManager.getChannelList());
                    for (Listener l : mListeners) {
                        l.onLoadFinished();
                    }
                }

                @Override
                public void onChannelListUpdated() {
                    updateChannelData(mChannelDataManager.getChannelList());
                }

                @Override
                public void onChannelBrowsableChanged() {
                    updateBrowsableChannels();
                    for (Listener l : mListeners) {
                        l.onBrowsableChannelListChanged();
                    }
                }
            };

    public ChannelTuner(ChannelDataManager channelDataManager, TvInputManagerHelper inputManager) {
        mChannelDataManager = channelDataManager;
        mInputManager = inputManager;
    }

    /** Starts ChannelTuner. It cannot be called twice before calling {@link #stop}. */
    public void start() {
        if (mStarted) {
            throw new IllegalStateException("start is called twice");
        }
        mStarted = true;
        mChannelDataManager.addListener(mChannelDataManagerListener);
        if (mChannelDataManager.isDbLoadFinished()) {
            mHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            mChannelDataManagerListener.onLoadFinished();
                        }
                    });
        }
    }

    /** Stops ChannelTuner. */
    public void stop() {
        if (!mStarted) {
            return;
        }
        mStarted = false;
        mHandler.removeCallbacksAndMessages(null);
        mChannelDataManager.removeListener(mChannelDataManagerListener);
        mCurrentChannel = null;
        mChannels.clear();
        mBrowsableChannels.clear();
        mChannelMap.clear();
        mChannelIndexMap.clear();
        mVideoChannels.clear();
        mRadioChannels.clear();
        mChannelDataManagerLoaded = false;
    }

    /** Returns true, if all the channels are loaded. */
    public boolean areAllChannelsLoaded() {
        return mChannelDataManagerLoaded;
    }

    /** Returns browsable channel lists. */
    public List<Channel> getBrowsableChannelList() {
        return Collections.unmodifiableList(mBrowsableChannels);
    }

    public List<Channel> getAllChannelList() {
        return Collections.unmodifiableList(mChannels);
    }

    /** Returns the number of browsable channels. */
    public int getBrowsableChannelCount() {
        return mBrowsableChannels.size();
    }

    /** Returns the current channel. */
    @Nullable
    public Channel getCurrentChannel() {
        return mCurrentChannel;
    }

    /**
     * Returns the current channel index.
     */
    public int getCurrentChannelIndex() {
        int currentChannelIndex = 0;
        if (mCurrentChannel != null) {
            Integer integer = mChannelIndexMap.get(mCurrentChannel.getId());
            if (integer != null) {
                currentChannelIndex = integer.intValue();
            }
        }
        return currentChannelIndex;
    }

    /**
     * Returns the channel index by given channel.
     */
    public int getChannelIndex(Channel channel) {
        return mChannelIndexMap.get(channel.getId());
    }

    /**
     * Returns the channel by index.
     */
    public Channel getChannelByIndex(int channelIndex) {
        Channel mChannel = null;
        if (channelIndex >= 0 && channelIndex < mChannels.size()) {
            mChannel = mChannels.get(channelIndex);
         }
        return mChannel;
    }

    /**
     * Returns the channel index which will be deleted by channelId.
     */
    public int getChannelIndexById(long channelId) {
        int mChannelIndex = 0;
        for (int i = 0; i < mChannels.size(); i++) {
            if (mChannels.get(i).getId() == channelId) {
                mChannelIndex = i;
                break;
            }
        }
        return mChannelIndex;
    }

    /**
     * Sets the current channel. Call this method only when setting the current channel without
     * actually tuning to it.
     *
     * @param currentChannel The new current channel to set to.
     */
    public void setCurrentChannel(Channel currentChannel) {
        mCurrentChannel = currentChannel;
    }

    /** Returns the current channel's ID. */
    public long getCurrentChannelId() {
        return mCurrentChannel != null ? mCurrentChannel.getId() : Channel.INVALID_ID;
    }

    /** Returns the current channel's URI */
    public Uri getCurrentChannelUri() {
        if (mCurrentChannel == null) {
            return null;
        }
        if (mCurrentChannel.isPassthrough()) {
            return TvContract.buildChannelUriForPassthroughInput(mCurrentChannel.getInputId());
        } else {
            return TvContract.buildChannelUri(mCurrentChannel.getId());
        }
    }

    /** Returns the current {@link TvInputInfo}. */
    @Nullable
    public TvInputInfo getCurrentInputInfo() {
        return mCurrentChannelInputInfo;
    }

    public boolean setCurrentInputInfo(TvInputInfo prepareone) {
        if (prepareone != null) {
            mCurrentChannelInputInfo = mInputManager.getTvInputInfo(prepareone.getId());
            return true;
        }
        return false;
    }

    /** Returns true, if the current channel is for a passthrough TV input. */
    public boolean isCurrentChannelPassthrough() {
        return mCurrentChannel != null && mCurrentChannel.isPassthrough();
    }

    /**
     * Moves the current channel to the next (or previous) browsable channel.
     *
     * @return true, if the channel is changed to the adjacent channel. If there is no browsable
     *     channel, it returns false.
     */
    public boolean moveToAdjacentBrowsableChannel(boolean up) {
        Channel channel = getAdjacentBrowsableChannel(up);
        if (channel == null || (mCurrentChannel != null && mCurrentChannel.equals(channel))) {
            return false;
        }
        setCurrentChannelAndNotify(mChannelMap.get(channel.getId()));
        return true;
    }

    /**
     * Returns a next browsable channel. It doesn't change the current channel unlike {@link
     * #moveToAdjacentBrowsableChannel}.
     */
    public Channel getAdjacentBrowsableChannel(boolean up) {
        if (isCurrentChannelPassthrough() || getBrowsableChannelCount() == 0) {
            return null;
        }
        int channelIndex;
        if (mCurrentChannel == null) {
            channelIndex = 0;
            Channel channel = mChannels.get(channelIndex);
            if (((!channel.isOtherChannel() && channel.isBrowsable()) || (channel.isOtherChannel() && !channel.IsHidden())) && ((MainActivity)mContext).mQuickKeyInfo.isChannelMatchAtvDtvSource(channel)) {
                return channel;
            }
        } else {
            channelIndex = mChannelIndexMap.get(mCurrentChannel.getId());
        }
        int size = mChannels.size();
        for (int i = 0; i < size; ++i) {
            int nextChannelIndex = up ? channelIndex + 1 + i : channelIndex - 1 - i + size;
            if (nextChannelIndex >= size) {
                nextChannelIndex -= size;
            }
            Channel channel = mChannels.get(nextChannelIndex);
            if (((!channel.isOtherChannel() && channel.isBrowsable()) || (channel.isOtherChannel() && !channel.IsHidden())) && ((MainActivity)mContext).mQuickKeyInfo.isChannelMatchAtvDtvSource(channel)) {
                return channel;
            }
        }
        Log.e(TAG, "This code should not be reached");
        return null;
    }

    /**
     * Finds the nearest browsable channel from a channel with {@code channelId}. If the channel
     * with {@code channelId} is browsable, the channel will be returned.
     */
    public Channel findNearestBrowsableChannel(long channelId) {
        if (getBrowsableChannelCount() == 0) {
            return null;
        }
        Channel channel = mChannelMap.get(channelId);
        if (channel == null) {
            return mBrowsableChannels.get(0);
        } else if ((!channel.isOtherChannel() && channel.isBrowsable()) || (channel.isOtherChannel() && !channel.IsHidden())) {
            return channel;
        }
        int index = mChannelIndexMap.get(channelId);
        int size = mChannels.size();
        for (int i = 1; i <= size / 2; ++i) {
            Channel upChannel = mChannels.get((index + i) % size);
            if ((!upChannel.isOtherChannel() && upChannel.isBrowsable()) || (upChannel.isOtherChannel() && !upChannel.IsHidden())) {
                return upChannel;
            }
            Channel downChannel = mChannels.get((index - i + size) % size);
            if ((!downChannel.isOtherChannel() && downChannel.isBrowsable()) || (downChannel.isOtherChannel() && !downChannel.IsHidden())) {
                return downChannel;
            }
        }
        throw new IllegalStateException(
                "This code should be unreachable in findNearestBrowsableChannel");
    }

    /**
     * Moves the current channel to {@code channel}. It can move to a non-browsable channel as well
     * as a browsable channel.
     *
     * @return true, the channel change is success. But, if the channel doesn't exist, the channel
     *     change will be failed and it will return false.
     */
    public boolean moveToChannel(Channel channel) {
        if (channel == null) {
            return false;
        }
        if (channel.isPassthrough()) {
            setCurrentChannelAndNotify(channel);
            return true;
        }
        SoftPreconditions.checkState(mChannelDataManagerLoaded, TAG, "Channel data is not loaded");
        Channel newChannel = mChannelMap.get(channel.getId());
        if (newChannel != null && ((MainActivity)mContext).mQuickKeyInfo.isChannelMatchAtvDtvSource(channel)) {
            setCurrentChannelAndNotify(newChannel);
            return true;
        }
        return false;
    }

    /** Resets the current channel to {@code null}. */
    public void resetCurrentChannel() {
        setCurrentChannelAndNotify(null);
    }

    /** Adds {@link Listener}. */
    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    /** Removes {@link Listener}. */
    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    public interface Listener {
        /** Called when all the channels are loaded. */
        void onLoadFinished();
        /** Called when the browsable channel list is changed. */
        void onBrowsableChannelListChanged();
        /** Called when the current channel is removed. */
        void onCurrentChannelUnavailable(Channel channel);
        /** Called when the current channel is changed. */
        void onChannelChanged(Channel previousChannel, Channel currentChannel);
        void onAllChannelsListChanged();
    }

    private void setCurrentChannelAndNotify(Channel channel) {
        if (mCurrentChannel == channel
                || (channel != null && channel.hasSameReadOnlyInfo(mCurrentChannel))) {
            return;
        }
        Channel previousChannel = mCurrentChannel;
        if (previousChannel != null) {
            setRecentChannelId(previousChannel);
        }
        mCurrentChannel = channel;
        if (mCurrentChannel != null) {
            mCurrentChannelInputInfo = mInputManager.getTvInputInfo(mCurrentChannel.getInputId());
        }
        for (Listener l : mListeners) {
            l.onChannelChanged(previousChannel, mCurrentChannel);
        }
    }

    private synchronized void updateChannelData(List<Channel> channels) {
        if (SystemProperties.USE_DEBUG_CHANNEL_UPDATE.getValue()) {
            Log.d(TAG, "updateChannelData channels = " + (channels != null ? channels.size() : 0));
        }
        if (mContext != null) {
            //[DroidLogic]
            //when updateChannelData,save the channel counts in Settings.
            Settings.System.putInt(mContext.getContentResolver(), DroidLogicTvUtils.ALL_CHANNELS_NUMBER, channels.size());
        }
        mChannels.clear();
        mChannels.addAll(channels);
        Collections.sort(mChannels, new TypeComparator());

        mChannelMap.clear();
        mChannelIndexMap.clear();
        mVideoChannels.clear();
        mRadioChannels.clear();
        for (int i = 0; i < mChannels.size(); ++i) {
            Channel channel = mChannels.get(i);
            long channelId = channel.getId();
            mChannelMap.put(channelId, channel);
            mChannelIndexMap.put(channelId, i);
            if (channel.isVideoChannel()) {
                mVideoChannels.add(channel);
            } else if (channel.isRadioChannel()) {
                mRadioChannels.add(channel);
            }
            if (SystemProperties.USE_DEBUG_CHANNEL_UPDATE.getValue()) {
                Log.d(TAG, "updateChannelData no." + i + "->" + channel);
            }
        }
        updateBrowsableChannels();

        if (mCurrentChannel != null && !mCurrentChannel.isPassthrough()) {
            Channel prevChannel = mCurrentChannel;
            setCurrentChannelAndNotify(mChannelMap.get(mCurrentChannel.getId()));
            if (mCurrentChannel == null) {
                for (Listener l : mListeners) {
                    l.onCurrentChannelUnavailable(prevChannel);
                }
            }
        }
        // TODO: Do not call onBrowsableChannelListChanged, when only non-browsable
        // channels are changed.
        for (Listener l : mListeners) {
            l.onAllChannelsListChanged();
            l.onBrowsableChannelListChanged();
        }
    }

    private synchronized void updateBrowsableChannels() {
        if (SystemProperties.USE_DEBUG_CHANNEL_UPDATE.getValue()) {
            Log.d(TAG, "updateBrowsableChannels");
        }
        mBrowsableChannels.clear();
        int i = 0;
        for (Channel channel : mChannels) {
            if (SystemProperties.USE_DEBUG_CHANNEL_UPDATE.getValue()) Log.d(TAG, "updateBrowsableChannels no." + (i++) + "->" + channel);
            //also delete hidden channel
            if ((!channel.isOtherChannel() && channel.isBrowsable()) || (channel.isOtherChannel() && !channel.IsHidden())) {//other source may not have permissions to write browse
                mBrowsableChannels.add(channel);
            }
        }

        if (!Utils.isCurrentDeviceIdPassthrough(mContext)) {
            if (mBrowsableChannels.size() == 0 ) {
                Intent intent = new Intent();
                intent.setAction(BROADCAST_SKIP_ALL_CHANNEL);
                mContext.sendBroadcast(intent);
            } else {
                Log.d(TAG, "mBrowsableChannels.size(): " + mBrowsableChannels.size());
            }
        }
    }

    public void setContext(Context context) {
        mContext = context;
    }

    public void setRecentChannelId(Channel channel) {
        if (mContext != null && channel != null) {
            long recentChannelIndex = Utils.getRecentWatchedChannelId(mContext);
            if (recentChannelIndex != channel.getId()) {
                Utils.setRecentWatchedChannelId(mContext, channel);
            }
        }
    }

    public Channel getChannelById(long id) {
        Channel channelbyid = null;
        if (mChannelMap != null) {
            channelbyid = mChannelMap.get(id);
        }
        return channelbyid;
    }

    public List<Channel> getVideoChannelList() {
        return mVideoChannels;
    }

    public List<Channel> getRadioChannelList() {
        return mRadioChannels;
    }

    private class TypeComparator implements Comparator<Channel> {
        public int compare(Channel object1, Channel object2) {
            boolean b1 = object1.isVideoChannel() ;
            boolean b2 = object2.isVideoChannel();
            if (b1 && !b2) {
                return -1;
            } else if (!b1 && b2) {
                return 1;
            }
            b1 = object1.isOtherChannel();
            b2 = object2.isOtherChannel();
            //add other channel before analog channel
            if (b1 && !b2) {
                if (object2.isAnalogChannel()) {
                   return -1;
                }
            } else if (!b1 && b2) {
                if (object1.isAnalogChannel()) {
                   return 1;
                }
            }
            String p1 = object1.getDisplayNumber();
            String p2 = object2.getDisplayNumber();
            return ChannelNumber.compare(p1, p2);
        }
    }
}
