package com.trichome.app.ui.screens.journal

import com.trichome.app.data.entity.GrowEvent
import com.trichome.app.data.entity.Plant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.io.File

/**
 * Contract for the journal event editor.
 *
 * ## The gap this fills
 *
 * `EventDao.updateEvent` had no caller at all. A journal entry could be recorded
 * and deleted but never corrected: a grower who logged the wrong pH, the wrong
 * day or the wrong plant had exactly one way to fix it, and that way destroyed
 * every other field on the row.
 *
 * ## Why the test is about the unexposed columns
 *
 * The dangerous failure here is not a crash and not a wrong number. It is a
 * *successful* save that quietly clears the fields the dialog never showed:
 * `imagePath`, `nutrientN/P/K`, `defoliationLevel`, `diagnosisResult`,
 * `diagnosisCertainty`, `groupId` and `isActive`. The row comes back looking
 * fine, so nothing reports a problem, and the data is gone. The project's
 * data-loss budget is zero (AGENTS.md §11), so these tests seed a row with every
 * one of those columns populated and assert they survive an edit untouched.
 *
 * That is the property that cannot be observed by looking at the screen, which
 * is why it is asserted here rather than on the device.
 */
class EventEditingTest {

    private val zone: ZoneId = ZoneId.of("Europe/Madrid")
    private val plant = Plant(id = 7L, tentId = 1L, name = "Blueberry")

    /** A row with *every* column the entity has populated. */
    private fun fullRow() = GrowEvent(
        id = 42L,
        plantId = 7L,
        groupId = "group-abc",
        eventType = "TEMPERATURE_HUMIDITY",
        timestamp = Instant.parse("2026-10-04T09:28:00Z").toEpochMilli(),
        notes = "Riego de la mañana",
        temperature = 24.5f,
        humidity = 62f,
        ph = 6.4f,
        ec = 1.8f,
        nutrientN = 120f,
        nutrientP = 40f,
        nutrientK = 180f,
        amount = 1500f,
        height = 87.5f,
        lampDistance = 45f,
        trainingType = "LST",
        defoliationLevel = 3,
        vpd = 0.92f,
        trichomeMaturity = "lechoso",
        diagnosisResult = "mosaico",
        diagnosisCertainty = 0.81f,
        imagePath = "/data/user/0/com.trichome.app/files/leaf_01.jpg",
        isActive = true
    )

    private fun fail(message: String): Nothing = throw AssertionError(message)

    // ── The data-loss contract ───────────────────────────────────────

    @Test
    fun editingTheNotesLeavesEveryColumnTheDialogDoesNotShowUntouched() {
        // The exact regression: a form that rebuilds the row from the fields it
        // shows would null all eleven of these on the first save.
        val stored = fullRow()
        val edited = EventEditing.applyTo(
            EventEditing.draftOf(stored, zone).copy(notes = "Riego de la tarde"),
            stored,
            zone
        )

        assertEquals("the edited field is the one that changed", "Riego de la tarde", edited.notes)
        assertEquals("id must never move or the update inserts a second row", 42L, edited.id)
        assertEquals("groupId", "group-abc", edited.groupId)
        assertEquals("isActive", true, edited.isActive)
        assertEquals("imagePath", "/data/user/0/com.trichome.app/files/leaf_01.jpg", edited.imagePath)
        assertEquals("nutrientN", 120f, edited.nutrientN!!, 0.0001f)
        assertEquals("nutrientP", 40f, edited.nutrientP!!, 0.0001f)
        assertEquals("nutrientK", 180f, edited.nutrientK!!, 0.0001f)
        assertEquals("defoliationLevel", 3, edited.defoliationLevel)
        assertEquals("diagnosisResult", "mosaico", edited.diagnosisResult)
        assertEquals("diagnosisCertainty", 0.81f, edited.diagnosisCertainty!!, 0.0001f)
    }

    @Test
    fun theWholeRowIsByteIdenticalWhenNothingIsEdited() {
        // Open the dialog, press Save. The row must come back exactly as it was --
        // this is the test that would have caught the same class of bug as
        // `SuperCycleFormTest`, where pressing Save without dragging a slider
        // wrote the defaults over a saved cycle.
        val stored = fullRow()
        val edited = EventEditing.applyTo(EventEditing.draftOf(stored, zone), stored, zone)

        assertEquals(stored, edited)
    }

