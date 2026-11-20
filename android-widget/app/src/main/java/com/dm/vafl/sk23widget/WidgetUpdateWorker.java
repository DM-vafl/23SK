package com.dm.vafl.sk23widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WidgetUpdateWorker extends Worker {
    private static final String DATA_URL = "https://dm-vafl.github.io/23SK/data.json";

    public WidgetUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            JSONObject data = fetchData();
            updateWidgets(data, null);
            return Result.success();
        } catch (Exception e) {
            updateWidgets(null, e.getMessage());
            return Result.retry();
        }
    }

    private JSONObject fetchData() throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(DATA_URL + "?widget=" + System.currentTimeMillis());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Cache-Control", "no-cache");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code);
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            return new JSONObject(body.toString());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void updateWidgets(JSONObject data, String error) {
        Context context = getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
        String date = data == null ? "" : data.optString("date_label", "");
        String updated = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
            views.setTextViewText(R.id.widget_date, date.isEmpty() ? "Расписание группы 23СК" : date);
            views.setTextViewText(R.id.widget_updated, "↻ " + updated);

            if (data == null) {
                views.setTextViewText(R.id.widget_pair, "НЕТ СВЯЗИ");
                views.setTextViewText(R.id.widget_from, "Не удалось загрузить данные");
                views.setTextViewText(R.id.widget_to, error == null ? "Нажми ↻ для повтора" : "Нажми ↻ для повтора");
                views.setTextViewText(R.id.widget_room, "");
            } else if ("has_changes".equals(data.optString("status"))) {
                JSONArray lines = data.optJSONArray("lines");
                if (lines != null && lines.length() > 0) {
                    Change change = parse(lines.optString(0, ""));
                    if (change != null) {
                        int count = data.optInt("count", lines.length());
                        views.setTextViewText(R.id.widget_pair, change.pair + " ПАРА"
                                + (count > 1 ? "  ·  +" + (count - 1) : ""));
                        views.setTextViewText(R.id.widget_from, change.from);
                        views.setTextViewText(R.id.widget_to, "↓ " + change.to);
                        views.setTextViewText(R.id.widget_room,
                                change.room.isEmpty() ? "Есть замена" : "Кабинет " + change.room);
                    } else {
                        views.setTextViewText(R.id.widget_pair, "ЕСТЬ ЗАМЕНЫ");
                        views.setTextViewText(R.id.widget_from, lines.optString(0, ""));
                        views.setTextViewText(R.id.widget_to, "");
                        views.setTextViewText(R.id.widget_room, "");
                    }
                } else {
                    showNoChanges(views);
                }
            } else {
                showNoChanges(views);
            }
            WidgetProvider.attachClicks(context, views);
            manager.updateAppWidget(id, views);
        }
    }

    private void showNoChanges(RemoteViews views) {
        views.setTextViewText(R.id.widget_pair, "РАСПИСАНИЕ");
        views.setTextViewText(R.id.widget_from, "Новых замен нет");
        views.setTextViewText(R.id.widget_to, "Всё по плану ✨");
        views.setTextViewText(R.id.widget_room, "");
    }

    private Change parse(String line) {
        String[] parts = line.split("\\|");
        if (parts.length < 5) return null;
        String roman = parts[0].trim().toUpperCase(Locale.ROOT);
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII"};
        String pair = roman;
        for (int i = 0; i < numerals.length; i++) {
            if (numerals[i].equals(roman)) {
                pair = String.valueOf(i + 1);
                break;
            }
        }
        String from = subject(parts[2]);
        String to = subject(parts[3]);
        StringBuilder room = new StringBuilder();
        for (int i = 4; i < parts.length; i++) {
            if (room.length() > 0) room.append(" | ");
            room.append(parts[i].trim());
        }
        if (from.isEmpty() || to.isEmpty()) return null;
        return new Change(pair, from, to, room.toString());
    }

    private String subject(String value) {
        Matcher matcher = Pattern.compile("\\(([^()]*)\\)\\s*$").matcher(value.trim());
        if (matcher.find()) return matcher.group(1).trim();
        return value.replaceFirst("^.*/\\s*", "").trim();
    }

    private static final class Change {
        final String pair;
        final String from;
        final String to;
        final String room;

        Change(String pair, String from, String to, String room) {
            this.pair = pair;
            this.from = from;
            this.to = to;
            this.room = room;
        }
    }
}
