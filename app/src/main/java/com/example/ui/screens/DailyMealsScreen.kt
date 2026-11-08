package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RoomService
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.calculation.MessCalculationEngine
import com.example.data.model.MealAttendanceEntry
import com.example.data.model.MealBookingEntry
import com.example.data.model.Member
import com.example.ui.MessViewModel
import com.example.util.DhakaTimeHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyMealsScreen(
    viewModel: MessViewModel,
    onBack: () -> Unit
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val members by viewModel.members.collectAsState()
    val dailyMeals by viewModel.dailyMeals.collectAsState()
    val currentMess by viewModel.currentMess.collectAsState()
    val isManager by viewModel.isManager.collectAsState()
    val kitchenSummary by viewModel.kitchenSummary.collectAsState()

    val currentRecord = dailyMeals.firstOrNull { it.id == selectedDate || it.date == selectedDate }
    val bookingsMap = currentRecord?.bookings ?: emptyMap()
    val attendancesMap = currentRecord?.attendances ?: emptyMap()

    val lunchDeadline = currentMess?.lunchBookingDeadline ?: "09:00"
    val nightDeadline = currentMess?.nightBookingDeadline ?: "17:00"

    val isFriday = currentRecord?.isFriday ?: DhakaTimeHelper.isFriday(selectedDate)
    val isLunchDeadlinePassed = DhakaTimeHelper.isDeadlinePassed(selectedDate, lunchDeadline)
    val isNightDeadlinePassed = if (isFriday) true else DhakaTimeHelper.isDeadlinePassed(selectedDate, nightDeadline)

    val currentMember = members.firstOrNull { it.userId == viewModel.currentUserId }
    val myMemberId = currentMember?.id ?: viewModel.currentUserId
    val myBooking = bookingsMap[myMemberId] ?: MealBookingEntry()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: My Booking, 1: Kitchen Orders, 2: Actual Attendance
    var showDeadlineDialog by remember { mutableStateOf(false) }
    var showServiceStatusDialog by remember { mutableStateOf(false) }
    var overrideMemberTarget by remember { mutableStateOf<Member?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meal Operations", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isManager) {
                        IconButton(onClick = { showDeadlineDialog = true }) {
                            Icon(Icons.Default.AccessTime, contentDescription = "Configure Deadlines")
                        }
                        IconButton(onClick = { showServiceStatusDialog = true }) {
                            Icon(Icons.Default.RoomService, contentDescription = "Service Status")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Date Switcher Header
            DateSwitcher(
                currentDate = selectedDate,
                isFriday = isFriday,
                onDateChanged = { newDate -> viewModel.setSelectedDate(newDate) },
                onTodayClicked = { viewModel.setSelectedDate(viewModel.todayDhakaDate) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Service Notice or Friday Notice
            if (isFriday || !kitchenSummary.lunchActive || !kitchenSummary.nightActive) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!kitchenSummary.lunchActive || !kitchenSummary.nightActive)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (!kitchenSummary.lunchActive || !kitchenSummary.nightActive) Icons.Default.Warning else Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = if (!kitchenSummary.lunchActive || !kitchenSummary.nightActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                !kitchenSummary.lunchActive && !kitchenSummary.nightActive -> "All meals cancelled for today: ${kitchenSummary.cancellationReason}"
                                !kitchenSummary.lunchActive -> "Lunch cancelled: ${kitchenSummary.cancellationReason} (Night = 1 unit)"
                                !kitchenSummary.nightActive && !isFriday -> "Night meal cancelled: ${kitchenSummary.cancellationReason} (Lunch = 1 unit)"
                                isFriday -> "Friday Schedule: Only Lunch served (Counts as 1 unit). Night meal closed."
                                else -> "Normal Day: Lunch & Night served."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = if (!kitchenSummary.lunchActive || !kitchenSummary.nightActive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Tab Navigation: My Booking | Kitchen Orders | Actual Attendance
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("My Booking", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Kitchen Orders", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Attendance", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (selectedTab) {
                0 -> {
                    // TAB 0: Member's Own Booking
                    MyBookingTab(
                        date = selectedDate,
                        booking = myBooking,
                        lunchActive = kitchenSummary.lunchActive,
                        nightActive = kitchenSummary.nightActive,
                        isFriday = isFriday,
                        lunchDeadline = lunchDeadline,
                        nightDeadline = nightDeadline,
                        isLunchPassed = isLunchDeadlinePassed,
                        isNightPassed = isNightDeadlinePassed,
                        onSaveBooking = { statusLunch, statusNight, guestL, guestN ->
                            viewModel.saveMyBooking(selectedDate, statusLunch, statusNight, guestL, guestN)
                        }
                    )
                }
                1 -> {
                    // TAB 1: Kitchen Orders & All Member Bookings
                    KitchenOrdersTab(
                        summary = kitchenSummary,
                        members = members,
                        bookings = bookingsMap,
                        lunchDeadline = lunchDeadline,
                        nightDeadline = nightDeadline,
                        isManager = isManager,
                        onOverride = { member -> overrideMemberTarget = member },
                        onSaveFinalOrder = { mealType -> viewModel.saveFinalOrderSummary(selectedDate, mealType) }
                    )
                }
                2 -> {
                    // TAB 2: Actual Confirmed Attendance (Drives Units & Financial Billing)
                    AttendanceTab(
                        members = members,
                        attendances = attendancesMap,
                        isFriday = isFriday,
                        lunchActive = kitchenSummary.lunchActive,
                        nightActive = kitchenSummary.nightActive,
                        isManager = isManager,
                        onSaveAttendance = { memberId, memberName, attendedL, attendedN, guestL, guestN ->
                            viewModel.saveConfirmedAttendance(
                                selectedDate,
                                memberId,
                                memberName,
                                attendedL,
                                attendedN,
                                guestL,
                                guestN
                            )
                        }
                    )
                }
            }
        }

        if (showDeadlineDialog && isManager) {
            ConfigureDeadlineDialog(
                currentLunch = lunchDeadline,
                currentNight = nightDeadline,
                onDismiss = { showDeadlineDialog = false },
                onSave = { lunch, night ->
                    viewModel.configureDeadlines(lunch, night)
                    showDeadlineDialog = false
                }
            )
        }

        if (showServiceStatusDialog && isManager) {
            ServiceStatusDialog(
                date = selectedDate,
                lunchActive = kitchenSummary.lunchActive,
                nightActive = kitchenSummary.nightActive,
                onDismiss = { showServiceStatusDialog = false },
                onSave = { lunchStatus, nightStatus, reason ->
                    viewModel.updateMealServiceStatus(selectedDate, lunchStatus, nightStatus, reason)
                    showServiceStatusDialog = false
                }
            )
        }

        overrideMemberTarget?.let { member ->
            val prev = bookingsMap[member.id] ?: MealBookingEntry()
            ManagerOverrideBookingDialog(
                member = member,
                date = selectedDate,
                prevBooking = prev,
                isFriday = isFriday,
                onDismiss = { overrideMemberTarget = null },
                onOverride = { statusL, statusN, guestL, guestN, reason ->
                    viewModel.managerOverrideBooking(
                        selectedDate,
                        member.id,
                        member.displayName,
                        statusL,
                        statusN,
                        guestL,
                        guestN,
                        reason
                    )
                    overrideMemberTarget = null
                }
            )
        }
    }
}

@Composable
private fun MyBookingTab(
    date: String,
    booking: MealBookingEntry,
    lunchActive: Boolean,
    nightActive: Boolean,
    isFriday: Boolean,
    lunchDeadline: String,
    nightDeadline: String,
    isLunchPassed: Boolean,
    isNightPassed: Boolean,
    onSaveBooking: (statusLunch: String, statusNight: String, guestLunch: Int, guestNight: Int) -> Unit
) {
    // Members only select BOOKED or CANCELLED
    val initialLunch = if (booking.statusLunch == "CANCELLED") "CANCELLED" else "BOOKED"
    val initialNight = if (booking.statusNight == "CANCELLED") "CANCELLED" else "BOOKED"

    var statusLunch by remember(booking) { mutableStateOf(initialLunch) }
    var statusNight by remember(booking) { mutableStateOf(initialNight) }
    var guestLunch by remember(booking) { mutableIntStateOf(booking.guestLunch) }
    var guestNight by remember(booking) { mutableIntStateOf(booking.guestNight) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Your Meal Booking ($date)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Select Booked or Cancelled for your own Lunch and Night meals, including any guest portions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Lunch Booking Card
                    MealBookingCard(
                        mealName = "Lunch (Day Meal)",
                        deadlineTime = DhakaTimeHelper.format12Hour(lunchDeadline),
                        isPassed = isLunchPassed,
                        isActive = lunchActive,
                        status = statusLunch,
                        guestCount = guestLunch,
                        onStatusChanged = { statusLunch = it },
                        onGuestChanged = { guestLunch = maxOf(0, it) }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Night Booking Card
                    if (isFriday) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Night Meal: Friday Closed", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Night meal booking is disabled on Fridays as per mess rules (Only Lunch served).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    } else {
                        MealBookingCard(
                            mealName = "Night (Dinner)",
                            deadlineTime = DhakaTimeHelper.format12Hour(nightDeadline),
                            isPassed = isNightPassed,
                            isActive = nightActive,
                            status = statusNight,
                            guestCount = guestNight,
                            onStatusChanged = { statusNight = it },
                            onGuestChanged = { guestNight = maxOf(0, it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    val canSave = (!isLunchPassed && lunchActive) || (!isNightPassed && nightActive && !isFriday)

                    Button(
                        onClick = {
                            onSaveBooking(statusLunch, if (isFriday) "CANCELLED" else statusNight, guestLunch, if (isFriday) 0 else guestNight)
                        },
                        enabled = canSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (canSave) "Confirm / Save Meal Booking" else "Bookings Locked (Deadline Passed)",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!canSave) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Once the deadline passes, bookings are locked. Only Admins can override with a recorded reason.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun MealBookingCard(
    mealName: String,
    deadlineTime: String,
    isPassed: Boolean,
    isActive: Boolean,
    status: String,
    guestCount: Int,
    onStatusChanged: (String) -> Unit,
    onGuestChanged: (Int) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (!isActive) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(mealName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = if (!isActive) "CANCELLED BY SERVICE" else "Cutoff: $deadlineTime (Asia/Dhaka)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        !isActive -> MaterialTheme.colorScheme.errorContainer
                        isPassed -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPassed || !isActive) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (!isActive) "Service Cancelled" else if (isPassed) "Locked" else "Open",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (!isActive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Simplified 2-Way Choice: BOOKED or CANCELLED
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusButton(
                    modifier = Modifier.weight(1f),
                    label = "Booked",
                    isSelected = status == "BOOKED",
                    enabled = !isPassed && isActive,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onStatusChanged("BOOKED") }
                )
                StatusButton(
                    modifier = Modifier.weight(1f),
                    label = "Cancelled",
                    isSelected = status == "CANCELLED",
                    enabled = !isPassed && isActive,
                    color = MaterialTheme.colorScheme.error,
                    onClick = { onStatusChanged("CANCELLED") }
                )
            }

            if (isPassed && isActive) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🔒 Booking deadline has passed ($deadlineTime). Only Admins can override.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Guest portions configuration under member's account
            if (status == "BOOKED" && isActive) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Guest Portions", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                        Text("Billed under your account", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (guestCount > 0) onGuestChanged(guestCount - 1) },
                            enabled = !isPassed,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease guests", modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = "$guestCount",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        IconButton(
                            onClick = { onGuestChanged(guestCount + 1) },
                            enabled = !isPassed,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase guests", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusButton(
    modifier: Modifier = Modifier,
    label: String,
    isSelected: Boolean,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    if (isSelected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(42.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = color)
        ) {
            Icon(
                if (label == "Booked") Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(42.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun KitchenOrdersTab(
    summary: com.example.ui.KitchenOrderSummary,
    members: List<Member>,
    bookings: Map<String, MealBookingEntry>,
    lunchDeadline: String,
    nightDeadline: String,
    isManager: Boolean,
    onOverride: (Member) -> Unit,
    onSaveFinalOrder: (mealType: String) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Manager Alert Notification Banner when a deadline has passed
        if (isManager && (summary.isLunchLocked || summary.isNightLocked)) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Manager Kitchen Alert: Deadlines Passed",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (summary.isLunchLocked) {
                            Text(
                                text = "• Lunch deadline ($lunchDeadline) passed: ${summary.lunchTotalPortions} total portions (${summary.lunchMemberCount} members + ${summary.lunchGuestCount} guests). Order locked for kitchen preparation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        if (summary.isNightLocked && !summary.isFriday) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Night deadline ($nightDeadline) passed: ${summary.nightTotalPortions} total portions (${summary.nightMemberCount} members + ${summary.nightGuestCount} guests). Order locked for kitchen preparation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Section Title
        item {
            Text(
                text = "Kitchen Preparation Order Summary",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // LUNCH ORDER CARD with Member and Guest count breakdown
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LUNCH PREPARATION",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (summary.isLunchLocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (summary.isLunchLocked) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = if (summary.isLunchLocked) "Locked ($lunchDeadline)" else "Open (Cutoff: $lunchDeadline)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (summary.isLunchLocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "${summary.lunchTotalPortions}",
                                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Total Lunch Portions",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // Member and Guest Breakdown
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${summary.lunchMemberCount}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text("Members", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${summary.lunchGuestCount}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text("Guests", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }

                    if (summary.lunchFinalOrder != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "✓ Final summary archived at ${summary.lunchFinalOrder.lockedAtDhakaTime.ifBlank { lunchDeadline }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }

                    if (isManager && summary.isLunchLocked) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { onSaveFinalOrder("LUNCH") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Archive / Lock Final Lunch Summary", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // NIGHT ORDER CARD with Member and Guest count breakdown
        item {
            if (summary.isFriday) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("NIGHT ORDER: FRIDAY CLOSED", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("No night preparation on Fridays as per mess rules.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            } else {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NIGHT PREPARATION (DINNER)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (summary.isNightLocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (summary.isNightLocked) {
                                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = if (summary.isNightLocked) "Locked ($nightDeadline)" else "Open (Cutoff: $nightDeadline)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (summary.isNightLocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "${summary.nightTotalPortions}",
                                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Total Night Portions",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            // Member and Guest Breakdown
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "${summary.nightMemberCount}",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text("Members", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "${summary.nightGuestCount}",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text("Guests", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }

                        if (summary.nightFinalOrder != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "✓ Final summary archived at ${summary.nightFinalOrder.lockedAtDhakaTime.ifBlank { nightDeadline }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }

                        if (isManager && summary.isNightLocked) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { onSaveFinalOrder("NIGHT") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Archive / Lock Final Night Summary", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Individual Member Bookings",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        items(members) { member ->
            val booking = bookings[member.id] ?: MealBookingEntry()
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(member.displayName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            if (member.room.isNotBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("(${member.room})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BookingStatusBadge("Lunch", booking.statusLunch, booking.guestLunch)
                            Spacer(modifier = Modifier.width(8.dp))
                            if (!summary.isFriday) {
                                BookingStatusBadge("Night", booking.statusNight, booking.guestNight)
                            }
                        }
                        if (booking.managerOverrideReason.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            ) {
                                Text(
                                    text = "⚠️ Override Reason: ${booking.managerOverrideReason}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (isManager) {
                        OutlinedButton(
                            onClick = { onOverride(member) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Override", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun BookingStatusBadge(meal: String, status: String, guest: Int) {
    val displayStatus = if (status == "BOOKED") "BOOKED" else "CANCELLED"
    val isBooked = displayStatus == "BOOKED"
    val bgColor = if (isBooked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val textColor = if (isBooked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = "$meal: $displayStatus" + (if (guest > 0 && isBooked) " (+$guest Guest)" else ""),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun AttendanceTab(
    members: List<Member>,
    attendances: Map<String, MealAttendanceEntry>,
    isFriday: Boolean,
    lunchActive: Boolean,
    nightActive: Boolean,
    isManager: Boolean,
    onSaveAttendance: (memberId: String, memberName: String, attendedL: Boolean, attendedN: Boolean, guestL: Int, guestN: Int) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Actual Confirmed Attendance",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Attendance determines actual meal units and financial accounts. Unit formula: Both meals = 2 units, Only Lunch = 1.5 units, Only Night = 1 unit. Friday/Single-meal day = 1 unit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                    if (!isManager) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "(View-only. Only Managers have permission to confirm attendance).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        items(members) { member ->
            val attendance = attendances[member.id] ?: MealAttendanceEntry()
            MemberAttendanceCard(
                member = member,
                attendance = attendance,
                isFriday = isFriday,
                lunchActive = lunchActive,
                nightActive = nightActive,
                isManager = isManager,
                onSave = { attendedL, attendedN, guestL, guestN ->
                    onSaveAttendance(member.id, member.displayName, attendedL, attendedN, guestL, guestN)
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}

@Composable
private fun MemberAttendanceCard(
    member: Member,
    attendance: MealAttendanceEntry,
    isFriday: Boolean,
    lunchActive: Boolean,
    nightActive: Boolean,
    isManager: Boolean,
    onSave: (attendedL: Boolean, attendedN: Boolean, guestL: Int, guestN: Int) -> Unit
) {
    var attendedLunch by remember(attendance) { mutableStateOf(attendance.attendedLunch) }
    var attendedNight by remember(attendance) { mutableStateOf(attendance.attendedNight) }
    var guestLunch by remember(attendance) { mutableIntStateOf(attendance.guestLunchAttended) }
    var guestNight by remember(attendance) { mutableIntStateOf(attendance.guestNightAttended) }

    val calculatedUnits = MessCalculationEngine.calculateAttendanceUnits(
        isFriday = isFriday,
        lunchServiceStatus = if (lunchActive) "ACTIVE" else "CANCELLED",
        nightServiceStatus = if (nightActive) "ACTIVE" else "CANCELLED",
        attendance = MealAttendanceEntry(attendedLunch, attendedNight, guestLunch, guestNight)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(member.displayName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$calculatedUnits units",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Checkboxes for Lunch & Night
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = attendedLunch,
                        onCheckedChange = {
                            if (isManager && lunchActive) {
                                attendedLunch = it
                                onSave(it, attendedNight, guestLunch, guestNight)
                            }
                        },
                        enabled = isManager && lunchActive
                    )
                    Text(
                        text = if (lunchActive) "Attended Lunch" else "Lunch Cancelled",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (!isFriday) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = attendedNight,
                            onCheckedChange = {
                                if (isManager && nightActive) {
                                    attendedNight = it
                                    onSave(attendedLunch, it, guestLunch, guestNight)
                                }
                            },
                            enabled = isManager && nightActive
                        )
                        Text(
                            text = if (nightActive) "Attended Night" else "Night Cancelled",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Guests section
            if (isManager) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Guest Attended: Lunch $guestLunch | Night $guestNight", style = MaterialTheme.typography.labelSmall)
                    Row {
                        TextButton(
                            onClick = {
                                guestLunch += 1
                                onSave(attendedLunch, attendedNight, guestLunch, guestNight)
                            }
                        ) {
                            Text("+Guest Lunch", fontSize = 11.sp)
                        }
                        if (!isFriday) {
                            TextButton(
                                onClick = {
                                    guestNight += 1
                                    onSave(attendedLunch, attendedNight, guestLunch, guestNight)
                                }
                            ) {
                                Text("+Guest Night", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSwitcher(
    currentDate: String,
    isFriday: Boolean,
    onDateChanged: (String) -> Unit,
    onTodayClicked: () -> Unit
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = DhakaTimeHelper.DHAKA_ZONE
    }

    fun shiftDays(days: Int) {
        try {
            val date = sdf.parse(currentDate) ?: Date()
            val cal = Calendar.getInstance(DhakaTimeHelper.DHAKA_ZONE).apply {
                time = date
                add(Calendar.DAY_OF_YEAR, days)
            }
            onDateChanged(sdf.format(cal.time))
        } catch (_: Exception) {}
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { shiftDays(-1) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Day")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = currentDate + (if (isFriday) " (Friday)" else ""),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row {
                IconButton(onClick = onTodayClicked) {
                    Icon(Icons.Default.Today, contentDescription = "Today", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { shiftDays(1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Day")
                }
            }
        }
    }
}

@Composable
private fun ConfigureDeadlineDialog(
    currentLunch: String,
    currentNight: String,
    onDismiss: () -> Unit,
    onSave: (lunch: String, night: String) -> Unit
) {
    var lunchDeadline by remember { mutableStateOf(currentLunch) }
    var nightDeadline by remember { mutableStateOf(currentNight) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Booking Deadlines", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Times must be entered in 24-hr format (HH:mm) under Asia/Dhaka timezone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = lunchDeadline,
                    onValueChange = { lunchDeadline = it },
                    label = { Text("Lunch Deadline (Asia/Dhaka)") },
                    placeholder = { Text("e.g. 09:00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nightDeadline,
                    onValueChange = { nightDeadline = it },
                    label = { Text("Night Deadline (Asia/Dhaka)") },
                    placeholder = { Text("e.g. 17:00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(lunchDeadline.trim(), nightDeadline.trim()) },
                enabled = lunchDeadline.isNotBlank() && nightDeadline.isNotBlank()
            ) {
                Text("Save Deadlines")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ServiceStatusDialog(
    date: String,
    lunchActive: Boolean,
    nightActive: Boolean,
    onDismiss: () -> Unit,
    onSave: (lunchStatus: String, nightStatus: String, reason: String) -> Unit
) {
    var isLunchActive by remember { mutableStateOf(lunchActive) }
    var isNightActive by remember { mutableStateOf(nightActive) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Meal Service Status", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Date: $date", fontWeight = FontWeight.Bold)
                Text(
                    text = "If a meal is unexpectedly cancelled due to external issues (e.g. cook absent, gas shortage), update here. The remaining meal will count as 1 unit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isLunchActive, onCheckedChange = { isLunchActive = it })
                    Text("Lunch Service Active")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isNightActive, onCheckedChange = { isNightActive = it })
                    Text("Night Service Active")
                }

                if (!isLunchActive || !isNightActive) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Cancellation Reason") },
                        placeholder = { Text("e.g. Gas cylinder shortage") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        if (isLunchActive) "ACTIVE" else "CANCELLED",
                        if (isNightActive) "ACTIVE" else "CANCELLED",
                        reason.trim()
                    )
                }
            ) {
                Text("Update Service Status")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ManagerOverrideBookingDialog(
    member: Member,
    date: String,
    prevBooking: MealBookingEntry,
    isFriday: Boolean,
    onDismiss: () -> Unit,
    onOverride: (statusL: String, statusN: String, guestL: Int, guestN: Int, reason: String) -> Unit
) {
    var statusLunch by remember { mutableStateOf(if (prevBooking.statusLunch == "CANCELLED") "CANCELLED" else "BOOKED") }
    var statusNight by remember { mutableStateOf(if (prevBooking.statusNight == "CANCELLED") "CANCELLED" else "BOOKED") }
    var guestLunch by remember { mutableIntStateOf(prevBooking.guestLunch) }
    var guestNight by remember { mutableIntStateOf(prevBooking.guestNight) }
    var overrideReason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Admin Override Booking", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Member: ${member.displayName} ($date)", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Admins can override locked bookings after deadlines. A recorded reason is strictly required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Text("Lunch Status:", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("BOOKED", "CANCELLED").forEach { st ->
                        FilterChip(
                            selected = statusLunch == st,
                            onClick = { statusLunch = st },
                            label = { Text(st, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
                if (statusLunch == "BOOKED") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Guest Lunch Portions:", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (guestLunch > 0) guestLunch -= 1 }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Text("$guestLunch", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
                            IconButton(onClick = { guestLunch += 1 }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                if (!isFriday) {
                    Text("Night Status:", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("BOOKED", "CANCELLED").forEach { st ->
                            FilterChip(
                                selected = statusNight == st,
                                onClick = { statusNight = st },
                                label = { Text(st, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                    if (statusNight == "BOOKED") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Guest Night Portions:", style = MaterialTheme.typography.bodySmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { if (guestNight > 0) guestNight -= 1 }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Text("$guestNight", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
                                IconButton(onClick = { guestNight += 1 }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = overrideReason,
                    onValueChange = { overrideReason = it },
                    label = { Text("Reason for Override (Required)") },
                    placeholder = { Text("e.g. Member called manager after deadline") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (overrideReason.trim().isNotBlank()) {
                        onOverride(statusLunch, if (isFriday) "CANCELLED" else statusNight, guestLunch, if (isFriday) 0 else guestNight, overrideReason.trim())
                    }
                },
                enabled = overrideReason.trim().isNotBlank()
            ) {
                Text("Save Override")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
