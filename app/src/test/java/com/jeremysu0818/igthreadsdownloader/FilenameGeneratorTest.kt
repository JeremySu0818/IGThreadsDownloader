package com.jeremysu0818.igthreadsdownloader

import com.jeremysu0818.igthreadsdownloader.domain.download.FilenameGenerator
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaItemType
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaPlatform
import org.junit.Assert.assertEquals
import org.junit.Test

class FilenameGeneratorTest {
    @Test
    fun stableFilenameSanitizesAuthorAndKeepsExtension() {
        val filename = FilenameGenerator.generate(
            platform = MediaPlatform.INSTAGRAM,
            author = "je re.my",
            shortcode = "AbC_123",
            index = 0,
            type = MediaItemType.IMAGE,
            mediaUrl = "https://cdninstagram.com/media/original.webp?token=x",
        )

        assertEquals("instagram_je_re_my_AbC_123_01.webp", filename)
    }

    @Test
    fun occupiedFilenameGetsDeterministicSuffix() {
        val first = "threads_author_Code_01.mp4"
        val filename = FilenameGenerator.generate(
            platform = MediaPlatform.THREADS,
            author = "author",
            shortcode = "Code",
            index = 0,
            type = MediaItemType.VIDEO,
            mediaUrl = "https://cdn.example/media",
            mimeType = "video/mp4",
            occupiedNames = setOf(first, "threads_author_Code_01_2.mp4"),
        )

        assertEquals("threads_author_Code_01_3.mp4", filename)
    }

    @Test
    fun serverMimeTypeTakesPriorityOverMisleadingUrlExtension() {
        assertEquals(
            "jpg",
            FilenameGenerator.extensionFor(
                mediaUrl = "https://cdn.example/media.webp?stp=dst-jpg",
                mimeType = "image/jpeg",
                type = MediaItemType.IMAGE,
            ),
        )
    }
}
