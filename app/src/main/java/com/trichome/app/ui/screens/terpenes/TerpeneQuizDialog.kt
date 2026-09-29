package com.trichome.app.ui.screens.terpenes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trichome.app.data.repository.Terpene
import com.trichome.app.model.TerpeneQuiz
import com.trichome.app.model.TerpeneQuizState
import com.trichome.app.model.TerpeneQuizQuestion

/**
 * Trivia minigame over the terpenes catalog.
 *
 * This composable renders a [TerpeneQuizState] and nothing else. The lifecycle
 * lives in [TerpeneQuiz] because the previous version kept `round`, `score` and
 * `finished` in local Compose state and advanced inside the tap handler, which
 * regenerated the question before the explanation could be read.
 *
 * Two deliberate affordances here:
 * - answering only reveals; advancing is the explicit **Siguiente** button;
 * - the reveal marks the correct option *and* the player's own pick, so being
 *   wrong is visible on the question and not only in the final tally.
 *
 * @param onAnswer fired once per answered round, with the outcome.
 * @param onCompleted fired once when the last round is finished, so the caller
 *   can award the completion bonus. It is not fired on restart or dismiss.
 */
@Composable
fun TerpeneQuizDialog(
    pool: List<Terpene>,
    onDismiss: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    onCompleted: () -> Unit = {}
) {
    val quiz = remember(pool) { TerpeneQuiz(pool) }
    var state by remember(quiz) { mutableStateOf(quiz.state) }

    // Every transition goes through the machine, which rejects anything that is
    // not legal for the current state; only a real change is propagated.
    fun dispatch(next: TerpeneQuizState) {
        if (next === state) return
        state = next
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (val current = state) {
                    is TerpeneQuizState.Finished -> "Resultado"
                    is TerpeneQuizState.Unavailable -> "Trivia"
                    is TerpeneQuizState.Asking -> "Trivia · ronda ${current.round}/${current.totalRounds}"
                    is TerpeneQuizState.Revealed -> "Trivia · ronda ${current.round}/${current.totalRounds}"
                }
            )
        },
        text = {
            when (val current = state) {
                is TerpeneQuizState.Unavailable -> Text(current.reason)

                is TerpeneQuizState.Finished -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "${current.score} de ${current.totalRounds}",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            current.score == current.totalRounds -> "Perfecto. Dominas la biblioteca."
                            current.score * 2 >= current.totalRounds -> "Bien jugado. Repasa las fichas que fallaste."
                            else -> "Sigue explorando la enciclopedia para mejorar."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                is TerpeneQuizState.Asking -> QuestionBlock(
                    question = current.question,
                    chosenIndex = null,
                    onPick = { index ->
                        val before = state
                        val after = quiz.answer(index)
                        if (after === before) return@QuestionBlock
                        dispatch(after)
                        (after as? TerpeneQuizState.Revealed)?.let { onAnswer(it.isCorrect) }
                    }
                )

                is TerpeneQuizState.Revealed -> QuestionBlock(
                    question = current.question,
                    chosenIndex = current.chosenIndex,
                    onPick = null
                )
            }
        },
        confirmButton = {
            when (val current = state) {
                is TerpeneQuizState.Finished -> TextButton(onClick = onDismiss) { Text("Cerrar") }
                is TerpeneQuizState.Unavailable -> TextButton(onClick = onDismiss) { Text("Cerrar") }
                is TerpeneQuizState.Revealed -> {
                    if (current.isLastRound) {
                        TextButton(onClick = {
                            val after = quiz.next()
                            dispatch(after)
                            if (after is TerpeneQuizState.Finished) onCompleted()
                        }) { Text("Ver resultado") }
                    } else {
                        TextButton(onClick = { dispatch(quiz.next()) }) { Text("Siguiente") }
                    }
                }
                // An unanswered round has no meaningful confirm action; advancing
                // is only allowed after a reveal, by design.
                is TerpeneQuizState.Asking -> Unit
            }
        },
        dismissButton = {
            if (state is TerpeneQuizState.Finished) {
                TextButton(onClick = { dispatch(quiz.restart()) }) { Text("Otra ronda") }
            }
        }
    )
}

/**
 * Renders a question and its options.
 *
 * [chosenIndex] is null while the round is unanswered, which disables the taps
 * and hides the verdict. Once set, the correct option and the player's own pick
 * are both called out.
 */
@Composable
private fun QuestionBlock(
    question: TerpeneQuizQuestion,
    chosenIndex: Int?,
    onPick: ((Int) -> Unit)?
) {
    val scheme = MaterialTheme.colorScheme
    val answered = chosenIndex != null

    Column {
        Text(question.prompt, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        question.options.forEachIndexed { index, option ->
            OptionRow(
                option = option,
                background = when {
                    !answered -> scheme.surfaceVariant
                    index == question.correctIndex -> scheme.primaryContainer
                    index == chosenIndex -> scheme.errorContainer
                    else -> scheme.surfaceVariant
                },
                border = when {
                    index == question.correctIndex && answered -> scheme.primary
                    index == chosenIndex && answered -> scheme.error
                    else -> Color.Transparent
                },
                label = when {
                    !answered -> null
                    index == question.correctIndex -> "Correcta"
                    index == chosenIndex -> "Tu respuesta"
                    else -> null
                },
                onClick = onPick?.let { pick -> { pick(index) } }
            )
        }

        if (answered) {
            Spacer(Modifier.height(12.dp))
            Verdict(chosenIndex == question.correctIndex)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = scheme.tertiary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    question.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OptionRow(
    option: String,
    background: Color,
    border: Color,
    label: String?,
    onClick: (() -> Unit)?
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(background)
            .then(
                if (border == Color.Transparent) Modifier
                else Modifier.border(2.dp, border, shape)
            )
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                option,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (label != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Per-round right/wrong feedback, so the outcome is visible on the question. */
@Composable
private fun Verdict(correct: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (correct) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = null,
            tint = if (correct) scheme.primary else scheme.error
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (correct) "¡Correcto!" else "No era esa",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (correct) scheme.primary else scheme.error
        )
    }
}
