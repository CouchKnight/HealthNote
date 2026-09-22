package io.github.couchknight.healthnote.app.ui

import android.app.Application
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.couchknight.healthnote.app.data.AppGraph
import io.github.couchknight.healthnote.app.data.PublishStatus
import io.github.couchknight.healthnote.model.DoseSlot
import io.github.couchknight.healthnote.model.DoseSource
import io.github.couchknight.healthnote.model.DoseStatus
import io.github.couchknight.healthnote.model.Formats
import io.github.couchknight.healthnote.model.LogResult
import io.github.couchknight.healthnote.model.MedicationSchedule
import io.github.couchknight.healthnote.model.MonthReport
import io.github.couchknight.healthnote.render.DocumentRenderer
import io.github.couchknight.healthnote.render.android.AndroidPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID

enum class Screen(val title: String) {
    TODAY("Today"),
    MEDS("Medications"),
    EDIT("Edit schedule"),
    SLEEP("Sleep"),
    DOC("Document"),
    NFC("NFC tag"),
    ;

    /** Edit and NFC are reached from Meds and go back to it. */
    val parent: Screen? get() = if (this == EDIT || this == NFC) MEDS else null
}

/** A due dose awaiting Taken / Skip in the confirm sheet. */
data class PendingDose(val schedule: MedicationSchedule, val slot: DoseSlot)

data class ScheduleDraft(
    val id: String?,
    val name: String,
    val dose: String,
    val time: LocalTime,
    val days: Set<DayOfWeek>,
    val colorSlot: Int,
    val remind: Boolean,
) {
    val isNew: Boolean get() = id == null
}

data class Toast(val message: String, val id: Long = System.nanoTime())

data class UiState(
    val screen: Screen,
    val report: MonthReport,
    val sheet: PendingDose? = null,
    val toast: Toast? = null,
    val draft: ScheduleDraft? = null,
    val tagScheduleId: String? = null,
    val publish: PublishStatus,
    val pdfBytes: Int? = null,
    val batteryExempt: Boolean = false,
    /** Design default: tapping a due dose opens a confirm sheet rather than logging at once. */
    val confirmBeforeLogging: Boolean = true,
    val showSyncLine: Boolean = true,
)

class HealthNoteViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = AppGraph()

    private val _ui = MutableStateFlow(
        UiState(
            screen = Screen.TODAY,
            report = buildReport(),
            publish = graph.publisher.status(),
            tagScheduleId = graph.schedules.all().firstOrNull()?.id,
        ),
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var renderJob: Job? = null

    init {
        refreshBattery()
        renderDocument()
    }

    private fun buildReport(): MonthReport {
        val now = graph.clock.now()
        return MonthReport(YearMonth.from(now), now, graph.schedules.all(), graph.doses.events(), graph.sleep.nights())
    }

    private fun refresh() {
        _ui.update { it.copy(report = buildReport(), publish = graph.publisher.status()) }
        renderDocument()
    }

    /** Renders the real month PDF off the main thread, for its size on the Document screen. */
    private fun renderDocument() {
        renderJob?.cancel()
        val report = _ui.value.report
        renderJob = viewModelScope.launch {
            val size = withContext(Dispatchers.Default) {
                runCatching { AndroidPdf.render(getApplication<Application>(), DocumentRenderer.render(report)).size }.getOrNull()
            }
            _ui.update { it.copy(pdfBytes = size) }
        }
    }

    fun refreshBattery() {
        val app = getApplication<Application>()
        val pm = app.getSystemService(PowerManager::class.java)
        _ui.update { it.copy(batteryExempt = pm?.isIgnoringBatteryOptimizations(app.packageName) == true) }
    }

    private fun toast(message: String) = _ui.update { it.copy(toast = Toast(message)) }

    fun dismissToast(id: Long) = _ui.update { if (it.toast?.id == id) it.copy(toast = null) else it }

    fun go(screen: Screen) = _ui.update { it.copy(screen = screen, sheet = null) }

    /** Returns false when there is nowhere further back to go inside the app. */
    fun back(): Boolean {
        val s = _ui.value
        return when {
            s.sheet != null -> { closeSheet(); true }
            s.screen.parent != null -> { go(s.screen.parent!!); true }
            s.screen != Screen.TODAY -> { go(Screen.TODAY); true }
            else -> false
        }
    }

    // Dose logging --------------------------------------------------------------------------

    fun openDose(scheduleId: String, slot: DoseSlot) {
        val schedule = graph.schedules.all().firstOrNull { it.id == scheduleId } ?: return
        if (_ui.value.confirmBeforeLogging) {
            _ui.update { it.copy(sheet = PendingDose(schedule, slot)) }
        } else {
            log(PendingDose(schedule, slot), DoseStatus.TAKEN)
        }
    }

    fun closeSheet() = _ui.update { it.copy(sheet = null) }

    fun confirm(taken: Boolean) {
        val pending = _ui.value.sheet ?: return
        log(pending, if (taken) DoseStatus.TAKEN else DoseStatus.SKIPPED)
    }

    private fun log(p: PendingDose, status: DoseStatus) {
        val now = graph.clock.now()
        val result = graph.doses.log(p.schedule.id, now, status, DoseSource.MANUAL, slot = p.slot)
        _ui.update { it.copy(sheet = null) }
        when (result) {
            is LogResult.Logged -> {
                graph.publisher.markDoseLogged()
                refresh()
                toast(
                    if (status == DoseStatus.TAKEN) {
                        "Logged ${p.schedule.name} at ${Formats.clock(now.toLocalTime())}. Nice one."
                    } else {
                        "${p.schedule.name} skipped for today."
                    },
                )
            }
            is LogResult.AlreadyLogged -> toast("${p.schedule.name} was already logged for that slot.")
            LogResult.NoSlotInWindow, LogResult.UnknownSchedule -> toast("Couldn't match that to a scheduled dose.")
        }
    }

    // Schedules -----------------------------------------------------------------------------

    fun editSchedule(id: String?) {
        val s = id?.let { i -> graph.schedules.all().firstOrNull { it.id == i } }
        val draft = if (s != null) {
            ScheduleDraft(s.id, s.name, s.dose, s.timesOfDay.min(), s.daysOfWeek, s.colorSlot, s.remind)
        } else {
            val used = graph.schedules.all().map { it.colorSlot }.toSet()
            ScheduleDraft(
                null, "", "", LocalTime.of(8, 0), DayOfWeek.entries.toSet(),
                (0..3).firstOrNull { it !in used } ?: 0, remind = true,
            )
        }
        _ui.update { it.copy(draft = draft, screen = Screen.EDIT) }
    }

    fun updateDraft(transform: (ScheduleDraft) -> ScheduleDraft) =
        _ui.update { st -> st.draft?.let { st.copy(draft = transform(it)) } ?: st }

    fun saveDraft() {
        val d = _ui.value.draft ?: return
        if (d.name.isBlank()) return toast("Give the medication a name first.")
        val existing = d.id?.let { i -> graph.schedules.all().firstOrNull { it.id == i } }
        val today = graph.clock.now().toLocalDate()
        graph.schedules.save(
            MedicationSchedule(
                id = d.id ?: UUID.randomUUID().toString(),
                name = d.name.trim(),
                dose = d.dose.trim(),
                timesOfDay = listOf(d.time),
                daysOfWeek = d.days,
                startDate = existing?.startDate ?: today,
                endDate = existing?.endDate,
                colorSlot = d.colorSlot,
                active = d.days.isNotEmpty(),
                remind = d.remind,
            ),
        )
        _ui.update { it.copy(draft = null, screen = Screen.MEDS) }
        refresh()
        toast("${d.name.trim()} saved.")
    }

    fun deleteDraft() {
        val d = _ui.value.draft ?: return
        d.id?.let { graph.schedules.delete(it) }
        _ui.update { it.copy(draft = null, screen = Screen.MEDS) }
        refresh()
        if (d.id != null) toast("${d.name} removed.")
    }

    // NFC and publishing --------------------------------------------------------------------

    fun pickTag(scheduleId: String) {
        val name = graph.schedules.all().firstOrNull { it.id == scheduleId }?.name ?: return
        _ui.update { it.copy(tagScheduleId = scheduleId) }
        toast("Tag will log $name.")
    }

    fun sendNow() {
        val report = _ui.value.report
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.Default) {
                runCatching { AndroidPdf.render(getApplication<Application>(), DocumentRenderer.render(report)) }.getOrNull()
            }
            if (bytes == null) return@launch toast("Couldn't render the document.")
            val now = graph.clock.now()
            graph.publisher.sendToDeviceNow(bytes, now)
            _ui.update { it.copy(publish = graph.publisher.status()) }
            toast("Sent to INBOX over Wi-Fi at ${Formats.clock(now.toLocalTime())}.")
        }
    }
}
