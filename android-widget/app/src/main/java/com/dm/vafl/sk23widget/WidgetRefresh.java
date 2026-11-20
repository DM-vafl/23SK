package com.dm.vafl.sk23widget;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

final class WidgetRefresh {
    private static final String PERIODIC_NAME = "23sk_widget_periodic_refresh";
    private static final String ONCE_NAME = "23sk_widget_refresh_now";

    private WidgetRefresh() {}

    static void schedulePeriodic(Context context) {
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(WidgetUpdateWorker.class, 15, TimeUnit.MINUTES)
                        .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    static void enqueueNow(Context context) {
        OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(WidgetUpdateWorker.class).build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(ONCE_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    static void cancelPeriodic(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(PERIODIC_NAME);
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(ONCE_NAME);
    }
}
