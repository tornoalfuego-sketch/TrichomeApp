package com.trichome.app.ui.screens.breeding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trichome.app.data.prefs.BreedingProgressRepository
import com.trichome.app.model.BreedingChapter
import com.trichome.app.model.BreedingChapters
import com.trichome.app.model.BreedingMedalTier
import com.trichome.app.model.BreedingProgress
import com.trichome.app.model.BreedingQuizResult
import com.trichome.app.model.BreedingContentSource
import com.trichome.app.model.percentLabelEs
import com.trichome.app.model.requiredCorrectFor
import com.trichome.app.model.scoreBreedingQuiz
import com.trichome.app.ui.components.SolidPanel
import kotlinx.coroutines.launch

/**
 * Theory tab: the chapters, their quizzes, and the medals they award.
 *
 * The whole thing is a function of the asset (for the text) and of
 * [BreedingProgressRepository] (for the medals). Nothing about the gamification
 * lives in this file: the unlock rule, the scoring and the additive award are all
 * pure functions in `com.trichome.app.model`, which is what makes them testable
 * without a composition.
 *
 * Every chapter is open. See [BreedingChapters.ALL_CHAPTERS_OPEN] for why the
 * medals are the gate rather than the text.
 */
@Composable
fun TheoryChaptersTab(
    chapters: List<BreedingChapter>,
    progress: BreedingProgress,
    repository: BreedingProgressRepository,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("📖 Teoría de breeding", style = MaterialTheme.typography.titleMedium)
        Text(
            "Cada capítulo termina con un cuestionario corto. Las medallas no caducan: " +
                "repetir el cuestionario nunca te quita lo que ya ganaste.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )

        if (chapters.isEmpty()) {
            Text("No se ha podido cargar el contenido.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        chapters.forEach { chapter ->
            val medals = progress.medalsEarnedIn(chapter.id)
            val unlocked = BreedingChapters.isUnlocked(chapter.id, chapters)
            ChapterCard(
                chapter = chapter,
                medals = medals.map { it.tier },
                unlocked = unlocked,
                progress = progress,
                repository = repository,
                accent = accent,
            )
        }
    }
}

@Composable
private fun ChapterCard(
    chapter: BreedingChapter,
    medals: List<BreedingMedalTier>,
    unlocked: Boolean,
    progress: BreedingProgress,
    repository: BreedingProgressRepository,
    accent: Color,
) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var answers by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var outcome by remember { mutableStateOf<BreedingQuizResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val best = medals.maxByOrNull { it.ordinal }

    SolidPanel(accentColor = accent) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(chapter.titleEs, style = MaterialTheme.typography.titleMedium)
            Text(
                chapter.summaryEs,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BreedingMedalTier.entries.forEach { tier ->
                    val banked = tier in medals
                    MedalChip(tier = tier, banked = banked, accent = accent)
                }
                Spacer(Modifier.weight(1f))
                if (progress.totalMedals > 0) {
                    Text(
                        "${progress.totalMedals} medallas",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Ocultar capítulo" else "Leer capítulo")
            }

            if (!unlocked) {
                Text(
                    "Este capítulo aún no está disponible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProvenanceBadge(chapter, accent)

                    chapter.bodyEs.forEach { paragraph ->
                        Text(paragraph, style = MaterialTheme.typography.bodyMedium)
                    }

                    if (chapter.keyPointsEs.isNotEmpty()) {
                        Text("Claves", style = MaterialTheme.typography.titleSmall)
                        chapter.keyPointsEs.forEach { point ->
                            Text("• $point", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    QuizSection(
                        chapter = chapter,
                        answers = answers,
                        outcome = outcome,
                        error = error,
                        accent = accent,
                        bestTier = best,
                        onAnswer = { index, option ->
                            answers = answers + (index to option)
                            error = null
                            outcome = null
                        },
                        onSubmit = {
                            when (val result = scoreBreedingQuiz(chapter, answers.keys.sorted().map { answers.getValue(it) })) {
                                is BreedingQuizResult.Invalid -> error = result.reason
                                is BreedingQuizResult.Scored -> {
                                    error = null
                                    outcome = result
                                    scope.launch {
                                        repository.recordQuiz(
                                            chapter.id,
                                            result.outcome.correct,
                                            result.outcome.total
                                        )
                                    }
                                }
                            }
                        },
                        onRetry = {
                            answers = emptyMap()
                            outcome = null
                            error = null
                        }
                    )
                }
            }
        }
    }
}

/**
 * Where the chapter's text came from.
 *
 * Shown rather than hidden because a chapter with authored biology and a chapter
 * quoting the shipped reference look identical otherwise, and the request was
 * explicit that reference must not be invented.
 */
@Composable
private fun ProvenanceBadge(chapter: BreedingChapter, accent: Color) {
    val authored = chapter.source is BreedingContentSource.Authored
    val label = if (authored) {
        (chapter.source as BreedingContentSource.Authored).reason
    } else {
        "Texto de la biblioteca de breeding (breeding.json)."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (authored) accent.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (authored) "✍️" else "📚", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.size(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun MedalChip(tier: BreedingMedalTier, banked: Boolean, accent: Color) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .background(
                color = if (banked) accent.copy(alpha = 0.25f) else scheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(tier.icon, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.size(4.dp))
        Text(
            tier.labelEs,
            style = MaterialTheme.typography.labelSmall,
            color = if (banked) scheme.onSurface else scheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun QuizSection(
    chapter: BreedingChapter,
    answers: Map<Int, Int>,
    outcome: BreedingQuizResult?,
    error: String?,
    accent: Color,
    bestTier: BreedingMedalTier?,
    onAnswer: (Int, Int) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit
) {
    val scored = outcome as? BreedingQuizResult.Scored
    val answered = answers.size == chapter.questions.size

    SolidPanel(accentColor = accent) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val question = chapter.questions.size
            val bar = requiredCorrectFor(BreedingMedalTier.BRONZE, question)
            val silverBar = requiredCorrectFor(BreedingMedalTier.SILVER, question)
            Text("Cuestionario", style = MaterialTheme.typography.titleSmall)
            Text(
                "$question preguntas · ${requiredCorrectFor(BreedingMedalTier.BRONZE, question)} para bronce, " +
                    "$silverBar para plata, $question para oro",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            chapter.questions.forEachIndexed { index, q ->
                QuestionBlock(
                    prompt = q.promptEs,
                    options = q.optionsEs,
                    chosen = answers[index],
                    revealed = scored != null,
                    correctIndex = q.correctIndex,
                    explanation = q.explanationEs,
                    accent = accent,
                    onSelect = { onAnswer(index, it) }
                )
            }

            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onSubmit, enabled = answered) {
                    Text("Comprobar")
                }
                OutlinedButton(onClick = onRetry) {
                    Text("Reiniciar")
                }
            }

            if (scored != null) {
                val o = scored.outcome
                Text(
                    "${o.correct} de ${o.total} (${percentLabelEs(o.correct.toDouble() / o.total)})",
                    style = MaterialTheme.typography.titleSmall
                )
                val earnedThisRun = o.bestTier
                when {
                    earnedThisRun != null -> Text(
                        "¡${earnedThisRun.labelEs} conseguida en este intento!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    bar <= o.total -> Text(
                        "Necesitas $bar de ${o.total} para el bronce.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (bestTier != null && earnedThisRun != null && bestTier.ordinal > earnedThisRun.ordinal) {
                    Text(
                        "Tu mejor medalla en este capítulo sigue siendo ${bestTier.labelEs}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionBlock(
    prompt: String,
    options: List<String>,
    chosen: Int?,
    revealed: Boolean,
    correctIndex: Int,
    explanation: String,
    accent: Color,
    onSelect: (Int) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(prompt, style = MaterialTheme.typography.bodyMedium)
        options.forEachIndexed { index, option ->
            val isChosen = chosen == index
            val isCorrect = revealed && index == correctIndex
            val isWrong = revealed && isChosen && index != correctIndex
            val border = when {
                isCorrect -> scheme.primary
                isWrong -> scheme.error
                isChosen -> accent
                else -> scheme.outlineVariant
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isCorrect) accent.copy(alpha = 0.22f)
                        else if (isWrong) scheme.error.copy(alpha = 0.14f)
                        else scheme.surfaceVariant.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(option, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                if (isCorrect) Text("✅")
                if (isWrong) Text("❌")
            }
        }
        if (revealed) {
            Text(
                explanation,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurface.copy(alpha = 0.75f)
            )
        }
    }
}

/** The two inputs the theory tab renders. */
data class TheoryChaptersState(
    val chapters: List<BreedingChapter>,
    val progress: BreedingProgress
)

/**
 * Loads the chapters from the asset and collects the persisted medals.
 *
 * Collected rather than sampled: a medal earned in this session has to appear
 * without leaving the tab, or the user taps "Comprobar" and sees nothing happen.
 */
@Composable
fun rememberTheoryChapters(
    contentRepository: com.trichome.app.data.repository.BreedingContentRepository,
    progressRepository: BreedingProgressRepository
): TheoryChaptersState {
    var chapters by remember { mutableStateOf<List<BreedingChapter>>(emptyList()) }

    LaunchedEffect(Unit) {
        chapters = BreedingChapters.catalog(
            generations = contentRepository.getGenerations(),
            techniques = contentRepository.getTechniques(),
            glossary = contentRepository.getGlossary()
        )
    }

    val progress by progressRepository.progress.collectAsState(initial = BreedingProgress())
    return TheoryChaptersState(chapters = chapters, progress = progress)
}
