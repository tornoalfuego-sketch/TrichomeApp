package com.trichome.app.ui.screens.cycle

import com.trichome.app.data.entity.SuperCycleConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression test for a real data-loss bug in the supercycle screen.
 *
 * `SuperCycleScreen` used to hold the photoperiod in three independent
 * `mutableIntStateOf` values defaulted to 18/6 and assign them from `vm.config`
 * on the line right after a fire-and-forget `vm.load(plantId)`. The read always
 * won the race and saw `null`, so the sliders stayed on 18/6 while the result
 * card showed the saved cycle. Pressing Save without dragging a slider wrote
 * 18/6 over the saved photoperiod — and since the v3 migration the supercycle
 * belongs to the tent, so one bad save hit every plant in it.
 *
 * Compose's `LaunchedEffect` and the sliders themselves cannot be asserted here:
 * there is no `compose-ui-test` on the unit-test classpath, only on
 * `androidTest`, which needs a device. So what is pinned is the decision the
 * screen now delegates to — [SuperCycleForm], a plain immutable object with no
 * Compose or coroutine types — plus a source check that the screen keeps
 * delegating to it and has no second writer of the hours.
 *
 * What this does NOT cover: the actual slider drag, the recomposition, and the
 * order in which `LaunchedEffect(plantId)` runs against the composition. Those
 * remain device-only. What it does guarantee is that a save before the load
 * resolves has no request to write, and that a resolved load puts the saved
 * numbers — not the defaults — into every value the save path reads.
 */
class SuperCycleFormTest {

    private val now: Long = 1_800_000_000_000L

    /** `cycleStartAt` is pinned: the entity defaults it to the wall clock. */
    private fun config(
        lightHours: Int = 13,
        darkHours: Int = 13,
        presetType: String = "custom",
        cycleStartAt: Long = now - 5 * 86_400_000L
    ) = SuperCycleConfig(
        id = 3L,
        tentId = 4L,
        plantId = 11L,
        lightHours = lightHours,
        darkHours = darkHours,
        cycleStartAt = cycleStartAt,
        presetType = presetType
    )

    private fun fail(message: String): Nothing = throw AssertionError(message)

    @Test
    fun anUnresolvedFormCannotProduceASaveRequest() {
        // The invariant that closes the window. Before the load returns there is
        // nothing the user has seen, so there is nothing to write: `null`, not
        // 18/6.
        val form = SuperCycleForm()

        assertFalse("save must not be reachable before the load resolves", form.canSave)
        assertNull("an unresolved load must not produce a request", form.saveRequest(now))
    }

    @Test
    fun aResolvedLoadPutsTheSavedHoursOnScreenNotTheDefaults() {
        val form = SuperCycleForm().onLoaded(config(lightHours = 13, darkHours = 13))

        assertTrue(form.canSave)
        assertEquals(13, form.lightHours)
        assertEquals(13, form.darkHours)
        assertEquals("custom", form.presetType)
        assertFalse("a load alone is not an edit", form.dirty)
    }

    @Test
    fun savingWithoutTouchingASliderRewritesTheSavedRowNotTheDefaults() {
        // The exact user action that lost data: open the screen, press Save.
        val saved = config(lightHours = 13, darkHours = 13)
        val request = SuperCycleForm().onLoaded(saved).saveRequest(now)
            ?: fail("a resolved load must produce a request")

        assertEquals(13, request.lightHours)
        assertEquals(13, request.darkHours)
        assertEquals(saved.presetType, request.presetType)
        assertEquals(
            "saving must not reset the cycle start",
            saved.cycleStartAt,
            request.cycleStartAt
        )
    }

    @Test
    fun aTentWithNoConfigStartsLoadedOnTheDefaults() {
        // Null is a resolved load with nothing saved, not a failure — and it must
        // not leave Save permanently disabled, or a tent could never get a
        // config at all.
        val form = SuperCycleForm().onLoaded(null)

        assertTrue(form.canSave)
        assertEquals(18, form.lightHours)
        assertEquals(6, form.darkHours)
        assertEquals("18/6", form.presetType)
        assertNull(form.savedCycleStartAt)
        assertNotNull("a first save needs a start instant", form.saveRequest(now))
    }

    @Test
    fun aFirstSaveUsesTheInstantTheRequestWasMade() {
        val request = SuperCycleForm().onLoaded(null).saveRequest(now) ?: fail("expected a request")

        assertEquals(now, request.cycleStartAt)
    }

