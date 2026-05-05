package com.piashmsu.tvapk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.piashmsu.tvapk.R
import com.piashmsu.tvapk.data.Channel
import com.piashmsu.tvapk.data.Movie
import com.piashmsu.tvapk.ui.AppViewModel
import com.piashmsu.tvapk.ui.components.ChannelTile
import com.piashmsu.tvapk.ui.components.EmptyState
import com.piashmsu.tvapk.ui.components.MovieCard
import com.piashmsu.tvapk.ui.components.SectionHeader
import com.piashmsu.tvapk.ui.theme.GradientHero

@Composable
fun HomeScreen(
    onChannelTap: (Channel) -> Unit,
    onMovieTap: (Movie) -> Unit,
    onTabRequest: (String) -> Unit,
) {
    val vm: AppViewModel = viewModel(factory = AppViewModel.Factory)
    val channels by vm.channels.collectAsState()
    val movies by vm.movies.collectAsState()
    val playlistSources by vm.playlistSources.collectAsState()
    val movieUrl by vm.movieCatalogUrl.collectAsState()
    val recents by vm.recents.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val epg by vm.epg.collectAsState()

    val featured = remember(movies) {
        movies.firstOrNull { !it.backdrop.isNullOrBlank() } ?: movies.firstOrNull()
    }
    val isOnboarding = playlistSources.isEmpty() && movieUrl.isBlank()

    // Tick once per minute so "now playing" titles refresh as programmes end.
    var nowMinute by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000L) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            nowMinute = System.currentTimeMillis() / 60_000L
        }
    }
    val nowByTvgId = remember(epg, nowMinute) {
        val nowTs = nowMinute * 60_000L
        epg.mapValues { (_, list) ->
            list.firstOrNull { p -> nowTs in p.start..p.end }?.title
        }
    }

    val recentChannels = remember(recents, channels) {
        recents.asSequence()
            .mapNotNull { rc -> channels.firstOrNull { it.id == rc.channelId } }
            .take(10)
            .toList()
    }

    val topChannels = remember(channels) { channels.take(20) }

    val groupedMovies = remember(movies) {
        movies.groupBy { it.genre.ifBlank { "Other" } }
            .toList() // stable order, list of pairs avoids map iteration in compose loop
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "brand") { TopBrandBar() }
        if (isOnboarding) {
            item(key = "onboard") {
                EmptyState(
                    title = "Welcome to TV APK",
                    body = "Add your IPTV M3U playlist URL or pick an .m3u file from your device in Settings to start watching live channels and movies.",
                    actionLabel = "Open Settings",
                    onAction = { onTabRequest("settings") },
                )
            }
            item(key = "dev") { DeveloperBadge() }
            return@LazyColumn
        }

        if (featured != null) {
            item(key = "hero_movie") { Hero(featured = featured, onPlay = { onMovieTap(featured) }) }
        } else if (channels.isNotEmpty()) {
            item(key = "hero_live") {
                LiveHero(
                    channelCount = channels.size,
                    spotlight = channels.firstOrNull { !it.logo.isNullOrBlank() } ?: channels.first(),
                    onWatch = { onTabRequest("live") },
                )
            }
        }

        item(key = "quick") {
            QuickActionRow(
                onLive = { onTabRequest("live") },
                onMovies = { onTabRequest("movies") },
                onSettings = { onTabRequest("settings") },
            )
        }

        if (recentChannels.isNotEmpty()) {
            item(key = "rec_h") {
                SectionHeader(
                    title = "Recently watched",
                    subtitle = "Pick up where you left off",
                )
            }
            item(key = "rec_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(recentChannels, key = { it.id }) { ch ->
                        ChannelTile(
                            name = ch.name,
                            logo = ch.logo,
                            group = ch.country ?: ch.language ?: ch.group,
                            isFavorite = ch.id in favorites,
                            nowPlayingTitle = nowByTvgId[ch.tvgId.orEmpty()],
                            onClick = { onChannelTap(ch) },
                            onFavorite = { vm.toggleFavorite(ch.id) },
                        )
                    }
                }
            }
        }

        if (topChannels.isNotEmpty()) {
            item(key = "live_h") {
                SectionHeader(
                    title = "Live channels",
                    subtitle = "${channels.size} channel${if (channels.size != 1) "s" else ""} loaded",
                )
            }
            item(key = "live_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(topChannels, key = { it.id }) { ch ->
                        ChannelTile(
                            name = ch.name,
                            logo = ch.logo,
                            group = ch.group,
                            isFavorite = ch.id in favorites,
                            nowPlayingTitle = nowByTvgId[ch.tvgId.orEmpty()],
                            onClick = { onChannelTap(ch) },
                            onFavorite = { vm.toggleFavorite(ch.id) },
                        )
                    }
                }
            }
        }

        groupedMovies.forEach { (genre, list) ->
            item(key = "g_$genre") { SectionHeader(title = genre) }
            item(key = "g_row_$genre") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(list, key = { it.id }) { m ->
                        MovieCard(
                            title = m.title,
                            poster = m.poster,
                            subtitle = listOfNotNull(m.year?.toString(), m.language.takeIf { it.isNotBlank() }).joinToString(" • "),
                            onClick = { onMovieTap(m) },
                        )
                    }
                }
            }
        }

        item(key = "dev") { DeveloperBadge() }
    }
}

