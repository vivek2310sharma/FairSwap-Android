package com.fairswap.core.sellerlistings

object SellerMediaValidation {
    const val MAX_SOURCE_FILE_BYTES = 5 * 1024 * 1024L // 5 MiB

    fun detectFormatFromMagicBytes(bytes: ByteArray): String? {
        if (bytes.size < 3) return null

        // JPEG: FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return "image/jpeg"
        }

        // PNG: 89 50 4E 47
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }

        // WebP: RIFF ... WEBP
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) {
            return "image/webp"
        }

        return null
    }

    fun sanitizeFilename(filename: String?, fallbackExtension: String): String {
        if (filename.isNullOrBlank()) return "photo.$fallbackExtension"
        val nameOnly = filename.replace("\\", "/").substringAfterLast("/")
        val clean = nameOnly.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)
        if (clean.isBlank()) return "photo.$fallbackExtension"
        return if (clean.contains(".")) clean else "$clean.$fallbackExtension"
    }

    fun calculateRemainingCapacity(persistedTotalCount: Int): Int =
        (8 - persistedTotalCount).coerceIn(0, 8)
}
