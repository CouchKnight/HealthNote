package io.github.couchknight.healthnote.app.data

import io.github.couchknight.healthnote.model.DoseRepository
import io.github.couchknight.healthnote.model.InMemoryDoseRepository
import io.github.couchknight.healthnote.model.MedicationSchedule
import io.github.couchknight.healthnote.model.NightSleep
import io.github.couchknight.healthnote.model.sample.SampleData
import java.time.LocalDate
import java.time.LocalDateTime

/*
 * Stand-ins for the modules DESIGN.md §2 puts behind the UI. Each interface is what the real
 * implementation provides; the in-memory versions are seeded with the design's sample month.
 *
 *   ScheduleStore   -> Room in :core-data
 *   DoseRepository  -> Room in :core-data (the in-memory one already has the real slot matching
 *                      and per-slot idempotence from :core-model)
 *   SleepSource     -> Health Connect reader in :core-health
 *   Publisher       -> SupernoteCloudClient / BrowseAccessClient in :core-sync, scheduled by
 *                      DailyPublishWorker / DebouncedPublishWorker in :core-work
 */

interface Clock {
    fun now(): LocalDateTime
}

/** Fixed at the moment the designs depict, so the demo data reads as it does in the mockups. */
object DemoClock : Clock {
    override fun now(): LocalDateTime = SampleData.now
}

interface ScheduleStore {
    fun all(): List<MedicationSchedule>
    fun save(schedule: MedicationSchedule)
    fun delete(id: String)
}

/**
 * Edits are applied in place here. The real store must version a schedule on edit (end the old
 * one yesterday, start the new one today) so past slots keep the schedule they were logged
 * against, as the editor's footnote promises.
 */
class InMemoryScheduleStore(initial: List<MedicationSchedule>) : ScheduleStore {
    private val byId = LinkedHashMap<String, MedicationSchedule>().apply { initial.forEach { put(it.id, it) } }

    @Synchronized override fun all() = byId.values.toList()

    @Synchronized override fun save(schedule: MedicationSchedule) {
        byId[schedule.id] = schedule
    }

    @Synchronized override fun delete(id: String) {
        byId.remove(id)
    }
}

interface SleepSource {
    fun nights(): Map<LocalDate, NightSleep>
}

class InMemorySleepSource(private val nights: Map<LocalDate, NightSleep>) : SleepSource {
    override fun nights() = nights
}

data class PublishStatus(
    val lastUpload: LocalDateTime,
    val nextRun: LocalDateTime,
    val route: String,
    /** A dose was logged since the last upload; the debounced worker will send it. */
    val pendingChange: Boolean,
    val sentToDeviceAt: LocalDateTime?,
)

interface Publisher {
    fun status(): PublishStatus
    fun markDoseLogged()

    /** Browse & Access upload to the tablet's INBOX (DESIGN.md §5.3). */
    fun sendToDeviceNow(bytes: ByteArray, at: LocalDateTime)
}

class FakePublisher(now: LocalDateTime) : Publisher {
    private var status = PublishStatus(
        lastUpload = now.toLocalDate().atTime(6, 12),
        nextRun = now.toLocalDate().plusDays(1).atTime(6, 0),
        route = "Supernote Cloud (auto pull)",
        pendingChange = false,
        sentToDeviceAt = null,
    )

    @Synchronized override fun status() = status

    @Synchronized override fun markDoseLogged() {
        status = status.copy(pendingChange = true)
    }

    @Synchronized override fun sendToDeviceNow(bytes: ByteArray, at: LocalDateTime) {
        status = status.copy(sentToDeviceAt = at)
    }
}

class AppGraph(
    val clock: Clock = DemoClock,
    val schedules: ScheduleStore = InMemoryScheduleStore(SampleData.schedules),
    val sleep: SleepSource = InMemorySleepSource(SampleData.nights),
    val publisher: Publisher = FakePublisher(clock.now()),
) {
    val doses: DoseRepository = InMemoryDoseRepository({ schedules.all() }, SampleData.events)
}
