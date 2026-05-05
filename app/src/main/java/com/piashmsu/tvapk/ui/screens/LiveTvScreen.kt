package com.piashmsu.tvapk.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.piashmsu.tvapk.data.Category
import com.piashmsu.tvapk.data.Channel
import com.piashmsu.tvapk.data.ChannelStatus
import com.piashmsu.tvapk.data.LoadState
import com.piashmsu.tvapk.data.ProbeProgress
import com.piashmsu.tvapk.ui.AppViewModel
import com.piashmsu.tvapk.ui.components.ChannelTile
import com.piashmsu.tvapk.ui.components.EmptyState
import com.piashmsu.tvapk.ui.components.GenreChip
import com.piashmsu.tvapk.ui.components.SectionHeader

private const val FAVORITES_GROUP = "★ Favorites"

private enum class LiveTab { Online, Offline, All }

@Composable
fun LiveTvScreen(onChannelTap: (Channel) -> Unit) {
    val vm: AppViewModel = viewModel(factory = AppViewModel.Factory)
    val channels by vm.channels.collectAsState()
    val state by vm.channelState.collectAsState()
    val sources by vm.playlistSources.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val epg by vm.epg.collectAsState()
    val statuses by vm.channelStatuses.collectAsState()
    val probe by vm.probeProgress.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var selectedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(LiveTab.Online) }

    val anyProbed = remember(statuses) { statuses.isNotEmpty() }
    val sortMode by vm.sortMode.collectAsState()
    val filterCountry by vm.filterCountry.collectAsState()
    val filterLanguage by vm.filterLanguage.collectAsState()
    val lockedGroups by vm.lockedGroups.collectAsState()
    val unlockedGroups by vm.unlockedGroups.collectAsState()

    // Tick once per minute so "now playing" titles refresh as programmes end.
    var nowMinute by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000L) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            nowMinute = System.currentTimeMillis() / 60_000L
        }
    }
    // Precompute "now playing" map once per epg refresh / minute tick. Avoids
    // per-tile currentTimeMillis() + linear scan on every recomposition.
    val nowByTvgId = remember(epg, nowMinute) {
        val nowTs = nowMinute * 60_000L
        epg.mapValues { (_, list) ->
            list.firstOrNull { p -> nowTs in p.start..p.end }?.title
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopRow(
            title = "Live TV",
            subtitle = if (channels.isEmpty()) "No channels yet" else "${channels.size} channels",
            onRefresh = { vm.refreshChannels(); vm.refreshEpg() },
            isRefreshing = state is LoadState.Loading,
            onProbe = { vm.probeChannels() },
            isProbing = probe is ProbeProgress.Running,
        )

        if (sources.isEmpty()) {
            EmptyState(
                title = "No playlists configured",
                body = "Add at least one IPTV M3U playlist URL or pick a local file in Settings.",
            )
            return
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search channels…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF111527),
                unfocusedContainerColor = Color(0xFF111527),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0x33BFC4D6),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedPlaceholderColor = Color(0x88BFC4D6),
                unfocusedPlaceholderColor = Color(0x88BFC4D6),
                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                unfocusedLeadingIconColor = Color(0x88BFC4D6),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )

        ProbeBanner(probe = probe, anyProbed = anyProbed, onProbe = { vm.probeChannels() })

        TabRow(tab = tab, anyProbed = anyProbed, onTabChange = { tab = it })

        val baseGroups: List<Category<Channel>> by remember(
            channels, query, statuses, tab, sortMode, filterCountry, filterLanguage,
            favorites, lockedGroups, unlockedGroups,
        ) {
            derivedStateOf {
                vm.channelRepo.groupedByCategory(
                    query = query,
                    hideOffline = anyProbed && tab == LiveTab.Online,
                    onlyOffline = anyProbed && tab == LiveTab.Offline,
                    sortMode = sortMode,
                    countryFilter = filterCountry,
                    languageFilter = filterLanguage,
                    favoriteIds = favorites,
                    lockedGroups = lockedGroups,
                    unlockedGroups = unlockedGroups,
                )
            }
        }
        val favCategory by remember(channels, favorites, query, statuses, tab) {
            derivedStateOf {
                if (tab == LiveTab.Offline) return@derivedStateOf null
                val favList = channels.asSequence()
                    .filter { it.id in favorites }
                    .filter {
                        val matches = query.isBlank() ||
                            it.name.contains(query, true) ||
                            it.group.contains(query, true)
                        if (!matches) return@filter false
                        if (anyProbed && tab == LiveTab.Online) statuses[it.id] != ChannelStatus.Offline
                        else true
                    }
                    .sortedBy { it.name }
                    .toList()
                if (favList.isEmpty()) null else Category(FAVORITES_GROUP, favList)
            }
        }
        val groups = listOfNotNull(favCategory) + baseGroups
        val groupNames = groups.map { it.title }

        if (groupNames.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    GenreChip(
                        label = "All",
                        selected = selectedGroup == null,
                        onClick = { selectedGroup = null },
                    )
                }
                items(groupNames) { name ->
                    GenreChip(
                        label = name,
                        selected = selectedGroup == name,
                        onClick = { selectedGroup = if (selectedGroup == name) null else name },
                    )
                }
            }
        }

        val visibleGroups = if (selectedGroup == null) groups else groups.filter { it.title == selectedGroup }

        if (state is LoadState.Loading && channels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (state is LoadState.Error && channels.isEmpty()) {
            EmptyState(
                title = "Couldn't load channels",
                body = (state as LoadState.Error).message,
                actionLabel = "Try again",
                onAction = { vm.refreshChannels() },
            )
        } else if (visibleGroups.isEmpty()) {
            EmptyState(
                title = if (tab == LiveTab.Offline) "No offline channels yet"
                    else if (tab == LiveTab.Online && anyProbed) "Nothing online here"
                    else "No matches",
                body = if (tab == LiveTab.Offline)
                    "Tap \"Check connectivity\" to see which channels are unreachable."
                else "Try a different search or refresh the playlist.",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visibleGroups.forEach { cat ->
                    item(key = "h-${cat.title}") {
                        SectionHeader(
                            title = cat.title,
                            subtitle = "${cat.items.size} channel${if (cat.items.size != 1) "s" else ""}",
                        )
                    }
                    item(key = "r-${cat.title}") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(cat.items, key = { it.id }) { ch ->
                                val st = statuses[ch.id]
                                val statusColor = when (st) {
                                    ChannelStatus.Online -> Color(0xFF22C55E)
                                    ChannelStatus.Offline -> Color(0xFFEF4444)
                                    null -> null
                                }
                                val statusLabel = when (st) {
                                    ChannelStatus.Online -> "ONLINE"
                                    ChannelStatus.Offline -> "OFFLINE"
                                    null -> null
                                }
                                ChannelTile(
                                    name = ch.name,
                                    logo = ch.logo,
                                    group = ch.country ?: ch.language ?: ch.group,
                                    isFavorite = ch.id in favorites,
                                    nowPlayingTitle = nowByTvgId[ch.tvgId.orEmpty()],
                                    onClick = { onChannelTap(ch) },
                                    onFavorite = { vm.toggleFavorite(ch.id) },
                                    statusColor = statusColor,
                                    statusLabel = statusLabel,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProbeBanner(probe: ProbeProgress, anyProbed: Boolean, onProbe: () -> Unit) {
    when (probe) {
        ProbeProgress.Idle -> if (!anyProbed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x33A855F7))
                    .clickable(onClick = onProbe)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.NetworkCheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Check which channels are online",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        "Probes every channel — offline ones go to the Offline tab.",
                        color = Color(0xCCBFC4D6),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        is ProbeProgress.Running -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x331C8F4D))
                    .padding(12.dp),
            ) {
                Text(
                    "Checking ${probe.done}/${probe.total} channels…",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.size(6.dp))
                LinearProgressIndicator(
                    progress = { (probe.done.toFloat() / probe.total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0x331C8F4D),
                )
            }
        }
        is ProbeProgress.Finished -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x33059669))
                    .clickable(onClick = onProbe)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.NetworkCheck,
                    contentDescription = null,
                    tint = Color(0xFF22C55E),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Online: ${probe.online} • Offline: ${probe.offline}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        "Tap to re-check",
                        color = Color(0xCCBFC4D6),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun TabRow(tab: LiveTab, anyProbed: Boolean, onTabChange: (LiveTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TabPill("Online", tab == LiveTab.Online, enabled = anyProbed) { onTabChange(LiveTab.Online) }
        TabPill("All", tab == LiveTab.All, enabled = true) { onTabChange(LiveTab.All) }
        TabPill("Offline", tab == LiveTab.Offline, enabled = anyProbed) { onTabChange(LiveTab.Offline) }
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val container = if (selected) accent.copy(alpha = 0.22f) else Color(0xFF111527)
    val border = if (selected) accent else Color(0x33BFC4D6)
    val tint = when {
        !enabled -> Color(0x66BFC4D6)
        selected -> accent
        else -> Color.White
    }
    AssistChip(
        onClick = { if (enabled) onClick() },
        enabled = enabled,
        label = { Text(label, color = tint) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = container,
            disabledContainerColor = Color(0x22111527),
            disabledLabelColor = tint,
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = enabled,
            borderColor = border,
            disabledBorderColor = Color(0x22BFC4D6),
        ),
    )
}

@Composable
internal fun TopRow(
    title: String,
    subtitle: String,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    onProbe: (() -> Unit)? = null,
    isProbing: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineLarge)
            Text(subtitle, color = Color(0xCCBFC4D6), style = MaterialTheme.typography.labelMedium)
        }
        if (onProbe != null) {
            IconButton(onClick = onProbe, enabled = !isProbing) {
                if (isProbing) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.secondary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0x33A855F7)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.NetworkCheck,
                            contentDescription = "Check connectivity",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        IconButton(onClick = onRefresh, enabled = !isRefreshing) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    tint = Color.White,
                )
            }
        }
    }
}
