package com.mrboomdev.scrollix.data.contract

import com.mrboomdev.scrollix.data.storage.PlatformStorage

/**
 * In-memory storage for contract tests, so sessions that restore tokens or
 * caches start from a known empty state and never touch the real profile.
 */
class ContractPlatformStorage : PlatformStorage {
    private val textFiles = mutableMapOf<String, String>()
    private val byteFiles = mutableMapOf<String, ByteArray>()

    override val rootDirectory: String = "/contract"

    override fun readText(relativePath: String): String? = textFiles[relativePath]

    override fun writeText(relativePath: String, text: String) {
        textFiles[relativePath] = text
    }

    override fun readBytes(relativePath: String): ByteArray? = byteFiles[relativePath]

    override fun writeBytes(relativePath: String, bytes: ByteArray) {
        byteFiles[relativePath] = bytes
    }

    override fun delete(relativePath: String) {
        textFiles.keys.removeAll { path -> path.isInside(relativePath) }
        byteFiles.keys.removeAll { path -> path.isInside(relativePath) }
    }

    override fun ensureDirectory(relativePath: String): String = "$rootDirectory/$relativePath"

    override fun exists(relativePath: String): Boolean {
        return textFiles.containsKey(relativePath) || byteFiles.containsKey(relativePath)
    }

    override fun listFiles(relativePath: String): List<String> {
        val prefix = "$relativePath/"
        return (textFiles.keys + byteFiles.keys)
            .filter { path -> path.startsWith(prefix) }
            .map { path -> path.removePrefix(prefix) }
            .distinct()
    }

    override fun size(relativePath: String): Long {
        val prefix = "$relativePath/"
        val textSize = textFiles
            .filterKeys { path -> path.startsWith(prefix) }
            .values
            .sumOf { text -> text.encodeToByteArray().size.toLong() }
        val byteSize = byteFiles
            .filterKeys { path -> path.startsWith(prefix) }
            .values
            .sumOf { bytes -> bytes.size.toLong() }

        return textSize + byteSize
    }

    override fun absolutePath(relativePath: String): String = "$rootDirectory/$relativePath"

    private fun String.isInside(parent: String): Boolean {
        return this == parent || startsWith("$parent/")
    }
}
