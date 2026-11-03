package com.example.rea.data.service

import com.example.rea.data.model.ProjectFile
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class JadxExtractionResult(
    val files: List<ProjectFile>,
    val skippedCount: Int,
    val totalBytes: Long
)

object JadxArtifactExtractor {

    const val DECOMPILED_HEADER = "// Decompiled by JADX (approximate, may not recompile)\n// Notice: Use only on apps you own or are authorized to analyze.\n\n"
    const val DEFAULT_MAX_BYTES = 35 * 1024 * 1024L // 35 MB total text limit
    const val DEFAULT_MAX_FILES = 3000

    private data class EntryRecord(
        val originalPath: String,
        val tempFile: File,
        val size: Long,
        val priority: Int
    )

    fun isTextFile(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".java") || lower.endsWith(".kt") ||
                lower.endsWith(".xml") || lower.endsWith(".gradle") ||
                lower.endsWith(".gradle.kts") || lower.endsWith(".json") ||
                lower.endsWith(".properties") || lower.endsWith(".txt") ||
                lower.endsWith(".pro") || lower.endsWith(".toml")
    }

    fun getPriority(path: String): Int {
        val normalized = path.replace('\\', '/')
        val fileName = File(normalized).name
        return when {
            fileName.equals("AndroidManifest.xml", ignoreCase = true) -> 0
            normalized.startsWith("sources/") || normalized.endsWith(".java") || normalized.endsWith(".kt") -> 1
            normalized.endsWith(".gradle") || normalized.endsWith(".gradle.kts") || normalized.endsWith(".properties") -> 2
            normalized.startsWith("resources/") -> 3
            else -> 4
        }
    }

    fun detectLanguage(path: String): String {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".kt") -> "kotlin"
            lower.endsWith(".java") -> "java"
            lower.endsWith(".xml") -> "xml"
            lower.endsWith(".gradle") || lower.endsWith(".gradle.kts") -> "gradle"
            lower.endsWith(".json") -> "json"
            lower.endsWith(".toml") -> "toml"
            lower.endsWith(".properties") -> "properties"
            else -> "text"
        }
    }

    /**
     * Extracts text files from JADX zip artifact, prioritizing AndroidManifest.xml and sources/
     * over resources/. Applies memory and count limits, and returns the count of skipped files.
     */
    fun extract(
        inputStream: InputStream,
        maxBytes: Long = DEFAULT_MAX_BYTES,
        maxFiles: Int = DEFAULT_MAX_FILES
    ): JadxExtractionResult {
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: ".", "jadx_ext_${UUID.randomUUID()}").apply { mkdirs() }
        val records = mutableListOf<EntryRecord>()

        try {
            val zis = ZipInputStream(inputStream)
            var entry: ZipEntry? = zis.nextEntry
            var index = 0
            val buffer = ByteArray(8192)

            while (entry != null) {
                if (!entry.isDirectory && isTextFile(entry.name)) {
                    val originalPath = entry.name
                    val tempFile = File(tempDir, "entry_$index.tmp")
                    var entryBytes = 0L

                    FileOutputStream(tempFile).use { fos ->
                        var read: Int
                        while (zis.read(buffer).also { read = it } != -1) {
                            fos.write(buffer, 0, read)
                            entryBytes += read
                        }
                    }

                    // Only consider if <= 2MB for a single text file
                    if (entryBytes <= 2 * 1024 * 1024L) {
                        records.add(
                            EntryRecord(
                                originalPath = originalPath,
                                tempFile = tempFile,
                                size = entryBytes,
                                priority = getPriority(originalPath)
                            )
                        )
                        index++
                    } else {
                        tempFile.delete()
                    }
                }
                entry = zis.nextEntry
            }

            // Sort so AndroidManifest.xml (0) and sources/ (1) are processed first
            val sorted = records.sortedWith(compareBy({ it.priority }, { it.originalPath }))

            val extractedFiles = mutableListOf<ProjectFile>()
            var accumulatedBytes = 0L
            var skippedCount = 0

            for (rec in sorted) {
                if (accumulatedBytes + rec.size <= maxBytes && extractedFiles.size < maxFiles) {
                    val content = rec.tempFile.readText(Charsets.UTF_8)
                    extractedFiles.add(
                        ProjectFile(
                            id = rec.originalPath,
                            path = rec.originalPath,
                            name = File(rec.originalPath).name,
                            language = detectLanguage(rec.originalPath),
                            content = DECOMPILED_HEADER + content
                        )
                    )
                    accumulatedBytes += rec.size
                } else {
                    skippedCount++
                }
            }

            return JadxExtractionResult(
                files = extractedFiles,
                skippedCount = skippedCount,
                totalBytes = accumulatedBytes
            )
        } finally {
            try {
                tempDir.deleteRecursively()
            } catch (_: Exception) {}
        }
    }
}