    @Test
    fun movingASliderMarksTheFormCustomAndKeepsTheSavedStart() {
        val saved = config(lightHours = 13, darkHours = 13)
        val form = SuperCycleForm().onLoaded(saved).withLightHours(20)

        assertTrue(form.dirty)
        assertEquals(20, form.lightHours)
        assertEquals("custom", form.presetType)
        assertEquals(saved.cycleStartAt, form.savedCycleStartAt)
    }

    @Test
    fun aConfigArrivingAfterAnEditDoesNotDiscardTheEdit() {
        // The same defect class from the other side: a late emission must not
        // yank the slider back under the user's finger. It still adopts the saved
        // start instant, because the preview and the save both run against it.
        val edited = SuperCycleForm().withLightHours(20).withDarkHours(4)
        val reloaded = edited.onLoaded(config(lightHours = 13, darkHours = 13, cycleStartAt = 4242L))

        assertEquals(20, reloaded.lightHours)
        assertEquals(4, reloaded.darkHours)
        assertEquals(4242L, reloaded.savedCycleStartAt)
        assertTrue(reloaded.loaded)
    }

    @Test
    fun aLoadBeforeAnyEditDoesAdoptTheSavedRow() {
        val reloaded = SuperCycleForm().onLoaded(config(lightHours = 13, darkHours = 13))
            .onLoaded(config(lightHours = 16, darkHours = 8))

        assertEquals(16, reloaded.lightHours)
        assertEquals(8, reloaded.darkHours)
    }

    @Test
    fun everyPresetChipSetsTheHoursItAdvertises() {
        val base = SuperCycleForm().onLoaded(config())

        assertEquals(18 to 6, base.withPreset("18/6").let { it.lightHours to it.darkHours })
        assertEquals(12 to 12, base.withPreset("12/12").let { it.lightHours to it.darkHours })
        assertEquals(24 to 0, base.withPreset("24/0").let { it.lightHours to it.darkHours })
    }

    @Test
    fun theCustomChipKeepsTheHoursTheUserChose() {
        val custom = SuperCycleForm().onLoaded(config(lightHours = 13, darkHours = 13)).withPreset("custom")

        assertEquals(13, custom.lightHours)
        assertEquals(13, custom.darkHours)
        assertEquals("custom", custom.presetType)
    }

    @Test
    fun sliderValuesAreClampedToTheRangeTheSliderCanProduce() {
        // `Slider(steps = 23, valueRange = 0f..24f)` cannot emit these, but a
        // clamped state is cheaper than a slider that renders off its own range.
        val form = SuperCycleForm().onLoaded(config())

        assertEquals(24, form.withLightHours(99).lightHours)
        assertEquals(0, form.withDarkHours(-5).darkHours)
    }

    @Test
    fun theScreenHasNoSecondWriterOfTheHours() {
        // The state object is only a fix if the screen actually uses it. A
        // re-introduced `lightHours = config.lightHours` in the composable would
        // pass every behavioural test above and reintroduce the bug.
        val code = sourceOf("SuperCycleScreen.kt")
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")

        assertTrue(
            "the screen must drive the sliders from SuperCycleForm",
            code.contains("form.withLightHours") && code.contains("form.withDarkHours")
        )
        assertTrue(
            "the screen must hand the loaded row to the form",
            code.contains("SuperCycleForm().onLoaded(vm.load(plantId))")
        )
        assertFalse(
            "the sliders must not be assigned from the view model directly: " +
                "that read is the race, and it always saw null",
            Regex("""lightHours\s*=\s*config\.lightHours""").containsMatchIn(code) ||
                Regex("""darkHours\s*=\s*config\.darkHours""").containsMatchIn(code)
        )
        assertTrue(
            "Save must be gated on the load having resolved",
            code.contains("enabled = form.canSave")
        )
    }

    private fun sourceOf(name: String): String =
        listOf(
            File("src/main/java/com/trichome/app/ui/screens/cycle"),
            File("app/src/main/java/com/trichome/app/ui/screens/cycle")
        )
            .filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles { f -> f.name == name }?.toList() ?: emptyList() }
            .firstOrNull()
            ?.readText(Charsets.UTF_8)
            ?: error("$name is not in the cycle package")
}