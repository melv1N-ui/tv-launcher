package de.melvin.tvlauncher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kompaktes Glas-Dropdown unter der Eingänge-Pille: HDMI-Anschlüsse, AV und Live TV.
 * Umgeschaltet wird über das TV Input Framework: ein VIEW-Intent auf die Passthrough-URI
 * des Eingangs, den die TV-App des Fernsehers (com.mediatek.wwtv.tvcenter) entgegennimmt.
 */
final class InputsMenu {

    private static final class Item {
        final String label;
        final String detail;
        final String inputId;
        final boolean connected;

        Item(String label, String detail, String inputId, boolean connected) {
            this.label = label;
            this.detail = detail;
            this.inputId = inputId;
            this.connected = connected;
        }
    }

    interface Listener {
        void onMenuClosed();
    }

    private final Activity activity;
    private final FrameLayout layer;
    private final Listener listener;
    private final List<Item> items = new ArrayList<>();
    private final List<View> rows = new ArrayList<>();
    private LinearLayout panel;
    private int selected;

    InputsMenu(Activity activity, FrameLayout layer, Listener listener) {
        this.activity = activity;
        this.layer = layer;
        this.listener = listener;
    }

    boolean isOpen() {
        return layer.getVisibility() == View.VISIBLE;
    }

    /** Öffnet das Menü rechtsbündig unter der Pille anchor. */
    void open(View anchor) {
        loadItems();
        buildPanel();

        // Die Ebene ist noch unsichtbar und hat keine Größe; maßgeblich ist ihr Elternelement
        View parent = (View) layer.getParent();
        int[] pos = new int[2];
        anchor.getLocationOnScreen(pos);
        int[] parentPos = new int[2];
        parent.getLocationOnScreen(parentPos);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(280), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        lp.topMargin = pos[1] - parentPos[1] + anchor.getHeight() + dp(10);
        lp.rightMargin = parent.getWidth() - (pos[0] - parentPos[0] + anchor.getWidth());
        layer.removeAllViews();
        layer.addView(panel, lp);
        layer.setVisibility(View.VISIBLE);

        selected = 0;
        updateSelection();
        panel.setAlpha(0f);
        panel.setScaleX(0.96f);
        panel.setScaleY(0.96f);
        panel.setPivotX(dp(280));
        panel.setPivotY(0);
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start();
    }

    void close() {
        if (!isOpen()) return;
        layer.setVisibility(View.GONE);
        layer.removeAllViews();
        listener.onMenuClosed();
    }

    /** Verarbeitet Tasten, solange das Menü offen ist. Gibt true zurück, wenn verbraucht. */
    boolean onKeyDown(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (selected > 0) selected--;
                updateSelection();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (selected < items.size() - 1) selected++;
                updateSelection();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (!items.isEmpty()) switchTo(items.get(selected));
                close();
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_TV_INPUT:
                close();
                return true;
            default:
                // Links/Rechts und alles andere bleiben im Menü
                return true;
        }
    }

    private void loadItems() {
        items.clear();
        TvInputManager tim = activity.getSystemService(TvInputManager.class);
        if (tim == null) return;
        List<TvInputInfo> all = tim.getTvInputList();

        // CEC-Geräte hängen als Kind-Eingang am HDMI-Anschluss; ihr Name wird zur Zusatzzeile
        Map<String, String> deviceNames = new HashMap<>();
        for (TvInputInfo info : all) {
            if (info.getParentId() != null) {
                deviceNames.put(info.getParentId(), String.valueOf(info.loadLabel(activity)));
            }
        }

        List<Item> hdmi = new ArrayList<>();
        Item av = null;
        Item tuner = null;
        boolean tunerIsDigital = false;
        for (TvInputInfo info : all) {
            if (info.getParentId() != null || info.isHidden(activity)) continue;
            String id = info.getId();
            boolean connected = tim.getInputState(id) != TvInputManager.INPUT_STATE_DISCONNECTED;
            switch (info.getType()) {
                case TvInputInfo.TYPE_HDMI:
                    hdmi.add(new Item(label(info), deviceNames.get(id), id, connected));
                    break;
                case TvInputInfo.TYPE_COMPOSITE:
                    if (av == null) av = new Item(activity.getString(R.string.av), null, id, connected);
                    break;
                case TvInputInfo.TYPE_TUNER:
                    // Live TV = Tuner des Systems, bevorzugt der digitale ("DTV" statt "ATV").
                    // Apps wie Zattoo melden sich auch als Tuner, sind aber keine Systemapps.
                    if (info.getServiceInfo() != null
                            && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                        boolean digital = label(info).toUpperCase(java.util.Locale.ROOT).contains("DTV");
                        if (tuner == null || (digital && !tunerIsDigital)) {
                            tuner = new Item(activity.getString(R.string.live_tv), null, id, true);
                            tunerIsDigital = digital;
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        hdmi.sort((a, b) -> a.label.compareTo(b.label));
        items.addAll(hdmi);
        if (av != null) items.add(av);
        if (tuner != null) items.add(tuner);
    }

    private String label(TvInputInfo info) {
        CharSequence custom = info.loadCustomLabel(activity);
        return String.valueOf(custom != null ? custom : info.loadLabel(activity));
    }

    private void buildPanel() {
        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.menu_bg);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        rows.clear();

        if (items.isEmpty()) {
            TextView empty = text(activity.getString(R.string.no_inputs), 15, 0xB3FFFFFF, 400);
            empty.setPadding(dp(14), dp(12), dp(14), dp(12));
            panel.addView(empty);
            return;
        }
        for (Item item : items) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), 0, dp(14), 0);
            row.setMinimumHeight(dp(44));
            row.setAlpha(item.connected ? 1f : 0.5f);

            TextView label = text(item.label, 15, 0xF2FFFFFF, 450);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (item.detail != null) {
                TextView detail = text(item.detail, 13, 0x8CFFFFFF, 400);
                row.addView(detail);
            }
            panel.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            rows.add(row);
        }
    }

    private void updateSelection() {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setBackgroundResource(i == selected ? R.drawable.menu_item_focused : 0);
        }
    }

    private void switchTo(Item item) {
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(TvContract.buildChannelUriForPassthroughInput(item.inputId),
                        TvContract.Channels.CONTENT_ITEM_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // Ohne passende TV-App bleibt der Launcher einfach stehen
        }
    }

    private TextView text(String s, int sp, int color, int weight) {
        TextView tv = new TextView(activity);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setTypeface(Fonts.outfit(activity, weight));
        tv.setSingleLine(true);
        return tv;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