@Composable
private fun TopBrandBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Animated gradient logo — slow spin via hue-shift in the brush
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(GradientHero),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.LiveTv,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.app_name),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "Live TV • Hindi & Bangla • Movies",
                color = Color(0xCCBFC4D6),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        // Vibe badge — purely decorative neon chip.
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0x33A855F7))
                .border(1.dp, Color(0x66A855F7), CircleShape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                "Vibe",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun Hero(featured: Movie, onPlay: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(240.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A0F3A), Color(0xFF0B0E22))
                )
            )
            .border(1.dp, Color(0x33A855F7), RoundedCornerShape(28.dp))
            .clickable(onClick = onPlay),
    ) {
        if (!featured.backdrop.isNullOrBlank()) {
            AsyncImage(
                model = featured.backdrop,
                contentDescription = featured.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (!featured.poster.isNullOrBlank()) {
            AsyncImage(
                model = featured.poster,
                contentDescription = featured.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC050616), Color(0xFF050616))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
        ) {
            Text(
                "Featured",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                featured.title,
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GradientHero)
                        .clickable(onClick = onPlay)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Watch now", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    listOfNotNull(
                        featured.year?.toString(),
                        featured.language.takeIf { it.isNotBlank() },
                        featured.durationMinutes?.let { "${it}m" },
                    ).joinToString(" • "),
                    color = Color(0xCCBFC4D6),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/**
 * Live-TV hero shown when there are channels but no movies. The whole
 * hero is one big "Watch live" CTA that drops you on the Live TV tab.
 * A slow-rotating neon gradient runs in the background to give the
 * surface a "vibe-edition" feel even when there's no artwork to show.
 */
@Composable
private fun LiveHero(channelCount: Int, spotlight: Channel, onWatch: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(220.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF2A0F4A), Color(0xFF0E1E3D), Color(0xFF0B0E22))
                )
            )
            .border(1.dp, Color(0x44A855F7), RoundedCornerShape(28.dp))
            .clickable(onClick = onWatch),
    ) {
        // Decorative oversized logo bleed.
        if (!spotlight.logo.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = spotlight.logo,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        // Soft fade so the logo doesn't fight the title.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xEE0B0E22), Color(0x880B0E22), Color.Transparent)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text("LIVE NOW", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    "$channelCount channels",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "World TV at your fingertips",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GradientHero)
                    .clickable(onClick = onWatch)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Watch live", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun QuickActionRow(
    onLive: () -> Unit,
    onMovies: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickAction(
            label = "Live TV",
            icon = Icons.Outlined.LiveTv,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            onClick = onLive,
        )
        QuickAction(
            label = "Movies",
            icon = Icons.Outlined.Movie,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
            onClick = onMovies,
        )
        QuickAction(
            label = "Settings",
            icon = Icons.Outlined.Settings,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f),
            onClick = onSettings,
        )
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF161A36), Color(0xFF0B0E22))
                )
            )
            .border(1.dp, tint.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun DeveloperBadge() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Developed by ${stringResource(R.string.developer_name)}",
            color = Color(0xAABFC4D6),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            stringResource(R.string.developer_handle),
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
