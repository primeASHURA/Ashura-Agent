package ai.ashura.app.storage

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class ApkResourceInventoryTest {
    @Test fun countsAllResourcesAndPreservesQualifiersWhenSamplesFill() {
        val file = File.createTempFile("resources", ".apk")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                val names = (1..82).map { "res/layout/view$it.xml" } +
                    listOf("res/layout-land/view1.xml", "resources.arsc", "assets/other")
                for (name in names) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(byteArrayOf(1, 2, 3))
                    zip.closeEntry()
                }
            }
            val apk = ApkInspector(file)
            val report = apk.resourceInventory()
            assertEquals(83, report.getInt("resourceFiles"))
            assertEquals(80, report.getJSONArray("samplePaths").length())
            assertEquals(1, report.getJSONObject("folders").getInt("layout-land"))
            assertTrue(report.getBoolean("hasCompiledResourceTable"))
            assertNull(apk.readEntry("res/layout/view1.xml", 2))
            assertArrayEquals(byteArrayOf(1, 2, 3), apk.readEntry("res/layout/view1.xml", 3))
        } finally { file.delete() }
    }
}
