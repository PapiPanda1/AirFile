package com.airfile.tv

import com.airfile.tv.net.UploadUrl
import com.airfile.tv.storage.FileNames
import com.airfile.tv.ui.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {
    @Test fun stripsPathTraversal() {
        assertEquals("passwd", FileNames.sanitize("../../etc/passwd"))
        assertEquals("evil.exe", FileNames.sanitize("C:\\Windows\\evil.exe"))
    }

    @Test fun neverReturnsEmptyOrDotNames() {
        assertEquals("file", FileNames.sanitize(""))
        assertEquals("file", FileNames.sanitize(".."))
        assertEquals("file", FileNames.sanitize("   "))
    }

    @Test fun replacesReservedCharacters() {
        assertEquals("a_b_c.txt", FileNames.sanitize("a<b>c.txt"))
    }

    @Test fun keepsExtensionWhenTruncating() {
        val name = FileNames.sanitize("x".repeat(500) + ".mp4")
        assertEquals(200, name.length)
        assertTrue(name.endsWith(".mp4"))
    }

    @Test fun uniqueNameAddsCounter() {
        val taken = setOf("movie.mp4", "movie (1).mp4")
        assertEquals("movie (2).mp4", FileNames.uniqueName("movie.mp4") { it in taken })
        assertEquals("other.mp4", FileNames.uniqueName("other.mp4") { it in taken })
        assertEquals("README (1)", FileNames.uniqueName("README") { it == "README" })
    }
}

class UrlAndFormatTest {
    @Test fun buildsUploadUrl() {
        assertEquals("http://192.168.1.20:8080/?t=abc", UploadUrl.build("192.168.1.20", 8080, "abc"))
    }

    @Test fun formatsSizes() {
        assertEquals("512 B", Format.humanSize(512))
        assertEquals("1.0 KB", Format.humanSize(1024))
        assertEquals("1.5 MB", Format.humanSize(1536 * 1024L))
        assertEquals("2.0 GB", Format.humanSize(2L * 1024 * 1024 * 1024))
    }
}
