package com.piashmsu.tvapk.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.piashmsu.tvapk.data.LauncherVariant
import com.piashmsu.tvapk.data.SortMode
import com.piashmsu.tvapk.data.ThemePalette
import com.piashmsu.tvapk.data.UiLanguage
import com.piashmsu.tvapk.ui.AppViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun ThemeAndLocaleCard(vm: AppViewModel) {
    val palette by vm.themePalette.collectAsState()
    val launcher by vm.launcherVariant.collectAsState()
    val lang by vm.uiLanguage.collectAsState()

    Card("Theme & language") {
        Text("Color palette", color = Color(0xCCBFC4D6), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePalette.values().forEach { p ->
                ChipChoice(
                    label = p.label,
                    selected = palette == p,
                    onClick = { vm.setThemePalette(p) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Launcher icon", color = Color(0xCCBFC4D6), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LauncherVariant.values().forEach { v ->
                ChipChoice(
                    label = v.label,
                    selected = launcher == v,
                    onClick = { vm.setLauncherVariant(v) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Language", color = Color(0xCCBFC4D6), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UiLanguage.values().forEach { u ->
                ChipChoice(
                    label = u.label,
                    selected = lang == u,
                    onClick = { vm.setUiLanguage(u) },
                )
            }
        }
    }
}

@Composable
internal fun PlaybackAndPlayerCard(vm: AppViewModel) {
    val ext by vm.externalPlayerPkg.collectAsState()
    val buf by vm.playerBufferSeconds.collectAsState()
    val auto by vm.autoSkipOffline.collectAsState()
    val speed by vm.playbackSpeed.collectAsState()
    val boost by vm.audioBoostPercent.collectAsState()
    val subtitleScale by vm.subtitleScalePercent.collectAsState()
    val fastStart by vm.fastStart.collectAsState()
    val sortMode by vm.sortMode.collectAsState()

    var localExt by remember(ext) { mutableStateOf(ext) }

    Card("Playback & player") {
        Text("External player package (e.g. org.videolan.vlc)",
            color = Color(0xCCBFC4D6), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = localExt,
            onValueChange = { localExt = it },
            singleLine = true,
            placeholder = { Text("Leave empty to use built-in player") },
            colors = textFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        PrimaryButton(text = "Save external player") { vm.setExternalPlayerPkg(localExt) }

        Spacer(Modifier.height(10.dp))
        Text("Buffer: ${buf}s", color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(10, 20, 30, 60).forEach { s ->
                ChipChoice(label = "${s}s", selected = buf == s) { vm.setPlayerBufferSeconds(s) }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("Default playback speed", color = Color(0xCCBFC4D6), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                ChipChoice(label = "${s}x", selected = speed == s) { vm.setPlaybackSpeed(s) }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("Audio boost: ${boost}%", color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(100, 125, 150, 175, 200).forEach { s ->
                ChipChoice(label = "${s}%", selected = boost == s) { vm.setAudioBoostPercent(s) }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("Subtitle size: ${subtitleScale}%", color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(75, 100, 125, 150, 200).forEach { s ->
                ChipChoice(label = "${s}%", selected = subtitleScale == s) { vm.setSubtitleScalePercent(s) }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = auto,
                onCheckedChange = { vm.setAutoSkipOffline(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.size(8.dp))
            Text("Auto-skip offline channels on playback error", color = Color.White)
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = fastStart,
                onCheckedChange = { vm.setFastStart(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.size(8.dp))
            Text("Fast channel start (lower buffer for instant playback)", color = Color.White)
        }

        Spacer(Modifier.height(10.dp))
        Text("Live TV sort", color = Color(0xCCBFC4D6), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SortMode.values().forEach { s ->
                ChipChoice(label = s.label, selected = sortMode == s) { vm.setSortMode(s) }
            }
        }
    }
}

@Composable
internal fun SecurityCard(vm: AppViewModel) {
    val pinHash by vm.pinHash.collectAsState()
    val locked by vm.lockedGroups.collectAsState()
    var pin by remember { mutableStateOf("") }
    var addGroup by remember { mutableStateOf("") }

    Card("Adult / lock") {
        Text(
            if (pinHash.isBlank()) "No PIN set. Set a 4-digit code to lock channel groups."
            else "PIN is set. Use the field below to clear or replace it (empty + Save = clear).",
            color = Color(0xCCBFC4D6),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8) },
            singleLine = true, placeholder = { Text("PIN (digits)") },
            colors = textFieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        PrimaryButton(text = "Save PIN") { vm.setPin(pin); pin = "" }

        Spacer(Modifier.height(10.dp))
        Text("Locked groups (${locked.size})", color = Color.White, style = MaterialTheme.typography.titleMedium)
        if (locked.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Column {
                locked.forEach { g ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g, color = Color(0xCCBFC4D6), modifier = Modifier.weight(1f))
                        Text(
                            "Remove",
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .clickable { vm.setLockedGroups(locked - g) }
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = addGroup, onValueChange = { addGroup = it },
            singleLine = true, placeholder = { Text("Group name to lock (e.g. Adult)") },
            colors = textFieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        PrimaryButton(text = "Lock group") {
            if (addGroup.isNotBlank()) {
                vm.setLockedGroups(locked + addGroup); addGroup = ""
            }
        }
    }
}

@Composable
internal fun IntegrationsCard(vm: AppViewModel) {
    val tmdb by vm.tmdbKey.collectAsState()
    val trakt by vm.traktKey.collectAsState()
    var tmdbLocal by remember(tmdb) { mutableStateOf(tmdb) }
    var traktLocal by remember(trakt) { mutableStateOf(trakt) }

    Card("Integrations (optional)") {
        Text(
            "TMDB enriches movies with poster, plot, rating, cast. Trakt scrobbles your VOD watches. Both work offline if you skip the keys.",
            color = Color(0xCCBFC4D6),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text("TMDB API v3 key", color = Color(0xCCBFC4D6), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = tmdbLocal, onValueChange = { tmdbLocal = it },
            singleLine = true, placeholder = { Text("e.g. 0123abc…") },
            colors = textFieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        PrimaryButton(text = "Save TMDB key") { vm.setTmdbKey(tmdbLocal) }

        Spacer(Modifier.height(10.dp))
        Text("Trakt.tv client ID", color = Color(0xCCBFC4D6), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = traktLocal, onValueChange = { traktLocal = it },
            singleLine = true, placeholder = { Text("e.g. 0123abc…") },
            colors = textFieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        PrimaryButton(text = "Save Trakt key") { vm.setTraktKey(traktLocal) }
    }
}

@Composable
internal fun NotificationsCard(vm: AppViewModel) {
    val epgNotif by vm.notificationsEpg.collectAsState()
    val crash by vm.crashReporter.collectAsState()
    val onboarded by vm.onboarded.collectAsState()

    Card("Notifications & diagnostics") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = epgNotif,
                onCheckedChange = { vm.setNotificationsEpg(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.size(8.dp))
            Text("EPG: notify when a favorite channel programme starts", color = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = crash,
                onCheckedChange = { vm.setCrashReporter(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.size(8.dp))
            Text("Crash reporter (writes to local file, opt-in)", color = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        if (onboarded) {
            PrimaryButton(text = "Reset onboarding tutorial") { vm.resetOnboarding() }
        }
    }
}

@Composable
internal fun BackupCard(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = vm.importSettings(context, uri)
                Toast.makeText(
                    context,
                    if (ok) "Backup imported" else "Import failed",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    Card("Backup & restore") {
        Text(
            "Export your playlists, settings, favorites and watchlist to a JSON file you can re-import on another device.",
            color = Color(0xCCBFC4D6),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(text = "Export") {
                scope.launch {
                    val uri = vm.exportSettings(context) ?: return@launch
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(Intent.createChooser(intent, "Share backup")) }
                }
            }
            PrimaryButton(text = "Import") { pickImport.launch(arrayOf("application/json", "text/*")) }
        }
    }
}

@Composable
internal fun UpdateCard(vm: AppViewModel) {
    val tag by vm.updateLatestTag.collectAsState()
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    Card("Updates") {
        Text(
            if (tag.isBlank()) "No newer release detected." else "Newer release available: $tag",
            color = Color(0xCCBFC4D6),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(text = "Check for updates") {
                vm.checkForUpdates { result ->
                    val msg = when {
                        result == null -> "Check failed (no network?)"
                        result.updateAvailable -> "New version available: ${result.latestTag}"
                        else -> "You are on the latest version."
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
            if (tag.isNotBlank()) {
                PrimaryButton(text = "Open releases") {
                    runCatching {
                        uri.openUri("https://github.com/piashmsuf-eng/tv-apk/releases")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = if (selected)
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primary,
                labelColor = Color.White,
            )
        else AssistChipDefaults.assistChipColors(
            containerColor = Color(0xFF131730),
            labelColor = Color(0xCCBFC4D6),
        ),
    )
}
