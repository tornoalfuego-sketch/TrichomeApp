package com.trichome.app.ui.screens.protocol

import com.trichome.app.data.entity.Protocol
import com.trichome.app.data.entity.ProtocolStage
import com.trichome.app.data.model.GrowRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stage rows the protocol editor opens with, and the order the card prints them in.
 *
 * ## Why this is a separate concern from `initialBlocks`
 *
 * `initialBlocks` answers what a schedule looks like — a list of name/duration pairs,
 * which is all a dialog needs to draw one. It is not enough to *save* one:
 * `ProtocolRepository.saveStages` tells an edit from a removal by the stage's primary
 * key, so a draft without an id is indistinguishable from a stage the grower just added.
 * Seeding an edit from the persisted rows is what carries that identity from the table
 * to the save.
 *
 * The ordering matters for the same reason from the other direction: the per-stage rows
 * are controls now, so the object behind a tap has to be the stage the grower is
 * looking at rather than whatever position the query happened to return.
 */
class ProtocolStageDraftsTest {

    private fun protocol(id: Long = 7L) = Protocol(id = id, plantId = 1L, name = "Exterior")

    private fun stage(
        protocolId: Long = 7L,
        name: String,
        days: Int,
        order: Int,
        band: GrowRange? = null
    ) = ProtocolStage(
        id = order + 1L,
        protocolId = protocolId,
        stageName = name,
        durationDays = days,
        sortOrder = order,
        vpdTarget = band
    )

    @Test
    fun anExistingProtocolSeedsWithItsOwnRowsAndKeepsTheirIds() {
        val rows = listOf(
            stage(name = "Germinación", days = 10, order = 0, band = GrowRange(0.4f, 0.7f)),
            stage(name = "Vegetativa", days = 40, order = 1, band = GrowRange(1.0f, 1.4f)),
            stage(name = "Floración", days = 50, order = 2)
        )

        val drafts = initialStageDrafts(protocol(), rows)

        assertEquals(listOf("Germinación", "Vegetativa", "Floración"), drafts.map { it.stageName })
        assertEquals(listOf(10, 40, 50), drafts.map { it.durationDays })
        assertEquals(
            "the ids are the whole point: without them a save cannot tell an edit from an " +
                "addition",
            listOf(1L, 2L, 3L),
            drafts.map { it.id }
        )
        assertEquals(
            "and the bands ride along, so a save that reorders the schedule cannot lose them",
            GrowRange(1.0f, 1.4f),
            drafts[1].vpdTarget
        )
    }

    @Test
    fun theSeedsAreInSortOrderRatherThanQueryOrder() {
        val shuffled = listOf(
            stage(name = "Floración", days = 56, order = 2),
            stage(name = "Germinación", days = 7, order = 0),
            stage(name = "Vegetativa", days = 35, order = 1)
        )

        assertEquals(
            listOf("Germinación", "Vegetativa", "Floración"),
            initialStageDrafts(protocol(), shuffled).map { it.stageName }
        )
    }

    @Test
    fun aNewProtocolSeedsWithTheDefaultAndNoIds() {
        val drafts = initialStageDrafts(null, emptyList())

        assertEquals(DEFAULT_PROTOCOL_BLOCKS.map { it.first }, drafts.map { it.stageName })
        assertEquals(DEFAULT_PROTOCOL_BLOCKS.map { it.second }, drafts.map { it.durationDays })
        assertTrue(
            "every draft is a new row, so every id is 0",
            drafts.all { it.id == 0L }
        )
        assertEquals(listOf(0, 1, 2), drafts.map { it.sortOrder })
    }

    @Test
    fun anExistingProtocolWithNoStagesFallsBackToTheDefaultWithNoIds() {
        val drafts = initialStageDrafts(protocol(), emptyList())

        assertEquals(DEFAULT_PROTOCOL_BLOCKS.map { it.first }, drafts.map { it.stageName })
        assertTrue(drafts.all { it.id == 0L })
    }

    @Test
    fun switchingProtocolsReseedsWithTheOtherScheduleAndTheOtherIds() {
        val first = initialStageDrafts(
            protocol(1L),
            listOf(stage(name = "Floración", days = 60, order = 0))
        )
        val second = initialStageDrafts(
            protocol(2L),
            listOf(
                stage(name = "Germinación", days = 5, order = 0),
                stage(name = "Crecimiento", days = 25, order = 1)
            )
        )

        assertEquals(listOf("Floración"), first.map { it.stageName })
        assertEquals(listOf("Germinación", "Crecimiento"), second.map { it.stageName })
        assertTrue(
            "the two protocols' drafts must not be interchangeable, or a save would write " +
                "one protocol's stages onto another",
            first.map { it.stageName } != second.map { it.stageName }
        )
    }

    @Test
    fun theCardOrdersStagesTheSameWayTheGroupItPrintsDoes() {
        val shuffled = listOf(
            stage(name = "Floración", days = 56, order = 2),
            stage(name = "Germinación", days = 7, order = 0),
            stage(name = "Vegetativa", days = 35, order = 1)
        )

        assertEquals(
            orderedStages(shuffled).map { it.stageName },
            stageTargetBlocks(shuffled).single().rows.map { it.labelEs }
        )
    }

    @Test
    fun twoStagesSharingASortOrderStillPrintInAFixedOrder() {
        val one = listOf(
            stage(name = "Vegetativa", days = 35, order = 0),
            stage(name = "Floración", days = 56, order = 0)
        )

        assertEquals(
            orderedStages(one).map { it.stageName },
            orderedStages(one.reversed()).map { it.stageName }
        )
        assertEquals(
            orderedStages(one).map { it.stageName },
            stageTargetBlocks(one).single().rows.map { it.labelEs }
        )
    }

    @Test
    fun aStageWithNoTargetPrintsNoNumberAndNoBand() {
        val row = stageTargetRow(stage(name = "Vegetativa", days = 35, order = 1))

        assertNull(row.metricEs)
        assertEquals("Sin definir", row.detailEs)
    }

    @Test
    fun aDeclaredBandPrintsThroughTheSameRowTheCardUses() {
        val row = stageTargetRow(
            stage(name = "Floración", days = 56, order = 2, band = GrowRange(1.0f, 1.4f))
        )

        assertEquals("Floración", row.labelEs)
        assertEquals("1,00 – 1,40", row.metricEs)
        assertEquals("kPa", row.detailEs)
    }
}