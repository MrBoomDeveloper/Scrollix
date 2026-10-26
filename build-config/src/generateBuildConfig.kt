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

    val outputFile = generatedSourceDir.resolve("BuildConfig.kt")
    outputFile.createParentDirectories()
    outputFile.writeText(
        buildString {
            appendLine("package com.mrboomdev.scrollix.generated")
            appendLine()
            appendLine("public object BuildConfig {")
            appendLine("    public const val TELEGRAM_API_ID: Int = ${apiId ?: 0}")
            appendLine("    public const val TELEGRAM_API_HASH: String = \"${apiHash.orEmpty().escapeKotlinString()}\"")
            appendLine("}")
        }
    )
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
