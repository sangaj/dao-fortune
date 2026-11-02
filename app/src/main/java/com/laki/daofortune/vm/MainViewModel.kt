package com.laki.daofortune.vm

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.laki.daofortune.billing.BillingManager
import com.laki.daofortune.billing.BillingUiState
import com.laki.daofortune.core.Branch
import com.laki.daofortune.core.CalendarEngine
import com.laki.daofortune.core.DreamEngine
import com.laki.daofortune.core.DreamReading
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
import com.laki.daofortune.notify.NotificationScheduler
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
import java.time.LocalTime
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
    /**
     * The reader's birth day, when they have given one. Optional and local
     * only; when set it anchors every reading on the birth day's own pillar.
     */
    val birthDate: LocalDate? = null,
    /** The forward scan: the best days ahead, ranked. */
    val favourableDays: List<FavourableDay> = emptyList(),
    /** The days the reader asked to be reminded of. */
    val remindedDays: Set<LocalDate> = emptySet(),
    /** True when the daily reading notification is switched on. */
    val notifyEnabled: Boolean = false,
    val selected: ReadingKey? = null,
    /** The module waiting on the reader's yes before the one free reading is spent. */
    val confirmFreeSpend: FortuneModule? = null,
    /** True when a dream reading waits on the same yes. */
    val confirmFreeDream: Boolean = false,
    val pendingModule: FortuneModule? = null,
    val showPaywall: Boolean = false,
    val showAbout: Boolean = false,
    val notice: String? = null,
    val freeReadingsLeft: Int = 1,
    val unlocked: Boolean = false,
    /**
     * The dream the reader has written.
     *
     * It is never written to disk: it lives in this state object only, and
     * closing the sheet takes it away again.
     */
    val dreamText: String = "",
    /** The wake-up time the reader typed, as free text. */
    val dreamWokeAt: String = "",
    /** The reading of the dream, when it has been read. */
    val dreamReading: DreamReading? = null,
    /** The plate the dream was read against — the night the dream belongs to. */
    val dreamPlate: LuopanSnapshot? = null,
    /** True while the dream sheet is open. */
    val showDream: Boolean = false,
    /** True when a dream is waiting for the unlock to be bought. */
    val pendingDream: Boolean = false
) {
    /**
     * The branch every reading is measured against.
     *
     * The birth day's own pillar branch when a birth day was given (the
     * day-master anchor), otherwise the year sign. Noon is used for the birth
     * day so the 23:00 day-turn never makes the pillar ambiguous.
     */
    val natalBranch: Branch?
        get() = birthDate?.let { CalendarEngine.dayGanZhi(it.atTime(12, 0)).branch }
            ?: yearSign?.branch
}

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

    /** The localised tip price, when the tip jar exists in the Play Console. */
    val tipPrice: StateFlow<String?> get() = billing.tipPrice

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun initialState(): UiState {
        val today = LocalDate.now()
        val snapshot = LuopanEngine.snapshot(LocalDateTime.now(), zone)
        return UiState(
            snapshot = snapshot,
            selectedDate = today,
            isToday = true,
            yearSign = profile.yearSign,
            birthDate = profile.birthDate,
            notifyEnabled = profile.dailyReadingEnabled,
            remindedDays = profile.remindedDays.filter { it >= today }.toSet(),
            freeReadingsLeft = access.freeReadingsLeft(),
            unlocked = access.isUnlocked
        ).let {
            it.copy(
                previews = previewsFor(it.snapshot, it.natalBranch),
                // Today's Fortune is free every day, in full: it is drawn as soon
                // as the plate is known, so its card carries the score.
                readings = FortuneEngine.alwaysFree(today, it.snapshot, it.natalBranch)
            )
        }
    }

    init {
        billing.start()
        rescanFavourableDays()
        updateWidgets()
    }

    /** Refreshes the home-screen widget, if the reader has placed one. */
    private fun updateWidgets() {
        viewModelScope.launch {
            runCatching { com.laki.daofortune.widget.TodayWidget.refresh(getApplication()) }
        }
    }

    /** The headline of each module — the free teaser shown on the home cards. */
    private fun previewsFor(snapshot: LuopanSnapshot, natal: Branch?): Map<FortuneModule, String> {
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
                previews = previewsFor(fresh, it.natalBranch),
                // The hour moved, so today's fortune is redrawn with it.
                readings = it.readings +
                    FortuneEngine.alwaysFree(today, fresh, it.natalBranch)
            )
        }
        rescanFavourableDays()
        updateWidgets()
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
                previews = previewsFor(snapshot, it.natalBranch),
                // A different day is a different plate: today's fortune is free,
                // so it is drawn for that day too.
                readings = it.readings +
                    FortuneEngine.alwaysFree(date, snapshot, it.natalBranch),
                selected = null
            )
        }
    }

    /** Sets or clears the reader's year branch. */
    fun setYearSign(sign: YearSign?) {
        profile.setYearSign(sign)
        val state = _ui.value
        // The readings drawn without the year sign are still true, but they are
        // no longer the readings the reader would get now.
        val anchorChanged = sign != null || state.yearSign != null
        _ui.update {
            val next = it.copy(yearSign = sign)
            next.copy(
                previews = previewsFor(next.snapshot, next.natalBranch),
                // Today's Fortune is free, so it is redrawn against the new
                // anchor straight away; the rest go only when the anchor moved.
                readings = FortuneEngine.alwaysFree(next.selectedDate, next.snapshot, next.natalBranch) +
                    (if (anchorChanged) emptyMap() else next.readings),
                selected = if (anchorChanged) null else next.selected,
                dreamReading = if (anchorChanged) null else next.dreamReading
            )
        }
        rescanFavourableDays()
    }

    /**
     * Sets or clears the reader's birth day.
     *
     * When a birth day is set it becomes the anchor every reading is measured
     * against (the day pillar of that day), taking precedence over the year
     * sign. It stays on the device and out of the backup, exactly like the
     * year sign.
     */
    fun setBirthDate(date: LocalDate?) {
        profile.setBirthDate(date)
        val state = _ui.value
        val anchorChanged = date != null || state.birthDate != null
        _ui.update {
            val next = it.copy(birthDate = date)
            next.copy(
                previews = previewsFor(next.snapshot, next.natalBranch),
                // Same as the year sign: the free reading follows the anchor.
                readings = FortuneEngine.alwaysFree(next.selectedDate, next.snapshot, next.natalBranch) +
                    (if (anchorChanged) emptyMap() else next.readings),
                selected = if (anchorChanged) null else next.selected,
                dreamReading = if (anchorChanged) null else next.dreamReading
            )
        }
        rescanFavourableDays()
    }

    /**
     * Turns the daily reading notification on or off.
     *
     * The caller (MainActivity) is responsible for the runtime permission on
     * Android 13+: it only calls this with `true` once the permission is
     * granted (or not needed).
     */
    fun setDailyReadingEnabled(enabled: Boolean) {
        profile.setDailyReadingEnabled(enabled)
        val app = getApplication<Application>()
        if (enabled) NotificationScheduler.schedule(app) else NotificationScheduler.cancel(app)
        _ui.update { it.copy(notifyEnabled = enabled) }
    }

    /**
     * Marks or unmarks a favourable day for a morning reminder.
     *
     * The reminder is a one-off WorkManager task for 08:00 on that day; the
     * marked dates live in the profile file and the past ones are dropped on
     * every app start.
     */
    fun toggleDayReminder(date: LocalDate) {
        val app = getApplication<Application>()
        val current = _ui.value.remindedDays
        if (date in current) {
            profile.setDayReminded(date, false)
            NotificationScheduler.cancelDayReminder(app, date)
            _ui.update { it.copy(remindedDays = current - date) }
        } else {
            profile.setDayReminded(date, true)
            NotificationScheduler.scheduleDayReminder(app, date)
            _ui.update { it.copy(remindedDays = current + date) }
        }
    }

    /**
     * Opens a module.
     *
     * Today's Fortune is always free — every day, in full. The other four
     * modules keep the old rule: the first reading is free, later ones need
     * the unlock. Because there is exactly ONE free reading, a tap that would
     * spend it asks first ([confirmFreeSpend]); it should never be gone
     * before the reader knew it was the only one.
     */
    fun open(module: FortuneModule) {
        val state = _ui.value
        val key = ReadingKey(state.selectedDate, module)
        state.readings[key]?.let {
            _ui.update { current -> current.copy(selected = key) }
            return
        }
        val alwaysFree = module == FortuneModule.OVERALL
        if (!alwaysFree && !access.canRead()) {
            _ui.update { current -> current.copy(pendingModule = module, showPaywall = true) }
            return
        }
        val spendsFree = !alwaysFree && !access.isUnlocked && !access.freeReadingUsed
        if (spendsFree) {
            _ui.update { it.copy(confirmFreeSpend = module) }
            return
        }
        produceReading(module, key, spendsFree = false)
    }

    /** Spends the one free reading the reader just confirmed — module or dream. */
    fun confirmFreeSpend() {
        val state = _ui.value
        val module = state.confirmFreeSpend
        if (module != null) {
            _ui.update { it.copy(confirmFreeSpend = null) }
            val key = ReadingKey(state.selectedDate, module)
            produceReading(module, key, spendsFree = true)
        } else if (state.confirmFreeDream) {
            _ui.update { it.copy(confirmFreeDream = false) }
            produceDream()
        }
    }

    /** Closes the free-reading confirmation without spending it. */
    fun dismissFreeSpend() {
        _ui.update { it.copy(confirmFreeSpend = null, confirmFreeDream = false) }
    }

    /** Draws a reading and puts it on screen; consumes the free reading when asked. */
    private fun produceReading(module: FortuneModule, key: ReadingKey, spendsFree: Boolean) {
        val state = _ui.value
        val reading = FortuneEngine.reading(state.snapshot, module, state.natalBranch)
        if (spendsFree) access.consumeFreeReading()
        _ui.update { current ->
            current.copy(
                readings = current.readings + (key to reading),
                selected = key,
                freeReadingsLeft = access.freeReadingsLeft(),
                notice = if (spendsFree) NOTICE_FREE_READING_USED else null
            )
        }
    }

    /** Picks one of the days from the forward scan. */
    fun openFavourableDay(date: LocalDate) {
        selectDate(date)
    }

    // ---------------------------------------------------------------- the dream

    /**
     * Opens the dream sheet.
     *
     * It always opens empty: a dream belongs to the night it was dreamed in,
     * and the one that was read before is gone with the sheet.
     */
    fun showDream() {
        _ui.update {
            it.copy(
                showDream = true,
                dreamText = "",
                dreamWokeAt = "",
                dreamReading = null,
                dreamPlate = null,
                pendingDream = false
            )
        }
    }

    fun setDreamText(text: String) {
        _ui.update { it.copy(dreamText = text) }
    }

    fun setDreamWokeAt(text: String) {
        _ui.update { it.copy(dreamWokeAt = text) }
    }

    /**
     * Closes the dream sheet and forgets the dream.
     *
     * The dream text is not stored anywhere, so closing the sheet has to take
     * it with it. That is the whole privacy promise of this module.
     */
    fun closeDream() {
        _ui.update {
            it.copy(
                showDream = false,
                dreamText = "",
                dreamWokeAt = "",
                dreamReading = null,
                dreamPlate = null,
                pendingDream = false
            )
        }
    }

    /**
     * Reads the dream.
     *
     * The first reading is free; a later one needs the unlock, exactly as the
     * five modules do. Because there is exactly ONE free reading, a tap that
     * would spend it asks first ([confirmFreeDream]) — the same courtesy the
     * modules get. When the unlock is bought, the dream is read again.
     */
    fun readDream() {
        val state = _ui.value
        if (state.dreamText.isBlank()) return
        if (!access.canRead()) {
            // The text is kept so that the reading can be produced as soon as
            // the purchase goes through. The dream sheet steps aside for the
            // paywall — two sheets at once is a fight neither of them wins.
            _ui.update {
                it.copy(pendingDream = true, showDream = false, showPaywall = true)
            }
            return
        }
        if (!access.isUnlocked && !access.freeReadingUsed) {
            _ui.update { it.copy(confirmFreeDream = true) }
            return
        }
        produceDream()
    }

    /**
     * Reads the dream against the plate of the night it belongs to.
     *
     * A dream is read against a **day plate**: it is a past event, so it has
     * no hour of its own — the double-hour of waking is used separately, and
     * it is part of the reading rather than part of the plate.
     */
    private fun produceDream() {
        val state = _ui.value
        val text = state.dreamText
        if (text.isBlank()) return

        val now = LocalDateTime.now()
        val wokeAt = DreamEngine.parseWokeAt(state.dreamWokeAt, now.toLocalTime())
        val day = dreamDay(now.toLocalDate(), wokeAt)
        val plate = LuopanEngine.dayPlate(day, zone)
        val image = DreamEngine.extract(text, wokeAt)
        val reading = DreamEngine.reading(plate, image, state.natalBranch)

        val spentFree = !access.isUnlocked && !access.freeReadingUsed
        if (spentFree) access.consumeFreeReading()

        _ui.update {
            it.copy(
                dreamReading = reading,
                dreamPlate = plate,
                pendingDream = false,
                showDream = true,
                freeReadingsLeft = access.freeReadingsLeft(),
                notice = if (spentFree) NOTICE_FREE_READING_USED else it.notice
            )
        }
    }

    /**
     * The day a dream belongs to.
     *
     * A dream is read against the night it was dreamed in. A dream woken in
     * the small hours before three still belongs to the night that began the
     * day before; anything later is read against the day it woke in.
     */
    private fun dreamDay(today: LocalDate, wokeAt: LocalTime): LocalDate =
        if (wokeAt.hour < 3) today.minusDays(1) else today

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

        /** Shown after a tip has been given. */
        const val NOTICE_TIP_THANKED = "TIP_THANKED"
    }

    fun dismissPaywall() {
        _ui.update {
            it.copy(
                showPaywall = false,
                pendingModule = null,
                // A dream that was waiting for the unlock goes back to the sheet
                // it came from, with the words the reader had written.
                showDream = it.pendingDream,
                pendingDream = false
            )
        }
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
        val natal = _ui.value.natalBranch
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
                val pendingDream = _ui.value.pendingDream
                _ui.update {
                    it.copy(showPaywall = false, pendingModule = null, showDream = pendingDream)
                }
                if (pending != null) {
                    open(pending)
                } else if (pendingDream) {
                    produceDream()
                }
            }
        }
    }

    /**
     * Leaves a tip.
     *
     * A tip unlocks nothing and changes no state but the notice: the paywall
     * closes on a thank-you, and whatever was locked stays locked.
     */
    fun tip(activity: Activity) {
        viewModelScope.launch {
            val given = billing.purchaseTip(activity)
            if (given) {
                _ui.update {
                    it.copy(showPaywall = false, notice = NOTICE_TIP_THANKED)
                }
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
                val pendingDream = _ui.value.pendingDream
                _ui.update {
                    it.copy(showPaywall = false, pendingModule = null, showDream = pendingDream)
                }
                if (pending != null) {
                    open(pending)
                } else if (pendingDream) {
                    produceDream()
                }
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
