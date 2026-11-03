package com.example.rea.data.service

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Clean, lightweight Android Binary XML (AXML) parser.
 * Decompresses compiled binary AndroidManifest.xml from an APK into human-readable XML string.
 */
class AxmlParser {

    companion object {
        private const val CHUNK_XML = 0x00080003
        private const val CHUNK_STRING_POOL = 0x001C0001
        private const val CHUNK_RESOURCE_IDS = 0x00080180
        private const val CHUNK_START_NAMESPACE = 0x00100100
        private const val CHUNK_END_NAMESPACE = 0x00100101
        private const val CHUNK_START_TAG = 0x00100102
        private const val CHUNK_END_TAG = 0x00100103
        private const val CHUNK_TEXT = 0x00100104

        fun parse(bytes: ByteArray): String {
            if (bytes.size < 8) return ""
            return try {
                AxmlParser().decode(bytes)
            } catch (e: Exception) {
                fallbackManifestXml(bytes)
            }
        }

        private fun fallbackManifestXml(bytes: ByteArray): String {
            // Extract ASCII strings from the string pool as fallback
            val sb = StringBuilder()
            var i = 0
            val strings = mutableListOf<String>()
            while (i < bytes.size - 1) {
                if (bytes[i] in 32..126 && bytes[i + 1] == 0.toByte()) {
                    val start = i
                    val strBuilder = StringBuilder()
                    while (i < bytes.size - 1 && bytes[i] in 32..126 && bytes[i + 1] == 0.toByte()) {
                        strBuilder.append(bytes[i].toInt().toChar())
                        i += 2
                    }
                    val s = strBuilder.toString()
                    if (s.length > 2) strings.add(s)
                } else {
                    i++
                }
            }
            sb.append("<!-- Manifest extracted from binary string pool -->\n")
            sb.append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n")
            strings.filter { it.contains(".") || it.startsWith("android.") }.take(20).forEach {
                sb.append("    <!-- Identified symbol: $it -->\n")
            }
            sb.append("</manifest>")
            return sb.toString()
        }
    }

