package com.fairswap.core.sellerlistings

import com.fairswap.core.discovery.Rupees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SellerListingValidationTest {

    private val validUuid = "11111111-2222-3333-4444-555555555555"
    private val validCityUuid = "66666666-7777-8888-9999-000000000000"

    @Test
    fun `exact decimal paise conversions and round trips`() {
        assertEquals("₹123.45", Rupees.formatPaise(12345))
        assertEquals(12345, Rupees.toPaise("123.45"))

        assertEquals("₹0.01", Rupees.formatPaise(1))
        assertEquals(1, Rupees.toPaise("0.01"))

        assertEquals("₹10,00,000.00", Rupees.formatPaise(100_000_000))
        assertEquals(100_000_000, Rupees.toPaise("1000000.00"))
        assertEquals(100_000_000, Rupees.toPaise("1000000"))
    }

    @Test
    fun `draft validation - city is required for draft persistence`() {
        val noCity = SellerListingInput(title = "Valid Title", categoryId = validUuid, cityId = null)
        val err = SellerListingValidation.validateDraft(noCity)
        assertEquals("Select a valid city.", err.cityError)
        assertFalse(err.isValid)

        val withCity = noCity.copy(cityId = validCityUuid)
        val validErr = SellerListingValidation.validateDraft(withCity)
        assertNull(validErr.cityError)
        assertTrue(validErr.isValid)
    }

    @Test
    fun `draft validation - price upper bound displays correct rupee limit`() {
        val overflowPrice = SellerListingInput(
            title = "Valid Title",
            categoryId = validUuid,
            cityId = validCityUuid,
            priceRupees = "1000000.01",
        )
        val err = SellerListingValidation.validateDraft(overflowPrice)
        assertEquals("Enter a valid price up to ₹10,00,000.", err.priceError)
    }

    @Test
    fun `submit validation - rejects blank or short description`() {
        val whitespaceDesc = SellerListingInput(
            title = "Valid Title",
            categoryId = validUuid,
            cityId = validCityUuid,
            description = "   \n\t ",
            condition = "good",
            priceType = "fixed",
            priceRupees = "500",
        )
        val err = SellerListingValidation.validateSubmit(whitespaceDesc)
        assertEquals("Description must be at least 10 characters.", err.descriptionError)
        assertFalse(err.isValid)

        val validDesc = whitespaceDesc.copy(description = "Valid description containing enough text.")
        val validErr = SellerListingValidation.validateSubmit(validDesc)
        assertNull(validErr.descriptionError)
        assertTrue(validErr.isValid)
    }

    @Test
    fun `unknown listing status maps to UNKNOWN with canEdit false`() {
        val status = SellerListingStatus.fromWire("custom_non_existent_status")
        assertEquals(SellerListingStatus.UNKNOWN, status)
        assertFalse(status.canEdit)
    }
}