    @Test
    fun aMeasurementIsNeverSeededFromADefault() {
        // The draft is seeded from the row, so an editor that opened with pH 6.0
        // on a row that read 6.4 would write 6.0 the moment Save was pressed.
        val stored = fullRow()
        val draft = EventEditing.draftOf(stored, zone)

        assertEquals(6.4f, draft.ph!!, 0.0001f)
        assertEquals(24.5f, draft.temperature!!, 0.0001f)
        assertEquals(62f, draft.humidity!!, 0.0001f)
        assertEquals(1.8f, draft.ec!!, 0.0001f)
        assertEquals(1500f, draft.amount!!, 0.0001f)
        assertEquals(87.5f, draft.height!!, 0.0001f)
        assertEquals(45f, draft.lampDistance!!, 0.0001f)
        assertEquals(0.92f, draft.vpd!!, 0.0001f)
        assertEquals("LST", draft.trainingType)
        assertEquals("lechoso", draft.trichomeMaturity)
        assertEquals("Riego de la mañana", draft.notes)
    }

    @Test
    fun aColumnThatWasNeverRecordedStaysNullRatherThanBecomingAZero() {
        // A measurement the grower never took is not a measurement of zero. Writing
        // 0.0 would invent a reading and, for humidity, put a physically impossible
        // value into the chart.
        val sparse = GrowEvent(plantId = 7L, eventType = "PRUNING", notes = "Poda")
        val edited = EventEditing.applyTo(EventEditing.draftOf(sparse, zone), sparse, zone)

        assertNull(edited.ph)
        assertNull(edited.temperature)
        assertNull(edited.humidity)
        assertNull(edited.amount)
        assertNull(edited.height)
    }

    @Test
    fun clearingTheNotesStoresNullRatherThanAnEmptyString() {
        // Matches how the create path writes it, so an edited row and a created
        // one are indistinguishable to anything that reads `notes`.
        val stored = fullRow()
        val edited = EventEditing.applyTo(
            EventEditing.draftOf(stored, zone).copy(notes = "   "),
            stored,
            zone
        )

        assertNull(edited.notes)
    }

    // ── Type, plant and time ────────────────────────────────────────

    @Test
    fun theTypeCanBeChangedAndTheChangeIsWrittenVerbatim() {
        // `eventType` is a raw String on the entity, so the storage key is what has
        // to land -- not the Spanish label, which is presentation only.
        val stored = fullRow()
        val edited = EventEditing.applyTo(
            EventEditing.draftOf(stored, zone).copy(eventType = "VPD"),
            stored,
            zone
        )

        assertEquals("VPD", edited.eventType)
    }

    @Test
    fun anUnknownStoredTypeIsOpenedAsItIsRatherThanRelabelled() {
        // A row written by a newer version of the app must not be silently
        // rewritten as an irrigation the moment an older build opens it.
        val stored = fullRow().copy(eventType = "SOME_FUTURE_TYPE")
        val draft = EventEditing.draftOf(stored, zone)

        assertEquals("SOME_FUTURE_TYPE", draft.eventType)
    }

    @Test
    fun theEventCanBeMovedToAnotherPlant() {
        val stored = fullRow()
        val edited = EventEditing.applyTo(
            EventEditing.draftOf(stored, zone).copy(plantId = 9L),
            stored,
            zone
        )

        assertEquals(9L, edited.plantId)
    }

    @Test
    fun theDayCanBeMovedAndTheTimeKept() {
        val stored = fullRow()
        val draft = EventEditing.draftOf(stored, zone)
        val edited = EventEditing.applyTo(
            EventEditing.withDayShift(draft, draft.epochDay, 3),
            stored,
            zone
        )

        val before = Instant.ofEpochMilli(stored.timestamp).atZone(zone).toLocalDate()
        val after = Instant.ofEpochMilli(edited.timestamp).atZone(zone).toLocalDate()
        assertEquals(before.plusDays(3), after)
        // The clock time is untouched by a day shift, which is the whole point of
        // storing minute-of-day separately from the date.
        assertEquals(
            Instant.ofEpochMilli(stored.timestamp).atZone(zone).toLocalTime(),
            Instant.ofEpochMilli(edited.timestamp).atZone(zone).toLocalTime()
        )
    }

