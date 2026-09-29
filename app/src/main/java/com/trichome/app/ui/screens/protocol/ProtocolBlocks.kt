package com.trichome.app.ui.screens.protocol

import com.trichome.app.data.entity.Protocol
import com.trichome.app.data.entity.ProtocolStage

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
