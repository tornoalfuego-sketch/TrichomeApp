package com.trichome.app.ui.screens.entourage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trichome.app.model.EntourageAchievement
import com.trichome.app.model.EntourageQuizState
import com.trichome.app.model.EntourageQuizUi
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.components.accentButtonColors
import com.trichome.app.ui.theme.LocalTertiaryText
import com.trichome.app.ui.theme.TrichomeThemeState

/**
 * T8.4 — the Séquito quiz, and the badge it pays.
 *
 * The lifecycle lives in [com.trichome.app.model.EntourageQuiz] and only the
 * state reaches this file, the same split `TerpeneQuizDialog` uses: answering
 * *reveals*, advancing is an explicit "Siguiente", and a double tap cannot skip
 * a round or corrupt the tally.
 *
 * The reward is the module's own [EntourageAchievement], and it is paid through
 * the existing `achievements` table — this section never counts XP itself.
 * [EntourageAchievement.isEarned] decides whether a run qualifies, so a run
 * abandoned on the last question is not a completed run and cannot unlock it.
 *
 * A plain [Column] — the module's single `LazyColumn` owns the scroll. See
 * [EntourageNetworkSection] for why a nested vertical scroll is not an option.
 */
@Composable
fun EntourageQuizSection(
    state: EntourageQuizState,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    onRestart: () -> Unit,
    themeState: TrichomeThemeState
) {
    val scheme = themeState.colorScheme()
    val tertiary = LocalTertiaryText.current

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EntourageSectionHeading("Trivia de Séquito", themeState)

        when (state) {
            is EntourageQuizState.Unavailable -> SolidPanel(contentColor = scheme.onSurface) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Trivia no disponible",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            is EntourageQuizState.Asking -> {
                Text(
                    "Pregunta ${state.round} de ${state.totalRounds} · ${state.score} " +
                        "correctas",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant
                )
                QuestionPanel(state.question.promptEs, themeState)
                state.question.optionsEs.forEachIndexed { index, option ->
                    OptionRow(
                        label = option,
                        state = null,
                        onClick = { onAnswer(index) }
                    )
                }
            }

            is EntourageQuizState.Revealed -> {
                val question = state.question
                Text(
                    "Pregunta ${state.round} de ${state.totalRounds} · ${state.score} " +
                        "correctas",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant
                )
                QuestionPanel(question.promptEs, themeState)

                // The reveal marks the correct option *and* the player's own
                // pick, so being wrong is visible on the question rather than
                // only in the final tally.
                question.optionsEs.forEachIndexed { index, option ->
                    OptionRow(
                        label = option,
                        state = when {
                            index == question.correctIndex -> true
                            index == state.chosenIndex -> false
                            else -> null
                        },
                        // Answers are locked once revealed: the only legal move
                        // from `Revealed` is `next()`, so this tap is inert
                        // rather than a second scoring path.
                        onClick = {}
                    )
                }

                Surface(
                    color = scheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            if (state.isCorrect) "✓ Correcta" else "✗ No era la opción",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (state.isCorrect) scheme.primary else scheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            question.explanationEs,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface
                        )
                    }
                }

                Button(
                    onClick = onNext,
                    colors = accentButtonColors(scheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.isLastRound) "Ver resultado" else "Siguiente")
                }
            }

            is EntourageQuizState.Finished -> {
                val outcome = remember(state) { EntourageQuizUi.outcomeOf(state) }
                val threshold = remember(state.totalRounds) {
                    EntourageAchievement.thresholdFor(state.totalRounds)
                }

                SolidPanel(contentColor = scheme.onSurface) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Trivia terminada",
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${outcome.score} de ${outcome.rounds}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface
                        )
                        Text(
                            "${outcome.correctPercent}% de aciertos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            outcome.headlineEs,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface
                        )
                    }
                }

                outcome.rewards.forEach { earned ->
                    Surface(
                        color = scheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "${earned.icon} ${earned.nameEs} desbloqueado",
                                style = MaterialTheme.typography.titleSmall,
                                color = scheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                earned.descriptionEs,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "+${earned.xpReward} XP sumados a tu total.",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onPrimaryContainer
                            )
                        }
                    }
                }

                if (!outcome.unlockedBadge) {
                    Text(
                        "El sello \"${EntourageAchievement.ENTOURAGE_MASTER.labelEs}\" pide " +
                            "$threshold aciertos de ${state.totalRounds}. Esta ronda: " +
                            "${outcome.score}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = tertiary
                    )
                }

                Button(
                    onClick = onRestart,
                    colors = accentButtonColors(scheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Otra ronda")
                }
            }
        }
    }
}

@Composable
private fun QuestionPanel(promptEs: String, themeState: TrichomeThemeState) {
    val scheme = themeState.colorScheme()
    SolidPanel(contentColor = scheme.onSurface) {
        Column(Modifier.padding(16.dp)) {
            Text(
                promptEs,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface
            )
        }
    }
}

/**
 * One answer option.
 *
 * [state] is null while the round is open, true for the correct option after a
 * reveal and false for the player's own wrong pick. Rendering the player's pick
 * as well as the answer is the difference between learning from a miss and just
 * seeing the tally.
 */
@Composable
private fun OptionRow(
    label: String,
    state: Boolean?,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val tertiary = LocalTertiaryText.current
    val container = when (state) {
        true -> scheme.primaryContainer
        false -> scheme.errorContainer
        null -> scheme.surfaceVariant
    }
    val content = when (state) {
        true -> scheme.onPrimaryContainer
        false -> scheme.onErrorContainer
        null -> scheme.onSurface
    }

    Surface(
        color = container,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Tappable only while the round is open. After a reveal the tap
                // is inert, so a double tap cannot record a second answer.
                .then(if (state == null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = content,
                modifier = Modifier.weight(1f)
            )
            Text(
                when (state) {
                    true -> "✓"
                    false -> "✗"
                    null -> "•"
                },
                style = MaterialTheme.typography.titleSmall,
                color = if (state == null) tertiary else content
            )
        }
    }
}
