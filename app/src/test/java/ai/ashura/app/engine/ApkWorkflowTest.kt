package ai.ashura.app.engine

import org.junit.Assert.*
import org.junit.Test

class ApkWorkflowTest {
    @Test fun choosesMethodForArtifactWork() {
        for (request in listOf("is APK ko clone kro", "clone this Android app", "reverce eng karna hai", "inspect demo.apk")) {
            assertTrue(request, ApkWorkflow.needsChoice(request))
        }
        for (request in listOf("git clone my app repository", "APK build kro", "change the header color")) {
            assertFalse(request, ApkWorkflow.needsChoice(request))
        }
    }

    @Test fun selectedMethodConstrainsTools() {
        assertTrue(ApkWorkflow.INSPECT.allows("apk_resources"))
        assertFalse(ApkWorkflow.INSPECT.allows("ssh_decompile"))
        assertFalse(ApkWorkflow.INSPECT.allows("dex_smali"))
        assertTrue(ApkWorkflow.DECOMPILE.allows("ssh_decompile"))
        assertFalse(ApkWorkflow.DECOMPILE.allows("ssh_exec"))
    }
}
