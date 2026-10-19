package de.melvin.tvlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Liest die installierten Apps und teilt sie in Dock und Raster auf. */
final class AppRepository {

    /**
     * Die vier Apps im Dock, von links nach rechts. Überschreibbar ohne neuen Build über
     * dock.txt (ein Paketname pro Zeile), siehe README.
     */
    static final String[] DEFAULT_DOCK = {
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "org.jellyfin.androidtv",
            "com.disney.disneyplus",
    };

    static final int DOCK_SIZE = 4;

    private static final String SETTINGS_PACKAGE = "com.android.tv.settings";

    static final class App {
        final String packageName;
        final String label;
        final Drawable banner;
        final Drawable icon;
        /** Kräftige Farbe aus Banner oder Icon, für das Leuchten über dem Dock */
        final int glowColor;

        App(String packageName, String label, Drawable banner, Drawable icon, int glowColor) {
            this.packageName = packageName;
            this.label = label;
            this.banner = banner;
            this.icon = icon;
            this.glowColor = glowColor;
        }
    }

    static final class Result {
        final List<App> dock = new ArrayList<>();
        final List<App> grid = new ArrayList<>();
    }

    private AppRepository() {
    }

    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        return intent != null ? intent : pm.getLaunchIntentForPackage(pkg);
    }

    static Result load(Context context) {
        PackageManager pm = context.getPackageManager();
        // Nur echte TV-Apps (LEANBACK_LAUNCHER); Handy-Einträge wie Entwicklertools bleiben draußen
        Map<String, ResolveInfo> launchable = new LinkedHashMap<>();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(intent, 0)) {
            launchable.putIfAbsent(ri.activityInfo.packageName, ri);
        }
        launchable.remove(context.getPackageName());
        // Die Einstellungen erreicht man über die Pille oben rechts
        launchable.remove(SETTINGS_PACKAGE);

        Result result = new Result();
        List<String> dockPackages = dockPackages(context);
        for (String pkg : dockPackages) {
            ResolveInfo ri = launchable.remove(pkg);
            if (ri != null && result.dock.size() < DOCK_SIZE) result.dock.add(toApp(pm, ri));
        }
        for (ResolveInfo ri : launchable.values()) {
            result.grid.add(toApp(pm, ri));
        }
        Collections.sort(result.grid, (a, b) ->
                a.label.toLowerCase(Locale.GERMAN).compareTo(b.label.toLowerCase(Locale.GERMAN)));
        return result;
    }

    /** dock.txt im App-Ordner hat Vorrang vor DEFAULT_DOCK. */
    private static List<String> dockPackages(Context context) {
        List<String> list = new ArrayList<>();
        File dir = context.getExternalFilesDir(null);
        File file = dir != null ? new File(dir, "dock.txt") : null;
        if (file != null && file.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) list.add(line);
                }
            } catch (IOException ignored) {
                list.clear();
            }
        }
        if (list.isEmpty()) Collections.addAll(list, DEFAULT_DOCK);
        return list;
    }

    private static App toApp(PackageManager pm, ResolveInfo ri) {
        Drawable banner = ri.activityInfo.loadBanner(pm);
        if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
        Drawable icon = ri.loadIcon(pm);
        int color = vividColor(banner != null ? banner : icon);
        return new App(ri.activityInfo.packageName, ri.loadLabel(pm).toString(), banner, icon, color);
    }

    /**
     * Mittelt die Pixel einer verkleinerten Kopie, kräftige Farben zählen stärker als Grau.
     * Anschließend wird die Farbe aufgehellt und gesättigt, damit sie auf Schwarz leuchtet.
     */
    private static int vividColor(Drawable drawable) {
        if (drawable == null) return 0xFF8E8E93;
        int w = 24;
        int h = 14;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, w, h);
        drawable.draw(canvas);
        long r = 0, g = 0, b = 0, weight = 0;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int p = bmp.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                int pr = Color.red(p), pg = Color.green(p), pb = Color.blue(p);
                int sat = Math.max(pr, Math.max(pg, pb)) - Math.min(pr, Math.min(pg, pb));
                int wgt = sat > 60 ? 12 : 1;
                r += (long) pr * wgt;
                g += (long) pg * wgt;
                b += (long) pb * wgt;
                weight += wgt;
            }
        }
        bmp.recycle();
        if (weight == 0) return 0xFF8E8E93;
        float[] hsv = new float[3];
        Color.RGBToHSV((int) (r / weight), (int) (g / weight), (int) (b / weight), hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.25f);
        hsv[2] = Math.max(hsv[2], 0.85f);
        return Color.HSVToColor(hsv);
    }
}
