package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.ui.letNotEmpty
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gets people in media, specifically to check if they are favorited or not
 */
@Singleton
class PeopleFavorites
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
    ) {
        suspend fun getPeopleFor(item: BaseItem): List<Person> =
            item.data.people.orEmpty().map { it.id }.chunked(50).let { chunks ->
                val favorites =
                    chunks
                        .map {
                            api.libraryApi
                                .getItems(ids = it)
                                .content.items
                        }.flatten()
                        .associateBy({ it.id }, { it.userData?.isFavorite ?: false })
                val people =
                    item.data.people
                        ?.letNotEmpty { people ->
                            people.map {
                                Person.fromDto(
                                    context,
                                    it,
                                    favorites[it.id] ?: false,
                                    api,
                                )
                            }
                        }.orEmpty()
                // IvanaFin: one card per person - someone with several roles (Lauren Tom x3 in Batman Beyond,
                // Coodie Simmons narrator + producer) had a card for each; their roles are joined (beta round 6)
                people
                    .groupBy { it.id }
                    .values
                    .map { same ->
                        if (same.size == 1) {
                            same[0]
                        } else {
                            same[0].copy(role = same.mapNotNull { p -> p.role?.takeIf { r -> r.isNotBlank() } }.distinct().joinToString(" / ").ifBlank { null })
                        }
                    }
            }
    }