    @Test
    fun theTimeOfDayCanBeMovedAndTheDayKept() {
        val stored = fullRow()
        val draft = EventEditing.draftOf(stored, zone)
        val edited = EventEditing.applyTo(draft.copy(minuteOfDay = 18 * 60 + 45), stored, zone)

        val moment = Instant.ofEpochMilli(edited.timestamp).atZone(zone)
        assertEquals(18, moment.hour)
        assertEquals(45, moment.minute)
        assertEquals(
            Instant.ofEpochMilli(stored.timestamp).atZone(zone).toLocalDate(),
            moment.toLocalDate()
        )
    }

    @Test
    fun theTimestampRoundTripsThroughTheDraft() {
        // What the form reads is what the row gets back, in the zone it was read in.
        val stored = fullRow()
        val draft = EventEditing.draftOf(stored, zone)
        val edited = EventEditing.applyTo(draft, stored, zone)

        assertEquals(stored.timestamp, edited.timestamp)
    }

    @Test
    fun anImpossibleStoredTimeIsClampedRatherThanThrowing() {
        // `LocalDate.atTime` throws on hour 25. A row written by a future build, or
        // a corrupted one, must not take the editor down on open.
        val stored = fullRow().copy(timestamp = 4_000_000_000_000L)
        val draft = EventEditing.draftOf(stored, zone).copy(minuteOfDay = 5_000)

        val edited = EventEditing.applyTo(draft, stored, zone)
        assertNotNull("a clamped row must still be writable", edited.timestamp)
    }

    @Test
    fun theDaySliderIsBoundedToTheTravelTheFormOffers() {
        val draft = EventEditing.draftOf(fullRow(), zone)
        val stored = draft.epochDay

        // A shift past the limit lands exactly on the limit, measured from the day
        // the row was stored on. An earlier version clamped the delta instead of
        // the result, so a far-out draft plus a far-out shift stayed far out.
        assertEquals(
            stored + EventEditing.MAX_DAY_SHIFT,
            EventEditing.withDayShift(draft, stored, 5_000).epochDay
        )
        assertEquals(
            stored - EventEditing.MAX_DAY_SHIFT,
            EventEditing.withDayShift(draft, stored, -5_000).epochDay
        )
    }

    @Test
    fun theDayShiftIsAbsoluteSoRepeatedCompositionsDoNotDrift() {
        // The slider reports a position, not a delta, so asking for "day 3" twice
        // must land on the same day both times. A delta-based helper would give
        // day 3 and then day 6, and a recomposition would quietly walk the event
        // into the future.
        val draft = EventEditing.draftOf(fullRow(), zone)
        val stored = draft.epochDay

        val once = EventEditing.withDayShift(draft, stored, 3)
        val twice = EventEditing.withDayShift(once, stored, 3)
        val thrice = EventEditing.withDayShift(twice, stored, 3)

        assertEquals(stored + 3, once.epochDay)
        assertEquals("a second composition must not move the day again", stored + 3, twice.epochDay)
        assertEquals(stored + 3, thrice.epochDay)
    }

    @Test
    fun theDayShiftResolvesAgainstTheStoredDayNotTheCurrentOne() {
        // The stored day is the fixed reference. A draft already moved out to +10
        // and then dragged to +20 must land on +20, not on +30.
        val draft = EventEditing.draftOf(fullRow(), zone)
        val stored = draft.epochDay
        val moved = EventEditing.withDayShift(draft, stored, 10)

        assertEquals(stored + 20, EventEditing.withDayShift(moved, stored, 20).epochDay)
    }

    @Test
    fun aDraftThatDriftedPastTheSliderStillRendersAPositionItCanRepresent() {
        val draft = EventEditing.draftOf(fullRow(), zone)
        val stored = draft.epochDay
        val far = draft.copy(epochDay = stored + 10_000)

        // Clamped, so the thumb is not drawn past its own end. Without this the
        // next keystroke would snap it to the limit and silently move the event.
        assertEquals(
            EventEditing.MAX_DAY_SHIFT,
            EventEditing.dayShiftFor(far, stored)
        )
    }

