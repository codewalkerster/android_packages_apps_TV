/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.dvr.ui.list;

import android.os.Bundle;
import android.support.v17.leanback.app.DetailsFragment;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.android.tv.R;
import com.android.tv.TvSingletons;
import com.android.tv.dvr.ui.list.SchedulesHeaderRowPresenter.DateHeaderRowPresenter;

/** A fragment to show the DVR history. */
public class DvrHistoryFragment extends DetailsFragment {

    private DvrHistoryRowAdapter mRowsAdapter;
    private TextView mEmptyInfoScreenView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ClassPresenterSelector presenterSelector = new ClassPresenterSelector();
        presenterSelector.addClassPresenter(
                SchedulesHeaderRow.class, new DateHeaderRowPresenter(getContext()));
        presenterSelector.addClassPresenter(
                ScheduleRow.class, new ScheduleRowPresenter(getContext()));
        mRowsAdapter = new DvrHistoryRowAdapter(getContext(), presenterSelector);
        setAdapter(mRowsAdapter);
        mRowsAdapter.start();
        TvSingletons singletons = TvSingletons.getSingletons(getContext());
        mEmptyInfoScreenView = (TextView) getActivity().findViewById(R.id.empty_info_screen);
        // TODO: handle show/hide message
    }

    /** Shows the empty message. */
    void showEmptyMessage(int messageId) {
        mEmptyInfoScreenView.setText(messageId);
        if (mEmptyInfoScreenView.getVisibility() != View.VISIBLE) {
            mEmptyInfoScreenView.setVisibility(View.VISIBLE);
        }
    }

    /** Hides the empty message. */
    void hideEmptyMessage() {
        if (mEmptyInfoScreenView.getVisibility() == View.VISIBLE) {
            mEmptyInfoScreenView.setVisibility(View.GONE);
        }
    }

    @Override
    public View onInflateTitleView(
            LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        // Workaround of b/31046014
        return null;
    }
}
