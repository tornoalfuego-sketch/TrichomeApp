package com.trichome.app.ui.screens.protocol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trichome.app.data.entity.Protocol
import com.trichome.app.data.entity.ProtocolStage
import com.trichome.app.data.model.GrowRange
import com.trichome.app.model.ProtocolTargetEditor
import com.trichome.app.model.ProtocolTargetField
import com.trichome.app.model.ProtocolTargetForm
import com.trichome.app.model.ProtocolTargetGroup
import com.trichome.app.model.ProtocolTargetKind
import com.trichome.app.model.ProtocolTargetOutcome
import com.trichome.app.model.StageTargetOutcome
import com.trichome.app.model.TargetInput
import com.trichome.app.ui.components.AppTopBar
import com.trichome.app.ui.components.accentButtonColors
import com.trichome.app.ui.theme.LocalMetricValue
import com.trichome.app.ui.theme.LocalTertiaryText

/**
 * The write surfaces for a protocol's declared targets.
 *
 * ## Why these are pages and not dialogs
 *
 * `ProtocolEditorDialog` was measured on a device at 1080x2340 filling most of a
 * 2000-pixel-tall screen, and it already carries a name, four photoperiod chips and a
 * stage name/days pair. Fifteen more inputs do not fit in it, and this repository has
 * already shipped two defects from over-stuffing one dialog: a "CUSTOM" chip that
 * rendered one letter per line, and a row of buttons clipped to "SuperCicl". A dialog is
 * sized for a prompt; a form is not a prompt.
 *
 * So the targets are edited here instead, as two surfaces, and they are split because
 * the data is split: the fourteen grow-wide targets live on the `protocols` row and the
 * per-stage band lives on a `protocol_stages` row, and a band is set on a stage rather
 * than on the protocol. One screen each means neither surface has to know about the
 * other's entity.
 *
 * Both are full-screen pages with one scroll owner each, mounted by `ProtocolScreen` in
 * place of its list — never inside it. A page that scrolled inside the screen's
 * `LazyColumn` is the defect `ScrollOwnershipTest` was written for, and the mutual
 * exclusion is what keeps the axis to a single owner.
 *
 * ## What this file does not decide
 *
 * It does not decide what a typed field means. Labels, units, decimals, the band rules,
 * the refusals and the copy all come from `model/ProtocolTargetEditor.kt`, which is plain
 * Kotlin and therefore assertable on the JVM — this project has no Compose test runtime,
 * and a rule that lived here would have been a rule nobody could hold.
 */
data class ProtocolTargetSession(
    val protocol: Protocol,
    val group: ProtocolTargetGroup
)

/**
 * Side inset of an ordinary field.
 *
 * The page's own margin, so a label and its box sit where the rest of the app's content
 * does.
 */
private val FORM_FIELD_INSET = 16.dp

/**
 * Side inset of a band's pair of boxes, and the gap between them.
 *
 * **Narrower than [FORM_FIELD_INSET] on purpose.** Two numeric boxes share one row, and
 * this page is measured on a 360 dp-wide phone: at the ordinary inset and a 10 dp gap
 * each box gets about 156 dp, and a VPD band has to hold `0,80` twice plus whatever the
 * keyboard's suggestion strip pushes into the field.
 *
 * The obvious fix — `contentPadding` on the `OutlinedTextField` — does not exist at the
 * Material version this app builds against (compose-bom 2024.08.00, Material3 1.3.0), and
 * the compiler rejected the call. Hand-rolling a `BasicTextField` with its own decoration
 * box would shave the inset further, but a hand-drawn outline and floating label is
 * layout this phase cannot verify on hardware, and shipping a worse box to shave four
 * digits is not a trade worth making. So the width is won where it can be won honestly:
 * the band's row runs to the screen edges and the two boxes sit 8 dp apart.
 */
private val BAND_FIELD_INSET = 8.dp

/** Between the two bounds of a band. */
private val BAND_FIELD_GAP = 8.dp

/**
 * Editor for the grow-wide targets of one card group.
 *
 * @param onSave receives the whole protocol row to store, built by the model from a
 *   `copy` of the row this surface was opened with. Only this group's fields are read, so
 *   the name, the photoperiod and the other two groups cannot be disturbed by an edit
 *   here.
 */
