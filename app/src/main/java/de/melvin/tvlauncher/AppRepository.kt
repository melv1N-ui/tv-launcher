package de.melvin.tvlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** Eine fest eingeplante App mit eigener Farbe, Gruppe und Beschreibung. */
data class PinnedApp(
    val id: String,
    val name: String,
    val group: String,
    val tag: String,
    val desc: String,
    val color: Long,
    /** Mögliche Paketnamen, der erste installierte gewinnt. */
    val packages: List<String>,
    val wide: Boolean = false,
)

/** Was eine Kachel zum Anzeigen braucht. */
data class LauncherApp(
    val key: String,
    val name: String,
    val group: String,
    val tag: String,
    val desc: String,
    val color: Color,
    val packageName: String,
    val icon: ImageBitmap?,
    val installed: Boolean,
    val wide: Boolean,
)

data class AppRow(val label: String, val apps: List<LauncherApp>)

object Catalog {
    val gamingAndStreaming = listOf(
        PinnedApp(
            "moonlight", "Moonlight", "Gaming", "Gaming-PC streamen",
            "Spiele vom Gaming-PC direkt auf den Fernseher holen. Controller per Bluetooth, Bild in 4K.",
            0xFF3B5FA8, listOf("com.limelight"), wide = true,
        ),
        PinnedApp(
            "netflix", "Netflix", "Streaming", "Serien & Filme",
            "Weiterschauen, wo du aufgehört hast, oder etwas Neues entdecken.",
            0xFFA32424, listOf("com.netflix.ninja"),
        ),
        PinnedApp(
            "disney", "Disney+", "Streaming", "Serien & Filme",
            "Filme und Serien aus dem Disney+-Katalog.",
            0xFF2A4A9A, listOf("com.disney.disneyplus"),
        ),
        PinnedApp(
            "prime", "Prime Video", "Streaming", "Serien & Filme",
            "Inhalte aus deinem Prime-Abo und Leihfilme.",
            0xFF137285, listOf("com.amazon.amazonvideo.livingroom"),
        ),
        PinnedApp(
            "smarttube", "SmartTube", "Streaming", "YouTube ohne Werbung",
            "YouTube ohne Werbung, mit SponsorBlock und deinen Abos.",
            0xFFA04A18,
            listOf("com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv"),
        ),
    )

    val library = listOf(
        PinnedApp(
            "jellyfin", "Jellyfin", "Mediathek", "Eigene Mediathek",
            "Filme und Serien vom eigenen Server im Homelab.",
            0xFF6A3FB0, listOf("org.jellyfin.androidtv"),
        ),
        PinnedApp(
            "plex", "Plex", "Mediathek", "Eigene Mediathek",
            "Deine Plex-Bibliotheken und geteilte Server.",
            0xFF8A6A14, listOf("com.plexapp.android"),
        ),
        PinnedApp(
            "kodi", "Kodi", "Mediathek", "Mediacenter",
            "Lokale Dateien, Netzwerkfreigaben und Add-ons.",
            0xFF1F6F96, listOf("org.xbmc.kodi"),
        ),
        PinnedApp(
            "stremio", "Stremio", "Mediathek", "Add-on-Streaming",
            "Kataloge und Add-ons an einem Ort.",
            0xFF3A7A4A, listOf("com.stremio.one"),
        ),
    )
}

object AppRepository {

    fun launchIntent(pm: PackageManager, pkg: String): Intent? =
        pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)

    fun load(context: Context): List<AppRow> {
        val pm = context.packageManager
        val pinnedPackages = mutableSetOf<String>()

        fun pinned(p: PinnedApp): LauncherApp {
            pinnedPackages.addAll(p.packages)
            val pkg = p.packages.firstOrNull { launchIntent(pm, it) != null }
            return LauncherApp(
                key = p.id,
                name = p.name,
                group = p.group,
                tag = if (pkg != null) p.tag else "Nicht installiert",
                desc = p.desc,
                color = Color(p.color),
                packageName = pkg ?: p.packages.first(),
                icon = pkg?.let { loadIcon(pm, it) },
                installed = pkg != null,
                wide = p.wide,
            )
        }

        val first = Catalog.gamingAndStreaming.map { pinned(it) }
        val second = Catalog.library.map { pinned(it) }

        val others = queryLaunchable(pm)
            .filter { it.activityInfo.packageName !in pinnedPackages && it.activityInfo.packageName != context.packageName }
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                val bmp = loadBitmap(pm, pkg)
                LauncherApp(
                    key = "app:$pkg",
                    name = ri.loadLabel(pm).toString(),
                    group = "Alle Apps",
                    tag = "App",
                    desc = "",
                    color = bmp?.let { dominantColor(it) } ?: Color(0xFF2A3350),
                    packageName = pkg,
                    icon = bmp?.asImageBitmap(),
                    installed = true,
                    wide = false,
                )
            }
            .sortedBy { it.name.lowercase() }

        return buildList {
            add(AppRow("Gaming & Streaming", first))
            add(AppRow("Mediathek", second))
            if (others.isNotEmpty()) add(AppRow("Alle Apps", others))
        }
    }

    private fun queryLaunchable(pm: PackageManager): List<ResolveInfo> {
        val result = LinkedHashMap<String, ResolveInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            val list = if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            for (ri in list) result.putIfAbsent(ri.activityInfo.packageName, ri)
        }
        return result.values.toList()
    }

    private fun loadBitmap(pm: PackageManager, pkg: String): Bitmap? = try {
        pm.getApplicationIcon(pkg).toBitmap(160, 160, Bitmap.Config.ARGB_8888)
    } catch (e: Exception) {
        null
    }

    private fun loadIcon(pm: PackageManager, pkg: String): ImageBitmap? = loadBitmap(pm, pkg)?.asImageBitmap()

    /** Grobe Hauptfarbe des Icons, abgedunkelt, damit weiße Schrift darauf lesbar bleibt. */
    private fun dominantColor(bmp: Bitmap): Color {
        val small = Bitmap.createScaledBitmap(bmp, 12, 12, true)
        var r = 0L
        var g = 0L
        var b = 0L
        var weight = 0L
        for (x in 0 until 12) for (y in 0 until 12) {
            val p = small.getPixel(x, y)
            if ((p ushr 24) < 128) continue
            val pr = (p shr 16) and 0xFF
            val pg = (p shr 8) and 0xFF
            val pb = p and 0xFF
            val sat = maxOf(pr, pg, pb) - minOf(pr, pg, pb)
            val w = if (sat > 40) 4 else 1
            r += pr * w; g += pg * w; b += pb * w; weight += w
        }
        if (weight == 0L) return Color(0xFF2A3350)
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV((r / weight).toInt(), (g / weight).toInt(), (b / weight).toInt(), hsv)
        hsv[1] = (hsv[1] * 1.1f).coerceAtMost(0.85f)
        hsv[2] = hsv[2].coerceIn(0.3f, 0.6f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}
