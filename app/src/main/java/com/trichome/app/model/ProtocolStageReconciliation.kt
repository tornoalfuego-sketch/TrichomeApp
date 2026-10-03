package com.trichome.app.model

import com.trichome.app.data.entity.ProtocolStage

/**
 * How a protocol's stage list is turned into row-level writes.
 *
 * ## The defect this replaces
 *
 * `ProtocolRepository.replaceStages` did this:
 *
 * ```kotlin
 * stageDao.clearStages(protocolId)
 * blocks.sortedBy { it.sortOrder }.forEachIndexed { index, block ->
 *     stageDao.insertStage(block.copy(protocolId = protocolId, sortOrder = index))
 * }
 * ```
 *
 * Delete every stage, then insert them again. That was survivable for exactly as long
 * as nothing wrote `vpdTarget`, because there was nothing to lose — and the moment a
 * write path for that column existed, saving a protocol's *name* would have silently
 * erased the band the grower had just set. It also regenerated every stage's primary
 * key on every save, so "this stage" was a different row after each edit. `AGENTS.md`
 * §11 sets the data-loss budget at zero and this was the shape it forbids, so the
 * destructive write is not narrowed, it is gone.
 *
 * ## How an edit is told apart from a removal
 *
 * **By the stage's own primary key, and by nothing else.**
 *
 * A draft that carries an `id` present among the persisted rows is an *edit* of that
 * row: it is updated in place, keeping its id. A persisted row that no draft mentions is
 * a *removal* — the grower deleted that block — and it is the only thing this plan ever
 * deletes. A draft with no id (`0`) is an *addition*, because `ProtocolStage.id` is
 * `autoGenerate` and a stage the editor just built has never been in the table.
 *
 * Identity is deliberately **not** matched on the stage name, its duration, or its
 * position in the list:
 *
 * - matching on **name** reads a rename as a removal plus two additions, which throws
 *   away the band a renamed stage had;
 * - matching on **position** breaks the moment a stage is reordered or removed from the
 *   middle, shifting every later stage's identity onto its neighbour.
 *
 * So the id is the contract, and the editor carries it: `initialStageDrafts` seeds the
 * dialog from the persisted rows rather than from a rebuilt pair list, and the dialog
 * hands those rows back. The test for that is `ProtocolStageSaveTest`, and it fails
 * against a `clearStages` implementation.
 *
 * ## Why the band is not written here
 *
 * [updated] rows take their `vpdTarget` from the persisted row, never from the draft.
 * A schedule save is a caller that knows a stage's name and a duration; it has no
 * business writing the column it did not read, and a `null` in a draft is ambiguous —
 * "the grower cleared the band" and "this caller never knew about the band" look
 * identical. Collapsing that ambiguity by refusing to write the column from this path is
 * what keeps the two write paths honest: `ProtocolRepository.setStageVpdTarget` is the
 * only way a band changes, and it writes one column of one row.
 */
data class StageSavePlan(
    /** Rows to update in place. Ids and bands are preserved. */
    val updated: List<ProtocolStage>,
    /** Rows to insert. Their ids are whatever [ProtocolStage] was given, and drafts that
     *  named an unknown id arrive with `0` so they cannot overwrite another protocol. */
    val inserted: List<ProtocolStage>,
    /** Rows the grower removed. Nothing else ever reaches this list. */
    val deleted: List<ProtocolStage>
) {
    /** True when the write would touch no row at all. */
    val isEmpty: Boolean
        get() = updated.isEmpty() && inserted.isEmpty() && deleted.isEmpty()
}

/**
 * The plan that turns a protocol's stage drafts into row-level writes.
 *
 * Pure and in `model/` for the reason everything else in this package is: the decision
 * of which rows change is the decision that costs data when it is wrong, and it has to
 * be assertable without a device. The repository executes the plan; it does not decide
 * it, so there is exactly one implementation of "an edit is not a removal" in the app.
 *
 * @param protocolId the protocol being saved, stamped onto every inserted row rather
 *   than taken from the draft.
 * @param persisted the rows currently stored for this protocol, as the DAO returns them.
 * @param drafts the editor's list, in display order, each carrying the id of the row it
 *   came from or `0` for a stage that is new.
 *
 * Sort order is renumbered into a contiguous `0..n-1` following the draft list, because
 * that list is the schedule the card renders. Renumbering rides on the id, so the row an
 * edit refers to does not move: a reorder changes `sortOrder`, not which row is being
 * written.
 *
 * A draft that names an id this protocol does not have — a stale list, or a row that
 * belongs to a different protocol — is inserted with `id = 0` instead of being written
 * over. Inserting it with its own id would REPLACE whatever row holds that id in a table
 * where the primary key is global, which is a cross-protocol overwrite and the worst
 * outcome available; inserting it as a new row keeps the grower's stage inside the
 * protocol they edited and loses nothing.
 */
fun reconcileStages(
    protocolId: Long,
    persisted: List<ProtocolStage>,
    drafts: List<ProtocolStage>
): StageSavePlan {
    val known = persisted.associateBy { it.id }
    val mentioned = mutableSetOf<Long>()

    val updated = mutableListOf<ProtocolStage>()
    val inserted = mutableListOf<ProtocolStage>()

    drafts.forEachIndexed { index, draft ->
        val row = known[draft.id]
        if (row != null) {
            mentioned += row.id
            updated += row.copy(
                stageName = draft.stageName.trim(),
                durationDays = draft.durationDays,
                sortOrder = index,
                // The band is not this call's to write. See the KDoc above.
                vpdTarget = row.vpdTarget
            )
        } else {
            inserted += draft.copy(
                protocolId = protocolId,
                sortOrder = index,
                id = 0L
            )
        }
    }

    val deleted = persisted.filter { it.id !in mentioned }

    return StageSavePlan(updated = updated, inserted = inserted, deleted = deleted)
}