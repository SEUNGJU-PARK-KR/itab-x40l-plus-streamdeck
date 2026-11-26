package com.itab.deck;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class TabletActivity extends Activity {
    private static final int REQ_PICK_APK = 1001;
    private static final String APK_MIME = "application/vnd.android.package-archive";

    private PackageManager pm;
    private final List<AppEntry> apps = new ArrayList<>();
    private AppAdapter adapter;

    private static class AppEntry {
        String packageName;
        CharSequence label;
        Drawable icon;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tablet);
        pm = getPackageManager();

        findViewById(R.id.back_home).setOnClickListener(v -> finish());
        findViewById(R.id.install_apk).setOnClickListener(v -> pickApk());

        GridView gridView = findViewById(R.id.app_grid);
        adapter = new AppAdapter();
        gridView.setAdapter(adapter);
        gridView.setOnItemClickListener((parent, view, position, id) -> launch(apps.get(position)));
        gridView.setOnItemLongClickListener((parent, view, position, id) -> {
            showAppInfo(apps.get(position));
            return true;
        });

        loadApps();
    }

    private void loadApps() {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(intent, 0);
        Collator collator = Collator.getInstance(Locale.getDefault());
        for (ResolveInfo info : resolved) {
            String pkg = info.activityInfo.packageName;
            if (getPackageName().equals(pkg)) continue;
            AppEntry entry = new AppEntry();
            entry.packageName = pkg;
            entry.label = info.loadLabel(pm);
            entry.icon = info.loadIcon(pm);
            apps.add(entry);
        }
        Collections.sort(apps, (a, b) -> collator.compare(a.label.toString(), b.label.toString()));
        adapter.notifyDataSetChanged();
    }

    private void launch(AppEntry entry) {
        Intent intent = pm.getLaunchIntentForPackage(entry.packageName);
        if (intent == null) {
            Toast.makeText(this, "실행할 수 없는 앱입니다", Toast.LENGTH_SHORT).show();
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void showAppInfo(AppEntry entry) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + entry.packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "앱 정보를 열 수 없습니다", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickApk() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType(APK_MIME);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, REQ_PICK_APK);
        } catch (Exception e) {
            Toast.makeText(this, "파일 선택을 열 수 없습니다", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_APK || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, APK_MIME);
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(install);
        } catch (Exception e) {
            Toast.makeText(this, "APK 설치를 시작할 수 없습니다", Toast.LENGTH_LONG).show();
        }
    }

    private class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(TabletActivity.this).inflate(R.layout.item_app, parent, false);
            }
            AppEntry entry = apps.get(position);
            ((ImageView) view.findViewById(R.id.app_icon)).setImageDrawable(entry.icon);
            ((TextView) view.findViewById(R.id.app_label)).setText(entry.label);
            return view;
        }
    }
}
