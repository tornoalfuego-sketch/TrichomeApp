package com.trichome.app.ui.screens.tent

import com.trichome.app.data.entity.Plant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the tap target of a tent card.
 *
 * The bug this locks down: the card's `onOpen` closed over the *tent* and
 * always resolved that tent's first plant, so tapping row two opened plant one,
 * and an empty tent navigated to `plant_detail/-1`.
 */
class TentNavigationTest {

    private fun plant(id: Long, tentId: Long = 1L) = Plant(
        id = id,
        name = "Planta $id",
        tentId = tentId
    )

    @Test
    fun tappingAPlantNavigatesToThatPlant() {
        val target = TentNavigation.targetForTap(plant(id = 42L))

        assertEquals("plant_detail/42", target)
    }

    @Test
    fun anEmptyTentResolvesNoTargetAtAll() {
        assertNull(
            "a tent with no plants must not produce a plant detail route",
            TentNavigation.targetForTap(null)
        )
    }

    @Test
    fun everyRowInATentResolvesToItsOwnRouteNotTheTentsFirstPlant() {
        val tentPlants = listOf(plant(id = 10L), plant(id = 11L), plant(id = 12L))

        val routes = tentPlants.map { TentNavigation.targetForTap(it) }

        assertEquals(
            listOf("plant_detail/10", "plant_detail/11", "plant_detail/12"),
            routes
        )
        assertNotEquals(
            "row two must not fall back to the tent's first plant",
            TentNavigation.targetForTap(tentPlants.first()),
            TentNavigation.targetForTap(tentPlants[1])
        )
    }

    @Test
    fun routeKeepsTheFullLongRangeTheArgumentParserExpects() {
        // AppNavigation reads this argument with getLong("plantId"), so a wider
        // id must survive the route untouched instead of being truncated.
        assertEquals(
            "plant_detail/9223372036854775807",
            TentNavigation.plantDetailRoute(Long.MAX_VALUE)
        )
    }

    @Test
    fun aResolvedTargetIsNeverTheSentinelId() {
        val target = TentNavigation.targetForTap(plant(id = 3L))

        assertTrue(
            "the old fallback emitted plant_detail/-1, it must be unreachable",
            !target!!.endsWith("/-1")
        )
    }
}
