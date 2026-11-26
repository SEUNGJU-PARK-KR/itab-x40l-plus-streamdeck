package com.itab.deck.agentdeck;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * One row of the AgentDeck daemon's `sessions_list` frame.
 * Field names mirror shared/src/protocol.ts SessionInfo.
 */
public class DeckSession {

    public static class Option {
        public int index;
        public String label = "";
        public String shortcut;
        public boolean recommended;
        public boolean selected;

        public static Option from(JSONObject o, int fallbackIndex) {
            Option opt = new Option();
            if (o == null) {
                opt.index = fallbackIndex;
                opt.label = "";
                return opt;
            }
            opt.index = o.has("index") ? o.optInt("index", fallbackIndex) : fallbackIndex;
            opt.label = o.optString("label", "");
            opt.shortcut = o.optString("shortcut", null);
            opt.recommended = o.optBoolean("recommended", false);
            opt.selected = o.optBoolean("selected", false);
            return opt;
        }
    }

    public String id = "";
    public boolean alive;
    public String projectName = "";
    public String displayName;
    public String agentType = "";
    public String state = "";
    public String modelName = "";
    public String activity = "";
    public String currentTool;
    public String question;
    public String questionDetail;
    public String promptType;
    /** null when the daemon never reported a controlMode (field absent). */
    public String controlMode;
    public boolean controlModePresent;
    public String requestId;
    public boolean liveAnswerable;
    public boolean stopRequested;
    public int queuedDirectives;
    public double weight;
    public String startedAt;
    public final List<Option> options = new ArrayList<>();

    public static DeckSession from(JSONObject o) {
        DeckSession s = new DeckSession();
        if (o == null) return s;
        s.id = o.optString("id", "");
        s.alive = o.optBoolean("alive", false);
        s.projectName = o.optString("projectName", "");
        s.displayName = o.optString("displayName", null);
        s.agentType = o.optString("agentType", "");
        s.state = o.optString("state", "");
        s.modelName = o.optString("modelName", "");
        s.activity = o.optString("activity", "");
        s.currentTool = o.optString("currentTool", null);
        s.question = o.optString("question", null);
        s.questionDetail = o.optString("questionDetail", null);
        s.promptType = o.optString("promptType", null);
        s.controlModePresent = o.has("controlMode");
        s.controlMode = o.optString("controlMode", null);
        s.requestId = o.optString("requestId", null);
        if (s.requestId != null && s.requestId.isEmpty()) s.requestId = null;
        s.liveAnswerable = o.optBoolean("liveAnswerable", false);
        s.stopRequested = o.optBoolean("stopRequested", false);
        s.queuedDirectives = o.optInt("queuedDirectives", 0);
        s.weight = o.optDouble("weight", 0);
        s.startedAt = o.optString("startedAt", null);
        JSONArray opts = o.optJSONArray("options");
        if (opts != null) {
            for (int i = 0; i < opts.length(); i++) {
                s.options.add(Option.from(opts.optJSONObject(i), i));
            }
        }
        return s;
    }

    /** Parse the `sessions` array; daemon order preserved except stable sort by weight (lower first). */
    public static List<DeckSession> listFrom(JSONArray array) {
        List<DeckSession> out = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o != null) out.add(from(o));
            }
        }
        Collections.sort(out, new Comparator<DeckSession>() {
            @Override
            public int compare(DeckSession a, DeckSession b) {
                return Double.compare(a.weight, b.weight);
            }
        });
        return out;
    }

    public boolean isAwaiting() {
        return state != null && state.startsWith("awaiting");
    }

    public boolean isProcessing() {
        return "processing".equals(state);
    }

    public boolean isObserved() {
        return "observed".equals(controlMode);
    }

    /** Something is pending that the user may want to answer. */
    public boolean hasPendingPrompt() {
        return requestId != null || !options.isEmpty() || isAwaiting() || question != null;
    }

    /**
     * Whether a tap on this session's options can actually reach the agent.
     * Managed (PTY) sessions route through the session bridge; observed
     * sessions need the daemon's `liveAnswerable` delivery path.
     */
    public boolean answerable() {
        if (isObserved()) return liveAnswerable;
        return true;
    }

    public String title() {
        if (displayName != null && !displayName.isEmpty()) return displayName;
        if (projectName != null && !projectName.isEmpty()) return projectName;
        if (id.length() > 12) return id.substring(0, 12);
        return id;
    }

    public String agentLabel() {
        if (agentType == null || agentType.isEmpty()) return "agent";
        return agentType;
    }
}