@Composable
fun ProtocolTargetsScreen(
    session: ProtocolTargetSession,
    onClose: () -> Unit,
    onSave: (Protocol) -> Unit
) {
    val group = session.group
    // Keyed on the protocol and the group: the surface is reused as the grower opens a
    // second group on the same protocol, and an unkeyed `remember` would keep the first
    // group's typed values — the same defect `editorSeedKey` exists for.
    val seedKey = session.protocol.id to group.name
    var form by remember(seedKey) {
        mutableStateOf(ProtocolTargetEditor.formFor(session.protocol, group.fields))
    }

    val outcome = ProtocolTargetEditor.resolve(form, session.protocol, group.fields)
    val problemEs = (outcome as? ProtocolTargetOutcome.Invalid)?.problemEs

    TargetEditorPage(
        title = ProtocolTargetEditor.titleEs(group.titleEs),
        noteEs = ProtocolTargetEditor.GOAL_NOT_MEASUREMENT_ES,
        problemEs = problemEs,
        confirmEnabled = outcome is ProtocolTargetOutcome.Ready,
        onClose = onClose,
        onConfirm = {
            if (outcome is ProtocolTargetOutcome.Ready) onSave(outcome.protocol)
        }
    ) {
        group.fields.forEach { field ->
            val input = form.inputFor(field)
            when (field.kind) {
                ProtocolTargetKind.BAND -> TargetBandField(
                    field = field,
                    input = input as TargetInput.Band,
                    onChange = { form = form.withInput(field, it) }
                )
                ProtocolTargetKind.METRIC -> TargetMetricField(
                    field = field,
                    input = input as TargetInput.Metric,
                    onChange = { form = form.withInput(field, TargetInput.Metric(it)) }
                )
                ProtocolTargetKind.TEXT -> TargetFreeField(
                    field = field,
                    input = input as TargetInput.Free,
                    onChange = { form = form.withInput(field, TargetInput.Free(it)) }
                )
            }
        }
    }
}

/**
 * Editor for one stage's VPD target band.
 *
 * Its own surface because the band belongs to the stage: a grower opening "Floración"
 * is aiming at what that phase should look like, and asking for it on the protocol would
 * give one band three writers and no rule about which phase wins.
 *
 * Both boxes empty is a legitimate answer and writes `null` — the band is then unset and
 * the card reads "Sin definir" again, which is what a stage the grower has no opinion
 * about should say.
 */
@Composable
fun ProtocolStageTargetScreen(
    protocol: Protocol,
    stage: ProtocolStage,
    onClose: () -> Unit,
    onSave: (GrowRange?) -> Unit
) {
    var input by remember(protocol.id, stage.id) {
        mutableStateOf(
            ProtocolTargetEditor.seedInput(
                ProtocolTargetField.VPD_BAND,
                stage.vpdTarget
            ) as TargetInput.Band
        )
    }

    val outcome = ProtocolTargetEditor.resolveStageTarget(input)

    TargetEditorPage(
        title = ProtocolTargetEditor.stageTitleEs(stage.stageName),
        noteEs = ProtocolTargetEditor.GOAL_NOT_MEASUREMENT_ES,
        problemEs = (outcome as? StageTargetOutcome.Invalid)?.problemEs,
        confirmEnabled = outcome is StageTargetOutcome.Ready,
        onClose = onClose,
        onConfirm = {
            if (outcome is StageTargetOutcome.Ready) onSave(outcome.band)
        }
    ) {
        TargetBandField(
            field = ProtocolTargetField.VPD_BAND,
            input = input,
            onChange = { input = it }
        )
    }
}

/**
 * The chrome both write surfaces share: bar, one scroll owner, a refusal that cannot
 * scroll out of view, and the two buttons.
 *
 * Two copies of this shell is how the two surfaces would drift apart on the one thing
 * that matters here — whether the refusal is visible while the grower is looking at the
 * bottom of a long form.
 *
 * No scrollable of its own beyond the single [verticalScroll] below, and it is mounted
 * in place of the screen's `LazyColumn`, so the axis has exactly one owner.
 */
