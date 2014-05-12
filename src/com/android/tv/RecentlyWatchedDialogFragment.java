/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.TvContract;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

/**
 * Displays the watch history
 */
public class RecentlyWatchedDialogFragment extends DialogFragment implements
        LoaderManager.LoaderCallbacks<Cursor> {
    public static final String DIALOG_TAG = RecentlyWatchedDialogFragment.class.getName();

    private SimpleCursorAdapter mAdapter;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        getLoaderManager().initLoader(0, null, this);

        String[] from = {
                TvContract.WatchedPrograms._ID,
                TvContract.WatchedPrograms.CHANNEL_ID,
                TvContract.WatchedPrograms.WATCH_START_TIME_UTC_MILLIS,
                TvContract.WatchedPrograms.TITLE};
        int[] to = {
                R.id.watched_program_id,
                R.id.watched_program_channel_id,
                R.id.watched_program_watch_time,
                R.id.watched_program_title};
        mAdapter = new SimpleCursorAdapter(getActivity(), R.layout.list_item_watched_program, null,
                from, to, 0);
        mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                String name = cursor.getColumnName(columnIndex);
                if (TvContract.WatchedPrograms.CHANNEL_ID.equals(name)) {
                    long channleId = cursor.getLong(columnIndex);
                    ((TextView) view).setText(String.valueOf(channleId));
                    return true;
                } else if (TvContract.WatchedPrograms.WATCH_START_TIME_UTC_MILLIS.equals(name)) {
                    long time = cursor.getLong(columnIndex);
                    CharSequence timeString = DateUtils.getRelativeTimeSpanString(time,
                            System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS);
                    ((TextView) view).setText(String.valueOf(timeString));
                    return true;
                }
                return false;
            }
        });

        ListView listView = new ListView(getActivity());
        listView.setAdapter(mAdapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        return builder.setTitle(R.string.recently_watched).setView(listView).create();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // If we have an adapter we should close its cursor, which we do by assigning a null cursor.
        if (mAdapter != null) {
            mAdapter.changeCursor(null);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String[] projection = {
                TvContract.WatchedPrograms._ID,
                TvContract.WatchedPrograms.CHANNEL_ID,
                TvContract.WatchedPrograms.WATCH_START_TIME_UTC_MILLIS,
                TvContract.WatchedPrograms.TITLE};
        return new CursorLoader(getActivity(), TvContract.WatchedPrograms.CONTENT_URI, projection,
                null, null, TvContract.WatchedPrograms._ID + " DESC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        mAdapter.changeCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursor) {
        mAdapter.changeCursor(null);
    }
}
