package com.trichome.app.ui.screens.terpenes

/**
 * The height budget for a scrolling dialog body, as a pure function of the screen.
 *
 * ## The bug this exists for
 *
 * The Master Blender measured its own content twice over. The body was a
 * `Column(Modifier.verticalScroll(state).heightIn(max = 60% of the screen))`, and
 * that is a scroll that cannot scroll: `heightIn` runs *after* `verticalScroll`
 * in the chain, so the scroll was told the viewport was the whole clamped column
 * and there was nothing left to travel. Confirmed on the device rather than
 * reasoned about: a 500px swipe inside the dialog produced a byte-identical
 * screenshot. The fifth slider, the score report and the honesty note were not
 * merely hard to reach, they were gone.
 *
 * The fix is structural, not a bigger number. Raising the cap to 90% would have
 * hidden the symptom on this device and shipped the same bug on a short one.
 * What the body actually needs is *the height the dialog's own chrome leaves*,
 * which is a decision about arithmetic, not about Compose — so it lives here and
 * runs on the JVM.
 *
 * ## Why not just drop the cap
 *
 * Because an uncapped `AlertDialog` body is not "fully visible", it is a dialog
 * that runs off the top and bottom of the window with the confirm button pinned
 * somewhere in the middle of it. A cap is what keeps the title and the confirm
 * button on screen at the same time; the bug was the cap and the scroll fighting
 * over the same measurement, not the cap's existence.
 */
object BlenderLayout {

    /**
     * Height the dialog spends on itself before the body gets a say.
     *
     * The parts, measured on a 393x851dp screen at 440dpi:
     * - 24dp: `AlertDialog`'s own top and bottom content padding
     * - 56dp: the title row (`.headlineSmall` plus its slot)
     * - 48dp: the confirm button row and its 8dp gap
     * - 40dp: the status and navigation bar insets the dialog window reserves
     *
     * It is deliberately generous. Under-reserving reproduces the original bug
     * on a short screen, and over-reserving only costs a few dp of body height on
     * a tall one.
     */
    const val DIALOG_CHROME_DP: Int = 168

    /**
     * Body height for a [screenHeightDp] screen, in dp.
     *
     * Never returns 0 or less: a body with no height is the clipping bug again,
     * just with a smaller number in it. The floor is what a dialog shows when the
     * screen is shorter than its own chrome, and the scroll still works inside it.
     *
     * @param screenHeightDp `LocalConfiguration.screenHeightDp`. Not `screenHeightDp`
     *   minus insets: the dialog window already accounts for the system bars, and
     *   subtracting them twice would under-reserve on a device with a tall inset.
     */
    fun bodyMaxHeightDp(screenHeightDp: Int): Int =
        (screenHeightDp - DIALOG_CHROME_DP).coerceAtLeast(MIN_BODY_DP)

    /**
     * The floor for [bodyMaxHeightDp], in dp.
     *
     * Two slider rows is about the least that is still usable, and it is reached
     * only on a screen shorter than [DIALOG_CHROME_DP] plus this.
     */
    const val MIN_BODY_DP: Int = 200
}
