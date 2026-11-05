package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.ImageType

/**
 * IvanaFin home: just the top buttons and the libraries as big tiles filling the screen,
 * two per row in the server's library order (Movies, Shows / Star Wars, Movies Alternative).
 */
@Composable
fun IvanaFinLibraryHome(
    libraries: List<BaseItem>,
    onClickLibrary: (Int, BaseItem) -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focusedIndex by rememberSaveable { mutableStateOf(0) }
    val focusRequesters = remember(libraries.size) { List(libraries.size) { FocusRequester() } }
    LaunchedEffect(libraries.size) {
        focusRequesters.getOrNull(focusedIndex.coerceIn(0, (libraries.size - 1).coerceAtLeast(0)))?.tryRequestFocus()
    }
    Column(modifier = modifier.fillMaxSize()) {
        header()
        BoxWithConstraints(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 20.dp, end = 16.dp),
        ) {
            val columns = 2
            val rows = ((libraries.size + columns - 1) / columns).coerceAtLeast(1)
            val gap = 24.dp
            val tileWidth = (maxWidth - gap * (columns - 1)) / columns
            val tileHeight = (maxHeight - gap * (rows - 1)) / rows
            Column(
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.fillMaxSize(),
            ) {
                libraries.chunked(columns).forEachIndexed { rowIndex, rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        rowItems.forEachIndexed { columnIndex, library ->
                            val index = rowIndex * columns + columnIndex
                            LibraryTile(
                                library = library,
                                width = tileWidth,
                                height = tileHeight,
                                onClick = {
                                    focusedIndex = index
                                    onClickLibrary.invoke(index, library)
                                },
                                modifier =
                                    Modifier
                                        .focusRequester(focusRequesters[index])
                                        .onFocusChanged { if (it.isFocused) focusedIndex = index },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTile(
    library: BaseItem,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val imageUrl =
        remember(library, width, height) {
            library.imageUrlOverride
                ?: imageUrlService.getItemImageUrl(
                    library,
                    ImageType.PRIMARY,
                    fillWidth = with(density) { width.roundToPx() },
                    fillHeight = with(density) { height.roundToPx() },
                )
        }
    var imageError by remember(imageUrl) { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape),
        // Big tiles only grow a little so they don't run into each other
        scale = CardDefaults.scale(focusedScale = 1.04f),
        border =
            CardDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
            ),
        modifier = modifier.size(width, height),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!imageError && imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = library.name,
                    contentScale = ContentScale.Crop,
                    onError = { imageError = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = library.name ?: "",
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
