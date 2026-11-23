package com.github.damontecres.wholphin.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.NavigationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * IvanaFin (round-7 beta): a Discover title that Seerr calls "available" opens the library item by the
 * Jellyfin id Seerr remembers. When that item was since removed from Jellyfin the page showed a red
 * "Invalid HTTP status in response: 404" error. Check the item first; if it's gone, show the title's
 * Discover page in its place instead.
 */
@HiltViewModel
class DiscoverItemGuardViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        suspend fun exists(itemId: UUID): Boolean =
            try {
                api.libraryApi.getItem(userId = serverRepository.currentUser?.id, itemId = itemId)
                true
            } catch (e: Exception) {
                Timber.w(e, "Discover: library item %s not found, showing the Discover page", itemId)
                false
            }

        /** Swap the current (top) page for [destination], so Back still goes where it did. */
        fun swapTop(destination: Destination) {
            val stack = navigationManager.backStack
            if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
            stack.add(destination)
        }
    }

@Composable
fun DiscoverItemGuard(
    destination: Destination.MediaItem,
    content: @Composable () -> Unit,
) {
    val discover = destination.discover
    if (discover == null) {
        content()
        return
    }
    val viewModel: DiscoverItemGuardViewModel = hiltViewModel(key = "guard-" + destination.itemId)
    var ok by remember(destination) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(destination) {
        val found = viewModel.exists(destination.itemId)
        if (found) ok = true else viewModel.swapTop(Destination.DiscoveredItem(discover))
    }
    if (ok == true) content() else Box(Modifier.fillMaxSize())
}
