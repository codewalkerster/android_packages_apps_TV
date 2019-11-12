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
 * limitations under the License
 */

package com.android.tv.dvr.recorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.os.PowerManager;
import android.os.SystemClock;

import com.android.tv.Starter;
import com.android.tv.TvSingletons;

import java.lang.reflect.Method;

/** Signals the DVR to start recording shows <i>soon</i>. */
@RequiresApi(Build.VERSION_CODES.N)
public class DvrStartRecordingReceiver extends BroadcastReceiver {
    private static final String TAG = "DvrStartRecordingReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        checkSystemWakeUp(context);
        Starter.start(context);
        RecordingScheduler scheduler = TvSingletons.getSingletons(context).getRecordingScheduler();
        if (scheduler != null) {
            scheduler.updateAndStartServiceIfNeeded();
        }
    }

    private void checkSystemWakeUp(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean isScreenOpen = powerManager.isScreenOn();
        Log.d(TAG, "checkSystemWakeUp isScreenOpen = " + isScreenOpen);
        //Resume if the system is suspending
        if (!isScreenOpen) {
            Log.d(TAG, "checkSystemWakeUp wakeUp the android.");
            long time = SystemClock.uptimeMillis();
            wakeUp(powerManager, time);
        }
    }

    private void wakeUp(PowerManager powerManager, long time) {
         try {
             Class<?> cls = Class.forName("android.os.PowerManager");
             Method method = cls.getMethod("wakeUp", long.class);
             method.invoke(powerManager, time);
         } catch(Exception e) {
             e.printStackTrace();
             Log.d(TAG, "wakeUp Exception = " + e.getMessage());
         }
    }
}
