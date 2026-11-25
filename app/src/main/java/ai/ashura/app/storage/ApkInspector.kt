package ai.ashura.app.storage

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Reads and rewrites APK containers without any external decompiler.
 * Analysis is byte-level: zip directory, binary XML (AXML), DEX tables, printable strings.
 */
class ApkInspector(private val file: File) {

    private val classCache = HashMap<String, List<String>>()

    data class EntryInfo(val name: String, val size: Long, val compressed: Long)
    data class ManifestInfo(
        val packageName: String, val versionName: String, val versionCode: String,
        val permissions: List<String>, val activities: List<String>, val services: List<String>,
        val receivers: List<String>, val providers: List<String>, val applicationLabel: String,
        val applicationName: String, val minSdk: String, val targetSdk: String
    )
    data class DexInfo(
        val name: String, val classes: Int, val methods: Int, val strings: Int,
        val classNames: List<String>, val matchedMethods: List<String>
    )

    fun entries(limit: Int = 400): List<EntryInfo> = ZipFile(file).use { zip ->
        zip.entries().asSequence().take(limit).map { e ->
            EntryInfo(e.name, e.size, e.compressedSize)
        }.toList()
    }

    fun readEntry(name: String, maxBytes: Int = 96 * 1024 * 1024): ByteArray? = ZipFile(file).use { zip ->
        val entry = zip.getEntry(name) ?: return null
        if (entry.size > maxBytes) return null
        zip.getInputStream(entry).use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (n > maxBytes - output.size()) return null
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        }
    }

    fun hasEntry(name: String): Boolean = ZipFile(file).use { it.getEntry(name) != null }

    fun xmlResourceCandidates(request: String): List<String> = ZipFile(file).use { zip ->
        val name = request.trim().replace('\\', '/').removePrefix("./").removePrefix("/")
        val xml = zip.entries().asSequence().filter {
            !it.isDirectory && it.name.startsWith("res/") && it.name.endsWith(".xml", ignoreCase = true)
        }.map { it.name }.toList()
        if (name in xml) return@use listOf(name)
        val layoutName = name.removePrefix("@layout/").removePrefix("layout/")
        if (!layoutName.matches(Regex("[A-Za-z0-9_.-]+"))) return@use emptyList()
        val filename = if (layoutName.endsWith(".xml")) layoutName else "$layoutName.xml"
        xml.filter { it.substringBeforeLast('/').matches(Regex("res/layout(?:-[A-Za-z0-9-]+)?")) &&
            it.substringAfterLast('/') == filename }.take(80)
    }

    fun resourceInventory(): org.json.JSONObject = ZipFile(file).use { zip ->
        val counts = sortedMapOf<String, Int>()
        val examples = org.json.JSONArray()
        var total = 0
        for (entry in zip.entries().asSequence()) {
            if (entry.isDirectory || !entry.name.startsWith("res/")) continue
            val folder = entry.name.removePrefix("res/").substringBefore('/')
            counts[folder] = (counts[folder] ?: 0) + 1
            total++
            if (examples.length() < 80) examples.put(entry.name)
        }
        org.json.JSONObject().put("resourceFiles", total).put("folders", org.json.JSONObject(counts))
            .put("hasCompiledResourceTable", zip.getEntry("resources.arsc") != null).put("samplePaths", examples)
    }

    fun manifest(): ManifestInfo {
        val bytes = readEntry("AndroidManifest.xml") ?: error("APK has no AndroidManifest.xml")
        val root = BinaryXml.parse(bytes)
        var packageName = ""
        var versionName = ""
        var versionCode = ""
        var label = ""
        var appName = ""
        var minSdk = ""
        var targetSdk = ""
        val permissions = mutableListOf<String>()
        val activities = mutableListOf<String>()
        val services = mutableListOf<String>()
        val receivers = mutableListOf<String>()
        val providers = mutableListOf<String>()
        root.forEach { element ->
            when (element.name) {
                "manifest" -> {
                    packageName = element.attributes["package"].orEmpty()
                    versionName = element.attributes["versionName"].orEmpty()
                    versionCode = element.attributes["versionCode"].orEmpty()
                }
                "uses-permission", "uses-permission-sdk-23" -> element.attributes["name"]?.let { permissions += it }
                "activity", "activity-alias" -> element.attributes["name"]?.let { activities += it }
                "service" -> element.attributes["name"]?.let { services += it }
                "receiver" -> element.attributes["name"]?.let { receivers += it }
                "provider" -> element.attributes["name"]?.let { providers += it }
                "application" -> {
                    label = element.attributes["label"].orEmpty()
                    appName = element.attributes["name"].orEmpty()
                }
                "uses-sdk" -> {
                    minSdk = element.attributes["minSdkVersion"].orEmpty()
                    targetSdk = element.attributes["targetSdkVersion"].orEmpty()
                }
            }
        }
        return ManifestInfo(
            packageName, versionName, versionCode, permissions, activities, services,
            receivers, providers, label, appName, minSdk, targetSdk
        )
    }

    /** Rewrites one dex string in place when the replacement fits, then fixes header checksums. */
    fun patchDexString(entry: String, find: String, replace: String): Pair<ByteArray, Int> {
        val original = readEntry(entry) ?: error("Missing $entry")
        require(find.isNotEmpty()) { "find cannot be empty" }
        val findBytes = find.toByteArray(Charsets.UTF_8)
        val replaceBytes = replace.toByteArray(Charsets.UTF_8)
        require(replaceBytes.size <= findBytes.size) { "Replacement must not be longer than the original string" }
        val padded = ByteArray(findBytes.size) { index ->
            if (index < replaceBytes.size) replaceBytes[index] else ' '.code.toByte()
        }
        val dex = original.copyOf()
        val stringIdsSize = readU32At(dex, 0x38)
        val stringIdsOff = readU32At(dex, 0x3C)
        var patched = 0
        for (index in 0 until stringIdsSize) {
            val dataOff = readU32At(dex, stringIdsOff + index * 4)
            if (dataOff <= 0 || dataOff >= dex.size) continue
            var position = dataOff
            while (position < dex.size && (dex[position].toInt() and 0x80) != 0) position++
            position++ // skip the uleb128 utf16 length
            val start = position
            while (position < dex.size && dex[position].toInt() != 0) position++
            val length = position - start
            if (length != findBytes.size) continue
            var match = true
            for (offset in 0 until length) {
                if (dex[start + offset] != findBytes[offset]) { match = false; break }
            }
            if (!match) continue
            System.arraycopy(padded, 0, dex, start, padded.size)
            patched++
        }
        if (patched > 0) refreshDexHeader(dex)
        return dex to patched
    }

    /** Recomputes the SHA-1 signature and adler32 checksum at the head of a dex. */
    private fun refreshDexHeader(dex: ByteArray) {
        val signature = java.security.MessageDigest.getInstance("SHA-1").digest(dex.copyOfRange(32, dex.size))
        System.arraycopy(signature, 0, dex, 12, 20)
        val adler = java.util.zip.Adler32()
        adler.update(dex, 12, dex.size - 12)
        val value = adler.value.toInt()
        dex[8] = (value and 0xFF).toByte()
        dex[9] = ((value ushr 8) and 0xFF).toByte()
        dex[10] = ((value ushr 16) and 0xFF).toByte()
        dex[11] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun readU32At(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    fun dexFiles(): List<String> = ZipFile(file).use { zip ->
        zip.entries().asSequence().map { it.name }
            .filter { it.startsWith("classes") && it.endsWith(".dex") }
            .sorted().toList()
    }

    fun dexInfo(name: String, methodQuery: String? = null, classLimit: Int = 120, methodLimit: Int = 60): DexInfo {
        val bytes = readEntry(name) ?: error("Missing $name")
        val dex = DexParser(bytes)
        val classNames = dex.classNames(classLimit)
        val matched = if (methodQuery.isNullOrBlank()) emptyList() else dex.matchesMethods(methodQuery, methodLimit)
        return DexInfo(name, dex.classCountPublic, dex.methodCountPublic, dex.stringCountPublic, classNames, matched)
    }

    fun dexClasses(name: String, limit: Int = 200): List<String> {
        classCache[name]?.let { cached -> return cached.take(limit) }
        val bytes = readEntry(name) ?: error("Missing $name")
        val classes = DexParser(bytes).classNames(limit)
        classCache[name] = classes
        return classes
    }

    fun stringsInEntry(name: String, minLength: Int = 5, limit: Int = 200, query: String? = null): List<String> {
        val bytes = readEntry(name) ?: return emptyList()
        return printableStrings(bytes, minLength, limit, query)
    }

    fun searchStrings(query: String, minLength: Int = 4, limit: Int = 80): List<String> = ZipFile(file).use { zip ->
        val hits = ArrayList<String>()
        zip.entries().asSequence().forEach { entry ->
            if (hits.size >= limit) return@forEach
            if (entry.size > 32L * 1024 * 1024) return@forEach
            if (entry.isDirectory) return@forEach
            val name = entry.name
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".webp") ||
                name.endsWith(".so") || name.endsWith(".ttf") || name.endsWith(".mp4") || name.endsWith(".ogg")
            ) return@forEach
            val bytes = runCatching { zip.getInputStream(entry).use { it.readBytes() } }.getOrNull() ?: return@forEach
            printableStrings(bytes, minLength, limit - hits.size, query).forEach { hit -> hits += "$name: $hit" }
        }
        hits
    }

    fun layoutSummary(): String = ZipFile(file).use { zip ->
        val groups = LinkedHashMap<String, Pair<Int, Long>>()
        zip.entries().asSequence().forEach { entry ->
            val group = when {
                entry.isDirectory -> "directory"
                entry.name.startsWith("classes") && entry.name.endsWith(".dex") -> "dex"
                entry.name.startsWith("lib/") -> "native"
                entry.name.startsWith("res/") -> "resources"
                entry.name.startsWith("assets/") -> "assets"
                entry.name == "AndroidManifest.xml" -> "manifest"
                entry.name == "resources.arsc" -> "arsc"
                entry.name.startsWith("META-INF/") -> "signature"
                else -> "other"
            }
            val current = groups[group] ?: (0 to 0L)
            groups[group] = (current.first + 1) to (current.second + entry.size)
        }
        groups.entries.joinToString("\n") { (group, value) ->
            "$group: ${value.first} entries, ${value.second / 1024} KB"
        }
    }

    /**
     * Rewrites the container: replaces, adds or removes entries. Compression method is preserved,
     * resources.arsc is always stored uncompressed, and stored entries are 4-byte aligned so the
     * rebuilt APK keeps installing like the original.
     */
    fun rewrite(target: File, replace: Map<String, ByteArray>, remove: Set<String>): List<String> {
        val touched = mutableListOf<String>()
        ZipFile(file).use { zip ->
            java.util.zip.ZipOutputStream(target.outputStream().buffered()).use { out ->
                var offset = 0L
                val seen = HashSet<String>()
                val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
                entries.forEach { entry ->
                    if (entry.name in remove) { touched += "removed ${entry.name}"; return@forEach }
                    val override = replace[entry.name]
                    val bytes = override ?: zip.getInputStream(entry).use { it.readBytes() }
                    if (override != null) touched += "replaced ${entry.name}"
                    val store = entry.name == "resources.arsc" || override == null && entry.method == ZipEntry.STORED
                    offset = writeEntry(out, entry.name, bytes, store, offset)
                    seen += entry.name
                }
                replace.forEach { (name, bytes) ->
                    if (name !in seen) {
                        offset = writeEntry(out, name, bytes, false, offset)
                        touched += "added $name"
                    }
                }
            }
        }
        return touched
    }

    /** Writes one entry and returns the new stream offset. */
    private fun writeEntry(out: ZipOutputStream, name: String, bytes: ByteArray, store: Boolean, offset: Long): Long {
        val entry = ZipEntry(name)
        entry.time = 0L
        val nameBytes = name.toByteArray(Charsets.UTF_8).size
        if (store) {
            entry.method = ZipEntry.STORED
            entry.size = bytes.size.toLong()
            entry.compressedSize = bytes.size.toLong()
            val crc = java.util.zip.CRC32()
            crc.update(bytes)
            entry.crc = crc.value
            val base = offset + 30 + nameBytes
            val misalign = ((base + 4) % 4).toInt()
            val padding = if (misalign == 0) 0 else 4 - misalign
            if (padding > 0) {
                entry.extra = byteArrayOf(0x35, 0x00, padding.coerceAtMost(0xFF).toByte(), 0x00) + ByteArray(padding)
            }
            val extraLength = entry.extra?.size ?: 0
            out.putNextEntry(entry)
            out.write(bytes)
            out.closeEntry()
            return offset + 30 + nameBytes + extraLength + bytes.size
        }
        out.putNextEntry(entry)
        out.write(bytes)
        out.closeEntry()
        return offset + 30 + nameBytes + bytes.size
    }

    companion object {
        /** Printable ASCII/UTF-8 runs, optionally filtered by substring. */
        fun printableStrings(bytes: ByteArray, minLength: Int, limit: Int, query: String?): List<String> {
            val out = ArrayList<String>()
            val builder = StringBuilder()
            val needle = query?.trim()?.lowercase()
            var index = 0
            while (index < bytes.size && out.size < limit) {
                val value = bytes[index].toInt() and 0xFF
                if (value in 0x20..0x7E || value == 0x09) {
                    builder.append(value.toChar())
                } else {
                    if (builder.length >= minLength) {
                        val text = builder.toString()
                        if (needle.isNullOrEmpty() || text.lowercase().contains(needle)) out += text
                    }
                    builder.setLength(0)
                }
                index++
            }
            if (builder.length >= minLength && out.size < limit) {
                val text = builder.toString()
                if (needle.isNullOrEmpty() || text.lowercase().contains(needle)) out += text
            }
            return out
        }
    }
}

