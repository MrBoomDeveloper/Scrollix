package com.mrboomdev.scrollix.buildconfig

import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isRegularFile
import kotlin.io.path.reader
import kotlin.io.path.writeText

@OptIn(ExperimentalPathApi::class)
@TaskAction
fun generateBuildConfig(
    @Input propertiesFile: Path,
    @Output generatedSourceDir: Path,
) {
    generatedSourceDir.deleteRecursively()

    val properties = if (propertiesFile.isRegularFile()) {
        propertiesFile.reader().use { reader ->
            Properties().apply { load(reader) }
        }
    } else {
        println(
            "WARNING: $propertiesFile is missing. Copy local.properties.example to local.properties " +
                "and fill in telegram.apiId and telegram.apiHash."
        )
        Properties()
    }

    val apiId = properties.getProperty("telegram.apiId")?.trim()?.toIntOrNull()
    if (apiId == null) {
        println("WARNING: telegram.apiId is missing or invalid in $propertiesFile; using 0.")
    }

    val apiHash = properties.getProperty("telegram.apiHash")?.trim()
    if (apiHash.isNullOrEmpty()) {
        println("WARNING: telegram.apiHash is missing or empty in $propertiesFile; using an empty value.")
    }

    // Test-server mode isolates the whole app: separate storage profile and
    // TDLib database, the test DC instead of the production one, and only the
    // providers that actually have a public test server.
    val envTestServer = System.getenv("SCROLLIX_TEST_SERVER")?.trim()
    val propertyTestServer = properties.getProperty("telegram.testServer")?.trim()

    val testServer = when {
        !envTestServer.isNullOrEmpty() -> envTestServer.isTruthy()
        !propertyTestServer.isNullOrEmpty() -> propertyTestServer.isTruthy()
        else -> false
    }

    if (testServer) {
        println("Building Scrollix for the Telegram test server (isolated profile).")
    }

    val outputFile = generatedSourceDir.resolve("BuildConfig.kt")
    outputFile.createParentDirectories()
    outputFile.writeText(
        buildString {
            appendLine("package com.mrboomdev.scrollix.generated")
            appendLine()
            appendLine("public object BuildConfig {")
            appendLine("    public const val TELEGRAM_API_ID: Int = ${apiId ?: 0}")
            appendLine("    public const val TELEGRAM_API_HASH: String = \"${apiHash.orEmpty().escapeKotlinString()}\"")
            appendLine("    public const val TEST_SERVER_MODE: Boolean = $testServer")
            appendLine("}")
        }
    )
}

/** Accepts the usual spellings people put into an environment variable. */
private fun String.isTruthy(): Boolean {
    return lowercase() in setOf("1", "true", "yes", "on")
}

private fun String.escapeKotlinString(): String = buildString {
    for (char in this@escapeKotlinString) {
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}
