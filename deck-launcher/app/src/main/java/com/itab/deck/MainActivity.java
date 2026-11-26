package com.itab.deck;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PREFS = "deck_settings";
    private static final String KEY_STREAMDECK_URL = "streamdeck_url";
    private static final String KEY_LINUX_URL = "linux_url";
    private static final String DEFAULT_STREAMDECK_URL = "http://192.168.0.10:8193";
    private static final String DEFAULT_LINUX_URL = "http://127.0.0.1:8080";
    private static final String TERMINAL_URL = "http://127.0.0.1:7681";

    private SharedPreferences prefs;
    private View homeView;
    private View homeButton;
    private View webControls;
    private WebView webView;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(R.layout.activity_main);

        homeView = findViewById(R.id.home_view);
        homeButton = findViewById(R.id.home_button);
        webControls = findViewById(R.id.web_controls);
        webView = findViewById(R.id.web_view);
        statusText = findViewById(R.id.status_text);

        configureWebView();
        findViewById(R.id.linux_card).setOnClickListener(v -> openLinuxDashboard());
        findViewById(R.id.deck_card).setOnClickListener(v -> openStreamDeck());
        findViewById(R.id.tablet_card).setOnClickListener(v -> openTablet());
        findViewById(R.id.settings_button).setOnClickListener(v -> showSettings());
        findViewById(R.id.dashboard_button).setOnClickListener(v -> openWeb(prefs.getString(KEY_LINUX_URL, DEFAULT_LINUX_URL), "리눅스 서버 대시보드"));
        findViewById(R.id.terminal_button).setOnClickListener(v -> openTerminal());
        homeButton.setOnClickListener(v -> showHome());
        handleOpenUrlIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleOpenUrlIntent(intent);
    }

    private void handleOpenUrlIntent(Intent intent) {
        if (intent == null) return;
        String url = intent.getStringExtra("open_url");
        if (url != null && !url.trim().isEmpty()) {
            openWeb(url, "스트림덱 링크");
            intent.removeExtra("open_url");
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        webView.setKeepScreenOn(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                setStatus("연결 중…");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                setStatus("Connected");
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    Toast.makeText(MainActivity.this, "페이지를 열 수 없습니다: " + request.getUrl(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void openLinuxDashboard() {
        Intent service = new Intent(this, ServerService.class);
        startService(service);
        String url = prefs.getString(KEY_LINUX_URL, DEFAULT_LINUX_URL);
        String finalUrl = url;
        setStatus("리눅스 서버 대시보드 시작 중…");
        new Handler(Looper.getMainLooper()).postDelayed(() -> openWeb(finalUrl, "리눅스 서버 대시보드"), 650);
    }

    private void openStreamDeck() {
        startActivity(new Intent(this, DeckActivity.class));
    }

    private void openTablet() {
        startActivity(new Intent(this, TabletActivity.class));
    }

    private void openTerminal() {
        openWeb(TERMINAL_URL, "Alpine Linux 터미널");
    }

    private void openWeb(String url, String title) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "URL을 먼저 설정해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        homeView.setVisibility(View.GONE);
        webControls.setVisibility(View.VISIBLE);
        webView.setVisibility(View.VISIBLE);
        enterImmersiveMode();
        setStatus(title);
        webView.loadUrl(url);
    }

    private void showHome() {
        webView.stopLoading();
        webView.setVisibility(View.GONE);
        webControls.setVisibility(View.GONE);
        homeView.setVisibility(View.VISIBLE);
        exitImmersiveMode();
        setStatus("Ready");
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private void exitImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private void showSettings() {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        fields.setPadding(pad, pad, pad, 0);

        EditText deckInput = new EditText(this);
        deckInput.setSingleLine(true);
        deckInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        deckInput.setHint("Macro Deck URL");
        deckInput.setText(prefs.getString(KEY_STREAMDECK_URL, DEFAULT_STREAMDECK_URL));
        fields.addView(deckInput);

        EditText linuxInput = new EditText(this);
        linuxInput.setSingleLine(true);
        linuxInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        linuxInput.setHint("Linux dashboard URL");
        linuxInput.setText(prefs.getString(KEY_LINUX_URL, DEFAULT_LINUX_URL));
        fields.addView(linuxInput);

        new AlertDialog.Builder(this)
                .setTitle("연결 설정")
                .setView(fields)
                .setPositiveButton("저장", (dialog, which) -> {
                    prefs.edit()
                            .putString(KEY_STREAMDECK_URL, deckInput.getText().toString().trim())
                            .putString(KEY_LINUX_URL, linuxInput.getText().toString().trim())
                            .apply();
                    setStatus("Settings saved");
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void setStatus(String text) {
        statusText.setText(text);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onBackPressed() {
        if (webView.getVisibility() == View.VISIBLE) {
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                showHome();
            }
        } else {
            moveTaskToBack(true);
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