/** Minimal binary-XML (AXML) reader: string pool, resource map, start elements with attributes. */
internal class BinaryXml {

    data class Element(val name: String, val attributes: Map<String, String>)

    companion object {
        private const val TYPE_STRING_POOL = 0x0001
        private const val TYPE_START_ELEMENT = 0x0102

        fun parse(bytes: ByteArray): List<Element> {
            val strings = ArrayList<String>()
            val elements = ArrayList<Element>()
            var offset = 8 // skip XML chunk header
            while (offset + 8 <= bytes.size) {
                val type = readU16(bytes, offset)
                val headerSize = readU16(bytes, offset + 2)
                val size = readU32(bytes, offset + 4).toInt()
                if (size <= 0 || headerSize <= 0 || offset + size > bytes.size) break
                when (type) {
                    TYPE_STRING_POOL -> strings.addAll(readStringPool(bytes, offset, headerSize))
                    TYPE_START_ELEMENT -> elements.add(readElement(bytes, offset, headerSize, size, strings))
                }
                offset += size
            }
            return elements
        }

        private fun readStringPool(bytes: ByteArray, start: Int, headerSize: Int): List<String> {
            val count = readU32(bytes, start + 8).toInt()
            val flags = readU32(bytes, start + 16).toInt()
            val stringsStart = readU32(bytes, start + 20).toInt()
            val utf8 = flags and 0x100 != 0
            require(count in 0..((bytes.size - start - headerSize) / 4)) { "Invalid XML string pool" }
            val result = ArrayList<String>(count)
            val base = start + stringsStart
            for (index in 0 until count) {
                val stringOffset = readU32(bytes, start + headerSize + index * 4).toInt()
                val position = base + stringOffset
                if (position < 0 || position >= bytes.size) { result += ""; continue }
                result += if (utf8) readUtf8(bytes, position) else readUtf16(bytes, position)
            }
            return result
        }

        private fun readUtf8(bytes: ByteArray, position: Int): String {
            var index = position
            var length = bytes[index].toInt() and 0xFF
            index++
            if (length and 0x80 != 0) {
                length = ((length and 0x7F) shl 8) or (bytes[index].toInt() and 0xFF)
                index++
            }
            var byteLength = bytes[index].toInt() and 0xFF
            index++
            if (byteLength and 0x80 != 0) {
                byteLength = ((byteLength and 0x7F) shl 8) or (bytes[index].toInt() and 0xFF)
                index++
            }
            val end = minOf(index + byteLength, bytes.size)
            return String(bytes, index, end - index, Charsets.UTF_8)
        }

        private fun readUtf16(bytes: ByteArray, position: Int): String {
            var index = position
            var length = readU16(bytes, index)
            index += 2
            if (length and 0x8000 != 0) {
                length = ((length and 0x7FFF) shl 16) or readU16(bytes, index)
                index += 2
            }
            require(length in 0..((bytes.size - index) / 2)) { "Invalid XML string length" }
            val chars = CharArray(length)
            for (i in 0 until length) {
                chars[i] = readU16(bytes, index + i * 2).toChar()
            }
            return String(chars)
        }

        private fun readElement(bytes: ByteArray, start: Int, headerSize: Int, size: Int, strings: List<String>): Element {
            val nameIndex = readU32(bytes, start + 20).toInt()
            val attributeStart = readU16(bytes, start + 24)
            val attributeSize = readU16(bytes, start + 26)
            val attributeCount = readU16(bytes, start + 28)
            val attributes = LinkedHashMap<String, String>()
            // attributeStart is measured from the start of the element struct (after the 16-byte chunk header).
            val attributeOffset = start + 16 + attributeStart
            for (i in 0 until attributeCount) {
                val base = attributeOffset + i * attributeSize
                if (base + 20 > start + size) break
                val attrNameIndex = readU32(bytes, base + 4).toInt()
                val rawValueIndex = readU32(bytes, base + 8).toInt()
                val dataType = bytes[base + 15].toInt() and 0xFF
                val data = readU32(bytes, base + 16).toInt()
                val key = strings.getOrNull(attrNameIndex) ?: continue
                val value = when {
                    rawValueIndex != -1 && rawValueIndex >= 0 -> strings.getOrNull(rawValueIndex).orEmpty()
                    dataType == 0x01 -> "@0x" + Integer.toHexString(data)
                    dataType == 0x02 -> "?0x" + Integer.toHexString(data)
                    dataType == 0x03 -> strings.getOrNull(data).orEmpty()          // string
                    dataType == 0x12 -> if (data != 0) "true" else "false"          // boolean
                    dataType == 0x10 || dataType == 0x11 -> data.toString()         // int / hex
                    else -> ""
                }
                if (value.isNotEmpty() && !attributes.containsKey(key)) attributes[key] = value
            }
            val elementName = strings.getOrNull(nameIndex).orEmpty()
            return Element(elementName, attributes)
        }

        private fun readU16(bytes: ByteArray, offset: Int): Int =
            (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

        private fun readU32(bytes: ByteArray, offset: Int): Long =
            ((bytes[offset].toLong() and 0xFF) or
                ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 3].toLong() and 0xFF) shl 24))
    }
}