@Composable
private fun TargetEditorPage(
    title: String,
    noteEs: String,
    problemEs: String?,
    confirmEnabled: Boolean,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    fields: @Composable () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = title, onNavigateBack = onClose)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                noteEs,
                style = MaterialTheme.typography.bodySmall,
                color = LocalTertiaryText.current,
                modifier = Modifier.padding(horizontal = FORM_FIELD_INSET)
            )
            fields()
        }

        HorizontalDivider()

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (problemEs != null) {
                // Above the buttons and outside the scroll, so a refusal about the fifth
                // of seven fields is not something the grower has to scroll to find.
                Text(
                    problemEs,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(6.dp))
            }
            Row(
                // `navigationBarsPadding` because this row was measured on a device at
                // 1080x2340 with the buttons' own geometry: the button spans y 2191 to
                // 2300 on a 2340-tall screen, and the system navigation bar overlays
                // everything below roughly y 2240. The system bar takes those touches
                // before this app ever sees them, so "Guardar" — the only control that
                // writes anything — was drawn half-covered and could not reliably be
                // pressed. `PlantDetailScreen` and `MainBottomBar` already carry this
                // padding for the same reason.
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                TextButton(onClick = onClose) {
                    Text(ProtocolTargetEditor.CANCEL_ES)
                }
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    colors = accentButtonColors(accent)
                ) {
                    Text(ProtocolTargetEditor.CONFIRM_ES)
                }
            }
        }
    }
}

/**
 * A band: two numeric boxes under one heading, committing as one value.
 *
 * The heading carries the unit, so neither box's floating label has to — and the row is
 * inset less than the rest of the form for the reason on [BAND_FIELD_INSET]. The band
 * only commits when both boxes parse and the low bound is not above the high one; the
 * refusals come from `model/ProtocolTargetEditor.resolveBand`, and an empty pair writes
 * `null` rather than a zero band.
 */
@Composable
private fun TargetBandField(
    field: ProtocolTargetField,
    input: TargetInput.Band,
    onChange: (TargetInput.Band) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BAND_FIELD_INSET)
    ) {
        Text(
            ProtocolTargetEditor.fieldHeadingEs(field),
            style = MaterialTheme.typography.titleSmall,
            color = LocalTertiaryText.current
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BAND_FIELD_GAP)
        ) {
            NumericField(
                label = ProtocolTargetEditor.MIN_ES,
                text = input.low,
                onChange = { onChange(input.copy(low = it)) },
                modifier = Modifier.weight(1f)
            )
            NumericField(
                label = ProtocolTargetEditor.MAX_ES,
                text = input.high,
                onChange = { onChange(input.copy(high = it)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * One measured target.
 *
 * The typed number is instrumentation, so it is set in the metric register: pH, VPD, PPFD,
 * DLI, temperature and humidity are read against each other and a prose face would make
 * `0,80` and `0,8` the same width. Empty is unset and shows no number at all.
 */
@Composable
private fun TargetMetricField(
    field: ProtocolTargetField,
    input: TargetInput.Metric,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = input.text,
        onValueChange = onChange,
        label = { Text(field.labelEs) },
        singleLine = true,
        textStyle = LocalMetricValue.current,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = if (field.showsUnit) {
            { Text(field.unitEs.orEmpty()) }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FORM_FIELD_INSET)
    )
}

/** The grower's own words about the setup. Prose register, not the metric one. */
@Composable
private fun TargetFreeField(
    field: ProtocolTargetField,
    input: TargetInput.Free,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = input.text,
        onValueChange = onChange,
        label = { Text(field.labelEs) },
        minLines = if (field == ProtocolTargetField.OBSERVATIONS) 3 else 1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FORM_FIELD_INSET)
    )
}

/**
 * One numeric box.
 *
 * [KeyboardType.Decimal] rather than `Number`: the numeric keypad on some devices has no
 * decimal point at all, which is `VpdCalculatorCard`'s documented reason for the choice.
 * The parser accepts either separator, so a comma from the keyboard and a dot from a
 * physical one both land.
 */
@Composable
private fun NumericField(
    label: String,
    text: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        textStyle = LocalMetricValue.current,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

/** The affordance pencil on a tappable group or stage row. */
@Composable
internal fun TargetEditGlyph() {
    Icon(
        imageVector = Icons.Default.Edit,
        // Explicit tint: `Color.Unspecified` installs no ColorFilter, the vector draws
        // its own colour, and 184 invisible diagnosis glyphs are what that cost this app.
        tint = LocalTertiaryText.current,
        contentDescription = ProtocolTargetEditor.EDIT_HINT_ES,
        modifier = Modifier.size(18.dp)
    )
}