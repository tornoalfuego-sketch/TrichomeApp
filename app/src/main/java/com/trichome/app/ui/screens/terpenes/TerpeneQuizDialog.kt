package com.trichome.app.ui.screens.terpenes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.trichome.app.data.repository.Terpene
import kotlin.random.Random

/** One question built from the catalog: identify the terpene from its aroma. */
private data class QuizQuestion(
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

/**
 * Trivia minigame over the terpenes catalog.
 *
 * Questions are generated from the catalog itself rather than shipped as a
 * separate asset, so they stay consistent with the encyclopedia and never go
 * stale. Only terpenes that actually carry an aroma description are used as
 * answers, otherwise the question would be unanswerable.
 */
@Composable
fun TerpeneQuizDialog(
    pool: List<Terpene>,
    onDismiss: () -> Unit,
    onAnswer: (Boolean) -> Unit
) {
    val answerable = remember(pool) { pool.filter { it.aroma.isNotBlank() && it.name.isNotBlank() } }

    if (answerable.size < 4) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Trivia") },
            text = { Text("No hay suficientes terpenos cargados para armar el juego.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
        )
        return
    }

    var round by remember { mutableStateOf(0) }
    var score by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    val totalRounds = 5
    val question: QuizQuestion? = remember(round, answerable) {
        if (round >= totalRounds) null else buildQuestion(answerable)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (finished) "Resultado" else "Trivia · ronda ${round + 1}/$totalRounds") },
        text = {
            if (finished) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$score de $totalRounds",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            score == totalRounds -> "Perfecto. Dominas la biblioteca."
                            score >= totalRounds / 2 -> "Bien jugado. Repasa las fichas que fallaste."
                            else -> "Sigue explorando la enciclopedia para mejorar."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (question != null) {
                Column {
                    Text(
                        question.prompt,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    question.options.forEachIndexed { index, option ->
                        val isCorrect = index == question.correctIndex
                        // Only reveal correctness once the round has been answered.
                        val answered = round > 0
                        val highlight = if (answered) {
                            when {
                                isCorrect -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(highlight)
                                .clickable(enabled = !answered) {
                                    onAnswer(isCorrect)
                                    if (isCorrect) score++
                                    round++
                                }
                                .padding(12.dp)
                        ) {
                            Text(option, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (round > 0 && question != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Filled.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                question.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (finished) onDismiss() else {
                    // Reset for another run.
                    round = 0
                    score = 0
                    finished = false
                }
            }) {
                Text(if (finished) "Cerrar" else "Reiniciar")
            }
        },
        dismissButton = {
            if (finished) {
                TextButton(onClick = {
                    round = 0
                    score = 0
                    finished = false
                }) { Text("Otra ronda") }
            }
        }
    )

    // Flip the terminal flag as soon as the last round is consumed.
    LaunchedEffect(round) {
        if (round >= totalRounds) finished = true
    }
}

/**
 * Builds one question: "which terpene smells like X?".
 *
 * Distractors are picked from other answerable terpenes so they are plausible,
 * and the correct option's position is randomised to avoid a learnable pattern.
 */
private fun buildQuestion(pool: List<Terpene>): QuizQuestion {
    val correct = pool.random()
    val distractors = pool.asSequence()
        .filter { it.id != correct.id }
        .shuffled()
        .take(3)
        .toList()
    val options = (distractors + correct).shuffled()
    return QuizQuestion(
        prompt = "¿Qué terpeno tiene este aroma?",
        options = options.map { it.name },
        correctIndex = options.indexOfFirst { it.id == correct.id },
        explanation = "${correct.name}: ${correct.aroma}" +
            (if (correct.formula.isNotBlank()) " (${correct.formula})" else "")
    )
}
