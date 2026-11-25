package ai.ashura.app.storage

import org.jf.baksmali.Baksmali
import org.jf.baksmali.BaksmaliOptions
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.smali.Smali
import org.jf.smali.SmaliOptions
import java.io.File

/**
 * Disassembles and reassembles dex bytecode through baksmali/smali.
 * Edits happen on smali text; the dex is rebuilt from that text.
 */
object SmaliBridge {

    data class Disassembly(val directory: File, val classFiles: List<String>) {
        fun listing(limit: Int = 120): String = classFiles.take(limit).joinToString("\n")
    }

    fun disassemble(dex: File, outputDir: File, api: Int = 26): Disassembly {
        outputDir.mkdirs()
        val dexFile = DexFileFactory.loadDexFile(dex, Opcodes.forApi(api))
        val options = BaksmaliOptions()
        require(Baksmali.disassembleDexFile(dexFile, outputDir, 1, options)) { "baksmali failed" }
        val files = outputDir.walkTopDown().filter { it.isFile && it.extension == "smali" }
            .map { it.relativeTo(outputDir).path }.sorted().toList()
        return Disassembly(outputDir, files)
    }

    fun assemble(smaliRoot: File, outputDex: File, api: Int = 26) {
        val files = smaliRoot.walkTopDown().filter { it.isFile && it.extension == "smali" }.sortedBy { it.path }.toList()
        require(files.isNotEmpty()) { "No .smali files found under ${smaliRoot.name}" }
        outputDex.parentFile?.mkdirs()
        val options = SmaliOptions().apply {
            apiLevel = api
            jobs = 1
            outputDexFile = outputDex.absolutePath
        }
        require(Smali.assemble(options, files.map { it.absolutePath })) { "smali assembly failed" }
        require(outputDex.length() > 0) { "smali produced an empty dex" }
    }

    /** Returns the smali text of one class without leaving files behind. */
    fun classSmali(dex: File, className: String, api: Int = 26): String {
        val directory = java.nio.file.Files.createTempDirectory("ashura-smali-").toFile()
        return try {
            val result = disassemble(dex, directory, api)
            val needle = className.replace('.', '/').removePrefix("L").removeSuffix(";")
            val target = result.classFiles.firstOrNull { it.contains(needle) }
                ?: error("Class not found in this dex: $className")
            File(directory, target).readText()
        } finally {
            directory.deleteRecursively()
        }
    }
}
