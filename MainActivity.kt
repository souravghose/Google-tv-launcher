package com.myapp.tvhome

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                HomeScreen()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
    val intent: Intent,
)

/** Apps we treat as "streaming" and show first, if they are installed. */
private val STREAMING_PACKAGES = listOf(
    "com.google.android.youtube.tv",
    "com.google.android.youtube.tvunplugged",
    "com.netflix.ninja",
    "com.amazon.amazonvideo.livingroom",
    "com.disney.disneyplus",
    "com.apple.atve.androidtv.appletv",
    "com.hbo.hbonow",
    "com.wbd.stream",
    "com.spotify.tv.android",
    "com.plexapp.android",
    "org.videolan.vlc",
    "com.mxtech.videoplayer.ad",
    "tv.pluto.android",
    "com.tubitv",
)

private fun prefs(c: Context) = c.getSharedPreferences("tvhome", Context.MODE_PRIVATE)

private fun loadFavs(c: Context): Set<String> =
    HashSet(prefs(c).getStringSet("favs", emptySet()) ?: emptySet())

private fun saveFavs(c: Context, favs: Set<String>) {
    prefs(c).edit().putStringSet("favs", HashSet(favs)).apply()
}

private fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    fun query(category: String) = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(category), 0
    )

    val seen = HashSet<String>()
    val result = ArrayList<AppEntry>()
    for (ri in query(Intent.CATEGORY_LEANBACK_LAUNCHER) + query(Intent.CATEGORY_LAUNCHER)) {
        val pkg = ri.activityInfo.packageName
        if (pkg == ctx.packageName || !seen.add(pkg)) continue
        val launch = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
            ?: continue
        val bitmap = try {
            ri.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
        } catch (e: Exception) {
            continue
        }
        result.add(AppEntry(ri.loadLabel(pm).toString(), pkg, bitmap, launch))
    }
    return result.sortedBy { it.label.lowercase() }
}

private fun launchApp(ctx: Context, app: AppEntry) {
    try {
        ctx.startActivity(Intent(app.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
    }
}

private fun openSettings(ctx: Context, action: String) {
    try {
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Not available on this TV", Toast.LENGTH_SHORT).show()
    }
}

// ---------------------------------------------------------------------------
// Home screen
// ---------------------------------------------------------------------------

private val TABS = listOf("For you", "Apps", "Settings")

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var favs by remember { mutableStateOf(loadFavs(ctx)) }
    var tab by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }

    // Reload the app list whenever we come back to the home screen
    // (picks up newly installed or removed apps).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(refresh) {
        apps = withContext(Dispatchers.Default) { loadApps(ctx) }
    }

    // Back never leaves the home screen; from another tab it returns to "For you".
    BackHandler(enabled = true) { tab = 0 }

    val toggleFav: (AppEntry) -> Unit = { app ->
        favs = if (app.packageName in favs) favs - app.packageName else favs + app.packageName
        saveFavs(ctx, favs)
        Toast.makeText(
            ctx,
            if (app.packageName in favs) "${app.label} added to Favorites"
            else "${app.label} removed from Favorites",
            Toast.LENGTH_SHORT
        ).show()
    }
    val launch: (AppEntry) -> Unit = { launchApp(ctx, it) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF121A26), Color(0xFF0B0F14), Color(0xFF07090D))
                )
            )
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(tab) { tab = it }
            when (tab) {
                0 -> ForYouTab(apps, favs, launch, toggleFav)
                1 -> AppsTab(apps, favs, launch, toggleFav)
                else -> SettingsTab()
            }
        }
    }
}

@Composable
private fun TopBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TABS.forEachIndexed { i, name ->
                FocusCard(
                    shape = RoundedCornerShape(50),
                    onClick = { onSelect(i) },
                    onFocused = { onSelect(i) },
                ) {
                    Box(
                        Modifier
                            .background(if (i == selected) Color(0x33FFFFFF) else Color.Transparent)
                            .padding(horizontal = 22.dp, vertical = 10.dp)
                    ) {
                        Text(
                            name,
                            fontSize = 18.sp,
                            fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (i == selected) Color.White else Color(0xFFB0B8C4),
                        )
                    }
                }
            }
        }
        Clock()
    }
}

@Composable
private fun Clock() {
    val fmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val time by produceState(initialValue = "") {
        while (true) {
            value = fmt.format(Date())
            delay(15_000)
        }
    }
    Text(time, fontSize = 20.sp, color = Color(0xFFE0E4EA))
}

// ---------------------------------------------------------------------------
// Tabs
// ---------------------------------------------------------------------------

