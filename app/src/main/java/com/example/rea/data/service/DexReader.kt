package com.example.rea.data.service

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DexAnalysisInfo(
    val classCount: Int,
    val methodCount: Int,
    val classDescriptors: List<String>,
    val viewModelCount: Int,
    val packageHistogram: Map<String, Int>,
    val obfuscationPercent: Int,
    val detectedFrameworks: List<String>
)

class DexReader {

    data class SingleDexResult(
        val classCount: Int,
        val methodCount: Int,
        val classDescriptors: List<String>,
        val superclasses: List<String>,
        val viewModelCount: Int,
        val obfuscatedClassCount: Int
    )

    companion object {
        const val NO_INDEX = -1

        fun readUleb128(buffer: ByteBuffer): Int {
            var result = 0
            var shift = 0
            while (buffer.hasRemaining()) {
                val b = buffer.get().toInt()
                result = result or ((b and 0x7F) shl shift)
                if ((b and 0x80) == 0) break
                shift += 7
            }
            return result
        }

        fun parseDex(bytes: ByteArray, maxDescriptors: Int = 20000): SingleDexResult {
            if (bytes.size < 0x70) {
                return SingleDexResult(0, 0, emptyList(), emptyList(), 0, 0)
            }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            // Header offsets
            val stringIdsSize = buffer.getInt(0x38)
            val stringIdsOff = buffer.getInt(0x3C)
            val typeIdsSize = buffer.getInt(0x40)
            val typeIdsOff = buffer.getInt(0x44)
            val methodIdsSize = buffer.getInt(0x58)
            val classDefsSize = buffer.getInt(0x60)
            val classDefsOff = buffer.getInt(0x64)

            fun readStringAtIndex(idx: Int): String {
                if (idx < 0 || idx >= stringIdsSize) return ""
                val off = buffer.getInt(stringIdsOff + idx * 4)
                if (off < 0 || off >= bytes.size) return ""
                buffer.position(off)
                readUleb128(buffer) // length in chars
                val baos = ByteArrayOutputStream()
                while (buffer.hasRemaining()) {
                    val b = buffer.get()
                    if (b == 0.toByte()) break
                    baos.write(b.toInt())
                }
                return try {
                    baos.toString("UTF-8")
                } catch (_: Exception) {
                    ""
                }
            }

            fun readTypeDescriptor(typeIdx: Int): String {
                if (typeIdx < 0 || typeIdx >= typeIdsSize) return ""
                val descIdx = buffer.getInt(typeIdsOff + typeIdx * 4)
                return readStringAtIndex(descIdx)
            }

            val descriptors = mutableListOf<String>()
            val superclasses = mutableListOf<String>()
            var viewModelCount = 0
            var obfuscatedCount = 0

            for (i in 0 until classDefsSize) {
                val offset = classDefsOff + i * 32
                if (offset + 32 > bytes.size) break
                val classIdx = buffer.getInt(offset)
                val superclassIdx = buffer.getInt(offset + 8)

                val desc = readTypeDescriptor(classIdx)
                val superDesc = if (superclassIdx != NO_INDEX && superclassIdx in 0 until typeIdsSize) {
                    readTypeDescriptor(superclassIdx)
                } else ""

                if (descriptors.size < maxDescriptors) {
                    descriptors.add(desc)
                }
                if (superDesc.isNotEmpty()) {
                    superclasses.add(superDesc)
                }

                if (superDesc == "Landroidx/lifecycle/ViewModel;" ||
                    superDesc == "Landroidx/lifecycle/AndroidViewModel;"
                ) {
                    viewModelCount++
                }

                // Check obfuscation: packages like La/b/c;
                val parts = desc.removePrefix("L").removeSuffix(";").split("/").dropLast(1)
                if (parts.isNotEmpty() && parts.all { it.length in 1..2 }) {
                    obfuscatedCount++
                }
            }

            return SingleDexResult(
                classCount = classDefsSize,
                methodCount = methodIdsSize,
                classDescriptors = descriptors,
                superclasses = superclasses,
                viewModelCount = viewModelCount,
                obfuscatedClassCount = obfuscatedCount
            )
        }

        fun aggregate(dexResults: List<SingleDexResult>): DexAnalysisInfo {
            var totalClasses = 0
            var totalMethods = 0
            val allDescriptors = mutableListOf<String>()
            var totalViewModels = 0
            var totalObfuscated = 0
            val packageHistogram = mutableMapOf<String, Int>()
            val detectedFrameworks = mutableSetOf<String>()

            for (r in dexResults) {
                totalClasses += r.classCount
                totalMethods += r.methodCount
                totalViewModels += r.viewModelCount
                totalObfuscated += r.obfuscatedClassCount
                if (allDescriptors.size < 20000) {
                    val remaining = 20000 - allDescriptors.size
                    allDescriptors.addAll(r.classDescriptors.take(remaining))
                }
            }

            for (desc in allDescriptors) {
                // Framework detection
                when {
                    desc.startsWith("Landroidx/compose/") || desc.contains("/compose/") -> detectedFrameworks.add("Jetpack Compose")
                    desc.startsWith("Lkotlinx/coroutines/") -> detectedFrameworks.add("Coroutines")
                    desc.startsWith("Lretrofit2/") -> detectedFrameworks.add("Retrofit")
                    desc.startsWith("Lokhttp3/") -> detectedFrameworks.add("OkHttp")
                    desc.startsWith("Landroidx/room/") -> detectedFrameworks.add("Room")
                    desc.startsWith("Landroidx/camera/") -> detectedFrameworks.add("CameraX")
                    desc.startsWith("Lcom/google/firebase/") -> detectedFrameworks.add("Firebase")
                    desc.startsWith("Lcom/bumptech/glide/") -> detectedFrameworks.add("Glide")
                    desc.startsWith("Lcoil/") -> detectedFrameworks.add("Coil")
                }

                // Package histogram
                val clean = desc.removePrefix("L").removeSuffix(";")
                val topPkg = clean.substringBefore('/')
                if (topPkg.isNotEmpty() && topPkg != clean) {
                    packageHistogram[topPkg] = (packageHistogram[topPkg] ?: 0) + 1
                }
            }

            val obfuscationPercent = if (totalClasses > 0) {
                (totalObfuscated * 100) / totalClasses
            } else 0

            return DexAnalysisInfo(
                classCount = totalClasses,
                methodCount = totalMethods,
                classDescriptors = allDescriptors,
                viewModelCount = totalViewModels,
                packageHistogram = packageHistogram,
                obfuscationPercent = obfuscationPercent,
                detectedFrameworks = detectedFrameworks.toList()
            )
        }
    }
}
