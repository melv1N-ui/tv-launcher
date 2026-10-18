package de.melvin.tvlauncher

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Accent = Color(0xFF9DB8FF)
private val TextPrimary = Color(0xFFF2F3F5)
private val TextSecondary = Color(0xFFA3A9B5)
private val TextBody = Color(0xFFC4C9D2)
private val Glass = Color(0x8C0E121E)
private val GlassBorder = Color(0x1AFFFFFF)

@Composable
fun LauncherScreen(
    refreshKey: Int,
    onLaunch: (LauncherApp) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<AppRow>>(emptyList()) }
    var focused by remember { mutableStateOf<LauncherApp?>(null) }
    val firstTile = remember { FocusRequester() }

    LaunchedEffect(refreshKey) {
        rows = withContext(Dispatchers.Default) { AppRepository.load(context) }
    }

    val current = focused ?: rows.firstOrNull()?.apps?.firstOrNull()
    val glow by animateColorAsState(current?.color ?: Color(0xFF3B5FA8), tween(600), label = "glow")

    Box(Modifier.fillMaxSize()) {
        SpaceBackground(glow, Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Header(onOpenSettings)
            if (current != null) Hero(current, glow)
            rows.forEachIndexed { index, row ->
                AppRowView(
                    row = row,
                    firstTile = if (index == 0) firstTile else null,
                    onFocus = { focused = it },
                    onLaunch = onLaunch,
                )
            }
        }
    }

    // Beim Start die erste Kachel fokussieren
    LaunchedEffect(rows.isNotEmpty()) {
        if (rows.isNotEmpty() && focused == null) {
            delay(150)
            runCatching { firstTile.requestFocus() }
        }
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..10 -> "Guten Morgen"
        in 11..17 -> "Guten Tag"
        else -> "Guten Abend"
    }
    val clock = SimpleDateFormat("HH:mm", Locale.GERMANY).format(now)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlanetLogo()
            Text("$greeting, Melvin", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsButton(onOpenSettings)
            Text(clock, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PlanetLogo() {
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(0x0AFFFFFF))
            .border(1.dp, Color(0x40FFFFFF), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val stroke = Stroke(width = 1.dp.toPx())
            drawCircle(Accent, radius = size.minDimension * 0.22f, style = stroke)
            rotate(-20f) {
                drawOval(
                    Accent,
                    topLeft = Offset(0f, size.height * 0.35f),
                    size = Size(size.width, size.height * 0.3f),
                    style = stroke,
                )
            }
        }
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(if (isFocused) Color(0x33FFFFFF) else Color(0x0FFFFFFF))
            .border(if (isFocused) 1.5.dp else 1.dp, if (isFocused) Accent else GlassBorder, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Settings, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
        Text("Einstellungen", color = TextPrimary, fontSize = 11.sp)
    }
}

@Composable
private fun Hero(app: LauncherApp, glow: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        ) {
            Text(
                app.group.uppercase(Locale.GERMANY),
                color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
            )
            Text(
                app.name,
                color = TextPrimary, fontSize = 46.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (app.desc.isNotEmpty()) {
                Text(
                    app.desc,
                    color = TextBody, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 3,
                    modifier = Modifier.widthIn(max = 420.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (app.installed) "OK drücken zum Öffnen" else "Nicht installiert · OK öffnet den Play Store",
                color = Color(0xFF8A93A3), fontSize = 12.sp,
            )
        }

        Box(
            Modifier
                .width(320.dp)
                .fillMaxHeight()
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(glow.copy(alpha = 0.35f), Color.Transparent),
                            center = center,
                            radius = size.maxDimension * 0.75f,
                        ),
                        radius = size.maxDimension * 0.75f,
                        center = center,
                    )
                }
                .clip(RoundedCornerShape(16.dp))
                .background(Glass)
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(16.dp))
                .padding(22.dp),
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                AppIcon(app, 66.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        app.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(app.tag, color = Color(0xC7FFFFFF), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AppRowView(
    row: AppRow,
    firstTile: FocusRequester?,
    onFocus: (LauncherApp) -> Unit,
    onLaunch: (LauncherApp) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            row.label, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            itemsIndexed(row.apps, key = { _, app -> app.key }) { index, app ->
                AppTile(
                    app = app,
                    modifier = if (index == 0 && firstTile != null) Modifier.focusRequester(firstTile) else Modifier,
                    onFocus = onFocus,
                    onClick = { onLaunch(app) },
                )
            }
        }
    }
}

@Composable
private fun AppTile(
    app: LauncherApp,
    modifier: Modifier,
    onFocus: (LauncherApp) -> Unit,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    LaunchedEffect(isFocused, app) { if (isFocused) onFocus(app) }

    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, tween(180), label = "scale")
    val shape = RoundedCornerShape(13.dp)

    Row(
        modifier
            .width(if (app.wide) 235.dp else 150.dp)
            .height(75.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                if (isFocused) {
                    val halo = 3.dp.toPx()
                    drawRoundRect(
                        Brush.radialGradient(
                            listOf(app.color.copy(alpha = 0.55f), Color.Transparent),
                            center = center.copy(y = center.y + 10.dp.toPx()),
                            radius = size.width * 0.7f,
                        ),
                        topLeft = Offset(-size.width * 0.15f, -size.height * 0.2f),
                        size = Size(size.width * 1.3f, size.height * 1.6f),
                    )
                    drawRoundRect(
                        Accent.copy(alpha = 0.18f),
                        topLeft = Offset(-halo, -halo),
                        size = Size(size.width + halo * 2, size.height + halo * 2),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                    )
                }
            }
            .clip(shape)
            .background(Glass)
            .border(if (isFocused) 2.dp else 1.dp, if (isFocused) Accent else GlassBorder, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp)
            .alpha(if (app.installed) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        AppIcon(app, 34.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                app.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.tag, color = Color(0xB3FFFFFF), fontSize = 9.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AppIcon(app: LauncherApp, size: Dp) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(app.color),
        contentAlignment = Alignment.Center,
    ) {
        val icon = app.icon
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * 0.08f),
            )
        } else {
            Text(
                app.name.take(1),
                color = Color.White,
                fontSize = (size.value * 0.45f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
