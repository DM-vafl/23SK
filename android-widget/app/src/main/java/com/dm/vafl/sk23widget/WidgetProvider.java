package com.dm.vafl.sk23widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

public class WidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.dm.vafl.sk23widget.ACTION_REFRESH";
    private static final String SITE_URL = "https://dm-vafl.github.io/23SK/";

    @Override
    public void onEnabled(Context context) {
        WidgetRefresh.schedulePeriodic(context);
        WidgetRefresh.enqueueNow(context);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        WidgetRefresh.schedulePeriodic(context);
        WidgetRefresh.enqueueNow(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            WidgetRefresh.enqueueNow(context);
        }
    }

    @Override
    public void onDisabled(Context context) {
        WidgetRefresh.cancelPeriodic(context);
    }

    static void attachClicks(Context context, RemoteViews views) {
        Intent refreshIntent = new Intent(context, WidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        PendingIntent refreshPending = PendingIntent.getBroadcast(
                context, 201, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending);

        Intent siteIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(SITE_URL));
        PendingIntent sitePending = PendingIntent.getActivity(
                context, 202, siteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, sitePending);
    }

    static void showLoading(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
            views.setTextViewText(R.id.widget_pair, "ОБНОВЛЕНИЕ");
            views.setTextViewText(R.id.widget_from, "Получаем расписание…");
            views.setTextViewText(R.id.widget_to, "");
            views.setTextViewText(R.id.widget_room, "");
            views.setTextViewText(R.id.widget_updated, "");
            attachClicks(context, views);
            manager.updateAppWidget(id, views);
        }
    }
}
