package com.trichome.app.model

import com.trichome.app.data.entity.ProtocolStage
import com.trichome.app.data.model.GrowRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How an edit is told apart from a removal.
 *
 * ## The rule
 *
 * By the stage's primary key, and by nothing else. A draft naming a persisted id is an
 * **edit** of that row; a persisted row no draft mentions is a **removal**; a draft with
 * no id is an **addition**.
 *
 * Not by name (a rename would read as a removal plus two additions, discarding the
 * renamed stage's band), not by position (a removal from the middle shifts every later
 * stage's identity onto its neighbour), and not by content (two stages may legitimately
 * have the same name and the same duration).
 *
 * ## Why the band is not written here
 *
 * The plan takes `vpdTarget` from the persisted row, never from the draft. A schedule
 * save is a caller that knows a stage's name and a duration; a `null` in its draft would
 * otherwise express "no opinion" as "cleared" and erase the target. This is why the
 * assertions below pass a draft whose `vpdTarget` is null and demand the band survive.
 */
class ProtocolStageReconciliationTest {

    private fun stage(
        id: Long,
        name: String = "Etapa",
        days: Int = 7,
        order: Int = 0,
        band: GrowRange? = null
    ) = ProtocolStage(
        id = id,
        protocolId = 7L,
        stageName = name,
        durationDays = days,
        sortOrder = order,
        vpdTarget = band
    )

    private val stored = listOf(
        stage(11L, "Germinación", 7, 0, GrowRange(0.4f, 0.7f)),
        stage(12L, "Vegetativa", 35, 1, GrowRange(1.0f, 1.4f)),
        stage(13L, "Floración", 56, 2)
    )

    /** A draft the editor would hand back: it knows name, days and id, nothing else. */
    private fun draft(id: Long, name: String, days: Int, order: Int) = ProtocolStage(
        id = id,
        protocolId = 7L,
        stageName = name,
        durationDays = days,
        sortOrder = order
    )

    @Test
    fun anUnchangedScheduleUpdatesEveryRowAndDeletesNothing() {
        val drafts = stored.mapIndexed { index, row ->
            draft(row.id, row.stageName, row.durationDays, index)
        }

        val plan = reconcileStages(7L, stored, drafts)

        assertEquals(listOf(11L, 12L, 13L), plan.updated.map { it.id })
        assertTrue("nothing is inserted", plan.inserted.isEmpty())
        assertTrue("nothing is deleted", plan.deleted.isEmpty())
    }

    @Test
    fun anEditKeepsTheRowsIdentityAndItsBand() {
        val plan = reconcileStages(
            protocolId = 7L,
            persisted = stored,
            drafts = listOf(
                draft(11L, "Germinación", 10, 0),
                draft(12L, "  Vegetativa  ", 35, 1),
                draft(13L, "Floración", 56, 2)
            )
        )

        val vegetative = plan.updated.first { it.id == 12L }
        assertEquals(
            "`vpdTarget` is not this call's to write: a draft that never read the column " +
                "must not clear it",
            GrowRange(1.0f, 1.4f),
            vegetative.vpdTarget
        )
        assertEquals("the row keeps its id", 12L, vegetative.id)
        assertEquals("and the name is trimmed", "Vegetativa", vegetative.stageName)
        assertEquals(10, plan.updated.first { it.id == 11L }.durationDays)
        assertTrue("a rename is not a removal", plan.deleted.isEmpty())
    }

    @Test
    fun aStageTheGrowerRemovedIsDeletedAndTheOnesTheyKeptAreOnlyUpdated() {
        val plan = reconcileStages(
            protocolId = 7L,
            persisted = stored,
            drafts = listOf(
                draft(11L, "Germinación", 7, 0),
                draft(13L, "Floración", 60, 1),
                ProtocolStage(protocolId = 7L, stageName = "Lavado", durationDays = 3, sortOrder = 2)
            )
        )

        assertEquals(
            "exactly the stage the grower removed, and nothing else",
            listOf(12L),
            plan.deleted.map { it.id }
        )
        assertEquals(listOf(11L, 13L), plan.updated.map { it.id })
        assertEquals(1, plan.inserted.size)
        assertEquals("Lavado", plan.inserted.single().stageName)
        assertEquals(
            "an inserted row is a new row: the repository stamps the protocol and Room " +
                "assigns the id",
            0L,
            plan.inserted.single().id
        )
        assertEquals(7L, plan.inserted.single().protocolId)
        assertEquals(60, plan.updated.first { it.id == 13L }.durationDays)
    }

