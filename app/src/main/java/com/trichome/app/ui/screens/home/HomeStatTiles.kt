package com.trichome.app.ui.screens.home

/**
 * A home stat tile: an emoji, a label, and where it leads.
 *
 * The destination lives in a type of its own rather than inline in the
 * composable so it can be asserted on the JVM. The bug this fixes: `StatCard` had
 * no click parameter at all, so "Plantas", "Carpas" and "Hoy" were dead on screen
 * — and because they are styled as cards beside a row of working actions, a
 * grower reasonably reads a count as one tap from the list it counts.
 */
data class HomeStatTile(
    val icon: String,
    val labelEs: String,
    /**
     * The route this tile navigates to, or null when it has nowhere to go yet.
     *
     * Every shipped tile has one. The field is nullable so a future tile can be
     * added honestly — as a [pendingRoute] and no click handler — rather than by
     * pointing at a screen that does not exist.
     */
    val route: String?,
    /**
     * The route this tile stands in for, when it has not shipped yet.
     *
     * Non-blank only when [route] is a substitute. Making the substitution
     * greppable is the point: otherwise `plants -> tents` reads as deliberate and
     * nobody goes looking for the missing list.
     */
    val pendingRoute: String = ""
) {
    companion object {
        /**
         * "Plantas" opens the tent list, because there is no plant list yet.
         *
         * Plants live in Room with a `tentId`, and the only screen that lists them
         * is reached through a tent row, so a real list needs a new screen and is a
         * v1.3.0 item. `tents` is the honest interim answer — the tent list does
         * show plants, one hop further — and it beats a tile that swallows the tap.
         */
        const val PLANTS_STAND_IN_NOTE =
            "plants (v1.3.0: falta la pantalla de lista de plantas)"

        val PLANTS = HomeStatTile(
            icon = "🌱",
            labelEs = "Plantas",
            route = "tents",
            pendingRoute = PLANTS_STAND_IN_NOTE
        )

        val TENTS = HomeStatTile(icon = "🏕️", labelEs = "Carpas", route = "tents")

        /** The counter is today's event count, so the calendar is its home. */
        val TODAY = HomeStatTile(icon = "📒", labelEs = "Hoy", route = "calendar")

        /** Display order, left to right. */
        val ALL: List<HomeStatTile> = listOf(PLANTS, TENTS, TODAY)
    }
}
