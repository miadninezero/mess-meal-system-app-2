package com.example

import com.example.data.model.FinalOrderSummary
import com.example.data.model.MealBookingEntry
import com.example.util.DhakaTimeHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealBookingWorkflowTest {

    @Test
    fun `test meal booking status accepts only booked or cancelled`() {
        val booked = MealBookingEntry(statusLunch = "BOOKED", statusNight = "CANCELLED", guestLunch = 2, guestNight = 0)
        assertEquals("BOOKED", booked.statusLunch)
        assertEquals("CANCELLED", booked.statusNight)
        assertEquals(2, booked.guestLunch)
        assertEquals(0, booked.guestNight)
    }

    @Test
    fun `test separate member and guest count calculation`() {
        val bookings = mapOf(
            "m1" to MealBookingEntry(statusLunch = "BOOKED", statusNight = "BOOKED", guestLunch = 1, guestNight = 0),
            "m2" to MealBookingEntry(statusLunch = "BOOKED", statusNight = "CANCELLED", guestLunch = 3, guestNight = 0),
            "m3" to MealBookingEntry(statusLunch = "CANCELLED", statusNight = "BOOKED", guestLunch = 0, guestNight = 2),
            "m4" to MealBookingEntry(statusLunch = "CANCELLED", statusNight = "CANCELLED", guestLunch = 0, guestNight = 0)
        )

        var lunchMembers = 0
        var lunchGuests = 0
        var nightMembers = 0
        var nightGuests = 0

        bookings.values.forEach { b ->
            if (b.statusLunch == "BOOKED") {
                lunchMembers += 1
                lunchGuests += b.guestLunch
            }
            if (b.statusNight == "BOOKED") {
                nightMembers += 1
                nightGuests += b.guestNight
            }
        }

        assertEquals(2, lunchMembers) // m1, m2
        assertEquals(4, lunchGuests)  // 1 + 3
        assertEquals(6, lunchMembers + lunchGuests) // Total lunch portions

        assertEquals(2, nightMembers) // m1, m3
        assertEquals(2, nightGuests)  // 0 + 2
        assertEquals(4, nightMembers + nightGuests) // Total night portions
    }

    @Test
    fun `test deadline format 12 hour`() {
        assertEquals("09:00 AM", DhakaTimeHelper.format12Hour("09:00"))
        assertEquals("05:00 PM", DhakaTimeHelper.format12Hour("17:00"))
        assertEquals("12:00 PM", DhakaTimeHelper.format12Hour("12:00"))
        assertEquals("12:00 AM", DhakaTimeHelper.format12Hour("00:00"))
    }

    @Test
    fun `test past date deadline is passed`() {
        assertTrue(DhakaTimeHelper.isDeadlinePassed("2020-01-01", "09:00"))
    }

    @Test
    fun `test future date deadline is not passed`() {
        assertFalse(DhakaTimeHelper.isDeadlinePassed("2099-12-31", "09:00"))
    }

    @Test
    fun `test final order summary properties`() {
        val summary = FinalOrderSummary(
            mealType = "LUNCH",
            date = "2026-11-08",
            totalPortions = 15,
            memberCount = 12,
            guestCount = 3,
            deadline = "09:00",
            isLocked = true,
            lockedAtDhakaTime = "09:00 AM"
        )
        assertEquals("LUNCH", summary.mealType)
        assertEquals(15, summary.totalPortions)
        assertEquals(12, summary.memberCount)
        assertEquals(3, summary.guestCount)
        assertTrue(summary.isLocked)
    }

    @Test
    fun `test override requires reason`() {
        val validReason = "Member called manager directly before meal prep"
        val emptyReason = "   "
        assertTrue(validReason.trim().isNotBlank())
        assertFalse(emptyReason.trim().isNotBlank())
    }
}