    @Test
    fun reorderingRenumbersSortOrderWithoutMovingAnyRow() {
        val plan = reconcileStages(
            protocolId = 7L,
            persisted = stored,
            drafts = listOf(
                draft(13L, "Floración", 56, 0),
                draft(11L, "Germinación", 7, 1),
                draft(12L, "Vegetativa", 35, 2)
            )
        )

        assertEquals(listOf(13L, 11L, 12L), plan.updated.map { it.id })
        assertEquals(listOf(0, 1, 2), plan.updated.map { it.sortOrder })
        assertTrue("a reorder deletes nothing", plan.deleted.isEmpty())
    }

    @Test
    fun aDraftNamingAnIdThisProtocolDoesNotHaveIsInsertedRatherThanOverwriting() {
        // 99 belongs to another protocol. `insertStage` is a REPLACE on a globally unique
        // primary key, so writing that draft with its own id would overwrite a row of a
        // protocol nobody is editing.
        val plan = reconcileStages(
            protocolId = 7L,
            persisted = stored,
            drafts = listOf(draft(99L, "Ajena", 10, 0))
        )

        assertTrue(plan.updated.isEmpty())
        assertEquals(
            "the id is dropped so the insert cannot land on another protocol's row",
            0L,
            plan.inserted.single().id
        )
        assertEquals("Ajena", plan.inserted.single().stageName)
    }

    @Test
    fun anEmptyDraftListRemovesEveryStageAndInsertsNothing() {
        // Unreachable from the editor, which refuses to save with no blocks, and stated
        // here because it is the honest consequence of the rule: if the list is empty,
        // then every stage was removed.
        val plan = reconcileStages(7L, stored, emptyList())

        assertEquals(listOf(11L, 12L, 13L), plan.deleted.map { it.id })
        assertTrue(plan.inserted.isEmpty())
        assertTrue(plan.updated.isEmpty())
    }

    @Test
    fun aProtocolWithNoStoredStagesInsertsEveryDraft() {
        val plan = reconcileStages(
            protocolId = 7L,
            persisted = emptyList(),
            drafts = listOf(
                ProtocolStage(protocolId = 0L, stageName = "Germinación", durationDays = 7, sortOrder = 0),
                ProtocolStage(protocolId = 0L, stageName = "Floración", durationDays = 56, sortOrder = 1)
            )
        )

        assertEquals(
            listOf("Germinación", "Floración"),
            plan.inserted.map { it.stageName }
        )
        assertEquals(listOf(0, 1), plan.inserted.map { it.sortOrder })
        assertTrue(plan.deleted.isEmpty())
    }

    @Test
    fun aPlanThatChangesNothingSaysSo() {
        val drafts = stored.mapIndexed { index, row ->
            draft(row.id, row.stageName, row.durationDays, index)
        }

        assertFalse(
            "reconciling against no persisted rows still has inserts to make",
            reconcileStages(7L, emptyList(), drafts).isEmpty
        )
        assertTrue(
            "reconciling against no persisted rows and no drafts is the empty plan",
            reconcileStages(7L, emptyList(), emptyList()).isEmpty
        )
    }

    @Test
    fun aStageWithNoBandHasNoneToLose() {
        val plan = reconcileStages(
            protocolId = 7L,
            persisted = stored,
            drafts = listOf(draft(13L, "Floración", 56, 0))
        )

        assertNull(
            "the stage written before schema v6 keeps declaring no target",
            plan.updated.single().vpdTarget
        )
    }

    @Test
    fun aNewStageMayCarryItsOwnBandFromTheStart() {
        // The one path where the draft's band is honoured: there is no persisted row to
        // take it from, so nothing can be lost.
        val plan = reconcileStages(
            protocolId = 7L,
            persisted = emptyList(),
            drafts = listOf(
                ProtocolStage(
                    protocolId = 0L,
                    stageName = "Lavado",
                    durationDays = 3,
                    vpdTarget = GrowRange(1.6f, 2.0f)
                )
            )
        )

        assertEquals(GrowRange(1.6f, 2.0f), plan.inserted.single().vpdTarget)
    }
}