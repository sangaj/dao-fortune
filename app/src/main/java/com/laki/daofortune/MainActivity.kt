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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laki.daofortune.ui.AboutSheet
import com.laki.daofortune.ui.DaoFortuneTheme
import com.laki.daofortune.ui.DreamSheet
import com.laki.daofortune.ui.HomeScreen
import com.laki.daofortune.ui.PaywallSheet
import com.laki.daofortune.ui.ReadingSheet
import com.laki.daofortune.vm.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {            DaoFortuneTheme {
                val state by viewModel.ui.collectAsStateWithLifecycle()
                val billingState by viewModel.billingState.collectAsStateWithLifecycle()

                // Android 13+ asks for the notification permission at runtime;
                // older versions only need the toggle.
                val notifyPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) viewModel.setDailyReadingEnabled(true)
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
                        freeReadingsLeft = state.freeReadingsLeft,
                        unlocked = state.unlocked,
                        notice = state.notice,
                        notifyEnabled = state.notifyEnabled,
                        onToggleNotify = {
                            if (state.notifyEnabled) {
                                viewModel.setDailyReadingEnabled(false)
                            } else if (
                                Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(
                                    this, Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
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
                        onDismissNotice = viewModel::dismissNotice,
                        onAbout = { viewModel.showAbout(true) }
                    )

                    val selected = state.selected
                    val reading = selected?.let { state.readings[it] }
                    if (reading != null) {
                        ReadingSheet(reading = reading, onClose = viewModel::closeReading)
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
                }
            }
        }
    }
}
