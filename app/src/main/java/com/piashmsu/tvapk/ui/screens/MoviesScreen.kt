package com.piashmsu.tvapk.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val watchlist by vm.watchlist.collectAsState()
    val watchCounter by vm.watchCounter.collectAsState()

    var selectedGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var yearChip by rememberSaveable { mutableStateOf<String?>(null) }
    var langChip by rememberSaveable { mutableStateOf<String?>(null) }
    var showWatchlist by rememberSaveable { mutableStateOf(false) }

    val genres = remember(movies) {
        movies.map { it.genre.ifBlank { "Other" } }.distinct().sorted()
    }
    val years = remember(movies) {
        movies.mapNotNull { it.year }.toSet().sortedDescending().map { it.toString() }
    }
    val languages = remember(movies) {
        movies.map { it.language }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val filtered = remember(movies, query, selectedGenre, yearChip, langChip, showWatchlist, watchlist) {
        movies.asSequence()
            .filter {
                if (query.isNotBlank()) {
                    it.title.contains(query, true) ||
                        (it.description?.contains(query, true) == true) ||
                        it.genre.contains(query, true)
                } else true
            }
            .filter { selectedGenre == null || it.genre == selectedGenre }
            .filter { yearChip == null || it.year?.toString() == yearChip }
            .filter { langChip == null || it.language == langChip }
            .filter { !showWatchlist || it.id in watchlist }
            .toList()
    }

    val trending = remember(movies, watchCounter) {
        movies.asSequence()
            .map { it to (watchCounter["mv:${it.id}"] ?: 0) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(10)
            .map { it.first }
            .toList()
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

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search movies, cast, descriptions…") },
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
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )

        // Filter chips: watchlist + genre row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                GenreChip(
                    label = "Watchlist",
                    selected = showWatchlist,
                    onClick = { showWatchlist = !showWatchlist },
                )
            }
            item {
                GenreChip(label = "All genres", selected = selectedGenre == null) {
                    selectedGenre = null
                }
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

        if (years.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    GenreChip(label = "All years", selected = yearChip == null) { yearChip = null }
                }
                listItems(years) { y ->
                    GenreChip(label = y, selected = yearChip == y) {
                        yearChip = if (yearChip == y) null else y
                    }
                }
            }
        }
        if (languages.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    GenreChip(label = "All languages", selected = langChip == null) { langChip = null }
                }
                listItems(languages) { l ->
                    GenreChip(label = l, selected = langChip == l) {
                        langChip = if (langChip == l) null else l
                    }
                }
            }
        }

        if (trending.isNotEmpty() && !showWatchlist && query.isBlank() && selectedGenre == null) {
            Text(
                "Trending in your library",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listItems(trending) { m ->
                    Box(modifier = Modifier.height(220.dp)) {
                        MovieCard(
                            title = m.title,
                            poster = m.poster,
                            subtitle = "${watchCounter["mv:${m.id}"] ?: 0}× watched",
                            onClick = { onMovieTap(m) },
                        )
                    }
                }
            }
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
            EmptyState(
                title = if (showWatchlist) "Watchlist is empty" else "Nothing here",
                body = if (showWatchlist) "Tap the bookmark on any movie to save it for later."
                    else "Try a different genre, year, or language.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(filtered, key = { it.id }) { m ->
                    val saved = m.id in watchlist
                    Row(verticalAlignment = Alignment.Top) {
                        MovieCard(
                            title = m.title,
                            poster = m.poster,
                            subtitle = listOfNotNull(
                                m.year?.toString(),
                                m.language.takeIf { it.isNotBlank() },
                                if (saved) "★ Watchlist" else null,
                            ).joinToString(" • "),
                            onClick = { onMovieTap(m) },
                            onLongPress = { vm.toggleWatchlist(m.id) },
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}
