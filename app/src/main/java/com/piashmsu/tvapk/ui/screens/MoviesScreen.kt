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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.piashmsu.tvapk.data.LoadState
import com.piashmsu.tvapk.data.Movie
import com.piashmsu.tvapk.ui.AppViewModel
import com.piashmsu.tvapk.ui.components.EmptyState
import com.piashmsu.tvapk.ui.components.GenreChip
import com.piashmsu.tvapk.ui.components.MovieCard

@Composable
fun MoviesScreen(onMovieTap: (Movie) -> Unit) {
    val vm: AppViewModel = viewModel(factory = AppViewModel.Factory)
    val movies by vm.movies.collectAsState()
    val state by vm.movieState.collectAsState()
    val movieUrl by vm.movieCatalogUrl.collectAsState()
    val movieFavorites by vm.movieFavorites.collectAsState()
    val movieProgress by vm.movieProgress.collectAsState()

    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    val genres = remember(movies) {
        movies.map { it.genre.ifBlank { "Other" } }.distinct().sorted()
    }
    val filtered = remember(movies, selectedGenre, favoritesOnly, movieFavorites) {
        movies.asSequence()
            .filter { selectedGenre == null || it.genre == selectedGenre }
            .filter { !favoritesOnly || it.id in movieFavorites }
            .toList()
    }

    val continueWatching = remember(movies, movieProgress) {
        movies.mapNotNull { m -> movieProgress[m.id]?.let { p -> m to p } }
            .filter { (_, p) -> p.isInProgress }
            .sortedByDescending { (_, p) -> p.updatedAt }
            .take(12)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopRow(
            title = "Movies",
            subtitle = if (movies.isEmpty()) "No movies yet" else "${movies.size} titles",
            onRefresh = { vm.refreshMovies() },
            isRefreshing = state is LoadState.Loading,
        )

        if (movieUrl.isBlank()) {
            EmptyState(
                title = "No catalog configured",
                body = "Add a movie catalog JSON URL in Settings to populate this tab.",
            )
            return
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = { favoritesOnly = !favoritesOnly },
                label = { Text(if (favoritesOnly) "★ Favorites" else "All") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (favoritesOnly) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                        else Color(0x33FFFFFF),
                    labelColor = Color.White,
                ),
            )
            if (continueWatching.isNotEmpty()) {
                AssistChip(
                    onClick = { },
                    enabled = false,
                    label = { Text("${continueWatching.size} in progress") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0x33FFFFFF),
                        disabledContainerColor = Color(0x33FFFFFF),
                        disabledLabelColor = Color.White,
                    ),
                )
            }
        }

        if (genres.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    GenreChip(label = "All", selected = selectedGenre == null) { selectedGenre = null }
                }
                listItems(genres) { genre ->
                    GenreChip(
                        label = genre,
                        selected = selectedGenre == genre,
                    ) {
                        selectedGenre = if (selectedGenre == genre) null else genre
                    }
                }
            }
        }

        if (continueWatching.isNotEmpty() && !favoritesOnly && selectedGenre == null) {
            Text(
                "Continue watching",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listItems(continueWatching) { (m, p) ->
                    ContinueWatchingCard(
                        title = m.title,
                        poster = m.poster,
                        fraction = p.fraction,
                        onClick = { onMovieTap(m) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (state is LoadState.Loading && movies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
            }
        } else if (state is LoadState.Error && movies.isEmpty()) {
            EmptyState(
                title = "Couldn't load movies",
                body = (state as LoadState.Error).message,
                actionLabel = "Try again",
                onAction = { vm.refreshMovies() },
            )
        } else if (filtered.isEmpty()) {
            EmptyState(title = "Nothing here", body = "Try a different genre or clear the favorites filter.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(filtered) { m ->
                    Box {
                        MovieCard(
                            title = m.title,
                            poster = m.poster,
                            subtitle = listOfNotNull(
                                m.year?.toString(),
                                m.language.takeIf { it.isNotBlank() },
                            ).joinToString(" • "),
                            onClick = { onMovieTap(m) },
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                                .clip(CircleShape)
                                .background(Color(0x99000000))
                                .clickable { vm.toggleMovieFavorite(m.id) }
                                .padding(6.dp),
                        ) {
                            Icon(
                                imageVector = if (m.id in movieFavorites) Icons.Filled.Star
                                    else Icons.Outlined.StarBorder,
                                contentDescription = if (m.id in movieFavorites) "Remove from favorites"
                                    else "Add to favorites",
                                tint = if (m.id in movieFavorites) MaterialTheme.colorScheme.tertiary
                                    else Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        movieProgress[m.id]?.takeIf { it.isInProgress }?.let { p ->
                            LinearProgressIndicator(
                                progress = { p.fraction },
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                                    .fillMaxWidth(0.85f),
                                color = MaterialTheme.colorScheme.tertiary,
                                trackColor = Color(0x66000000),
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    title: String,
    poster: String?,
    fraction: Float,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1A1F46)),
            contentAlignment = Alignment.Center,
        ) {
            if (!poster.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = poster,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color(0xCC0B0E22))
                    .padding(10.dp),
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = Color(0x66000000),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${(fraction * 100).toInt()}% watched",
            color = Color(0xCCBFC4D6),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