    @Test
    fun aShiftWithinTheRangeIsHonouredExactly() {
        val draft = EventEditing.draftOf(fullRow(), zone)
        val stored = draft.epochDay

        assertEquals(
            stored - 7,
            EventEditing.withDayShift(draft, stored, -7).epochDay
        )
        // The shift is reported back from the stored day, so the thumb comes to
        // rest where the user put it.
        assertEquals(
            -7,
            EventEditing.dayShiftFor(EventEditing.withDayShift(draft, stored, -7), stored)
        )
    }

    // ── Labels ──────────────────────────────────────────────────────

    @Test
    fun theTimeLabelIsZeroPaddedToTheClockTheUserReads() {
        assertEquals("00:00", EventEditing.timeLabel(0))
        assertEquals("09:05", EventEditing.timeLabel(9 * 60 + 5))
        assertEquals("23:59", EventEditing.timeLabel(1_439))
    }

    @Test
    fun theDateLabelIsDayFirstTheWayTheAppWritesDates() {
        assertEquals("04/10/2026", EventEditing.dateLabel(LocalDate.of(2026, 10, 4).toEpochDay()))
    }

    // ── Validation ──────────────────────────────────────────────────

    @Test
    fun aValidDraftIsAccepted() {
        val draft = EventEditing.draftOf(fullRow(), zone)
        assertNull(EventEditing.validate(draft, setOf(7L)))
    }

    @Test
    fun anEventWithNoPlantIsRejectedWithAReadableSpanishMessage() {
        // The add dialog's discipline: report the reason, do not fail silently.
        // A row with a plant that no longer exists cannot be re-pointed at plant 0.
        val draft = EventEditing.draftOf(fullRow(), zone).copy(plantId = 999L)
        val problem = EventEditing.validate(draft, setOf(7L))

        assertNotNull("a foreign plant must be refused", problem)
        assertTrue("the message must be in Spanish: $problem", problem!!.contains("planta"))
    }

    @Test
    fun aRowWhosePlantNoLongerExistsIsStillRefused() {
        val draft = EventEditing.draftOf(fullRow(), zone)
        assertNotNull(
            "the plant list is empty, so there is nothing to re-point the row at",
            EventEditing.validate(draft, emptySet())
        )
    }

    @Test
    fun aBlankTypeIsRejected() {
        val draft = EventEditing.draftOf(fullRow(), zone).copy(eventType = "")
        assertNotNull(EventEditing.validate(draft, setOf(7L)))
    }

    @Test
    fun aPhOutsideTheScaleIsRejectedAndTheMessageSaysTheRange() {
        listOf(-0.5f, 15f).forEach { bad ->
            val draft = EventEditing.draftOf(fullRow(), zone).copy(ph = bad)
            val problem = EventEditing.validate(draft, setOf(7L))
            assertNotNull("pH $bad must be refused", problem)
            assertTrue("the message must name the range: $problem", problem!!.contains("pH"))
        }
    }

    @Test
    fun theEndsOfThePhScaleAreAccepted() {
        listOf(0f, 7f, 14f).forEach { good ->
            val draft = EventEditing.draftOf(fullRow(), zone).copy(ph = good)
            assertNull("pH $good is on the scale", EventEditing.validate(draft, setOf(7L)))
        }
    }

    @Test
    fun humidityOutsideZeroToOneHundredIsRejected() {
        val draft = EventEditing.draftOf(fullRow(), zone).copy(humidity = 140f)
        val problem = EventEditing.validate(draft, setOf(7L))

        assertNotNull("140% is not a humidity", problem)
        assertTrue(problem!!.contains("humedad"))
    }

    @Test
    fun aNegativeMagnitudeIsRejected() {
        // A negative volume or height was never recorded, so a sign is a typo.
        listOf(
            "temperatura" to EventEditing.draftOf(fullRow(), zone).copy(temperature = -1f),
            "cantidad" to EventEditing.draftOf(fullRow(), zone).copy(amount = -5f),
            "altura" to EventEditing.draftOf(fullRow(), zone).copy(height = -2f),
            "VPD" to EventEditing.draftOf(fullRow(), zone).copy(vpd = -0.1f)
        ).forEach { (label, draft) ->
            val problem = EventEditing.validate(draft, setOf(7L))
            assertNotNull("a negative $label must be refused", problem)
            assertTrue("the message must name the field: $problem", problem!!.contains(label))
        }
    }

