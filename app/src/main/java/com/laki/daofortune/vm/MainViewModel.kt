package com.laki.daofortune.vm

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.laki.daofortune.billing.BillingManager
import com.laki.daofortune.billing.BillingUiState
import com.laki.daofortune.core.FavourableDates
import com.laki.daofortune.core.FavourableDay
import com.laki.daofortune.core.FortuneEngine
import com.laki.daofortune.core.FortuneModule
import com.laki.daofortune.core.LuopanEngine
import com.laki.daofortune.core.LuopanSnapshot
import com.laki.daofortune.core.ModuleReading
import com.laki.daofortune.core.ReadingKey
import com.laki.daofortune.core.YearSign
import com.laki.daofortune.data.AccessState
import com.laki.daofortune.data.ProfileState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class UiState(
    val snapshot: LuopanSnapshot,
    val readings: Map<ReadingKey, ModuleReading> = emptyMap(),
    /**
     * One-line verdict per module, always visible. The headline is the teaser:
     * it says what the plate points to without giving away the score, the
     * reasoning or the advice, which stay behind the unlock.
     */
    val previews: Map<FortuneModule, String> = emptyMap(),
    /** The day the plate on screen describes. */
    val selectedDate: LocalDate,
    /** True when that day is the one the reader is living in. */
    val isToday: Boolean = true,
    /** The reader's year branch (nian ming), when they have chosen one. */
    val yearSign: YearSign? = null,
    /** The forward scan: the best days ahead, ranked. */
    val favourableDays: List<FavourableDay> = emptyList(),
    val selected: ReadingKey? = null,
    val pendingModule: FortuneModule? = null,
    val showPaywall: Boolean = false,
    val showAbout: Boolean = false,
    val notice: String? = null,
    val freeReadingsLeft: Int = 1,
    val unlocked: Boolean = false
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val access = AccessState(app)
    private val profile = ProfileState(app)

    /**
     * Play Billing, told what to do when it finds an existing purchase.
     *
     * The check runs whenever the connection comes up, so a purchase that
     * completed while the app was closed still unlocks the app.
     */
    val billing = BillingManager(app) { onOwnershipConfirmed() }

    private var scanJob: Job? = null

    private val _ui = MutableStateFlow(initialState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val billingState: StateFlow<BillingUiState> get() = billing.state

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun initialState(): UiState {
        val today = LocalDate.now()
        val snapshot = LuopanEngine.snapshot(LocalDateTime.now(), zone)
        return UiState(
            snapshot = snapshot,
            selectedDate = today,
            isToday = true,
            yearSign = profile.yearSign,
            freeReadingsLeft = access.freeReadingsLeft(),
            unlocked = access.isUnlocked
        ).let { it.copy(previews = previewsFor(it.snapshot, it.yearSign)) }
    }

    init {
        billing.start()
        rescanFavourableDays()
    }

    /** The headline of each module — the free teaser shown on the home cards. */
    private fun previewsFor(snapshot: LuopanSnapshot, yearSign: YearSign?): Map<FortuneModule, String> {
        val natal = yearSign?.branch
        return FortuneModule.entries.associateWith { FortuneEngine.reading(snapshot, it, natal).headline }
    }

    /**
     * Re-reads the plate when the app comes back to the foreground: the hour
     * pillar moves during the day, and the whole plate turns over at midnight.
     * Readings already produced are kept — they belong to the day they were
     * drawn on.
     */
    fun refresh() {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val state = _ui.value
        if (state.selectedDate != today) {
            // A future or past day was picked: its plate does not change with the clock.
            rescanFavourableDays()
            return
        }
        val fresh = LuopanEngine.snapshot(now, zone)
        _ui.update {
            it.copy(
                snapshot = fresh,
                selectedDate = today,
                isToday = true,
                previews = previewsFor(fresh, it.yearSign)
            )
        }
        rescanFavourableDays()
    }

    /**
     * Shows the plate of another day.
     *
     * A day that is not today is read as a **day plate**: no hour is involved,
     * because a day that has not happened yet has no hour.
     */
    fun selectDate(date: LocalDate) {
        val today = LocalDate.now()
        val snapshot = if (date == today) {
            LuopanEngine.snapshot(LocalDateTime.now(), zone)
        } else {
            LuopanEngine.dayPlate(date, zone)
        }
        _ui.update {
            it.copy(
                snapshot = snapshot,
                selectedDate = date,
                isToday = date == today,
                previews = previewsFor(snapshot, it.yearSign),
                selected = null
            )
        }
    }

    /** Sets or clears the reader's year branch. */
    fun setYearSign(sign: YearSign?) {
        profile.setYearSign(sign)
        val state = _ui.value
        val natal = sign?.branch
        _ui.update {
            it.copy(
                yearSign = sign,
                previews = previewsFor(it.snapshot, sign)
            )
        }
        // The readings drawn without the year sign are still true, but they are
        // no longer the readings the reader would get now.
        if (natal != null || state.yearSign != null) {
            _ui.update { it.copy(readings = emptyMap(), selected = null) }
        }
        rescanFavourableDays()
    }

    /** Opens a module. The first reading is free; later ones need the unlock. */
    fun open(module: FortuneModule) {
        val state = _ui.value
        val key = ReadingKey(state.selectedDate, module)
        state.readings[key]?.let {
            _ui.update { current -> current.copy(selected = key) }
            return
        }
        if (!access.canRead()) {
            _ui.update { current -> current.copy(pendingModule = module, showPaywall = true) }
            return
        }
        val reading = FortuneEngine.reading(state.snapshot, module, state.yearSign?.branch)
        val spentFree = !access.isUnlocked && !access.freeReadingUsed
        if (spentFree) access.consumeFreeReading()
        _ui.update { current ->
            current.copy(
                readings = current.readings + (key to reading),
                selected = key,
                freeReadingsLeft = access.freeReadingsLeft(),
                notice = if (spentFree) NOTICE_FREE_READING_USED else null
            )
        }
    }

    /** Picks one of the days from the forward scan. */
    fun openFavourableDay(date: LocalDate) {
        selectDate(date)
    }

    fun closeReading() {
        _ui.update { it.copy(selected = null, notice = null) }
    }

    /** Takes the banner down once the reader has read it. */
    fun dismissNotice() {
        _ui.update { it.copy(notice = null) }
    }

    companion object {
        /** Shown after the free reading has been spent. */
        const val NOTICE_FREE_READING_USED = "FREE_READING_USED"

        /** Shown when "Restore purchase" finds nothing on the account. */
        const val NOTICE_NO_PURCHASE = "NO_PURCHASE_FOUND"
    }

    fun dismissPaywall() {
        _ui.update { it.copy(showPaywall = false, pendingModule = null) }
    }

    fun showAbout(show: Boolean) {
        _ui.update { it.copy(showAbout = show) }
    }

    /**
     * Scans the days ahead for the ones the plate favours.
     *
     * Sixty day plates are computed off the main thread; the scan is cheap
     * because the solar terms are cached per year.
     */
    private fun rescanFavourableDays() {
        val natal = _ui.value.yearSign?.branch
        val today = LocalDate.now()
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val days = withContext(Dispatchers.Default) {
                FavourableDates.scan(today, zone, natal)
            }
            _ui.update { it.copy(favourableDays = days) }
        }
    }

    fun purchase(activity: Activity) {
        viewModelScope.launch {
            val bought = billing.purchase(activity)
            if (bought) {
                access.unlock()
                _ui.update { it.copy(unlocked = true, freeReadingsLeft = access.freeReadingsLeft()) }
                val pending = _ui.value.pendingModule
                _ui.update { it.copy(showPaywall = false, pendingModule = null) }
                pending?.let { open(it) }
            }
        }
    }

    fun restore() {
        viewModelScope.launch {
            val restored = billing.restore()
            if (restored) {
                access.unlock()
                _ui.update { it.copy(unlocked = true, freeReadingsLeft = access.freeReadingsLeft()) }
                val pending = _ui.value.pendingModule
                _ui.update { it.copy(showPaywall = false, pendingModule = null) }
                pending?.let { open(it) }
            } else if (billing.state.value !is BillingUiState.Failed) {
                // A clean answer from Play: this Google account owns nothing.
                _ui.update { it.copy(notice = NOTICE_NO_PURCHASE) }
            }
            // A failure keeps its own message in the paywall sheet.
        }
    }

    /** Play reports an existing purchase — the app is unlocked. */
    private fun onOwnershipConfirmed() {
        access.unlock()
        _ui.update {
            it.copy(unlocked = true, freeReadingsLeft = access.freeReadingsLeft())
        }
    }
}
