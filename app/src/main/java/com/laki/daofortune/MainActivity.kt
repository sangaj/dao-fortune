package com.laki.daofortune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laki.daofortune.ui.AboutSheet
import com.laki.daofortune.ui.DaoFortuneTheme
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

                Surface(color = MaterialTheme.colorScheme.background) {
                    HomeScreen(
                        snapshot = state.snapshot,
                        readings = state.readings,
                        previews = state.previews,
                        selectedDate = state.selectedDate,
                        isToday = state.isToday,
                        yearSign = state.yearSign,
                        favourableDays = state.favourableDays,
                        freeReadingsLeft = state.freeReadingsLeft,
                        unlocked = state.unlocked,
                        notice = state.notice,
                        onOpen = viewModel::open,
                        onSelectDate = viewModel::selectDate,
                        onSetYearSign = viewModel::setYearSign,
                        onDismissNotice = viewModel::dismissNotice,
                        onAbout = { viewModel.showAbout(true) }
                    )

                    val selected = state.selected
                    val reading = selected?.let { state.readings[it] }
                    if (reading != null) {
                        ReadingSheet(reading = reading, onClose = viewModel::closeReading)
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
