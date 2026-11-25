package ai.ashura.app.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebMcpPolicyTest {
    @Test fun allowsKnownWebDeploymentTools() {
        assertTrue(McpTools.webToolAllowed("https://mcp.vercel.com", "deploy_to_vercel"))
        assertTrue(McpTools.webToolAllowed("https://mcp.netlify.com/mcp", "netlify-deploy-services-updater"))
    }

    @Test fun blocksShellToolsAndLookalikeEndpoints() {
        assertFalse(McpTools.webToolAllowed("https://mcp.vercel.com", "use_vercel_cli"))
        assertFalse(McpTools.webToolAllowed("https://mcp.vercel.com.example.net", "deploy_to_vercel"))
        assertFalse(McpTools.webToolAllowed("https://mcp.netlify.com/other", "netlify-deploy-site"))
    }
}
