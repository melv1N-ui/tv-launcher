package de.melvin.tvlauncher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Startbildschirm im Stil von Apple TV.
 *
 * Fokus und Navigation verwaltet diese Klasse selbst (Bereich + Index), statt sich auf
 * Androids Fokus-Suche zu verlassen. Dadurch ist jeder Tastendruck vorhersehbar:
 * Hoch/Runter wechselt genau eine Zeile, Hoch aus dem Dock führt in die Top-Leiste,
 * an den Enden der Top-Leiste passiert nichts.
 */
public class MainActivity extends Activity implements InputsMenu.Listener {

    private static final int ZONE_TOP = 0;
    private static final int ZONE_DOCK = 1;
    private static final int ZONE_GRID = 2;

    private static final int COLUMNS = 4;
    private static final float FOCUS_SCALE = 1.1f;
    private static final float PILL_SCALE = 1.08f;
    private static final int PILL_INPUTS = 0;
    private static final int PILL_WIFI = 1;
    private static final int PILL_SETTINGS = 2;

    private View host;
    private ViewGroup page;
    private View band;
    private RecyclerView dock;
    private RecyclerView grid;
    private GlassHighlight highlight;
    private TextView greeting;
    private TextView clock;
    private TextView date;
    private TextView title;
    private TextView wifiText;
    private ImageView wifiIcon;
    private ImageView wallpaper;
    private View[] pills;

    private TileAdapter dockAdapter;
    private TileAdapter gridAdapter;
    private final List<AppRepository.App> dockApps = new ArrayList<>();
    private final List<AppRepository.App> gridApps = new ArrayList<>();

    private InputsMenu menu;
    private SpringAnimation pageScroll;
    private GradientDrawable glowDrawable;
    private ValueAnimator glowAnimator;
    private int glowColor;

    private int zone = ZONE_DOCK;
    private int index;
    private int lastTop = PILL_INPUTS;
    private int lastDock;
    private long wallpaperStamp = -1;

