package com.dm.vafl.sk23widget;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String SITE_URL = "https://dm-vafl.github.io/23SK/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WidgetRefresh.schedulePeriodic(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(255, 247, 250));

        TextView title = new TextView(this);
        title.setText("23СК · ЗАМЕНЫ");
        title.setTextColor(Color.rgb(105, 71, 91));
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title, matchWrap());

        TextView description = new TextView(this);
        description.setText("Виджет показывает последнюю опубликованную замену из сайта группы 23СК.");
        description.setTextColor(Color.rgb(142, 112, 130));
        description.setTextSize(15);
        description.setGravity(Gravity.CENTER);
        description.setPadding(0, dp(16), 0, dp(24));
        root.addView(description, matchWrap());

        Button refresh = new Button(this);
        refresh.setText("Обновить виджет сейчас");
        refresh.setOnClickListener(v -> {
            WidgetProvider.showLoading(this);
            WidgetRefresh.enqueueNow(this);
        });
        root.addView(refresh, matchWrap());

        Button site = new Button(this);
        site.setText("Открыть сайт 23СК");
        site.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SITE_URL))));
        LinearLayout.LayoutParams siteParams = matchWrap();
        siteParams.topMargin = dp(10);
        root.addView(site, siteParams);

        TextView hint = new TextView(this);
        hint.setText("Добавление на экран: зажми пустое место на главном экране → Виджеты → «23СК — виджет». Обновление в фоне — примерно раз в 15 минут; Android может задерживать его ради экономии батареи.");
        hint.setTextColor(Color.rgb(142, 112, 130));
        hint.setTextSize(13);
        hint.setPadding(0, dp(24), 0, 0);
        root.addView(hint, matchWrap());

        setContentView(root);
        WidgetRefresh.enqueueNow(this);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
