package com.trichome.app.ui.screens.protocol

import com.trichome.app.data.entity.Protocol
import com.trichome.app.data.entity.ProtocolStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contract for the protocol editor's initial block list.
 *
 * The bug this locks down: the editor seeded `blocks` with a hardcoded
 * three-stage default and never read the protocol's real `ProtocolStage` rows,
 * so saving an edit overwrote the existing schedule.
 */
class ProtocolBlocksTest {

    private fun protocol(id: Long, name: String = "Protocolo") = Protocol(
        id = id,
        plantId = 1L,
        name = name
    )

    private fun stage(
        protocolId: Long,
        name: String,
        days: Int,
        sortOrder: Int
    ) = ProtocolStage(
        protocolId = protocolId,
        stageName = name,
        durationDays = days,
        sortOrder = sortOrder
    )

    @Test
    fun anExistingProtocolKeepsItsOwnStages() {
        val target = protocol(id = 7L)
        val stages = listOf(
            stage(7L, "Germinación", 10, 0),
            stage(7L, "Vegetativa", 40, 1),
            stage(7L, "Floración", 50, 2)
        )

        assertEquals(
            listOf("Germinación" to 10, "Vegetativa" to 40, "Floración" to 50),
            initialBlocks(target, stages)
        )
    }

    @Test
    fun stagesAreSeededInSortOrderNotInQueryOrder() {
        val target = protocol(id = 7L)
        val shuffled = listOf(
            stage(7L, "Floración", 56, 2),
            stage(7L, "Germinación", 7, 0),
            stage(7L, "Vegetativa", 35, 1)
        )

        assertEquals(
            listOf("Germinación" to 7, "Vegetativa" to 35, "Floración" to 56),
            initialBlocks(target, shuffled)
        )
    }

    @Test
    fun anExistingProtocolWithNoStagesFallsBackToTheDefault() {
        assertEquals(
            DEFAULT_PROTOCOL_BLOCKS,
            initialBlocks(protocol(id = 7L), emptyList())
        )
    }

    @Test
    fun aNewProtocolGetsTheDefault() {
        assertEquals(
            DEFAULT_PROTOCOL_BLOCKS,
            initialBlocks(protocol = null, stages = emptyList())
        )
    }

    @Test
    fun aNewProtocolIgnoresStagesThatBelongToNobody() {
        // Defensive: a stray list must not leak into the create path.
        assertEquals(
            DEFAULT_PROTOCOL_BLOCKS,
            initialBlocks(protocol = null, stages = listOf(stage(9L, "Ajeno", 3, 0)))
        )
    }

    @Test
    fun switchingProtocolsReseedsWithTheNewSchedule() {
        // The `remember` bug: a dialog reused for a second protocol kept the
        // first protocol's blocks forever because the seed was never keyed.
        val first = protocol(id = 1L, name = "Exterior")
        val second = protocol(id = 2L, name = "Interior")
        val firstStages = listOf(stage(1L, "Floración", 60, 0))
        val secondStages = listOf(
            stage(2L, "Germinación", 5, 0),
            stage(2L, "Crecimiento", 25, 1)
        )

        val seededForFirst = initialBlocks(first, firstStages)
        val seededForSecond = initialBlocks(second, secondStages)

        assertEquals(listOf("Floración" to 60), seededForFirst)
        assertEquals(listOf("Germinación" to 5, "Crecimiento" to 25), seededForSecond)
        assertNotEquals(
            "the second protocol must not inherit the first protocol's schedule",
            seededForFirst,
            seededForSecond
        )
    }

    @Test
    fun twoDifferentProtocolsGetDifferentEditorSeedKeys() {
        val first = editorSeedKey(protocol(id = 1L))
        val second = editorSeedKey(protocol(id = 2L))
        assertNotNull("the editor seed must key on the protocol id", first)
        assertNotNull(second)
        assertNotEquals(
            "`remember(key)` only reseeds when the key changes; two protocols must differ",
            first!!,
            second!!
        )
    }

    @Test
    fun reopeningTheSameProtocolKeepsTheSameEditorSeedKey() {
        val key = editorSeedKey(protocol(id = 1L))
        assertNotNull("the editor seed must key on the protocol id", key)
        assertEquals(1L, key!!)
    }

    @Test
    fun theNewProtocolPathHasItsOwnSeedKey() {
        assertNull(editorSeedKey(null))
    }

    @Test
    fun theDefaultScheduleIsThreeOrderedBlocks() {
        assertEquals(3, DEFAULT_PROTOCOL_BLOCKS.size)
        assertEquals(
            listOf("Germinación", "Vegetativa", "Floración"),
            DEFAULT_PROTOCOL_BLOCKS.map { it.first }
        )
        assertEquals(listOf(7, 35, 56), DEFAULT_PROTOCOL_BLOCKS.map { it.second })
    }
}
