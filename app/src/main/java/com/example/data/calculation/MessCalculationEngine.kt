package com.example.data.calculation

import com.example.data.model.BazaarExpense
import com.example.data.model.DailyMealRecord
import com.example.data.model.Deposit
import com.example.data.model.FinalOrderSummary
import com.example.data.model.MealAttendanceEntry
import com.example.data.model.MealBookingEntry
import com.example.data.model.Member
import com.example.data.model.UtilityBill
import com.example.util.DhakaTimeHelper
import kotlin.math.roundToInt

data class MemberSettlement(
    val memberId: String,
    val memberName: String,
    val totalUnits: Double,
    val memberUnits: Double = totalUnits,
    val guestUnits: Double = 0.0,
    val mealCost: Double,
    val utilityCost: Double,
    val totalCost: Double,
    val totalDeposit: Double,
    val netBalance: Double
) {
    val totalMeals: Double get() = totalUnits
    val isRefund: Boolean get() = netBalance >= 0.0
}

data class MessSummary(
    val totalUnits: Double,
    val totalBazaarCost: Double,
    val mealRate: Double,
    val totalDeposits: Double,
    val totalUtilities: Double,
    val remainingFund: Double,
    val memberSettlements: List<MemberSettlement>
) {
    val totalMeals: Double get() = totalUnits
}

object MessCalculationEngine {

    /**
     * Calculates confirmed booking units for a member and their guests on a given date:
     * - Once the booking deadline passes, Booked meals count toward the member's monthly meal units.
     * - Cancelled and No Response meals count as zero.
     * - Members who book meals but do not eat them are charged because billing is based on confirmed bookings.
     * - Weighting rules:
     *     * Normal day (both meals active): Both = 2 units, Lunch only = 1.5 units, Night only = 1 unit.
     *     * Friday or any single-service day: Confirmed meal = 1 unit.
     * - Guest bookings: Follow the exact same weighting rules under the host member's account.
     */
    fun calculateBookingUnits(
        date: String,
        isFriday: Boolean,
        lunchServiceStatus: String,
        nightServiceStatus: String,
        lunchDeadline: String,
        nightDeadline: String,
        booking: MealBookingEntry,
        lunchFinalOrder: FinalOrderSummary? = null,
        nightFinalOrder: FinalOrderSummary? = null
    ): Pair<Double, Double> {
        val lunchActive = lunchServiceStatus != "CANCELLED"
        val nightActive = if (isFriday) false else (nightServiceStatus != "CANCELLED")

        val totalServedMeals = (if (lunchActive) 1 else 0) + (if (nightActive) 1 else 0)
        if (totalServedMeals == 0) return Pair(0.0, 0.0)

        val isLunchLocked = DhakaTimeHelper.isDeadlinePassed(date, lunchDeadline) || lunchFinalOrder?.isLocked == true
        val isNightLocked = if (isFriday) true else (DhakaTimeHelper.isDeadlinePassed(date, nightDeadline) || nightFinalOrder?.isLocked == true)

        val confirmedLunch = lunchActive && isLunchLocked && booking.statusLunch == "BOOKED"
        val confirmedNight = nightActive && isNightLocked && booking.statusNight == "BOOKED"

        fun computePersonUnits(hasL: Boolean, hasN: Boolean): Double {
            return when (totalServedMeals) {
                2 -> {
                    when {
                        hasL && hasN -> 2.0
                        hasL && !hasN -> 1.5
                        !hasL && hasN -> 1.0
                        else -> 0.0
                    }
                }
                1 -> {
                    val hasServed = (lunchActive && hasL) || (nightActive && hasN)
                    if (hasServed) 1.0 else 0.0
                }
                else -> 0.0
            }
        }

        val memberUnits = computePersonUnits(confirmedLunch, confirmedNight)

        val confirmedGuestLunch = if (lunchActive && isLunchLocked) maxOf(0, booking.guestLunch) else 0
        val confirmedGuestNight = if (nightActive && isNightLocked) maxOf(0, booking.guestNight) else 0

        val guestsBoth = minOf(confirmedGuestLunch, confirmedGuestNight)
        val guestsOnlyLunch = confirmedGuestLunch - guestsBoth
        val guestsOnlyNight = confirmedGuestNight - guestsBoth

        val guestUnits = (guestsBoth * computePersonUnits(true, true)) +
                         (guestsOnlyLunch * computePersonUnits(true, false)) +
                         (guestsOnlyNight * computePersonUnits(false, true))

        return Pair(roundTwoDecimals(memberUnits), roundTwoDecimals(guestUnits))
    }

