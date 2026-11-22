package com.mrboomdev.scrollix.data.storage

expect fun createPlatformStorage(): PlatformStorage
expect fun readFileBytes(path: String): ByteArray?
expect fun randomId(): String
expect fun currentTimeMillis(): Long
expect fun formatTime(timestamp: Long): String
expect fun saveFileToDevice(fileName: String, bytes: ByteArray): String?
