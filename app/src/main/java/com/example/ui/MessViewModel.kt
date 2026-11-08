package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.calculation.MessCalculationEngine
import com.example.data.calculation.MessSummary
import com.example.data.model.AuditLog
import com.example.data.model.BazaarExpense
import com.example.data.model.DailyMealRecord
import com.example.data.model.Deposit
import com.example.data.model.FinalOrderSummary
import com.example.data.model.MealAttendanceEntry
import com.example.data.model.MealBookingEntry
import com.example.data.model.Member
import com.example.data.model.Mess
import com.example.data.model.UserProfile
import com.example.data.model.UtilityBill
import com.example.data.repository.MessRepository
import com.example.util.DhakaTimeHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class KitchenOrderSummary(
    val lunchTotalPortions: Int,
    val lunchMemberCount: Int,
    val lunchGuestCount: Int,
    val nightTotalPortions: Int,
    val nightMemberCount: Int,
    val nightGuestCount: Int,
    val attendedLunchCount: Int,
    val attendedNightCount: Int,
    val lunchActive: Boolean,
    val nightActive: Boolean,
    val isFriday: Boolean,
    val cancellationReason: String,
    val isLunchLocked: Boolean,
    val isNightLocked: Boolean,
    val lunchFinalOrder: FinalOrderSummary? = null,
    val nightFinalOrder: FinalOrderSummary? = null
) {
    val bookedLunchCount: Int get() = lunchTotalPortions
    val bookedNightCount: Int get() = nightTotalPortions
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MessViewModel(
    val currentUserId: String,
    private val repository: MessRepository,
    context: Context? = null
) : ViewModel() {

    private val prefs = context?.applicationContext?.getSharedPreferences("mess_prefs", Context.MODE_PRIVATE)

    val todayDhakaDate: String = DhakaTimeHelper.getTodayDhakaDate()
    val todayString: String get() = todayDhakaDate

    private val _selectedDate = MutableStateFlow(todayDhakaDate)
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _selectedMessId = MutableStateFlow(prefs?.getString("current_mess_id_$currentUserId", "") ?: "")
    val selectedMessId: StateFlow<String> = _selectedMessId.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentMess: StateFlow<Mess?> = _selectedMessId.flatMapLatest { messId ->
        if (messId.isNotBlank()) repository.observeMess(messId) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isManager: StateFlow<Boolean> = currentMess.map { mess ->
        mess?.isManager(currentUserId) == true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val members: StateFlow<List<Member>> = _selectedMessId.flatMapLatest { messId ->
        if (messId.isNotBlank()) repository.observeMembers(messId) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyMeals: StateFlow<List<DailyMealRecord>> = _selectedMessId.flatMapLatest { messId ->
        if (messId.isNotBlank()) repository.observeDailyMeals(messId) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bazaarExpenses: StateFlow<List<BazaarExpense>> = _selectedMessId.flatMapLatest { messId ->
        if (messId.isNotBlank()) repository.observeBazaarExpenses(messId) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deposits: StateFlow<List<Deposit>> = _selectedMessId.flatMapLatest { messId ->
        if (messId.isNotBlank()) repository.observeDeposits(messId) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val utilityBills: StateFlow<List<UtilityBill>> = _selectedMessId.flatMapLatest { messId ->
        if (messId.isNotBlank()) repository.observeUtilityBills(messId) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLog>> = _selectedMessId.flatMapLatest { messId ->
        if (messId.isNotBlank()) repository.observeAuditLogs(messId) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val summary: StateFlow<MessSummary> = combine(
        combine(members, dailyMeals, bazaarExpenses) { mems, meals, bazaars ->
            Triple(mems, meals, bazaars)
        },
        combine(deposits, utilityBills, currentMess) { deps, utils, mess ->
            Triple(deps, utils, mess)
        }
    ) { (mems, meals, bazaars), (deps, utils, mess) ->
        MessCalculationEngine.calculateSummary(
            members = mems,
            dailyMeals = meals,
            bazaarExpenses = bazaars,
            deposits = deps,
            utilityBills = utils,
            lunchDeadline = mess?.lunchBookingDeadline ?: "09:00",
            nightDeadline = mess?.nightBookingDeadline ?: "17:00"
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MessSummary(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, emptyList())
    )

    // Kitchen Order Summary for the selected date - Separate Member & Guest Portions
    val kitchenSummary: StateFlow<KitchenOrderSummary> = combine(
        selectedDate,
        dailyMeals,
        currentMess
    ) { date, records, mess ->
        val record = records.firstOrNull { it.id == date || it.date == date }
        val isFri = record?.isFriday ?: DhakaTimeHelper.isFriday(date)
        val lunchActive = record?.lunchServiceStatus != "CANCELLED"
        val nightActive = if (isFri) false else (record?.nightServiceStatus != "CANCELLED")

        val lunchDeadline = mess?.lunchBookingDeadline ?: "09:00"
        val nightDeadline = mess?.nightBookingDeadline ?: "17:00"
        val isLunchLocked = DhakaTimeHelper.isDeadlinePassed(date, lunchDeadline)
        val isNightLocked = if (isFri) true else DhakaTimeHelper.isDeadlinePassed(date, nightDeadline)

        var lunchMembers = 0
        var lunchGuests = 0
        var nightMembers = 0
        var nightGuests = 0

        record?.bookings?.values?.forEach { b ->
            if (lunchActive && b.statusLunch == "BOOKED") {
                lunchMembers += 1
                lunchGuests += maxOf(0, b.guestLunch)
            }
            if (nightActive && b.statusNight == "BOOKED") {
                nightMembers += 1
                nightGuests += maxOf(0, b.guestNight)
            }
        }

        var aLunch = 0
        var aNight = 0
        record?.attendances?.values?.forEach { a ->
            if (lunchActive && a.attendedLunch) aLunch += (1 + maxOf(0, a.guestLunchAttended))
            if (nightActive && a.attendedNight) aNight += (1 + maxOf(0, a.guestNightAttended))
        }

        KitchenOrderSummary(
            lunchTotalPortions = lunchMembers + lunchGuests,
            lunchMemberCount = lunchMembers,
            lunchGuestCount = lunchGuests,
            nightTotalPortions = nightMembers + nightGuests,
            nightMemberCount = nightMembers,
            nightGuestCount = nightGuests,
            attendedLunchCount = aLunch,
            attendedNightCount = aNight,
            lunchActive = lunchActive,
            nightActive = nightActive,
            isFriday = isFri,
            cancellationReason = record?.cancellationReason ?: "",
            isLunchLocked = isLunchLocked,
            isNightLocked = isNightLocked,
            lunchFinalOrder = record?.lunchFinalOrder,
            nightFinalOrder = record?.nightFinalOrder
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        KitchenOrderSummary(
            lunchTotalPortions = 0,
            lunchMemberCount = 0,
            lunchGuestCount = 0,
            nightTotalPortions = 0,
            nightMemberCount = 0,
            nightGuestCount = 0,
            attendedLunchCount = 0,
            attendedNightCount = 0,
            lunchActive = true,
            nightActive = true,
            isFriday = false,
            cancellationReason = "",
            isLunchLocked = false,
            isNightLocked = false,
            lunchFinalOrder = null,
            nightFinalOrder = null
        )
    )

    init {
        viewModelScope.launch {
            try {
                userProfile.collect { profile ->
                    if (profile != null && profile.currentMessId.isNotBlank() && _selectedMessId.value.isBlank()) {
                        _selectedMessId.value = profile.currentMessId
                        prefs?.edit()?.putString("current_mess_id_$currentUserId", profile.currentMessId)?.apply()
                    }
                }
            } catch (e: Exception) {
                Log.w("MessViewModel", "UserProfile observer caught error: ${e.message}")
            }
        }
        // Auto-save final order summary at deadline when viewed/selected
        viewModelScope.launch {
            try {
                kitchenSummary.collect { summary ->
                    val messId = _selectedMessId.value
                    val date = _selectedDate.value
                    val mess = currentMess.value
                    if (messId.isNotBlank() && date.isNotBlank() && mess != null) {
                        if (summary.isLunchLocked && summary.lunchFinalOrder == null && summary.lunchActive) {
                            try {
                                val finalOrder = FinalOrderSummary(
                                    mealType = "LUNCH",
                                    date = date,
                                    totalPortions = summary.lunchTotalPortions,
                                    memberCount = summary.lunchMemberCount,
                                    guestCount = summary.lunchGuestCount,
                                    deadline = mess.lunchBookingDeadline,
                                    isLocked = true,
                                    lockedAtDhakaTime = DhakaTimeHelper.format12Hour(mess.lunchBookingDeadline)
                                )
                                repository.saveFinalMealOrderSummary(messId, date, "LUNCH", finalOrder)
                            } catch (_: Exception) {}
                        }
                        if (summary.isNightLocked && !summary.isFriday && summary.nightFinalOrder == null && summary.nightActive) {
                            try {
                                val finalOrder = FinalOrderSummary(
                                    mealType = "NIGHT",
                                    date = date,
                                    totalPortions = summary.nightTotalPortions,
                                    memberCount = summary.nightMemberCount,
                                    guestCount = summary.nightGuestCount,
                                    deadline = mess.nightBookingDeadline,
                                    isLocked = true,
                                    lockedAtDhakaTime = DhakaTimeHelper.format12Hour(mess.nightBookingDeadline)
                                )
                                repository.saveFinalMealOrderSummary(messId, date, "NIGHT", finalOrder)
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MessViewModel", "KitchenSummary observer caught error: ${e.message}")
            }
        }
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    fun selectMess(messId: String) {
        _selectedMessId.value = messId
        prefs?.edit()?.putString("current_mess_id_$currentUserId", messId)?.apply()
        viewModelScope.launch {
            val current = userProfile.value ?: UserProfile(
                id = currentUserId,
                displayName = "User",
                email = "",
                currentMessId = messId
            )
            repository.saveUserProfile(current.copy(currentMessId = messId))
        }
    }

    fun createMess(name: String, managerName: String) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                val newMessId = repository.createMess(name, managerName, currentMonth)
                _selectedMessId.value = newMessId
                prefs?.edit()?.putString("current_mess_id_$currentUserId", newMessId)?.apply()
                _uiMessage.value = "Mess '$name' created successfully!"
            } catch (e: Exception) {
                Log.e("MessViewModel", "Failed to create mess", e)
                _uiMessage.value = "Failed to create mess: ${e.localizedMessage ?: e.message}"
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun joinMess(messId: String, memberName: String, room: String) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                repository.joinMess(messId.trim(), memberName.trim(), room.trim())
                _selectedMessId.value = messId.trim()
                prefs?.edit()?.putString("current_mess_id_$currentUserId", messId.trim())?.apply()
                _uiMessage.value = "Joined mess successfully!"
            } catch (e: Exception) {
                Log.e("MessViewModel", "Failed to join mess", e)
                _uiMessage.value = "Failed to join mess: ${e.localizedMessage ?: e.message}"
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun configureDeadlines(lunchDeadline: String, nightDeadline: String) {
        val messId = _selectedMessId.value
        if (messId.isBlank()) return
        viewModelScope.launch {
            try {
                repository.updateBookingDeadlines(messId, lunchDeadline, nightDeadline)
                _uiMessage.value = "Booking deadlines updated (Asia/Dhaka)!"
            } catch (e: Exception) {
                _uiMessage.value = "Error updating deadlines: ${e.message}"
            }
        }
    }

    fun updateMealServiceStatus(
        date: String,
        lunchStatus: String,
        nightStatus: String,
        cancellationReason: String
    ) {
        val messId = _selectedMessId.value
        if (messId.isBlank()) return
        viewModelScope.launch {
            try {
                repository.updateMealServiceStatus(messId, date, lunchStatus, nightStatus, cancellationReason)
                _uiMessage.value = "Meal service status updated. Kitchen summary refreshed."
            } catch (e: Exception) {
                _uiMessage.value = "Error updating service: ${e.message}"
            }
        }
    }

    fun saveMyBooking(
        date: String,
        statusLunch: String,
        statusNight: String,
        guestLunch: Int,
        guestNight: Int
    ) {
        val messId = _selectedMessId.value
        val currentMember = members.value.firstOrNull { it.userId == currentUserId }
        val memberId = currentMember?.id ?: currentUserId
        if (messId.isBlank()) return

        // Verify deadline in Dhaka
        val lunchCutoff = currentMess.value?.lunchBookingDeadline ?: "09:00"
        val nightCutoff = currentMess.value?.nightBookingDeadline ?: "17:00"
        val isFri = DhakaTimeHelper.isFriday(date)

        val lunchPassed = DhakaTimeHelper.isDeadlinePassed(date, lunchCutoff)
        val nightPassed = if (isFri) true else DhakaTimeHelper.isDeadlinePassed(date, nightCutoff)

        val record = dailyMeals.value.firstOrNull { it.id == date || it.date == date }
        val prevBooking = record?.bookings?.get(memberId) ?: MealBookingEntry()

        // Members can only select BOOKED or CANCELLED
        val requestedLunch = if (statusLunch == "BOOKED") "BOOKED" else "CANCELLED"
        val requestedNight = if (statusNight == "BOOKED") "BOOKED" else "CANCELLED"

        // Regular members cannot edit locked meals after deadline:
        val effectiveLunch = if (lunchPassed) prevBooking.statusLunch else requestedLunch
        val effectiveNight = if (isFri) "CANCELLED" else if (nightPassed) prevBooking.statusNight else requestedNight
        val effectiveGuestLunch = if (lunchPassed) prevBooking.guestLunch else maxOf(0, guestLunch)
        val effectiveGuestNight = if (isFri) 0 else if (nightPassed) prevBooking.guestNight else maxOf(0, guestNight)

        if (lunchPassed && nightPassed) {
            _uiMessage.value = "Deadlines have passed. Bookings are locked. Contact Manager/Admin for overrides."
            return
        }

        viewModelScope.launch {
            try {
                val booking = MealBookingEntry(
                    statusLunch = effectiveLunch,
                    statusNight = effectiveNight,
                    guestLunch = effectiveGuestLunch,
                    guestNight = effectiveGuestNight,
                    updatedBy = currentUserId
                )
                repository.saveMemberBooking(
                    messId = messId,
                    date = date,
                    memberId = memberId,
                    booking = booking,
                    lunchDeadline = lunchCutoff,
                    nightDeadline = nightCutoff,
                    isManager = isManager.value
                )
                _uiMessage.value = "Meal booking saved (${if (effectiveLunch == "BOOKED") "Lunch Booked" else "Lunch Cancelled"} | ${if (effectiveNight == "BOOKED") "Night Booked" else "Night Cancelled"})."
            } catch (e: Exception) {
                _uiMessage.value = "Booking error: ${e.message}"
            }
        }
    }

    fun managerOverrideBooking(
        date: String,
        memberId: String,
        memberName: String,
        statusLunch: String,
        statusNight: String,
        guestLunch: Int,
        guestNight: Int,
        reason: String
    ) {
        val messId = _selectedMessId.value
        if (messId.isBlank()) return
        if (!isManager.value) {
            _uiMessage.value = "Access Denied: Only Admins/Managers can override locked bookings."
            return
        }
        if (reason.trim().isBlank()) {
            _uiMessage.value = "Please provide an override reason for audit history."
            return
        }

        viewModelScope.launch {
            try {
                val booking = MealBookingEntry(
                    statusLunch = if (statusLunch == "BOOKED") "BOOKED" else "CANCELLED",
                    statusNight = if (statusNight == "BOOKED") "BOOKED" else "CANCELLED",
                    guestLunch = maxOf(0, guestLunch),
                    guestNight = maxOf(0, guestNight),
                    updatedBy = currentUserId,
                    managerOverrideReason = reason.trim()
                )
                repository.overrideMemberBooking(messId, date, memberId, memberName, booking, reason.trim())
                _uiMessage.value = "Admin booking override recorded in audit history with reason."
            } catch (e: Exception) {
                _uiMessage.value = "Override error: ${e.message}"
            }
        }
    }

    fun saveFinalOrderSummary(date: String, mealType: String) {
        val messId = _selectedMessId.value
        if (messId.isBlank()) return
        val summary = kitchenSummary.value
        val isLunch = mealType.equals("LUNCH", ignoreCase = true)
        val total = if (isLunch) summary.lunchTotalPortions else summary.nightTotalPortions
        val members = if (isLunch) summary.lunchMemberCount else summary.nightMemberCount
        val guests = if (isLunch) summary.lunchGuestCount else summary.nightGuestCount
        val deadline = if (isLunch) (currentMess.value?.lunchBookingDeadline ?: "09:00") else (currentMess.value?.nightBookingDeadline ?: "17:00")

        viewModelScope.launch {
            try {
                val finalOrder = FinalOrderSummary(
                    mealType = if (isLunch) "LUNCH" else "NIGHT",
                    date = date,
                    totalPortions = total,
                    memberCount = members,
                    guestCount = guests,
                    deadline = deadline,
                    isLocked = true,
                    lockedAtDhakaTime = DhakaTimeHelper.format12Hour(deadline)
                )
                repository.saveFinalMealOrderSummary(messId, date, if (isLunch) "LUNCH" else "NIGHT", finalOrder)
                _uiMessage.value = "Final $mealType kitchen order summary saved & locked!"
            } catch (e: Exception) {
                _uiMessage.value = "Failed to save final order: ${e.message}"
            }
        }
    }

    fun saveConfirmedAttendance(
        date: String,
        memberId: String,
        memberName: String,
        attendedLunch: Boolean,
        attendedNight: Boolean,
        guestLunch: Int,
        guestNight: Int
    ) {
        val messId = _selectedMessId.value
        if (messId.isBlank()) return

        val record = dailyMeals.value.firstOrNull { it.id == date || it.date == date }
        val lunchStatus = record?.lunchServiceStatus ?: "ACTIVE"
        val nightStatus = record?.nightServiceStatus ?: "ACTIVE"

        viewModelScope.launch {
            try {
                val attendance = MealAttendanceEntry(
                    attendedLunch = attendedLunch,
                    attendedNight = attendedNight,
                    guestLunchAttended = guestLunch,
                    guestNightAttended = guestNight
                )
                repository.saveAttendance(
                    messId = messId,
                    date = date,
                    memberId = memberId,
                    memberName = memberName,
                    attendance = attendance,
                    lunchServiceStatus = lunchStatus,
                    nightServiceStatus = nightStatus
                )
                _uiMessage.value = "Confirmed attendance saved for $memberName."
            } catch (e: Exception) {
                _uiMessage.value = "Attendance error: ${e.message}"
            }
        }
    }

    fun addBazaarExpense(
        amount: Double,
        description: String,
        category: String,
        date: String
    ) {
        val messId = _selectedMessId.value
        if (messId.isBlank()) return

        viewModelScope.launch {
            try {
                val currentMember = members.value.firstOrNull { it.userId == currentUserId }
                val expense = BazaarExpense(
                    buyerMemberId = currentMember?.id ?: currentUserId,
                    buyerName = currentMember?.displayName ?: "Member",
                    amount = amount,
                    itemsDescription = description,
                    category = category,
                    date = date
                )
                repository.addBazaarExpense(messId, expense)
                _uiMessage.value = "Bazaar expense recorded!"
            } catch (e: Exception) {
                _uiMessage.value = "Failed to record expense: ${e.message}"
            }
        }
    }

    fun deleteBazaarExpense(expenseId: String) {
        val messId = _selectedMessId.value
        if (messId.isBlank()) return
        viewModelScope.launch {
            try {
                repository.deleteBazaarExpense(messId, expenseId)
            } catch (e: Exception) {
                _uiMessage.value = "Failed to delete expense: ${e.message}"
            }
        }
    }

    fun addDeposit(
        memberId: String,
        memberName: String,
        amount: Double,
        paymentMethod: String,
        notes: String
    ) {
        val messId = _selectedMessId.value
        if (messId.isBlank()) return
        if (!isManager.value) {
            _uiMessage.value = "Access Denied: Only Managers can add deposits."
            return
        }
        viewModelScope.launch {
            try {
                val deposit = Deposit(
                    memberId = memberId,
                    memberName = memberName,
                    amount = amount,
                    date = todayDhakaDate,
                    paymentMethod = paymentMethod,
                    notes = notes
                )
                repository.addDeposit(messId, deposit)
                _uiMessage.value = "Deposit added successfully!"
            } catch (e: Exception) {
                _uiMessage.value = "Failed to add deposit: ${e.message}"
            }
        }
    }

    fun deleteDeposit(depositId: String) {
        val d = deposits.value.firstOrNull { it.id == depositId } ?: Deposit(id = depositId)
        deleteDeposit(d)
    }

    fun deleteDeposit(deposit: Deposit) {
        val messId = _selectedMessId.value
        if (messId.isBlank()) return
        if (!isManager.value) {
            _uiMessage.value = "Access Denied: Only Managers can delete deposits."
            return
        }
        viewModelScope.launch {
            try {
                repository.deleteDeposit(messId, deposit)
                _uiMessage.value = "Deposit removed."
            } catch (e: Exception) {
                _uiMessage.value = "Failed to delete deposit: ${e.message}"
            }
        }
    }

    fun addUtilityBill(title: String, amount: Double) {
        val messId = _selectedMessId.value
        if (messId.isBlank()) return
        if (!isManager.value) {
            _uiMessage.value = "Access Denied: Only Managers can add utility bills."
            return
        }
        viewModelScope.launch {
            try {
                val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                val bill = UtilityBill(
                    title = title,
                    amount = amount,
                    month = currentMonth
                )
                repository.addUtilityBill(messId, bill)
                _uiMessage.value = "Utility bill added!"
            } catch (e: Exception) {
                _uiMessage.value = "Failed to add bill: ${e.message}"
            }
        }
    }

    fun deleteUtilityBill(billId: String) {
        val b = utilityBills.value.firstOrNull { it.id == billId } ?: UtilityBill(id = billId)
        deleteUtilityBill(b)
    }

    fun deleteUtilityBill(bill: UtilityBill) {
        val messId = _selectedMessId.value
        if (messId.isBlank()) return
        if (!isManager.value) {
            _uiMessage.value = "Access Denied: Only Managers can delete utility bills."
            return
        }
        viewModelScope.launch {
            try {
                repository.deleteUtilityBill(messId, bill.id, bill.title, bill.amount)
                _uiMessage.value = "Utility bill removed."
            } catch (e: Exception) {
                _uiMessage.value = "Failed to delete bill: ${e.message}"
            }
        }
    }

    class Factory(
        private val currentUserId: String,
        private val repository: MessRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MessViewModel(currentUserId, repository, context) as T
        }
    }
}