/** Minimal DEX table reader: counts, class descriptors and method names. No bytecode is decoded. */
internal class DexParser(private val bytes: ByteArray) {

    private val stringCount = readU32(0x38).toInt()
    private val stringIdsOff = readU32(0x3C).toInt()
    private val typeCount = readU32(0x40).toInt()
    private val typeIdsOff = readU32(0x44).toInt()
    private val methodCount = readU32(0x58).toInt()
    private val methodIdsOff = readU32(0x5C).toInt()
    private val classCount = readU32(0x60).toInt()
    private val classDefsOff = readU32(0x64).toInt()

    val methodCountPublic: Int get() = methodCount
    val stringCountPublic: Int get() = stringCount
    val classCountPublic: Int get() = classCount

    fun classNames(limit: Int): List<String> {
        val out = ArrayList<String>(minOf(limit, classCount))
        for (index in 0 until minOf(limit, classCount)) {
            val descriptorIndex = readU32(classDefsOff + index * 32).toInt()
            val name = typeName(descriptorIndex) ?: continue
            out += name
        }
        return out
    }

    fun matchesMethods(query: String, limit: Int): List<String> {
        val needle = query.lowercase()
        val out = ArrayList<String>()
        for (index in 0 until methodCount) {
            if (out.size >= limit) break
            val base = methodIdsOff + index * 8
            val classIndex = readU16(base)
            val nameIndex = readU32(base + 4).toInt()
            val name = string(nameIndex) ?: continue
            if (!name.lowercase().contains(needle)) continue
            val owner = typeName(classIndex).orEmpty()
            out += "$owner->$name"
        }
        return out
    }

    private fun typeName(typeIndex: Int): String? {
        if (typeIndex < 0 || typeIndex >= typeCount) return null
        val descriptorIndex = readU32(typeIdsOff + typeIndex * 4).toInt()
        return string(descriptorIndex)
    }

    private fun string(index: Int): String? {
        if (index < 0 || index >= stringCount) return null
        val dataOff = readU32(stringIdsOff + index * 4).toInt()
        if (dataOff <= 0 || dataOff >= bytes.size) return null
        var position = dataOff
        // uleb128 utf16 length
        while (position < bytes.size && (bytes[position].toInt() and 0x80) != 0) position++
        position++
        val start = position
        while (position < bytes.size && bytes[position].toInt() != 0) position++
        return String(bytes, start, position - start, Charsets.UTF_8)
    }

    private fun readU16(offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readU32(offset: Int): Long {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24))
    }
}
