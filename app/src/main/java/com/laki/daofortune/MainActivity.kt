package com.laki.daofortune

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import com.laki.daofortune.ui.AboutSheet
import com.laki.daofortune.ui.DaoFortuneTheme
import com.laki.daofortune.ui.DreamSheet
import com.laki.daofortune.ui.HomeScreen
import com.laki.daofortune.ui.OnboardingSheet
import com.laki.daofortune.ui.PaywallSheet
import com.laki.daofortune.ui.ReadingSheet
import com.laki.daofortune.vm.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
        // The permission can be taken back in Settings while the app is
        // closed. The bell must not go on promising a reminder that can never
        // arrive.
        if (viewModel.ui.value.notifyEnabled && !notificationsAllowed()) {
            viewModel.setDailyReadingEnabled(false)
            viewModel.showNotice(MainViewModel.NOTICE_NOTIFICATIONS_BLOCKED)
        }
    }

    /** Whether the app may post a notification right now. */
    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {            DaoFortuneTheme {
                val state by viewModel.ui.collectAsStateWithLifecycle()
                val billingState by viewModel.billingState.collectAsStateWithLifecycle()

                // Android 13+ asks for the notification permission at runtime;
                // older versions only need the toggle. One launcher serves both
                // the daily bell and the favourable-day reminders; a pending
                // reminder date says which flow asked.
                var pendingReminder by remember { mutableStateOf<LocalDate?>(null) }
                val notifyPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    val pending = pendingReminder
                    if (pending != null) {
                        pendingReminder = null
                        if (granted) {
                            viewModel.toggleDayReminder(pending)
                        } else {
                            // The day was not marked, and the reminder cannot
                            // fire: say so instead of leaving a silent no.
                            viewModel.showNotice(MainViewModel.NOTICE_REMINDER_BLOCKED)
                        }
                    } else if (granted) {
                        viewModel.setDailyReadingEnabled(true)
                    } else {
                        // The reader said no. The bell goes back to "off" and
                        // the app says why, rather than leaving a switch that
                        // silently does nothing.
                        viewModel.setDailyReadingEnabled(false)
                        viewModel.showNotice(MainViewModel.NOTICE_NOTIFICATIONS_BLOCKED)
                    }
                }

                Surface(color = MaterialTheme.colorScheme.background) {
                    HomeScreen(
                        snapshot = state.snapshot,
                        readings = state.readings,
                        previews = state.previews,
                        selectedDate = state.selectedDate,
                        isToday = state.isToday,
                        yearSign = state.yearSign,
                        birthDate = state.birthDate,
                        favourableDays = state.favourableDays,
                        activity = state.activity,
                        onSelectActivity = viewModel::setActivity,
                        remindedDays = state.remindedDays,
                        freeReadingsLeft = state.freeReadingsLeft,
                        unlocked = state.unlocked,
                        notice = state.notice,
                        notifyEnabled = state.notifyEnabled,
                        onToggleNotify = {
                            if (state.notifyEnabled) {
                                viewModel.setDailyReadingEnabled(false)
                            } else if (!notificationsAllowed()) {
                                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.setDailyReadingEnabled(true)
                            }
                        },
                        onOpen = viewModel::open,
                        onOpenDream = viewModel::showDream,
                        onSelectDate = viewModel::selectDate,
                        onSetYearSign = viewModel::setYearSign,
                        onSetBirthDate = viewModel::setBirthDate,
                        onToggleReminder = { date ->
                            if (date !in state.remindedDays && !notificationsAllowed()) {
                                pendingReminder = date
                                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.toggleDayReminder(date)
                            }
                        },
                        onDismissNotice = viewModel::dismissNotice,
                        onAbout = { viewModel.showAbout(true) }
                    )

                    val selected = state.selected
                    val reading = selected?.let { state.readings[it] }
                    if (reading != null) {
                        ReadingSheet(reading = reading, onClose = viewModel::closeReading)
                    }

                    // One free reading exists; it is never spent without asking,
                    // whether it would be spent on a module or on a dream.
                    val confirmModule = state.confirmFreeSpend
                    if (confirmModule != null || state.confirmFreeDream) {
                        AlertDialog(
                            onDismissRequest = viewModel::dismissFreeSpend,
                            title = { Text("Your one free reading") },
                            text = {
                                Text(
                                    if (confirmModule != null) {
                                        "Open ${confirmModule.title} in full? This is the one free reading " +
                                            "beyond Today's Fortune — after it, every reading needs " +
                                            "the one-off unlock."
                                    } else {
                                        "Read your dream in full? This is the one free reading " +
                                            "beyond Today's Fortune — after it, every reading needs " +
                                            "the one-off unlock."
                                    }
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = viewModel::confirmFreeSpend) {
                                    Text(if (confirmModule != null) "Open it" else "Read it")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = viewModel::dismissFreeSpend) {
                                    Text("Not now")
                                }
                            }
                        )
                    }

                    if (state.showDream) {
                        DreamSheet(
                            text = state.dreamText,
                            wokeAt = state.dreamWokeAt,
                            reading = state.dreamReading,
                            snapshot = state.dreamPlate,
                            yearSign = state.yearSign,
                            onTextChange = viewModel::setDreamText,
                            onWokeAtChange = viewModel::setDreamWokeAt,
                            onRead = viewModel::readDream,
                            onDismiss = viewModel::closeDream
                        )
                    }

                    if (state.showPaywall) {
                        PaywallSheet(
                            billingState = billingState,
                            onBuy = { viewModel.purchase(this) },
                            onRestore = { viewModel.restore() },
                            onDismiss = viewModel::dismissPaywall
                        )
                    }

                    if (state.showAbout) {
                        AboutSheet(onDismiss = { viewModel.showAbout(false) })
                    }

                    if (state.showOnboarding) {
                        OnboardingSheet(
                            yearSign = state.yearSign,
                            notifyEnabled = state.notifyEnabled,
                            onSetYearSign = viewModel::setYearSign,
                            onEnableNotify = {
                                if (!notificationsAllowed()) {
                                    notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.setDailyReadingEnabled(true)
                                }
                            },
                            onDone = viewModel::dismissOnboarding
                        )
                    }
                }
            }
        }
    }
}