    private SharedPreferences prefs;
    private final Rect rect = new Rect();
    private final RectF rectF = new RectF();
    private final DecelerateInterpolator decelerate = new DecelerateInterpolator(2f);

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reloadApps();
        }
    };

    // Netzwerkwechsel kommen als Ereignis; es wird nichts abgefragt oder gepollt
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            runOnUiThread(MainActivity.this::updateWifi);
        }

        @Override
        public void onLost(Network network) {
            runOnUiThread(MainActivity.this::updateWifi);
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
            runOnUiThread(MainActivity.this::updateWifi);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);

        host = findViewById(R.id.host);
        page = findViewById(R.id.page);
        band = findViewById(R.id.band);
        dock = findViewById(R.id.dock);
        grid = findViewById(R.id.grid);
        highlight = findViewById(R.id.highlight);
        greeting = findViewById(R.id.greeting);
        clock = findViewById(R.id.clock);
        date = findViewById(R.id.date);
        title = findViewById(R.id.title);
        wifiText = findViewById(R.id.pill_wifi_text);
        wifiIcon = findViewById(R.id.pill_wifi_icon);
        wallpaper = findViewById(R.id.wallpaper);
        pills = new View[]{findViewById(R.id.pill_inputs), findViewById(R.id.pill_wifi), findViewById(R.id.pill_settings)};

        greeting.setTypeface(Fonts.outfit(this, 400));
        clock.setTypeface(Fonts.outfit(this, 150));
        date.setTypeface(Fonts.outfit(this, 400));
        title.setTypeface(Fonts.outfit(this, 300));
        ((TextView) findViewById(R.id.pill_inputs_text)).setTypeface(Fonts.outfit(this, 450));
        wifiText.setTypeface(Fonts.outfit(this, 450));

        // Kopfbereich füllt den Bildschirm bis zum Dock, damit das Dock unten sitzt
        View header = findViewById(R.id.header);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        header.getLayoutParams().height = screenH
                - getResources().getDimensionPixelSize(R.dimen.band_h)
                - getResources().getDimensionPixelSize(R.dimen.band_bottom);

        dockAdapter = setupRecycler(dock);
        gridAdapter = setupRecycler(grid);

        glowDrawable = new GradientDrawable();
        glowDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowDrawable.setGradientRadius(dp(300));
        glowDrawable.setColors(new int[]{0, 0, 0});
        View glow = findViewById(R.id.glow);
        glow.setBackground(glowDrawable);
        glow.setScaleY(0.42f);

        pageScroll = new SpringAnimation(page, DynamicAnimation.TRANSLATION_Y, 0f);
        pageScroll.getSpring().setStiffness(320f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);

        menu = new InputsMenu(this, findViewById(R.id.menu_layer), this);

        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgFilter.addDataScheme("package");
        registerReceiver(packageReceiver, pkgFilter);

        lastTop = prefs.getInt("top", PILL_INPUTS);
        lastDock = prefs.getInt("dock", 0);
        reloadApps();
    }

    private TileAdapter setupRecycler(RecyclerView rv) {
        TileAdapter adapter = new TileAdapter(Fonts.outfit(this, 500));
        rv.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        rv.setAdapter(adapter);
        rv.setItemAnimator(null);
        rv.setFocusable(false);
        rv.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        return adapter;
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(networkCallback);
    }

    @Override
    protected void onStop() {
        getSystemService(ConnectivityManager.class).unregisterNetworkCallback(networkCallback);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter timeFilter = new IntentFilter(Intent.ACTION_TIME_TICK);
        timeFilter.addAction(Intent.ACTION_TIME_CHANGED);
        timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, timeFilter);
        updateClock();
        updateWifi();
        updateWallpaper();
    }

    @Override
    protected void onPause() {
        unregisterReceiver(timeReceiver);
        menu.close();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home-Taste, während der Startbildschirm schon offen ist: zurück zum Dock.
        // Kommt man aus einer App zurück, bleibt die zuletzt fokussierte Kachel stehen.
        // (Gleiche Prüfung wie im AOSP-Launcher: Fenster hatte Fokus und wurde nicht erst nach vorn geholt.)
        boolean alreadyHome = hasWindowFocus()
                && (intent.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0;
        menu.close();
        if (alreadyHome && zone != ZONE_DOCK && !dockApps.isEmpty()) setFocus(ZONE_DOCK, lastDock, true);
    }

    // ---------------------------------------------------------------- Apps

    private void reloadApps() {
        String focusedPkg = prefs.getString("pkg", null);
        AppRepository.Result result = AppRepository.load(this);
        dockApps.clear();
        dockApps.addAll(result.dock);
        gridApps.clear();
        gridApps.addAll(result.grid);
        dockAdapter.submit(dockApps);
        gridAdapter.submit(gridApps);

        // Zuletzt fokussierte Kachel wiederfinden, auch wenn sich ihre Position verschoben hat
        int newZone = ZONE_TOP;
        int newIndex = Math.min(lastTop, pills.length - 1);
        int dockPos = indexOf(dockApps, focusedPkg);
        int gridPos = indexOf(gridApps, focusedPkg);
        if (dockPos >= 0) {
            newZone = ZONE_DOCK;
            newIndex = dockPos;
        } else if (gridPos >= 0) {
            newZone = ZONE_GRID;
            newIndex = gridPos;
        } else if (zone != ZONE_TOP && !dockApps.isEmpty()) {
            newZone = ZONE_DOCK;
            newIndex = Math.min(lastDock, dockApps.size() - 1);
        }
        lastDock = Math.max(0, Math.min(lastDock, dockApps.size() - 1));
        if (newZone == ZONE_DOCK) lastDock = newIndex;
        if (!dockApps.isEmpty()) showTitle(dockApps.get(lastDock), false);

        final int z = newZone;
        final int i = newIndex;
        afterLayout(() -> setFocus(z, i, false));
    }

    private static int indexOf(List<AppRepository.App> list, String pkg) {
        if (pkg == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).packageName.equals(pkg)) return i;
        }
        return -1;
    }

    private void launch(AppRepository.App app) {
        Intent intent = AppRepository.launchIntent(getPackageManager(), app.packageName);
        try {
            if (intent == null) throw new ActivityNotFoundException();
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.cannot_start, app.label), Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings(String action) {
        try {
            // CLEAR_TASK: immer auf der Startseite der Einstellungen landen, nicht dort, wo man zuletzt war
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        } catch (ActivityNotFoundException e) {
            if (!Settings.ACTION_SETTINGS.equals(action)) openSettings(Settings.ACTION_SETTINGS);
        }
    }

    // ---------------------------------------------------------------- Tasten

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (menu.isOpen()) {
                        menu.onKeyDown(code);
                    } else {
                        onNavigate(code);
                    }
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void onNavigate(int code) {
        int dockN = dockApps.size();
        int gridN = gridApps.size();
        boolean select = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;

        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            // Zurück bringt aus Raster oder Top-Leiste zum Dock; im Dock passiert nichts
            if (zone != ZONE_DOCK && dockN > 0) setFocus(ZONE_DOCK, lastDock, true);
            return;
        }

        switch (zone) {
            case ZONE_TOP:
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && index > 0) {
                    setFocus(ZONE_TOP, index - 1, true);
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && index < pills.length - 1) {
                    setFocus(ZONE_TOP, index + 1, true);
                } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (dockN > 0) setFocus(ZONE_DOCK, lastDock, true);
                    else if (gridN > 0) setFocus(ZONE_GRID, 0, true);
                } else if (select) {
                    activatePill(index);
                }
                break;

            case ZONE_DOCK:
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && index > 0) {
                    setFocus(ZONE_DOCK, index - 1, true);
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && index < dockN - 1) {
                    setFocus(ZONE_DOCK, index + 1, true);
                } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    setFocus(ZONE_TOP, lastTop, true);
                } else if (code == KeyEvent.KEYCODE_DPAD_DOWN && gridN > 0) {
                    setFocus(ZONE_GRID, Math.min(index, gridN - 1), true);
                } else if (select) {
                    launch(dockApps.get(index));
                }
                break;

            case ZONE_GRID:
                int col = index % COLUMNS;
                int row = index / COLUMNS;
                int lastRow = (gridN - 1) / COLUMNS;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && col > 0) {
                    setFocus(ZONE_GRID, index - 1, true);
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && col < COLUMNS - 1 && index + 1 < gridN) {
                    setFocus(ZONE_GRID, index + 1, true);
                } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    if (row > 0) setFocus(ZONE_GRID, index - COLUMNS, true);
                    else if (dockN > 0) setFocus(ZONE_DOCK, Math.min(col, dockN - 1), true);
                    else setFocus(ZONE_TOP, lastTop, true);
                } else if (code == KeyEvent.KEYCODE_DPAD_DOWN && row < lastRow) {
                    // Genau eine Zeile tiefer; ist die letzte Zeile kürzer, auf deren letzte Kachel
                    setFocus(ZONE_GRID, Math.min(index + COLUMNS, gridN - 1), true);
                } else if (select) {
                    launch(gridApps.get(index));
                }
                break;

            default:
                break;
        }
    }

    private void activatePill(int pill) {
        if (pill == PILL_INPUTS) {
            menu.open(pills[PILL_INPUTS]);
        } else if (pill == PILL_WIFI) {
            openSettings(Settings.ACTION_WIFI_SETTINGS);
        } else {
            openSettings(Settings.ACTION_SETTINGS);
        }
    }

    @Override
    public void onMenuClosed() {
        // Fokus bleibt auf der Eingänge-Pille
    }

    // ---------------------------------------------------------------- Fokus

    private void setFocus(int newZone, int newIndex, boolean animate) {
        if (newZone == ZONE_DOCK && dockApps.isEmpty()) newZone = ZONE_TOP;
        if (newZone == ZONE_GRID && gridApps.isEmpty()) newZone = ZONE_TOP;
        int count = newZone == ZONE_TOP ? pills.length : newZone == ZONE_DOCK ? dockApps.size() : gridApps.size();
        newIndex = Math.max(0, Math.min(newIndex, count - 1));

        setTileFocused(zone, index, false, animate);
        zone = newZone;
        index = newIndex;
        setTileFocused(zone, index, true, animate);

        SharedPreferences.Editor edit = prefs.edit();
        if (zone == ZONE_TOP) {
            lastTop = index;
            edit.putInt("top", index).putString("pkg", null);
        } else {
            AppRepository.App app = zone == ZONE_DOCK ? dockApps.get(index) : gridApps.get(index);
            edit.putString("pkg", app.packageName);
            if (zone == ZONE_DOCK) {
                lastDock = index;
                edit.putInt("dock", index);
                showTitle(app, animate);
            }
        }
        edit.apply();

        moveHighlight(animate);
        float target = scrollTarget();
        if (animate) {
            pageScroll.animateToFinalPosition(target);
        } else {
            pageScroll.cancel();
            page.setTranslationY(target);
        }
    }

    private TileAdapter.Holder holder(int z, int i) {
        RecyclerView rv = z == ZONE_DOCK ? dock : z == ZONE_GRID ? grid : null;
        if (rv == null) return null;
        RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(i);
        return vh instanceof TileAdapter.Holder ? (TileAdapter.Holder) vh : null;
    }

    private void setTileFocused(int z, int i, boolean focused, boolean animate) {
        long duration = animate ? 220 : 0;
        if (z == ZONE_TOP) {
            float scale = focused ? PILL_SCALE : 1f;
            pills[i].animate().scaleX(scale).scaleY(scale).setDuration(duration).setInterpolator(decelerate).start();
            return;
        }
        TileAdapter.Holder h = holder(z, i);
        if (h == null) return;
        float scale = focused ? FOCUS_SCALE : 1f;
        h.frame.animate().scaleX(scale).scaleY(scale).setDuration(duration).setInterpolator(decelerate).start();
        h.label.animate().alpha(focused ? 1f : 0f).translationY(focused ? dp(6) : 0)
                .setDuration(duration).setInterpolator(decelerate).start();
    }

    /** Rechteck des fokussierten Elements in Seitenkoordinaten, inklusive Vergrößerung. */
    private void moveHighlight(boolean animate) {
        float radius;
        if (zone == ZONE_TOP) {
            View pill = pills[index];
            bounds(pill, rectF);
            rectF.inset(-rectF.width() * (PILL_SCALE - 1f) / 2f, -rectF.height() * (PILL_SCALE - 1f) / 2f);
            radius = rectF.height() / 2f;
        } else {
            TileAdapter.Holder h = holder(zone, index);
            if (h == null) return;
            bounds(h.frame, rectF);
            float dx = rectF.width() * (FOCUS_SCALE - 1f) / 2f;
            float dy = rectF.height() * (FOCUS_SCALE - 1f) / 2f;
            rectF.inset(-dx, -dy);
            radius = getResources().getDimension(R.dimen.tile_radius) * FOCUS_SCALE;
        }
        highlight.moveTo(rectF, radius, animate);
    }

    /** Wie weit die Seite nach oben geschoben wird, damit die fokussierte Zeile sichtbar ist. */
    private float scrollTarget() {
        if (zone != ZONE_GRID) return 0f;
        TileAdapter.Holder h = holder(ZONE_GRID, index);
        if (h == null) return page.getTranslationY();
        // Erste Rasterzeile: Dock rutscht nach oben an den Rand, darunter das Raster
        bounds(band, rectF);
        float target = -(rectF.top - dp(36));
        bounds(h.itemView, rectF);
        float limit = host.getHeight() - dp(20);
        if (rectF.bottom + target > limit) target -= rectF.bottom + target - limit;
        return target;
    }

    private void bounds(View view, RectF out) {
        rect.set(0, 0, view.getWidth(), view.getHeight());
        page.offsetDescendantRectToMyCoords(view, rect);
        out.set(rect);
    }

    private void afterLayout(Runnable action) {
        page.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                page.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                action.run();
            }
        });
        page.requestLayout();
    }

    // ---------------------------------------------------------------- Titel und Leuchten

    private void showTitle(AppRepository.App app, boolean animate) {
        if (!app.label.contentEquals(title.getText())) {
            title.setText(app.label);
            if (animate) {
                title.setAlpha(0.2f);
                title.animate().alpha(1f).setDuration(260).start();
            }
        }
        int from = glowColor;
        int to = app.glowColor;
        if (glowAnimator != null) glowAnimator.cancel();
        if (!animate || from == 0) {
            applyGlow(to);
            return;
        }
        glowAnimator = ValueAnimator.ofArgb(from, to);
        glowAnimator.setDuration(380);
        glowAnimator.addUpdateListener(a -> applyGlow((int) a.getAnimatedValue()));
        glowAnimator.start();
    }

    private void applyGlow(int color) {
        glowColor = color;
        int rgb = color & 0x00FFFFFF;
        glowDrawable.setColors(new int[]{0x55000000 | rgb, 0x1F000000 | rgb, rgb});
        title.setShadowLayer(dp(22), 0, 0, 0x8C000000 | rgb);
    }

    // ---------------------------------------------------------------- Uhr, WLAN, Hintergrund

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        String pattern = DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm";
        clock.setText(DateFormat.format(pattern, now));
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY);
        date.setText(dateFormat.format(now.getTime()));
        int hour = now.get(Calendar.HOUR_OF_DAY);
        String text;
        if (hour < 5) text = "Gute Nacht";
        else if (hour < 11) text = "Guten Morgen";
        else if (hour < 17) text = "Guten Tag";
        else if (hour < 22) text = "Guten Abend";
        else text = "Gute Nacht";
        greeting.setText(text);
    }

    private void updateWifi() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network network = cm.getActiveNetwork();
        NetworkCapabilities caps = network != null ? cm.getNetworkCapabilities(network) : null;
        // Den Netzwerknamen gibt Android 11 nur mit eingeschaltetem Standort heraus (siehe Manifest),
        // deshalb zeigt die Pille Verbindungsart und Signalstärke
        String text;
        int level = 3;
        if (caps == null) {
            text = getString(R.string.offline);
            level = 0;
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            text = getString(R.string.ethernet);
        } else {
            text = getString(R.string.wifi);
            WifiManager wm = getApplicationContext().getSystemService(WifiManager.class);
            int rssi = caps.getSignalStrength();
            if (wm != null && rssi != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) {
                int max = Math.max(1, wm.getMaxSignalLevel());
                level = Math.round(3f * wm.calculateSignalLevel(rssi) / max);
            }
        }
        wifiIcon.setImageLevel(level);
        if (!text.contentEquals(wifiText.getText())) {
            wifiText.setText(text);
            // Pillen sind rechtsbündig: ändert sich die Breite, wandern die Nachbarn mit
            if (zone == ZONE_TOP) afterLayout(() -> moveHighlight(false));
        }
    }

    /** wallpaper.jpg/.png im App-Ordner wird als gedimmter Hintergrund gezeigt (siehe README). */
    private void updateWallpaper() {
        File dir = getExternalFilesDir(null);
        File file = null;
        if (dir != null) {
            for (String name : new String[]{"wallpaper.jpg", "wallpaper.png"}) {
                File f = new File(dir, name);
                if (f.isFile()) {
                    file = f;
                    break;
                }
            }
        }
        long stamp = file != null ? file.lastModified() : 0;
        if (stamp == wallpaperStamp) return;
        wallpaperStamp = stamp;
        if (file == null) {
            wallpaper.setImageDrawable(null);
            wallpaper.setVisibility(View.GONE);
            return;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), opts);
        int sample = 1;
        while (opts.outWidth / (sample * 2) >= 1920) sample *= 2;
        opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeFile(file.getPath(), opts);
        wallpaper.setImageBitmap(bmp);
        wallpaper.setVisibility(bmp != null ? View.VISIBLE : View.GONE);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
