package com.itab.deck;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.itab.deck.agentdeck.AgentDeckClient;
import com.itab.deck.agentdeck.BridgeDiscovery;
import com.itab.deck.agentdeck.DeckPrefs;
import com.itab.deck.agentdeck.DeckSession;
import com.itab.deck.agentdeck.Pairing;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AgentDeck-compatible AI-agent controller.
 *
 * Replaces the old generic HTTP-button deck: connects to an AgentDeck daemon
 * (ws://host:9120[?token=..]), renders the daemon's sessions_list as fixed
 * tiles and routes taps to focus / select_option / permission_decision /
 * session_command commands. No fake data — an unconfigured endpoint shows
 * setup instructions.
 */
public class DeckActivity extends Activity implements AgentDeckClient.Listener {

    private static final int COLS = 4;
    private static final int MAX_ROWS = 3;
    private static final int CAPACITY = COLS * MAX_ROWS;

    private static final int COLOR_WORKING = 0xFF3ED6E8; // cyan
    private static final int COLOR_AWAITING = 0xFFFFA93D; // amber
    private static final int COLOR_IDLE = 0xFF9A9AA2;     // gray
    private static final int COLOR_OFFLINE = 0xFF7A8A9C;  // blue-gray
    private static final int COLOR_OK = 0xFF4ADE80;
    private static final int COLOR_BAD = 0xFFFF6B6B;

    private static final int CONN_IDLE = 0;
    private static final int CONN_CONNECTING = 1;
    private static final int CONN_CONNECTED = 2;
    private static final int CONN_RETRY = 3;
    private static final int CONN_CLOSED = 4;
    private static final int CONN_AUTH = 5;

    private DeckPrefs prefs;
    private AgentDeckClient client;

    private LinearLayout gridView;
    private TextView emptyView;
    private TextView connDot;
    private TextView connText;
    private TextView endpointText;
    private TextView usageText;
    private TextView detailTitle;
    private TextView detailQuestion;
    private LinearLayout actionsView;
    private TextView statusText;

    private final List<DeckSession> sessions = new ArrayList<>();
    private final List<ObjectAnimator> pulseAnimators = new ArrayList<>();

    private String selectedSessionId;
    private String daemonFocusedId;
    private JSONObject lastStateUpdate;
    private JSONObject lastUsage;
    private int connState = CONN_IDLE;
    private boolean discoveryRunning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deck);

        prefs = new DeckPrefs(this);
        client = new AgentDeckClient(this);

        gridView = findViewById(R.id.deck_sessions);
        emptyView = findViewById(R.id.deck_empty);
        connDot = findViewById(R.id.deck_conn_dot);
        connText = findViewById(R.id.deck_conn);
        endpointText = findViewById(R.id.deck_endpoint);
        usageText = findViewById(R.id.deck_usage);
        detailTitle = findViewById(R.id.deck_detail_title);
        detailQuestion = findViewById(R.id.deck_detail_question);
        actionsView = findViewById(R.id.deck_actions);
        statusText = findViewById(R.id.deck_status);

        findViewById(R.id.btn_home).setOnClickListener(v -> finish());
        findViewById(R.id.btn_settings).setOnClickListener(v -> openSettings());
        findViewById(R.id.btn_reconnect).setOnClickListener(v -> reconnectNow());
        findViewById(R.id.btn_interrupt).setOnClickListener(v -> sendInterrupt());
        findViewById(R.id.btn_escape).setOnClickListener(v -> sendEscape());
        usageText.setOnClickListener(v -> {
            client.queryUsage();
            setStatus("사용량 조회 요청");
        });
        emptyView.setOnClickListener(v -> {
            if (!prefs.configured()) openSettings();
        });

        updateTopBar();
        rebuildGrid();
        renderDetail();
        if (prefs.configured()) {
            startConnect();
        } else {
            setConnState(CONN_IDLE, "브릿지 검색 중", COLOR_OFFLINE);
            setStatus("AgentDeck 브릿지 자동 검색 중…");
            discoverBridge(true);
        }
    }

    private void discoverBridge(final boolean connectWhenFound) {
        if (discoveryRunning) return;
        discoveryRunning = true;
        BridgeDiscovery.discoverOnce(4500, (found, host, port, label, error) -> {
            discoveryRunning = false;
            if (found) {
                prefs.saveEndpoint(host, port);
                updateTopBar();
                setStatus("브릿지 발견: " + label);
                if (connectWhenFound) startConnect();
            } else if (!prefs.configured()) {
                setConnState(CONN_IDLE, "브릿지 미설정", COLOR_OFFLINE);
                setStatus("브릿지를 찾지 못했습니다 — 설정에서 주소를 입력하세요");
            }
        });
    }

    // ---- connection ---------------------------------------------------------

    private void startConnect() {
        try {
            URI uri = prefs.wsUri();
            setConnState(CONN_CONNECTING, "연결 중…", COLOR_AWAITING);
            client.connect(uri);
        } catch (Exception e) {
            setConnState(CONN_CLOSED, "주소 오류", COLOR_BAD);
            setStatus("브릿지 주소가 올바르지 않습니다: " + prefs.endpointLabel());
        }
    }

    private void reconnectNow() {
        if (!prefs.configured()) {
            openSettings();
            return;
        }
        setStatus("재연결 중…");
        startConnect();
    }

    private void setConnState(int state, String label, int dotColor) {
        connState = state;
        connText.setText(label);
        connDot.setTextColor(dotColor);
        updateTopBar();
        refreshEmptyText();
    }

    private void updateTopBar() {
        endpointText.setText(prefs.configured() ? prefs.endpointLabel() : "브릿지 미설정");
        String usage = formatUsage(lastUsage);
        usageText.setText(usage);
        usageText.setVisibility(usage.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ---- AgentDeckClient.Listener ------------------------------------------

    @Override
    public void onSocketOpen() {
        setConnState(CONN_CONNECTED, "연결됨", COLOR_OK);
        setStatus("브릿지 연결됨");
        client.queryUsage();
    }

    @Override
    public void onSessions(List<DeckSession> list) {
        sessions.clear();
        sessions.addAll(list);
        rebuildGrid();
        renderDetail();
        setStatus("세션 " + sessions.size() + "개");
    }

    @Override
    public void onStateUpdate(JSONObject frame) {
        lastStateUpdate = frame;
        String focused = nullableString(frame, "focusedSessionId");
        if (focused != null && !focused.isEmpty()) daemonFocusedId = focused;
        renderDetail();
    }

    @Override
    public void onUsageUpdate(JSONObject frame) {
        lastUsage = frame;
        updateTopBar();
    }

    @Override
    public void onConnectionEvent(JSONObject frame) {
        // Daemon-side link status; the socket state above is authoritative.
        String status = frame.optString("status", "");
        if ("disconnected".equals(status)) {
            setStatus("데몬 링크 끊김");
        }
    }

    @Override
    public void onSocketClosed(int code, String reason) {
        setConnState(CONN_CLOSED, "연결 끊김", COLOR_OFFLINE);
        setStatus("연결 종료 (" + code + (reason == null || reason.isEmpty() ? "" : " " + reason) + ")");
    }

    @Override
    public void onSocketError(String message) {
        // Transient errors precede onClose; only surface them in the status line.
        setStatus("소켓 오류: " + message);
    }

    @Override
    public void onAuthRequired() {
        setConnState(CONN_AUTH, "승인 필요", COLOR_BAD);
        setStatus("승인 필요 — 설정에서 페어링 코드 또는 토큰을 입력하세요");
        Toast.makeText(this, "페어링/토큰 필요", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onReconnectScheduled(long delayMs, int attempt) {
        setConnState(CONN_RETRY, "재연결 대기 (" + (delayMs / 1000) + "s, #" + attempt + ")", COLOR_AWAITING);
        setStatus((delayMs / 1000) + "초 후 재연결…");
    }

    // ---- grid ---------------------------------------------------------------

    private void rebuildGrid() {
        cancelPulseAnimators();
        gridView.removeAllViews();
        if (sessions.isEmpty()) {
            refreshEmptyText();
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        emptyView.setVisibility(View.GONE);

        int display = sessions.size() <= CAPACITY ? sessions.size() : CAPACITY - 1;
        int overflow = sessions.size() - display;
        int cells = display + (overflow > 0 ? 1 : 0);
        int rows = (cells + COLS - 1) / COLS;

        int index = 0;
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            row.setLayoutParams(rowParams);
            for (int c = 0; c < COLS; c++) {
                View tile;
                if (index < display) {
                    tile = createSessionTile(sessions.get(index));
                } else if (index < cells) {
                    tile = createOverflowTile(overflow);
                } else {
                    tile = createEmptyTile();
                }
                row.addView(tile);
                index++;
            }
            gridView.addView(row);
        }
    }

    private void refreshEmptyText() {
        if (emptyView == null) return;
        String text;
        switch (connState) {
            case CONN_CONNECTING:
                text = "브릿지에 연결 중…";
                break;
            case CONN_RETRY:
                text = "재연결 대기 중…";
                break;
            case CONN_AUTH:
                text = "승인 필요\n설정에서 6자리 페어링 코드로 토큰을 발급받거나 토큰을 직접 입력하세요.";
                break;
            case CONN_CONNECTED:
                text = "연결됨 — 활성 세션 없음";
                break;
            case CONN_CLOSED:
                text = "연결 끊김 — [재연결]을 눌러주세요";
                break;
            default:
                text = "AgentDeck 브릿지가 설정되지 않았습니다.\n\n"
                        + "우측 [설정]에서 브릿지 주소(예: 192.168.0.10:9120)를 입력하세요.\n"
                        + "토큰이 필요하면 데스크톱 AgentDeck에서 페어링 창을 열고\n"
                        + "표시된 6자리 코드를 [코드로 페어링]에 입력하세요.\n\n"
                        + "탭하면 설정이 열립니다.";
        }
        emptyView.setText(text);
    }

    private View createSessionTile(DeckSession s) {
        boolean selected = s.id.equals(selectedSessionId);
        int color = stateColor(s);

        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(6), dp(8), dp(6));
        tile.setBackground(tileBackground(color, selected));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        params.setMargins(dp(5), dp(5), dp(5), dp(5));
        tile.setLayoutParams(params);

        // Top row: state dot + agent type
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER);
        int iconRes = agentIconRes(s.agentType);
        if (iconRes != 0) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(iconRes);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(48), dp(48));
            icon.setLayoutParams(iconParams);
            head.addView(icon);
        } else {
            TextView dot = new TextView(this);
            dot.setText("●");
            dot.setTextColor(color);
            dot.setTextSize(12);
            head.addView(dot);
        }
        TextView agent = new TextView(this);
        agent.setText(s.agentLabel());
        agent.setTextColor(0xFFAAB4D6);
        agent.setTextSize(10);
        agent.setSingleLine(true);
        agent.setPadding(dp(4), 0, 0, 0);
        head.addView(agent);

        TextView title = new TextView(this);
        title.setText(s.title());
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView sub = new TextView(this);
        sub.setText(subText(s));
        sub.setTextColor(s.isAwaiting() ? COLOR_AWAITING : 0xFF8A94AD);
        sub.setTextSize(11);
        sub.setGravity(Gravity.CENTER);
        sub.setMaxLines(2);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);

        tile.addView(head);
        tile.addView(title);
        tile.addView(sub);

        tile.setOnClickListener(v -> onSessionTap(s));

        if (s.alive && s.isAwaiting()) {
            ObjectAnimator anim = ObjectAnimator.ofFloat(tile, "alpha", 1f, 0.55f);
            anim.setDuration(850);
            anim.setRepeatMode(ValueAnimator.REVERSE);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.start();
            pulseAnimators.add(anim);
        }
        return tile;
    }

    private View createOverflowTile(int overflow) {
        LinearLayout tile = baseTile(0xFF8A94AD);
        TextView label = new TextView(this);
        label.setText("+" + overflow + " 세션");
        label.setTextColor(0xFFAAB4D6);
        label.setTextSize(13);
        label.setGravity(Gravity.CENTER);
        tile.addView(label);
        return tile;
    }

    private View createEmptyTile() {
        LinearLayout tile = baseTile(0x338A94AD);
        TextView label = new TextView(this);
        label.setText("+");
        label.setTextColor(Color.WHITE);
        label.setAlpha(0.22f);
        label.setTextSize(18);
        label.setGravity(Gravity.CENTER);
        tile.addView(label);
        return tile;
    }

    private LinearLayout baseTile(int strokeColor) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(6), dp(8), dp(6));
        tile.setBackground(tileBackground(strokeColor, false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        params.setMargins(dp(5), dp(5), dp(5), dp(5));
        tile.setLayoutParams(params);
        return tile;
    }

    private GradientDrawable tileBackground(int strokeColor, boolean selected) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(20));
        d.setColor(selected ? 0x33FFFFFF : 0x1AFFFFFF);
        d.setStroke(dp(selected ? 3 : 2), strokeColor);
        return d;
    }

    private void cancelPulseAnimators() {
        for (ObjectAnimator anim : pulseAnimators) anim.cancel();
        pulseAnimators.clear();
    }

    // ---- session actions ----------------------------------------------------

    private void onSessionTap(DeckSession s) {
        vibrate();
        selectedSessionId = s.id;
        rebuildGrid();
        renderDetail();

        if (s.requestId != null) {
            setStatus("권한 요청 — 아래에서 허용/거부를 선택하세요");
        } else if (!s.options.isEmpty() && s.answerable()) {
            setStatus("옵션을 선택하세요");
        } else if (s.hasPendingPrompt() && !s.answerable()) {
            setStatus("터미널에서 응답해야 하는 프롬프트입니다");
        } else {
            client.focusSession(s.id);
            setStatus("포커스: " + s.title());
        }
    }

    private void sendInterrupt() {
        String target = commandTarget();
        if (target == null) {
            setStatus("중단할 세션을 먼저 선택하세요");
            return;
        }
        client.sessionCommand(target, "interrupt");
        setStatus("중단 전송: " + shortId(target));
    }

    private void sendEscape() {
        String target = commandTarget();
        if (target == null) {
            setStatus("ESC를 보낼 세션을 먼저 선택하세요");
            return;
        }
        client.sessionCommand(target, "escape");
        setStatus("ESC 전송: " + shortId(target));
    }

    /** Session targeted by global interrupt/escape: explicit selection, else daemon focus, else a single alive session. */
    private String commandTarget() {
        if (selectedSessionId != null && findSession(selectedSessionId) != null) return selectedSessionId;
        if (daemonFocusedId != null && findSession(daemonFocusedId) != null) return daemonFocusedId;
        DeckSession only = null;
        int alive = 0;
        for (DeckSession s : sessions) {
            if (s.alive) {
                only = s;
                alive++;
            }
        }
        return alive == 1 ? only.id : null;
    }

    private DeckSession findSession(String id) {
        for (DeckSession s : sessions) {
            if (s.id.equals(id)) return s;
        }
        return null;
    }

    private String shortId(String id) {
        return id.length() > 10 ? id.substring(0, 10) + "…" : id;
    }

    // ---- detail panel -------------------------------------------------------

    private DeckSession detailSession() {
        if (selectedSessionId != null) {
            DeckSession s = findSession(selectedSessionId);
            if (s != null) return s;
        }
        if (daemonFocusedId != null) {
            DeckSession s = findSession(daemonFocusedId);
            if (s != null) return s;
        }
        for (DeckSession s : sessions) {
            if (s.hasPendingPrompt()) return s;
        }
        return sessions.isEmpty() ? null : sessions.get(0);
    }

    private void renderDetail() {
        actionsView.removeAllViews();
        DeckSession s = detailSession();
        if (s == null) {
            detailTitle.setText("세션을 선택하세요");
            detailQuestion.setText("");
            return;
        }

        detailTitle.setText(s.title() + " · " + s.agentLabel() + " · " + stateLabel(s)
                + (s.modelName != null && !s.modelName.isEmpty() ? " · " + s.modelName : ""));

        // The live state_update may carry a fresher prompt for the focused session.
        JSONObject live = liveUpdateFor(s);
        String question = s.question;
        String questionDetail = s.questionDetail;
        String requestId = s.requestId;
        List<DeckSession.Option> options = s.options;
        if (live != null) {
            if (live.has("question")) question = nullableString(live, "question");
            if (live.has("requestId")) requestId = nullableString(live, "requestId");
            JSONArray liveOpts = live.optJSONArray("options");
            if (liveOpts != null) options = parseOptions(liveOpts);
        }

        StringBuilder body = new StringBuilder();
        if (question != null && !question.isEmpty()) body.append(question);
        if (questionDetail != null && !questionDetail.isEmpty()) {
            if (body.length() > 0) body.append('\n');
            body.append(questionDetail);
        }
        if (body.length() == 0) {
            if (s.stopRequested) {
                body.append("중단 요청됨 — 다음 tool call에서 정지");
            } else if (s.activity != null && !s.activity.isEmpty()) {
                body.append(s.activity);
            } else if (s.currentTool != null && !s.currentTool.isEmpty()) {
                body.append(s.currentTool);
            }
        }
        detailQuestion.setText(body.toString());

        if (requestId != null && !requestId.isEmpty()) {
            renderPermissionActions(s, requestId);
        } else if (!options.isEmpty()) {
            renderOptionActions(s, options, question);
        } else if (s.hasPendingPrompt()) {
            // Awaiting but nothing pressable here (observed without
            // liveAnswerable, or a prompt with no exposed options) —
            // never fabricate Yes/No; direct the user to the terminal.
            renderTerminalHint();
        }
    }

    /** The focused session's live state_update frame, when it names this session. */
    private JSONObject liveUpdateFor(DeckSession s) {
        if (lastStateUpdate == null) return null;
        String sid = lastStateUpdate.optString("sessionId", null);
        String fid = lastStateUpdate.optString("focusedSessionId", null);
        if ((sid != null && sid.equals(s.id)) || (fid != null && fid.equals(s.id))) {
            return lastStateUpdate;
        }
        return null;
    }

    private static List<DeckSession.Option> parseOptions(JSONArray array) {
        List<DeckSession.Option> out = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                out.add(DeckSession.Option.from(array.optJSONObject(i), i));
            }
        }
        return out;
    }

    private static String nullableString(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key)) return null;
        String value = object.optString(key, null);
        return "null".equals(value) ? null : value;
    }

    private void renderPermissionActions(final DeckSession s, final String requestId) {
        Button allow = actionButton("한 번 허용", COLOR_OK);
        allow.setOnClickListener(v -> {
            client.permissionDecision(requestId, "allow", s.id);
            setStatus("허용 전송: " + s.title());
        });
        Button deny = actionButton("거부", COLOR_BAD);
        deny.setOnClickListener(v -> {
            client.permissionDecision(requestId, "deny", s.id);
            setStatus("거부 전송: " + s.title());
        });
        actionsView.addView(allow);
        actionsView.addView(deny);
    }

    private void renderOptionActions(final DeckSession s, List<DeckSession.Option> options, final String question) {
        if (!s.answerable()) {
            for (DeckSession.Option opt : options) {
                Button chip = actionButton(optionLabel(opt), COLOR_IDLE);
                chip.setEnabled(false);
                chip.setAlpha(0.5f);
                actionsView.addView(chip);
            }
            renderTerminalHint();
            return;
        }
        for (final DeckSession.Option opt : options) {
            Button b = actionButton(optionLabel(opt), opt.recommended ? COLOR_WORKING : COLOR_IDLE);
            if (opt.recommended) b.setTypeface(Typeface.DEFAULT_BOLD);
            if (opt.selected) b.setAlpha(0.6f);
            b.setOnClickListener(v -> {
                client.selectOption(opt.index, s.id, question);
                setStatus("선택 전송: " + opt.label);
            });
            actionsView.addView(b);
        }
    }

    private void renderTerminalHint() {
        TextView hint = new TextView(this);
        hint.setText("터미널에서 응답");
        hint.setTextColor(COLOR_AWAITING);
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(10), 0, dp(4), 0);
        actionsView.addView(hint);
    }

    private String optionLabel(DeckSession.Option opt) {
        String label = opt.label == null || opt.label.isEmpty() ? "옵션 " + (opt.index + 1) : opt.label;
        if (opt.shortcut != null && !opt.shortcut.isEmpty()) {
            label = opt.shortcut + ". " + label;
        }
        if (opt.recommended) label = "★ " + label;
        if (label.length() > 28) label = label.substring(0, 28) + "…";
        return label;
    }

    private Button actionButton(String text, int textColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(textColor);
        b.setMinHeight(dp(48));
        b.setMinimumHeight(dp(48));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(4), 0, 0, 0);
        b.setLayoutParams(params);
        return b;
    }

    // ---- settings / pairing --------------------------------------------------

    private void openSettings() {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        fields.setPadding(pad, pad / 2, pad, 0);

        TextView endpointLabel = fieldLabel("브릿지 주소 (호스트 또는 호스트:포트)");
        final EditText endpointInput = new EditText(this);
        endpointInput.setSingleLine(true);
        endpointInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpointInput.setHint("예: 192.168.0.10:9120");
        endpointInput.setText(prefs.configured() ? prefs.endpointLabel() : "");

        TextView tokenLabel = fieldLabel("토큰 (선택 — 페어링으로 자동 발급 가능)");
        final EditText tokenInput = new EditText(this);
        tokenInput.setSingleLine(true);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenInput.setHint("32자 hex 토큰");
        tokenInput.setText(prefs.token());

        TextView codeLabel = fieldLabel("페어링 코드 (데스크톱 AgentDeck 페어링 창의 6자리)");
        final EditText codeInput = new EditText(this);
        codeInput.setSingleLine(true);
        codeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        codeInput.setHint("6자리 코드");

        final Button discoverButton = new Button(this);
        discoverButton.setText("브릿지 자동 검색");

        final Button pairButton = new Button(this);
        pairButton.setText("코드로 페어링");

        final TextView pairStatus = new TextView(this);
        pairStatus.setTextColor(0xFFAAB4D6);
        pairStatus.setTextSize(12);
        pairStatus.setPadding(0, dp(6), 0, 0);

        fields.addView(endpointLabel);
        fields.addView(endpointInput);
        fields.addView(tokenLabel);
        fields.addView(tokenInput);
        fields.addView(codeLabel);
        fields.addView(codeInput);
        fields.addView(discoverButton);
        fields.addView(pairButton);
        fields.addView(pairStatus);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("브릿지 설정")
                .setView(fields)
                .setPositiveButton("저장+연결", null)
                .setNeutralButton("연결 해제", null)
                .setNegativeButton("닫기", null)
                .create();

        discoverButton.setOnClickListener(v -> {
            discoverButton.setEnabled(false);
            pairStatus.setText("브릿지 자동 검색 중…");
            BridgeDiscovery.discoverOnce(4500, (found, host, port, label, error) -> {
                discoverButton.setEnabled(true);
                if (found) {
                    endpointInput.setText(host + ":" + port);
                    pairStatus.setText("브릿지 발견: " + label);
                } else {
                    pairStatus.setText("브릿지를 찾지 못했습니다 — 수동 입력 또는 페어링을 사용하세요");
                }
            });
        });

        pairButton.setOnClickListener(v -> {
            DeckPrefs.Endpoint ep = DeckPrefs.parseEndpoint(endpointInput.getText().toString());
            String code = codeInput.getText().toString().trim();
            if (ep == null) {
                pairStatus.setText("브릿지 주소를 먼저 입력하세요");
                return;
            }
            if (code.length() != 6) {
                pairStatus.setText("6자리 페어링 코드를 입력하세요");
                return;
            }
            pairStatus.setText("페어링 요청 중…");
            pairButton.setEnabled(false);
            Pairing.pair(ep.host, ep.port, code, (ok, token, port, error) -> {
                pairButton.setEnabled(true);
                if (ok) {
                    prefs.saveEndpoint(ep.host, port);
                    prefs.saveToken(token);
                    endpointInput.setText(prefs.endpointLabel());
                    tokenInput.setText(token);
                    dialog.dismiss();
                    updateTopBar();
                    setStatus("페어링 완료 — 연결합니다");
                    startConnect();
                } else {
                    pairStatus.setText("페어링 실패: " + error);
                }
            });
        });

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            DeckPrefs.Endpoint ep = DeckPrefs.parseEndpoint(endpointInput.getText().toString());
            if (ep == null) {
                prefs.save("", DeckPrefs.DEFAULT_PORT, tokenInput.getText().toString());
                client.disconnect();
                sessions.clear();
                selectedSessionId = null;
                daemonFocusedId = null;
                updateTopBar();
                rebuildGrid();
                renderDetail();
                setConnState(CONN_IDLE, "브릿지 미설정", COLOR_OFFLINE);
                dialog.dismiss();
                setStatus("브릿지 주소가 비어 있습니다");
                return;
            }
            prefs.save(ep.host, ep.port, tokenInput.getText().toString());
            updateTopBar();
            dialog.dismiss();
            setStatus("저장됨 — 연결합니다");
            startConnect();
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            client.disconnect();
            setConnState(CONN_CLOSED, "연결 해제됨", COLOR_OFFLINE);
            setStatus("연결 해제됨");
            dialog.dismiss();
        });
    }

    private TextView fieldLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(0xFFAAB4D6);
        label.setTextSize(12);
        label.setPadding(0, dp(8), 0, dp(2));
        return label;
    }

    // ---- helpers --------------------------------------------------------------

    private static int stateColor(DeckSession s) {
        if (!s.alive || "disconnected".equals(s.state)) return COLOR_OFFLINE;
        if (s.isAwaiting()) return COLOR_AWAITING;
        if (s.isProcessing()) return COLOR_WORKING;
        return COLOR_IDLE;
    }

    private static String stateLabel(DeckSession s) {
        if (!s.alive) return "오프라인";
        switch (s.state == null ? "" : s.state) {
            case "processing":
                return "작업 중";
            case "awaiting_permission":
                return "권한 대기";
            case "awaiting_option":
                return "선택 대기";
            case "awaiting_diff":
                return "DIFF 확인";
            case "idle":
                return "대기";
            case "disconnected":
                return "오프라인";
            default:
                return s.state == null || s.state.isEmpty() ? "대기" : s.state;
        }
    }

    private static String subText(DeckSession s) {
        if (!s.alive) return "오프라인";
        if (s.isAwaiting() && s.question != null && !s.question.isEmpty()) return s.question;
        if (s.activity != null && !s.activity.isEmpty()) return s.activity;
        if (s.currentTool != null && !s.currentTool.isEmpty()) return s.currentTool;
        return stateLabel(s);
    }

    private static String formatUsage(JSONObject u) {
        if (u == null) return "";
        List<String> parts = new ArrayList<>();
        if (u.has("fiveHourPercent")) {
            parts.add("5h " + Math.round(u.optDouble("fiveHourPercent")) + "%");
        }
        if (u.has("sevenDayPercent")) {
            parts.add("7d " + Math.round(u.optDouble("sevenDayPercent")) + "%");
        }
        JSONArray scoped = u.optJSONArray("scopedLimits");
        if (scoped != null && scoped.length() > 0) {
            JSONObject s0 = scoped.optJSONObject(0);
            if (s0 != null) {
                parts.add(s0.optString("label", "limit") + " " + Math.round(s0.optDouble("percent")) + "%");
            }
        }
        if (u.has("estimatedCostUsd")) {
            parts.add("$" + String.format(Locale.US, "%.2f", u.optDouble("estimatedCostUsd")));
        }
        String joined = join(parts, " · ");
        if (u.optBoolean("usageStale", false) && !joined.isEmpty()) joined += " (이전 값)";
        return joined;
    }

    private static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private int agentIconRes(String agentType) {
        if (agentType == null) return 0;
        String key = agentType.toLowerCase(java.util.Locale.US);
        if (key.contains("claude")) return R.drawable.ic_agent_claude;
        if (key.contains("codex")) return R.drawable.ic_agent_codex;
        if (key.contains("opencode") || key.equals("open-code")) return R.drawable.ic_agent_opencode;
        if (key.contains("openclaw")) return R.drawable.ic_agent_openclaw;
        if (key.contains("antigravity")) return R.drawable.ic_agent_antigravity;
        if (key.contains("kiro")) return R.drawable.ic_agent_kiro;
        if (key.contains("hermes")) return R.drawable.ic_agent_hermes;
        if (key.contains("zai") || key.contains("z.ai")) return R.drawable.ic_agent_zai;
        return 0;
    }

    private void vibrate() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) vibrator.vibrate(30);
        } catch (Exception ignored) {
        }
    }

    private void setStatus(String text) {
        statusText.setText(text);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        cancelPulseAnimators();
        if (client != null) client.destroy();
        super.onDestroy();
    }
}