    /**
     * Preserved calculation for historical attendance records prior to Step 1 & 2.
     */
    fun calculateAttendanceUnits(
        isFriday: Boolean,
        lunchServiceStatus: String,
        nightServiceStatus: String,
        attendance: MealAttendanceEntry
    ): Double {
        val lunchActive = lunchServiceStatus == "ACTIVE"
        val nightActive = if (isFriday) false else (nightServiceStatus == "ACTIVE")

        val totalServedMeals = (if (lunchActive) 1 else 0) + (if (nightActive) 1 else 0)

        fun computePersonUnits(ateLunch: Boolean, ateNight: Boolean): Double {
            return when (totalServedMeals) {
                2 -> {
                    when {
                        ateLunch && ateNight -> 2.0
                        ateLunch && !ateNight -> 1.5
                        !ateLunch && ateNight -> 1.0
                        else -> 0.0
                    }
                }
                1 -> {
                    val ateServed = (lunchActive && ateLunch) || (nightActive && ateNight)
                    if (ateServed) 1.0 else 0.0
                }
                else -> 0.0
            }
        }

        val memberUnits = computePersonUnits(attendance.attendedLunch, attendance.attendedNight)

        val guestsBoth = minOf(attendance.guestLunchAttended, attendance.guestNightAttended)
        val guestsOnlyLunch = attendance.guestLunchAttended - guestsBoth
        val guestsOnlyNight = attendance.guestNightAttended - guestsBoth

        val guestUnits = (guestsBoth * computePersonUnits(true, true)) +
                         (guestsOnlyLunch * computePersonUnits(true, false)) +
                         (guestsOnlyNight * computePersonUnits(false, true))

        return roundTwoDecimals(memberUnits + guestUnits)
    }

