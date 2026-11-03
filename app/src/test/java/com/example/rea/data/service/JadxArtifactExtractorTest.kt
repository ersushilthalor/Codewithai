package com.example.rea.data.service

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class JadxArtifactExtractorTest {

    private fun createZip(vararg entries: Pair<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((path, content) in entries) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun testPriority_sourcesAndManifestPrioritizedOverResources() {
        // Zip where resources/ are entered before sources/ and AndroidManifest.xml
        val zipBytes = createZip(
            "resources/res/values/strings.xml" to "<resources><string name=\"app\">Test</string></resources>",
            "resources/res/layout/activity_main.xml" to "<LinearLayout></LinearLayout>",
            "sources/com/example/MainActivity.java" to "package com.example;\npublic class MainActivity {}",
            "resources/AndroidManifest.xml" to "<manifest package=\"com.example\"></manifest>"
        )

        val result = JadxArtifactExtractor.extract(ByteArrayInputStream(zipBytes))
        val paths = result.files.map { it.path }

        // AndroidManifest.xml must be first (priority 0)
        assertEquals("resources/AndroidManifest.xml", paths[0])
        // sources/ must come before secondary resources
        assertEquals("sources/com/example/MainActivity.java", paths[1])
        assertTrue(paths.contains("resources/res/values/strings.xml"))
        assertTrue(paths.contains("resources/res/layout/activity_main.xml"))
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun testCapacityConstraint_skipsResourcesBeforeSources() {
        // Small budget that only fits 2 files
        val zipBytes = createZip(
            "resources/res/layout/huge_layout.xml" to "A".repeat(500),
            "sources/com/example/Core.java" to "B".repeat(200),
            "resources/AndroidManifest.xml" to "C".repeat(100)
        )

        // Set maxBytes = 400 bytes so only AndroidManifest (100) and Core.java (200) fit
        val result = JadxArtifactExtractor.extract(
            inputStream = ByteArrayInputStream(zipBytes),
            maxBytes = 400L,
            maxFiles = 10
        )

        assertEquals(2, result.files.size)
        assertEquals("resources/AndroidManifest.xml", result.files[0].path)
        assertEquals("sources/com/example/Core.java", result.files[1].path)
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun testLanguageDetectionAndHeader() {
        val zipBytes = createZip(
            "sources/com/example/Screen.kt" to "class Screen",
            "sources/com/example/Helper.java" to "class Helper {}",
            "resources/AndroidManifest.xml" to "<manifest></manifest>"
        )

        val result = JadxArtifactExtractor.extract(ByteArrayInputStream(zipBytes))
        val ktFile = result.files.first { it.path.endsWith(".kt") }
        val javaFile = result.files.first { it.path.endsWith(".java") }
        val xmlFile = result.files.first { it.path.endsWith(".xml") }

        assertEquals("kotlin", ktFile.language)
        assertEquals("java", javaFile.language)
        assertEquals("xml", xmlFile.language)

        assertTrue(ktFile.content.startsWith("// Decompiled by JADX"))
        assertTrue(ktFile.content.contains("class Screen"))
    }

    @Test
    fun testBinaryFilesIgnored() {
        val zipBytes = createZip(
            "resources/res/drawable/icon.png" to "\u0089PNG\r\n\u001a\n",
            "sources/com/example/Util.java" to "public class Util {}"
        )

        val result = JadxArtifactExtractor.extract(ByteArrayInputStream(zipBytes))
        assertEquals(1, result.files.size)
        assertEquals("sources/com/example/Util.java", result.files[0].path)
        assertEquals(0, result.skippedCount)
    }
}
