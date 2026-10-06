package com.fairswap.core.sellerlistings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SellerMediaValidationTest {

    @Test
    fun `detectFormatFromMagicBytes detects JPEG PNG WebP`() {
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        assertEquals("image/jpeg", SellerMediaValidation.detectFormatFromMagicBytes(jpegBytes))

        val pngBytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte())
        assertEquals("image/png", SellerMediaValidation.detectFormatFromMagicBytes(pngBytes))

        val webpBytes = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0, 0, 0, 0,
            'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte()
        )
        assertEquals("image/webp", SellerMediaValidation.detectFormatFromMagicBytes(webpBytes))

        val heicBytes = byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 'h'.code.toByte(), 'e'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte())
        assertNull(SellerMediaValidation.detectFormatFromMagicBytes(heicBytes))
    }

    @Test
    fun `sanitizeFilename produces safe filename`() {
        assertEquals("photo.jpg", SellerMediaValidation.sanitizeFilename(null, "jpg"))
        assertEquals("photo.png", SellerMediaValidation.sanitizeFilename("", "png"))
        assertEquals("my_photo.jpg", SellerMediaValidation.sanitizeFilename("../../my photo.jpg", "jpg"))
    }

    @Test
    fun `calculateRemainingCapacity clamps between 0 and 8`() {
        assertEquals(8, SellerMediaValidation.calculateRemainingCapacity(0))
        assertEquals(5, SellerMediaValidation.calculateRemainingCapacity(3))
        assertEquals(0, SellerMediaValidation.calculateRemainingCapacity(8))
        assertEquals(0, SellerMediaValidation.calculateRemainingCapacity(10))
    }
}
