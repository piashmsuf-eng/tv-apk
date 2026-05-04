package com.piashmsu.tvapk.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.piashmsu.tvapk.R
import com.piashmsu.tvapk.data.PlaylistSource
import com.piashmsu.tvapk.data.RefreshInterval
import com.piashmsu.tvapk.ui.AppViewModel
import java.util.UUID

@Composable
fun SettingsScreen() {
    val vm: AppViewModel = viewModel(factory = AppViewModel.Factory)
    val playlistSources by vm.playlistSources.collectAsState()
    val movieUrl by vm.movieCatalogUrl.collectAsState()
    val refreshInterval by vm.refreshInterval.collectAsState()
    val lastAutoRefresh by vm.lastAutoRefresh.collectAsState()

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var localMovies by remember(movieUrl) { mutableStateOf(movieUrl) }
    var editing by remember { mutableStateOf<PlaylistSource?>(null) }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Persist read access so the URI stays valid across reboots and
            // background-refresh cycles. Some providers don't support this
            // (e.g. one-shot share intents) — best-effort.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val display = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null }
                ?: "Local M3U"
            vm.upsertPlaylistSource(
                PlaylistSource(
                    id = UUID.randomUUID().toString(),
                    name = display,
                    url = uri.toString(),
                    enabled = true,
                )
            )
            vm.refreshChannels()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TopHeader() }

        item {
            Card("IPTV / M3U playlists") {
                Text(
                    "Add an M3U playlist URL or pick an .m3u/.m3u8 file from your device. The Live TV tab merges channels from every enabled source. Each source can carry its own EPG (XMLTV), User-Agent, and Referer overrides for protected streams. The bundled \"World TV\" source auto-updates daily from iptv-org's free public list.",
                    color = Color(0xCCBFC4D6),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))

                if (playlistSources.isEmpty()) {
                    EmptySources()
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        playlistSources.forEach { src ->
                            SourceRow(
                                source = src,
                                onToggle = { vm.togglePlaylistEnabled(src) },
                                onEdit = { editing = src },
                                onDelete = { vm.removePlaylistSource(src.id) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            editing = PlaylistSource(
                                id = UUID.randomUUID().toString(),
                                name = "",
                                url = "",
                            )
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Add URL", style = MaterialTheme.typography.labelLarge)
                    }
                    FilledTonalButton(
                        onClick = {
                            // Most file managers don't register an explicit
                            // MIME type for .m3u/.m3u8; allow */* with a
                            // hint so HLS playlists are also reachable.
                            pickFile.launch(arrayOf("*/*"))
                        },
                        shape = RoundedCornerShape(50),
                    ) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Pick from device", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        item {
            Card("Auto-refresh") {
                Text(
                    "Auto-update every enabled playlist and EPG in the background so the Live TV tab always shows the latest channels. New installs default to every 12 hours.",
                    color = Color(0xCCBFC4D6),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RefreshInterval.values().forEach { value ->
                        AssistChip(
                            onClick = { vm.setRefreshInterval(value) },
                            label = { Text(value.label, style = MaterialTheme.typography.labelLarge) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (refreshInterval == value)
                                    MaterialTheme.colorScheme.primary
                                else Color(0xFF1B2143),
                                labelColor = if (refreshInterval == value) Color.White else Color(0xCCBFC4D6),
                            ),
                            border = null,
                        )
                    }
                }
                if (lastAutoRefresh.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Last refresh: $lastAutoRefresh",
                        color = Color(0x88BFC4D6),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        item {
            Card("Movie catalog JSON URL (optional)") {
                Text(
                    "Provide a JSON endpoint that returns an array of movie objects (title, streamUrl, poster, genre, language, year, …). The Movies tab is empty until this is set.",
                    color = Color(0xCCBFC4D6),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = localMovies,
                    onValueChange = { localMovies = it },
                    placeholder = { Text("https://example.com/movies.json") },
                    singleLine = true,
                    colors = textFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton(text = "Save & load catalog") {
                    vm.saveMovieCatalogUrl(localMovies)
                }
            }
        }

        item {
            Card("Appearance") {
                Text(
                    "Pick how the app should look. \"Follow system\" honours the device's dark-mode setting; the other options force a specific theme regardless of system settings.",
                    color = Color(0xCCBFC4D6),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                val theme by vm.appTheme.collectAsState()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.piashmsu.tvapk.data.AppTheme.values().forEach { option ->
                        AssistChip(
                            onClick = { vm.setAppTheme(option) },
                            label = { Text(option.label) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (theme == option) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                                    else Color(0x33FFFFFF),
                                labelColor = Color.White,
                            ),
                        )
                    }
                }
            }
        }

        item {
            Card("About") {
                AboutRow("App", "TV APK • v5.0 Cinema")
                AboutRow("Developer", stringResource(R.string.developer_name))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { uriHandler.openUri("https://" + "fb.com/piashmsuf") }
                        .padding(vertical = 6.dp),
                ) {
                    Text("Facebook", color = Color(0xAABFC4D6), modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.developer_handle),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                AboutRow("Player", "Media3 / ExoPlayer 1.4.1")
                AboutRow("Streaming", "HLS • DASH • SmoothStreaming • RTSP • Progressive")
                AboutRow("Live features", "EPG • Catch-up • Recording • Favorites")
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    "TV APK is a player shell. The user is responsible for ensuring the streams and catalogs they configure are legal to consume in their jurisdiction.",
                    color = Color(0x88BFC4D6),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }

    val source = editing
    if (source != null) {
        PlaylistEditorDialog(
            initial = source,
            onDismiss = { editing = null },
            onSave = {
                vm.upsertPlaylistSource(it)
                editing = null
            },
        )
    }
}

@Composable
private fun EmptySources() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0B0F1F))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.LiveTv,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No playlists yet",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Tap “Add URL” to paste an M3U link or “Pick from device” to load an .m3u/.m3u8 file from your file manager.",
            color = Color(0xCCBFC4D6),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SourceRow(
    source: PlaylistSource,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0B0F1F))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.LiveTv, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                source.name.ifBlank { "Untitled playlist" },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val display = when {
                source.url.startsWith("content://") -> "Local file • " +
                    (source.url.substringAfterLast('/').substringBefore('?').ifBlank { source.url })
                source.url.startsWith("file://") -> "Local file • " + source.url.substringAfterLast('/')
                else -> source.url
            }
            Text(
                display,
                color = Color(0xAABFC4D6),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val flags = listOfNotNull(
                source.epgUrl?.let { "EPG" },
                source.userAgent?.let { "UA" },
                source.referer?.let { "Referer" },
            )
            if (flags.isNotEmpty()) {
                Text(
                    flags.joinToString(" • "),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Switch(
            checked = source.enabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
        IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = Color(0xCCBFC4D6))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color(0xCCFF6B81))
        }
    }
}

@Composable
private fun PlaylistEditorDialog(
    initial: PlaylistSource,
    onDismiss: () -> Unit,
    onSave: (PlaylistSource) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.url) }
    var epg by remember { mutableStateOf(initial.epgUrl.orEmpty()) }
    var ua by remember { mutableStateOf(initial.userAgent.orEmpty()) }
    var referer by remember { mutableStateOf(initial.referer.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161B2E),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LiveTv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(
                    if (initial.url.isBlank()) "Add playlist" else "Edit playlist",
                    color = Color.White,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Name (e.g. My desh playlist)") },
                    singleLine = true,
                    colors = textFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("M3U / M3U8 URL") },
                    singleLine = true,
                    colors = textFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = epg,
                    onValueChange = { epg = it },
                    placeholder = { Text("EPG / XMLTV URL (optional)") },
                    singleLine = true,
                    colors = textFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ua,
                    onValueChange = { ua = it },
                    placeholder = { Text("Default User-Agent (optional)") },
                    singleLine = true,
                    colors = textFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = referer,
                    onValueChange = { referer = it },
                    placeholder = { Text("Default Referer (optional)") },
                    singleLine = true,
                    colors = textFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim().ifBlank { "Playlist" },
                            url = url.trim(),
                            epgUrl = epg.trim().ifBlank { null },
                            userAgent = ua.trim().ifBlank { null },
                            referer = referer.trim().ifBlank { null },
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Save")
            }
        },
        dismissButton = {
            FilledTonalButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun TopHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text("Settings", color = Color.White, style = MaterialTheme.typography.headlineLarge)
            Text(
                "Playlists, EPG, auto-refresh & catalogs",
                color = Color(0xCCBFC4D6),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF111527))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when {
                    title.contains("Movie", ignoreCase = true) -> Icons.Outlined.Movie
                    title.contains("refresh", ignoreCase = true) -> Icons.Outlined.Schedule
                    else -> Icons.Outlined.LiveTv
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(8.dp))
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(50),
    ) {
        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = Color(0xAABFC4D6), modifier = Modifier.weight(1f))
        Text(value, color = Color.White)
    }
}

@Composable
internal fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color(0xFF0B0F1F),
    unfocusedContainerColor = Color(0xFF0B0F1F),
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = Color(0x33BFC4D6),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedPlaceholderColor = Color(0x88BFC4D6),
    unfocusedPlaceholderColor = Color(0x88BFC4D6),
)