    @Test
    fun everyProblemIsWrittenInSpanish() {
        // A user-facing string that fell back to English would be invisible in the
        // source review and obvious on the device.
        val bad = listOf(
            EventEditing.draftOf(fullRow(), zone).copy(eventType = ""),
            EventEditing.draftOf(fullRow(), zone).copy(plantId = 999L),
            EventEditing.draftOf(fullRow(), zone).copy(ph = 99f),
            EventEditing.draftOf(fullRow(), zone).copy(humidity = -1f),
            EventEditing.draftOf(fullRow(), zone).copy(amount = -1f)
        )
        bad.forEach { draft ->
            val problem = EventEditing.validate(draft, setOf(7L)) ?: return@forEach
            assertTrue(
                "not a Spanish sentence: \"$problem\"",
                problem.first().isUpperCase() && problem.contains(' ')
            )
        }
    }

    // ── Wiring ──────────────────────────────────────────────────────

    @Test
    fun theJournalScreenWiresTheEditDialogToTheViewModel() {
        // The state object and the DAO method are only a fix if the screen uses
        // them. A re-introduced dead `updateEvent` would pass every test above.
        val code = stripComments(sourceOf("JournalScreen.kt"))

        assertTrue(
            "the screen must hold the event being edited",
            code.contains("editingEvent") && code.contains("mutableStateOf<GrowEvent?>")
        )
        assertTrue(
            "the row must offer the edit action",
            code.contains("onEdit = { editingEvent = e }")
        )
        assertTrue(
            "the save must go through the view model, which owns the repository",
            code.contains("vm.updateEvent(edited)")
        )
        assertTrue(
            "the dialog must only close once the write succeeded, or a refused " +
                "update throws away every edit the grower just made",
            code.contains("vm.updateEvent(edited) { editingEvent = null }")
        )
    }

    @Test
    fun theViewModelReportsTheRealOutcomeOfTheUpdate() {
        val code = stripComments(sourceOfInViewModels())

        assertTrue(
            "updateEvent must report whether the write landed",
            code.contains("fun updateEvent(event: GrowEvent, onSaved: (Boolean) -> Unit")
        )
        assertTrue(
            "a refused write has to say so rather than looking saved",
            code.contains("No se pudo guardar el evento.")
        )
    }

    @Test
    fun theDialogBodyIsTheOnlyVerticalScrollOnTheScreen() {
        // `ScrollOwnershipTest` exists because a nested vertical scroll measured
        // with an infinite maximum height killed the process. The dialog body
        // scrolls; the type and plant pickers are `LazyRow`s, which are horizontal
        // and contribute no vertical axis.
        val code = stripComments(sourceOf("EditEventDialog.kt"))
        val scrolls = Regex("""\.verticalScroll\(""").findAll(code).count()

        assertEquals(
            "the dialog must have exactly one vertical scroll owner, found $scrolls",
            1,
            scrolls
        )
        assertTrue(
            "the cap must be applied to the scroll container, not the content",
            Regex("""\.heightIn\([^)]*\)\s*\n?\s*\.verticalScroll\(""").containsMatchIn(code)
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun sourceOf(name: String): String =
        listOf(
            File("src/main/java/com/trichome/app/ui/screens/journal"),
            File("app/src/main/java/com/trichome/app/ui/screens/journal")
        )
            .filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles { f -> f.name == name }?.toList() ?: emptyList() }
            .firstOrNull()
            ?.readText(Charsets.UTF_8)
            ?: error("$name is not in the journal package")

    private fun sourceOfInViewModels(): String =
        listOf(
            File("src/main/java/com/trichome/app/viewmodel"),
            File("app/src/main/java/com/trichome/app/viewmodel")
        )
            .filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles { f -> f.name == "MainViewModels.kt" }?.toList() ?: emptyList() }
            .firstOrNull()
            ?.readText(Charsets.UTF_8)
            ?: error("MainViewModels.kt is not in the viewmodel package")

    /** Block and line comments removed, so this file's own KDoc cannot satisfy an
     *  assertion or trip a scan. */
    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""(?m)//.*$"""), " ")
}
