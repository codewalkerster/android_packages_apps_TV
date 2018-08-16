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

package com.android.tv.droidlogic.channelui;

import android.os.Bundle;
import android.util.Log;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.ui.sidepanel.Item;
import com.android.tv.ui.sidepanel.SideFragment;
import com.android.tv.ui.sidepanel.SubMenuItem;
import com.android.tv.ui.sidepanel.ActionItem;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

public class ChannelEditFragment extends SideFragment {
    private static final String TAG = "ChannelEditFragment";
    private List<DynamicActionItem> mActionItems;
    private static final int[] ITEMNAME = {R.string.channel_edit_tv, R.string.channel_edit_radio};

    private final SideFragmentListener mSideFragmentListener = new SideFragmentListener() {
        @Override
        public void onSideFragmentViewDestroyed() {
            notifyDataSetChanged();
        }
    };

    @Override
    protected String getTitle() {
        return getString(R.string.channel_channel_edit);
    }

    @Override
    public String getTrackerLabel() {
        return TAG;
    }

    @Override
    protected List<Item> getItemList() {
        List<Item> items = new ArrayList<>();
        mActionItems = new ArrayList<>();
        ChannelSettingsManager manager = new ChannelSettingsManager(getMainActivity());
        int i;
        for (i = 0; i < ITEMNAME.length; i++) {
            mActionItems.add(new DynamicSubMenuItem(getString(ITEMNAME[i]), null, i,
                getMainActivity().getOverlayManager().getSideFragmentManager()) {
            @Override
            protected SideFragment getFragment() {
                SideFragment fragment = new ChannelModifyFragment();
                fragment.setListener(mSideFragmentListener);
                return fragment;
            }
        });
        }
        items.addAll(mActionItems);

        return items;
    }
}