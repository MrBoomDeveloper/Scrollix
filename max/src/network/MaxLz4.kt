package com.mrboomdev.scrollix.data.max.network

/**
 * Decodes a raw LZ4 block, the compression MAX applies to protocol payloads.
 *
 * Only decompression is implemented: outgoing frames are sent uncompressed,
 * which the protocol allows through a zero compression factor. The expected
 * size is `payloadLength * compressionFactor` from the frame header and is an
 * upper bound; the decoded length is returned as-is.
 */
internal fun decompressLz4Block(
    source: ByteArray,
    expectedSize: Int
): ByteArray {
    if (expectedSize <= 0) return ByteArray(0)

    val output = ByteArray(expectedSize)
    var inputIndex = 0
    var outputIndex = 0

    try {
        while (inputIndex < source.size) {
            val token = source[inputIndex++].toInt() and 0xFF

            var literals = token ushr 4
            if (literals == 15) {
                while (true) {
                    val extra = source[inputIndex++].toInt() and 0xFF
                    literals += extra
                    if (extra != 255) break
                }
            }

            repeat(literals) {
                if (outputIndex >= output.size) return output.copyOf(outputIndex)
                output[outputIndex++] = source[inputIndex++]
            }

            if (inputIndex >= source.size) break

            val offset = (source[inputIndex++].toInt() and 0xFF) or
                ((source[inputIndex++].toInt() and 0xFF) shl 8)

            var matchLength = token and 0xF
            if (matchLength == 15) {
                while (true) {
                    val extra = source[inputIndex++].toInt() and 0xFF
                    matchLength += extra
                    if (extra != 255) break
                }
            }
            matchLength += 4

            if (offset <= 0 || offset > outputIndex) {
                // Malformed block: keep what was decoded so far.
                return output.copyOf(outputIndex)
            }

            var from = outputIndex - offset
            repeat(matchLength) {
                if (outputIndex >= output.size) return output.copyOf(outputIndex)
                output[outputIndex++] = output[from++]
            }
        }
    } catch (_: IndexOutOfBoundsException) {
        // A truncated block still yields the prefix that was decoded.
    }

    return output.copyOf(outputIndex)
}
