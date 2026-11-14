package com.github.damontecres.wholphin.data.model

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.extensions.ticks
import java.util.UUID
import kotlin.time.Duration

/**
 * Represents a chapter within a video
 */
data class Chapter(
    val itemId: UUID,
    val name: String?,
    val position: Duration,
    val tag: String?,
    val index: Int,
) {
    companion object {
        private val CHINESE_CHAPTER = Regex("""^\s*第\s*(\d+)\s*章\s*$""")

        fun fromDto(dto: BaseItemDto): List<Chapter> =
            dto.chapters
                ?.mapIndexed { index, chapter ->
                    Chapter(
                        itemId = dto.id,
                        // IvanaFin: some files name chapters "第 01 章" (Chinese "Chapter 01") - Twin Peaks FWWM, beta round 6
                        name = chapter.name?.let { n -> CHINESE_CHAPTER.matchEntire(n)?.let { "Chapter ${it.groupValues[1]}" } ?: n },
                        position = chapter.startPositionTicks.ticks,
                        tag = chapter.imageTag,
                        index = index,
                    )
                }?.sortedBy { it.position }
                .orEmpty()
    }
}