    fun calculateSummary(
        members: List<Member>,
        dailyMeals: List<DailyMealRecord>,
        bazaarExpenses: List<BazaarExpense>,
        deposits: List<Deposit>,
        utilityBills: List<UtilityBill>,
        lunchDeadline: String = "09:00",
        nightDeadline: String = "17:00"
    ): MessSummary {
        val activeMembers = members.filter { it.isActive }
        val memberCount = if (activeMembers.isNotEmpty()) activeMembers.size else 1

        // 1. Calculate Total Bazaar Cost
        val totalBazaarCost = bazaarExpenses.sumOf { it.amount }

        // 2. Calculate Total Units per member and total mess units across the month
        val memberUnitsMap = mutableMapOf<String, Double>()
        val memberOnlyUnitsMap = mutableMapOf<String, Double>()
        val guestOnlyUnitsMap = mutableMapOf<String, Double>()
        for (member in members) {
            memberUnitsMap[member.id] = 0.0
            memberOnlyUnitsMap[member.id] = 0.0
            guestOnlyUnitsMap[member.id] = 0.0
        }

        var totalMessUnits = 0.0

        for (record in dailyMeals) {
            val isFriday = record.isFriday || DhakaTimeHelper.isFriday(record.date)

            if (record.bookings.isNotEmpty()) {
                // Step 2: Driven by confirmed bookings once deadline passes!
                for (member in members) {
                    val booking = record.bookings[member.id] ?: MealBookingEntry()
                    val (mUnits, gUnits) = calculateBookingUnits(
                        date = record.date,
                        isFriday = isFriday,
                        lunchServiceStatus = record.lunchServiceStatus,
                        nightServiceStatus = record.nightServiceStatus,
                        lunchDeadline = lunchDeadline,
                        nightDeadline = nightDeadline,
                        booking = booking,
                        lunchFinalOrder = record.lunchFinalOrder,
                        nightFinalOrder = record.nightFinalOrder
                    )
                    val memberDayTotal = mUnits + gUnits
                    memberUnitsMap[member.id] = (memberUnitsMap[member.id] ?: 0.0) + memberDayTotal
                    memberOnlyUnitsMap[member.id] = (memberOnlyUnitsMap[member.id] ?: 0.0) + mUnits
                    guestOnlyUnitsMap[member.id] = (guestOnlyUnitsMap[member.id] ?: 0.0) + gUnits
                    totalMessUnits += memberDayTotal
                }
            } else if (record.attendances.isNotEmpty()) {
                // Historical attendance preservation
                for ((memberId, attendance) in record.attendances) {
                    val units = calculateAttendanceUnits(
                        isFriday = isFriday,
                        lunchServiceStatus = record.lunchServiceStatus,
                        nightServiceStatus = record.nightServiceStatus,
                        attendance = attendance
                    )
                    memberUnitsMap[memberId] = (memberUnitsMap[memberId] ?: 0.0) + units
                    memberOnlyUnitsMap[memberId] = (memberOnlyUnitsMap[memberId] ?: 0.0) + units
                    totalMessUnits += units
                }
            }
        }

        // 3. Compute Meal Rate: Total Bazaar Cost / Total Mess Units
        val mealRate = if (totalMessUnits > 0.0) {
            totalBazaarCost / totalMessUnits
        } else {
            0.0
        }

        // 4. Utility bills split equally among active members (Chef salary, Gas, WiFi, etc.)
        val totalUtilities = utilityBills.sumOf { it.amount }
        val perHeadUtility = if (memberCount > 0) totalUtilities / memberCount else 0.0

        // 5. Total deposits per member
        val memberDepositsMap = mutableMapOf<String, Double>()
        var totalDeposits = 0.0
        for (deposit in deposits) {
            memberDepositsMap[deposit.memberId] = (memberDepositsMap[deposit.memberId] ?: 0.0) + deposit.amount
            totalDeposits += deposit.amount
        }

        // 6. Build individual member settlements based on confirmed booking units
        val settlements = members.map { member ->
            val totalUnits = memberUnitsMap[member.id] ?: 0.0
            val mUnits = memberOnlyUnitsMap[member.id] ?: totalUnits
            val gUnits = guestOnlyUnitsMap[member.id] ?: 0.0
            val mealCost = totalUnits * mealRate
            val utilityShare = if (member.isActive) perHeadUtility else 0.0
            val totalCost = mealCost + utilityShare
            val deposit = memberDepositsMap[member.id] ?: 0.0
            val netBalance = deposit - totalCost

            MemberSettlement(
                memberId = member.id,
                memberName = member.displayName.ifBlank { "Member" },
                totalUnits = roundTwoDecimals(totalUnits),
                memberUnits = roundTwoDecimals(mUnits),
                guestUnits = roundTwoDecimals(gUnits),
                mealCost = roundTwoDecimals(mealCost),
                utilityCost = roundTwoDecimals(utilityShare),
                totalCost = roundTwoDecimals(totalCost),
                totalDeposit = roundTwoDecimals(deposit),
                netBalance = roundTwoDecimals(netBalance)
            )
        }

        val remainingFund = totalDeposits - totalBazaarCost - totalUtilities

        return MessSummary(
            totalUnits = roundTwoDecimals(totalMessUnits),
            totalBazaarCost = roundTwoDecimals(totalBazaarCost),
            mealRate = roundTwoDecimals(mealRate),
            totalDeposits = roundTwoDecimals(totalDeposits),
            totalUtilities = roundTwoDecimals(totalUtilities),
            remainingFund = roundTwoDecimals(remainingFund),
            memberSettlements = settlements
        )
    }

    fun roundTwoDecimals(value: Double): Double {
        return (value * 100.0).roundToInt() / 100.0
    }
}