@Composable
private fun ForYouTab(
    apps: List<AppEntry>,
    favs: Set<String>,
    onLaunch: (AppEntry) -> Unit,
    onToggleFav: (AppEntry) -> Unit,
) {
    val streaming = STREAMING_PACKAGES.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
    val favApps = apps.filter { it.packageName in favs }
    val featured = if (streaming.isNotEmpty()) streaming else apps.take(5)

    LazyColumn(
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        if (featured.isNotEmpty()) {
            item { Hero(featured, onLaunch) }
        }
        if (favApps.isNotEmpty()) {
            item { AppRow("Favorites", favApps, favs, onLaunch, onToggleFav) }
        }
        if (streaming.isNotEmpty()) {
            item { AppRow("Your streaming apps", streaming, favs, onLaunch, onToggleFav) }
        }
        item { AppRow("Apps", apps, favs, onLaunch, onToggleFav) }
        item {
            Text(
                "Tip: press and hold OK on an app to add or remove it from Favorites.",
                modifier = Modifier.padding(horizontal = 48.dp),
                fontSize = 14.sp,
                color = Color(0xFF7D8794),
            )
        }
    }
}

@Composable
private fun AppsTab(
    apps: List<AppEntry>,
    favs: Set<String>,
    onLaunch: (AppEntry) -> Unit,
    onToggleFav: (AppEntry) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(168.dp),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        gridItems(apps, key = { it.packageName }) { app ->
            AppTile(app, app.packageName in favs, onLaunch, onToggleFav)
        }
    }
}

private data class QuickSetting(val label: String, val action: String)

private val QUICK_SETTINGS = listOf(
    QuickSetting("Wi-Fi & network", Settings.ACTION_WIFI_SETTINGS),
    QuickSetting("Display", Settings.ACTION_DISPLAY_SETTINGS),
    QuickSetting("Sound", Settings.ACTION_SOUND_SETTINGS),
    QuickSetting("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS),
    QuickSetting("Apps", Settings.ACTION_APPLICATION_SETTINGS),
    QuickSetting("Date & time", Settings.ACTION_DATE_SETTINGS),
    QuickSetting("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    QuickSetting("All settings", Settings.ACTION_SETTINGS),
)

@Composable
private fun SettingsTab() {
    val ctx = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(220.dp),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        gridItems(QUICK_SETTINGS, key = { it.label }) { s ->
            FocusCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                onClick = { openSettings(ctx, s.action) },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1C2430)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(s.label, fontSize = 18.sp, color = Color.White)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Components
// ---------------------------------------------------------------------------

@Composable
private fun Hero(featured: List<AppEntry>, onLaunch: (AppEntry) -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(featured.size) {
        while (featured.size > 1) {
            delay(7_000)
            index = (index + 1) % featured.size
        }
    }
    val app = featured[index % featured.size]

    Crossfade(targetState = app, label = "hero") { a ->
        val hue = (a.packageName.hashCode().let { if (it < 0) -it else it } % 360).toFloat()
        FocusCard(
            modifier = Modifier
                .padding(horizontal = 48.dp)
                .fillMaxWidth()
                .height(260.dp),
            shape = RoundedCornerShape(24.dp),
            onClick = { onLaunch(a) },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.hsv(hue, 0.55f, 0.50f), Color(0xFF0B0F14))
                        )
                    )
            )
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Featured", fontSize = 16.sp, color = Color(0xCCFFFFFF))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        a.label,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Press OK to open", fontSize = 18.sp, color = Color(0xCCFFFFFF))
                }
                Image(
                    bitmap = a.icon,
                    contentDescription = a.label,
                    modifier = Modifier.size(120.dp),
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    title: String,
    apps: List<AppEntry>,
    favs: Set<String>,
    onLaunch: (AppEntry) -> Unit,
    onToggleFav: (AppEntry) -> Unit,
) {
    if (apps.isEmpty()) return
    Column {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppTile(app, app.packageName in favs, onLaunch, onToggleFav)
            }
        }
    }
}

@Composable
private fun AppTile(
    app: AppEntry,
    isFav: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onToggleFav: (AppEntry) -> Unit,
) {
    Column(Modifier.width(168.dp)) {
        FocusCard(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            onClick = { onLaunch(app) },
            onLongClick = { onToggleFav(app) },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1C2430)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(64.dp),
                )
            }
            if (isFav) {
                Text(
                    "★",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    color = Color(0xFFFFC107),
                    fontSize = 16.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            app.label,
            fontSize = 14.sp,
            color = Color(0xFFD0D5DD),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A D-pad focusable card: grows slightly and gets a white outline when focused,
 * like the cards on Google TV.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FocusCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFocused: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "focusScale")

    Box(
        modifier = modifier
            .scale(scale)
            .border(3.dp, if (focused) Color.White else Color.Transparent, shape)
            .clip(shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        content = content,
    )
}
