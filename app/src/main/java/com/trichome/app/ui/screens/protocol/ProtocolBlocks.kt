package com.trichome.app.ui.screens.protocol

import com.trichome.app.data.entity.Protocol
import com.trichome.app.data.entity.ProtocolStage
import com.trichome.app.model.ProtocolExtendedFields
import com.trichome.app.model.ProtocolFieldGroup
import com.trichome.app.model.ProtocolFieldRow
import com.trichome.app.model.ProtocolTargetGroup
import com.trichome.app.model.protocolFieldGroups
import com.trichome.app.model.protocolStageTargetGroups
import com.trichome.app.model.protocolStageTargetRow

/** Schedule a brand new protocol starts from. */
val DEFAULT_PROTOCOL_BLOCKS: List<Pair<String, Int>> = listOf(
    "Germinación" to 7,
    "Vegetativa" to 35,
    "Floración" to 56
)

/**
 * Blocks the protocol editor starts with.
 *
 * An existing protocol keeps its own schedule, ordered by
 * [ProtocolStage.sortOrder]. The default is only correct for the create path:
 * seeding an edit with it silently overwrote the protocol's real stages the
 * moment the user saved.
 */
fun initialBlocks(
    protocol: Protocol?,
    stages: List<ProtocolStage>
): List<Pair<String, Int>> =
    if (protocol != null && stages.isNotEmpty()) {
        stages.sortedBy { it.sortOrder }.map { it.stageName to it.durationDays }
    } else {
        DEFAULT_PROTOCOL_BLOCKS
    }

/**
 * Key for the editor's `remember` seed.
 *
 * The editor is reused across protocols, so an unkeyed `remember { }` would keep
 * the first protocol's name, photoperiod and blocks forever. Keying on the id
 * makes Compose drop the state and reseed whenever the user opens another one.
 */
fun editorSeedKey(protocol: Protocol?): Long? = protocol?.id

/**
 * The stage rows the editor opens with, carrying each row's own id.
 *
 * ## Why the id has to survive to this function
 *
 * [initialBlocks] answers "what does this schedule look like", and it answers it as a
 * list of name/duration pairs because that is all a dialog needs to *draw* one. It is
 * not enough to *save* one: `ProtocolRepository.saveStages` tells an edit from a removal
 * by the stage's primary key, so a draft that arrives without its id is indistinguishable
 * from a brand-new stage and the save would insert duplicates while the bands of the
 * rows the grower did not touch sat there untouched.
 *
 * So an edit seeds from the persisted rows themselves, which is also the only way the
 * editor can renumber `sortOrder` and carry `vpdTarget` without a second lookup.
 *
 * The create path has no rows to carry, so it seeds from [initialBlocks] and the default
 * schedule — `id = 0`, `protocolId` stamped by the repository on insert.
 */
fun initialStageDrafts(
    protocol: Protocol?,
    stages: List<ProtocolStage>
): List<ProtocolStage> =
    if (protocol != null && stages.isNotEmpty()) {
        stages.sortedBy { it.sortOrder }
    } else {
        initialBlocks(protocol, stages).mapIndexed { index, (stageName, durationDays) ->
            ProtocolStage(
                protocolId = protocol?.id ?: 0L,
                stageName = stageName,
                durationDays = durationDays,
                sortOrder = index
            )
        }
    }

/**
 * The stages of a protocol in the order the card prints them.
 *
 * [protocolStageTargetGroups] sorts with the same two keys, and this has to sort the same
 * way: the stage rows are now tappable, and the object behind a tap must be the row the
 * grower actually sees rather than whatever position the query happened to return.
 */
fun orderedStages(stages: List<ProtocolStage>): List<ProtocolStage> =
    stages.sortedWith(compareBy({ it.sortOrder }, { it.stageName }))

/**
 * The extended agronomic blocks of a protocol, in display order.
 *
 * Delegated to `protocolFieldGroups`, which owns the Spanish copy and the
 * formatting, so the labels and units are testable without Compose and there is
 * one place that decides what an unset field looks like. Re-exported here
 * because the card is the only caller and this file is the protocol layer's
 * vocabulary — a screen importing `model.protocolFieldGroups` directly would be
 * reaching past the seam.
 */
fun extendedFieldBlocks(protocol: Protocol): List<ProtocolFieldGroup> =
    protocolFieldGroups(protocol)

/**
 * Each stage's own VPD target, as the second run of blocks on the card.
 *
 * Delegated for the same reason as [extendedFieldBlocks]: the label, the unit, the
 * `0,80 – 1,20` formatting and the `Sin definir` empty state are all decisions with
 * agronomy in them, and they belong in `model/` where a JVM test can reach them without
 * a Compose runtime. Empty when the protocol has no stages, so the card does not print a
 * heading with nothing under it.
 */
fun stageTargetBlocks(stages: List<ProtocolStage>): List<ProtocolFieldGroup> =
    protocolStageTargetGroups(stages)

/**
 * One stage's own row, resolved for display.
 *
 * Re-exported for the same reason as [stageTargetBlocks], and for one more: the per-stage
 * rows are tappable, so the card draws each one from the [ProtocolStage] it was handed
 * rather than from the group's pre-resolved row list. The label, the band and the
 * `Sin definir` empty state still come from `model/`, so the write surface and the read
 * surface cannot describe the same stage differently.
 */
fun stageTargetRow(stage: ProtocolStage): ProtocolFieldRow = protocolStageTargetRow(stage)

/**
 * The target group a card group title belongs to, or null when it belongs to none.
 *
 * The card is handed a [ProtocolFieldGroup] and opens the write surface from its title,
 * so this is the lookup that connects the two — and `null` is the honest answer for the
 * per-stage block, which is written on a different surface because a band belongs to a
 * stage.
 */
fun targetGroupFor(titleEs: String): ProtocolTargetGroup? = ProtocolTargetGroup.forTitle(titleEs)

/**
 * Whether every row of [group] is still unset.
 *
 * What the card needs to decide whether to say so. A group of fourteen "Sin definir"
 * rows with no affordance reads as a fact sheet about a grow nobody has measured yet,
 * which is a different claim from "you can fill this in".
 */
fun isGroupUnset(group: ProtocolFieldGroup): Boolean =
    group.rows.all { it.detailEs == ProtocolExtendedFields.SIN_DEFINIR }
