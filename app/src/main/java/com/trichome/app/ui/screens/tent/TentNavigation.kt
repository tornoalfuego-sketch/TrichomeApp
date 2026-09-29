package com.trichome.app.ui.screens.tent

import com.trichome.app.data.entity.Plant

/**
 * Tap targets for the tent list.
 *
 * The card's `onOpen` used to close over its *tent* and resolve that tent's
 * first plant on every tap, so tapping row two opened plant one, and an empty
 * tent navigated to the `plant_detail/-1` sentinel. A tap now carries the plant
 * it belongs to, and a card with nothing to open resolves to `null`.
 */
object TentNavigation {

    /**
     * Route for [plantId], matching the `plant_detail/{plantId}` destination
     * declared in `AppNavigation`.
     *
     * The argument stays a `Long` because that screen reads it with
     * `getLong("plantId")`.
     */
    fun plantDetailRoute(plantId: Long): String = "plant_detail/$plantId"

    /**
     * Resolves what a tap on a tent card should open.
     *
     * @param tapped the row that was tapped, or `null` when the card holds no
     *   plants at all.
     * @return the route to navigate to, or `null` when there is nothing to open
     *   and the caller must stay put.
     */
    fun targetForTap(tapped: Plant?): String? = tapped?.let { plantDetailRoute(it.id) }
}