    private fun decode(bytes: ByteArray): String {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val fileType = buffer.int
        if (fileType != CHUNK_XML && (fileType and 0xFFFF) != 0x0003) {
            return "<!-- Not a valid Android Binary XML format (magic: 0x${fileType.toString(16)}) -->"
        }
        val fileSize = buffer.int

        val stringPool = mutableListOf<String>()
        val output = StringBuilder()
        output.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")

        var indent = 0
        fun appendIndent() {
            for (i in 0 until indent) output.append("    ")
        }

        while (buffer.hasRemaining()) {
            val chunkPos = buffer.position()
            if (chunkPos + 8 > bytes.size) break
            val chunkType = buffer.int
            val chunkSize = buffer.int
            if (chunkSize <= 0 || chunkPos + chunkSize > bytes.size) break

            when (chunkType) {
                CHUNK_STRING_POOL, 0x0001 -> {
                    val stringCount = buffer.int
                    val styleCount = buffer.int
                    val flags = buffer.int
                    val stringsOffset = chunkPos + buffer.int
                    val stylesOffset = if (styleCount > 0) chunkPos + buffer.int else 0
                    val isUtf8 = (flags and (1 shl 8)) != 0

                    val stringOffsets = IntArray(stringCount)
                    for (i in 0 until stringCount) {
                        stringOffsets[i] = buffer.int
                    }

                    for (i in 0 until stringCount) {
                        val pos = stringsOffset + stringOffsets[i]
                        if (pos < bytes.size) {
                            stringPool.add(readStringAt(bytes, pos, isUtf8))
                        } else {
                            stringPool.add("")
                        }
                    }
                    buffer.position(chunkPos + chunkSize)
                }

                CHUNK_RESOURCE_IDS, 0x0180 -> {
                    // Resource IDs mapping, skip to next chunk
                    buffer.position(chunkPos + chunkSize)
                }

                CHUNK_START_NAMESPACE -> {
                    // line number, comment
                    buffer.int
                    buffer.int
                    val prefixIdx = buffer.int
                    val uriIdx = buffer.int
                    // Namespaces are tracked
                    buffer.position(chunkPos + chunkSize)
                }

                CHUNK_END_NAMESPACE -> {
                    buffer.position(chunkPos + chunkSize)
                }

                CHUNK_START_TAG -> {
                    // lineNumber, comment
                    buffer.int
                    buffer.int
                    val nsIdx = buffer.int
                    val nameIdx = buffer.int
                    val attrStart = buffer.short
                    val attrSize = buffer.short
                    val attrCount = buffer.short.toInt() and 0xFFFF
                    val idIdx = buffer.short
                    val classIdx = buffer.short
                    val styleIdx = buffer.short

                    val tagName = stringPool.getOrNull(nameIdx) ?: "element"

                    appendIndent()
                    output.append("<").append(tagName)
                    if (indent == 0) {
                        output.append(" xmlns:android=\"http://schemas.android.com/apk/res/android\"")
                    }

                    for (a in 0 until attrCount) {
                        val aNsIdx = buffer.int
                        val aNameIdx = buffer.int
                        val aValStrIdx = buffer.int
                        val aType = buffer.int shr 24
                        val aData = buffer.int

                        val attrName = stringPool.getOrNull(aNameIdx) ?: "attr$a"
                        val attrVal = if (aValStrIdx >= 0 && aValStrIdx < stringPool.size) {
                            stringPool[aValStrIdx]
                        } else {
                            formatAttrValue(aType, aData)
                        }

                        val prefix = if (aNsIdx >= 0) "android:" else ""
                        output.append("\n")
                        appendIndent()
                        output.append("    ").append(prefix).append(attrName).append("=\"").append(escapeXml(attrVal)).append("\"")
                    }

                    output.append(">\n")
                    indent++
                    buffer.position(chunkPos + chunkSize)
                }

                CHUNK_END_TAG -> {
                    // lineNumber, comment, nsIdx, nameIdx
                    buffer.int
                    buffer.int
                    buffer.int
                    val nameIdx = buffer.int
                    val tagName = stringPool.getOrNull(nameIdx) ?: "element"
                    indent = (indent - 1).coerceAtLeast(0)
                    appendIndent()
                    output.append("</").append(tagName).append(">\n")
                    buffer.position(chunkPos + chunkSize)
                }

                CHUNK_TEXT -> {
                    buffer.position(chunkPos + chunkSize)
                }

                else -> {
                    buffer.position(chunkPos + chunkSize)
                }
            }
        }

        return output.toString()
    }

    private fun readStringAt(bytes: ByteArray, offset: Int, isUtf8: Boolean): String {
        return try {
            if (isUtf8) {
                var pos = offset
                // UTF-8 length can be 1 or 2 bytes
                var len = bytes[pos].toInt() and 0xFF
                pos++
                if ((len and 0x80) != 0) {
                    len = ((len and 0x7F) shl 8) or (bytes[pos].toInt() and 0xFF)
                    pos++
                }
                var byteLen = bytes[pos].toInt() and 0xFF
                pos++
                if ((byteLen and 0x80) != 0) {
                    byteLen = ((byteLen and 0x7F) shl 8) or (bytes[pos].toInt() and 0xFF)
                    pos++
                }
                String(bytes, pos, byteLen.coerceAtMost(bytes.size - pos), Charsets.UTF_8)
            } else {
                var pos = offset
                var len = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
                pos += 2
                if ((len and 0x8000) != 0) {
                    val high = len and 0x7FFF
                    val low = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
                    pos += 2
                    len = (high shl 16) or low
                }
                val charBytes = len * 2
                String(bytes, pos, charBytes.coerceAtMost(bytes.size - pos), Charsets.UTF_16LE)
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun formatAttrValue(type: Int, data: Int): String {
        return when (type) {
            0x03 -> "string#$data"
            0x10 -> data.toString()
            0x11 -> "0x" + Integer.toHexString(data)
            0x12 -> if (data != 0) "true" else "false"
            0x01 -> "@0x" + Integer.toHexString(data)
            0x02 -> "?0x" + Integer.toHexString(data)
            0x04 -> java.lang.Float.intBitsToFloat(data).toString()
            0x05 -> "${data shr 8}dp"
            else -> data.toString()
        }
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
