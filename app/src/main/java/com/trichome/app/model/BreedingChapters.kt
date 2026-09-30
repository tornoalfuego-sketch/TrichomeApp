package com.trichome.app.model

import com.trichome.app.data.repository.BreedingGeneration
import com.trichome.app.data.repository.BreedingTechnique
import com.trichome.app.data.repository.BreedingTerm

/* ─────────────────────────── Content types ───────────────────────────────── */

/**
 * Which array of `assets/data/breeding.json` a piece of text came from.
 *
 * The asset has exactly three top-level collections of prose. Naming which one
 * a chapter quotes is what lets a test read the real file off disk and prove the
 * text was not quietly rewritten.
 */
enum class BreedingContentCollection(val key: String) {
    GENERATIONS("generations"),
    TECHNIQUES("techniques"),
    GLOSSARY("glossary")
}

/** One addressable entry inside the asset: `generations/f1`, `techniques/reversal`. */
data class BreedingAssetRef(
    val collection: BreedingContentCollection,
    val id: String
) {
    /** Stable, greppable, and safe to use as a DataStore key. */
    val key: String get() = "${collection.key}/$id"
}

/**
 * Where a chapter's text came from.
 *
 * Three cases, because the honest answer for an expanded chapter is neither of the
 * two originals. `reversal` and `phenotype_stabilization` exist in the asset and
 * the chapters on those subjects need them; those same chapters also need prose
 * the asset does not have at all. Forcing the choice meant either dropping the
 * asset citations or describing authored paragraphs as library text, and both
 * misinform the reader.
 */
sealed interface BreedingContentSource {

    /**
     * Every paragraph is a string copied verbatim out of [refs], either on its
     * own or as `"<asset string> — <asset string>"`.
     */
    data class FromAsset(val refs: List<BreedingAssetRef>) : BreedingContentSource

    /**
     * Some paragraphs are quoted from [refs] and the rest were written for the
     * app because the asset stops short of the subject.
     *
     * [addedForApp] is reader-facing Spanish naming what the expansion covers, so
     * the badge can say which half is which instead of implying the whole chapter
     * came out of the library.
     */
    data class Mixed(
        val refs: List<BreedingAssetRef>,
        val addedForApp: String
    ) : BreedingContentSource

    /**
     * Written for this app because the asset has nothing on the subject.
     *
     * [reason] is reader-facing Spanish stating that, so the UI can show it as a
     * badge rather than passing editorial prose off as reference.
     */
    data class Authored(val reason: String) : BreedingContentSource
}

/** One quiz question at the end of a chapter. */
data class BreedingChapterQuestion(
    val promptEs: String,
    /** Ordered options; [correctIndex] indexes this list. */
    val optionsEs: List<String>,
    /** Zero-based index into [optionsEs]. */
    val correctIndex: Int,
    /** Shown after answering, in Spanish. */
    val explanationEs: String
)

/**
 * A claim the chapter makes about inheritance, kept as data.
 *
 * The point is that a ratio quoted in prose cannot be checked. Held here as two
 * parent genotypes and three expected probabilities, it is checked against
 * [Punnett.square] on every test run: if the genetics in the text ever drifts
 * from the genetics the simulator implements, the build fails.
 */
data class BreedingRatioClaim(
    val firstParent: String,
    val secondParent: String,
    val dominantPhenotypeProbability: Double,
    val recessivePhenotypeProbability: Double,
    val carrierProbability: Double,
    /** Short Spanish label naming the cross, e.g. "Aa x Aa". */
    val labelEs: String
)

/**
 * One chapter of the breeding theory, with its quiz.
 *
 * [questions] is sized to [BreedingChapters.QUIZ_QUESTIONS] for every chapter on
 * purpose: medal thresholds are fractions of the question count, so a chapter
 * with fewer questions would be easier to medal and the medals would not mean the
 * same thing across chapters.
 */
data class BreedingChapter(
    val id: String,
    val titleEs: String,
    val summaryEs: String,
    /** Paragraphs, in reading order. Verbatim from the asset, or authored. */
    val bodyEs: List<String>,
    /**
     * Short take-aways.
     *
     * Always authored. These are the chapter's own editorial lines, so they are
     * deliberately not presented as asset quotes; [source] says which the body is.
     */
    val keyPointsEs: List<String>,
    val source: BreedingContentSource,
    /** Assertions about inheritance, verified against [Punnett]. */
    val ratioClaims: List<BreedingRatioClaim> = emptyList(),
    val questions: List<BreedingChapterQuestion>
) {
    /** True when every paragraph came out of `breeding.json`. */
    val isFromAsset: Boolean get() = source is BreedingContentSource.FromAsset

    /** Reader-facing provenance badge: what to say about where the body came from. */
    val provenanceLabelEs: String
        get() = when (val source = source) {
            is BreedingContentSource.FromAsset -> "Texto de la biblioteca de breeding"
            is BreedingContentSource.Mixed ->
                "Parte de la biblioteca de breeding · ${source.addedForApp}"
            is BreedingContentSource.Authored -> source.reason
        }
}

/* ─────────────────────────── Medals ──────────────────────────────────────── */

/**
 * A medal tier, awarded per chapter.
 *
 * These are **breeding** medals and they are deliberately not terpene badges: the
 * two live in different DataStore files and neither reads the other. The app
 * already carries a terpene progression and a Room-backed grow-event
 * progression; a third system quietly reading one of them would let one activity
 * revoke another's rewards.
 *
 * Thresholds are fractions of the chapter's question count and are cumulative: a
 * silver always implies a bronze, because the three medals for a chapter are one
 * achievement at three levels of certainty rather than three separate prizes.
 */
enum class BreedingMedalTier(
    val id: String,
    val labelEs: String,
    val icon: String,
    val minimumFraction: Double
) {
    BRONZE("bronce", "Bronce", "🥉", 0.5),
    SILVER("plata", "Plata", "🥈", 0.75),
    GOLD("oro", "Oro", "🥇", 1.0);

    /** Displayed with the Spanish percentage spacing rule. */
    val labelWithFractionEs: String get() = "${wholePercent(minimumFraction)} % $labelEs"
}

/** A medal, identified by the chapter it was earned on. */
data class BreedingMedal(val chapterId: String, val tier: BreedingMedalTier) {
    /** Stable storage form: `mendel:oro`. */
    val id: String get() = "$chapterId:${tier.id}"
}

/**
 * Reads back a persisted medal id.
 *
 * Returns null rather than guessing for anything unexpected, so a key written by
 * a future build is counted as unknown instead of crashing this one or being
 * silently reinterpreted as a different medal.
 */
fun parseBreedingMedalId(raw: String): BreedingMedal? {
    val parts = raw.split(':')
    if (parts.size != 2) return null
    val (chapterId, tierId) = parts
    if (chapterId.isBlank()) return null
    val tier = BreedingMedalTier.entries.firstOrNull { it.id == tierId } ?: return null
    return BreedingMedal(chapterId, tier)
}

/* ─────────────────────────── Scoring ─────────────────────────────────────── */

/** Correct answers needed for [tier] on a quiz of [questionCount] questions. */
fun requiredCorrectFor(tier: BreedingMedalTier, questionCount: Int): Int {
    if (questionCount <= 0) return 0
    val needed = kotlin.math.ceil(questionCount * tier.minimumFraction).toInt()
    return needed.coerceIn(0, questionCount)
}

/**
 * The highest tier a score reaches, or null for no medal at all.
 *
 * A negative [correct] is treated as zero rather than being a lookup miss: a
 * corrupted counter should read as "did not pass", not as a crash.
 */
fun medalTierFor(correct: Int, questionCount: Int): BreedingMedalTier? {
    if (questionCount <= 0) return null
    val score = correct.coerceAtLeast(0)
    return BreedingMedalTier.entries.lastOrNull { score >= requiredCorrectFor(it, questionCount) }
}

/** Every tier reached by a score, so bronze comes with silver and gold. */
fun medalsEarnedBy(chapterId: String, correct: Int, questionCount: Int): Set<BreedingMedal> {
    val best = medalTierFor(correct, questionCount) ?: return emptySet()
    return BreedingMedalTier.entries
        .take(BreedingMedalTier.entries.indexOf(best) + 1)
        .mapTo(LinkedHashSet()) { BreedingMedal(chapterId, it) }
}

/** What a quiz run produced. */
data class BreedingQuizOutcome(
    val chapterId: String,
    val correct: Int,
    val total: Int,
    val earned: Set<BreedingMedal>
) {
    /** Highest tier this run reached, or null when nothing was earned. */
    val bestTier: BreedingMedalTier?
        get() = medalsEarnedBy(chapterId, correct, total).maxByOrNull { it.tier.ordinal }?.tier

    /** Whole percentage, for the result line. */
    fun percentCorrect(): Int = if (total <= 0) 0 else wholePercent(correct.toDouble() / total)
}

/** Result of scoring a quiz, including why a score was not accepted. */
sealed interface BreedingQuizResult {
    data class Scored(val outcome: BreedingQuizOutcome) : BreedingQuizResult

    /** [reason] is reader-facing Spanish. */
    data class Invalid(val reason: String) : BreedingQuizResult
}

/**
 * Scores a chapter quiz as a pure function of the chosen options.
 *
 * [answers] holds one index per question, in question order. An out-of-range
 * index is a programming error rather than a wrong answer — a wrong answer is
 * simply a valid index that is not [BreedingChapterQuestion.correctIndex] — so it
 * is rejected with the offending position named.
 */
fun scoreBreedingQuiz(
    chapter: BreedingChapter,
    answers: List<Int>
): BreedingQuizResult {
    if (chapter.questions.isEmpty()) {
        return BreedingQuizResult.Invalid("Este capítulo todavía no tiene preguntas.")
    }
    if (answers.size != chapter.questions.size) {
        return BreedingQuizResult.Invalid(
            "Se esperaban ${chapter.questions.size} respuestas y llegaron ${answers.size}."
        )
    }
    answers.forEachIndexed { index, chosen ->
        val options = chapter.questions[index].optionsEs
        if (chosen !in options.indices) {
            return BreedingQuizResult.Invalid(
                "La respuesta $chosen en la pregunta ${index + 1} está fuera de rango."
            )
        }
    }

    val correct = chapter.questions.indices.count { index ->
        answers[index] == chapter.questions[index].correctIndex
    }
    return BreedingQuizResult.Scored(
        BreedingQuizOutcome(
            chapterId = chapter.id,
            correct = correct,
            total = chapter.questions.size,
            earned = medalsEarnedBy(chapter.id, correct, chapter.questions.size)
        )
    )
}

/* ─────────────────────────── Progress ────────────────────────────────────── */

/** What a persisted quiz run awarded, and whether the chapter is now finished. */
data class BreedingAward(
    /** Medals this run crossed that were not already stored. */
    val newlyEarned: Set<BreedingMedal>,
    /** True when every tier for the chapter is now banked. */
    val chapterCompleted: Boolean
)

/**
 * The additive award computation.
 *
 * Pure, and additive on purpose: [earned] is what is already stored, and a run
 * that scores *worse* than a previous one returns nothing rather than removing a
 * medal. Medals are not a live scoreboard; taking one back because of a bad
 * rerun would be both unfair and surprising.
 */
fun breedingAwardFor(
    earned: Set<String>,
    chapterId: String,
    correct: Int,
    total: Int
): BreedingAward {
    val reached = medalsEarnedBy(chapterId, correct, total)
    val newlyEarned = reached.filterTo(LinkedHashSet()) { it.id !in earned }
    val banked = (reached.map { it.id } + earned).toSet()
    val completed = BreedingMedalTier.entries.all { BreedingMedal(chapterId, it).id in banked }
    return BreedingAward(newlyEarned = newlyEarned, chapterCompleted = completed)
}

/* ─────────────────────────── The catalog ─────────────────────────────────── */

/**
 * The breeding theory, as chapters.
 *
 * Built from the asset so the quoted text is always the text that ships: the
 * assembly is a pure function of the three lists `BreedingContentRepository`
 * returns, which means the tests can feed it the real `breeding.json` read off
 * disk and check the result end to end.
 *
 * Five chapters. Three are quoted from `breeding.json` and two are authored —
 * Mendel's laws and the STS mechanism, neither of which the asset covers — and
 * the authored ones are marked as such rather than passed off as reference.
 */
object BreedingChapters {

    /** Questions per chapter. Uniform so medal thresholds compare across chapters. */
    const val QUIZ_QUESTIONS = 4

    /**
     * Every chapter is readable from the first launch.
     *
     * The alternative — unlocking a chapter once the previous one is passed — was
     * rejected for two reasons. First, this is reference material, not a skill
     * tree: someone looking up STS feminisation should not have to earn a Mendel
     * quiz to read the paragraph they searched for. Second, the gamification the
     * request asked for lives in the medals, which the quiz already awards;
     * gating the text would add a second, redundant gate in front of the same
     * reward.
     */
    const val ALL_CHAPTERS_OPEN = true

    /**
     * Whether [chapterId] can be read.
     *
     * With [ALL_CHAPTERS_OPEN] this reduces to a membership check, and the check
     * stays because an unknown id must not open anything: a typo in a deep link
     * would otherwise render an empty screen.
     */
    fun isUnlocked(chapterId: String, chapters: List<BreedingChapter>): Boolean =
        ALL_CHAPTERS_OPEN && chapters.any { it.id == chapterId }

    /**
     * Assembles the chapters from the shipped asset.
     *
     * Order is fixed and ids are stable, because they are persisted as medal
     * keys; reordering or renaming would orphan what a grower had already earned.
     */
    fun catalog(
        generations: List<BreedingGeneration>,
        techniques: List<BreedingTechnique>,
        glossary: List<BreedingTerm> = emptyList()
    ): List<BreedingChapter> = listOf(
        mendelChapter(),
        generationsChapter(generations),
        feminizationChapter(generations, techniques),
        phenotypeChapter(techniques),
        glossaryChapter(glossary)
    )

    /** Every asset ref the fixed set of chapters draws on, for documentation. */
    val CATALOG_ASSET_REFS: List<BreedingAssetRef> = listOf(
        BreedingAssetRef(BreedingContentCollection.GENERATIONS, "f1"),
        BreedingAssetRef(BreedingContentCollection.GENERATIONS, "f2"),
        BreedingAssetRef(BreedingContentCollection.GENERATIONS, "f3"),
        BreedingAssetRef(BreedingContentCollection.GENERATIONS, "f4"),
        BreedingAssetRef(BreedingContentCollection.GENERATIONS, "f5"),
        BreedingAssetRef(BreedingContentCollection.GENERATIONS, "ibl"),
        BreedingAssetRef(BreedingContentCollection.GENERATIONS, "feminized"),
        BreedingAssetRef(BreedingContentCollection.TECHNIQUES, "reversal"),
        BreedingAssetRef(BreedingContentCollection.TECHNIQUES, "regular_selection"),
        BreedingAssetRef(BreedingContentCollection.TECHNIQUES, "phenotype_stabilization")
    )

    /* ── Chapter 1: authored ─────────────────────────────────────────────── */

    /**
     * Chapter 1, written for the app.
     *
     * `breeding.json` has no Mendel entry, so there was nothing to quote. The
     * ratios stated here are held as [BreedingRatioClaim]s and asserted against
     * [Punnett.square], which is the point: the prose and the simulator cannot
     * disagree without a failing test.
     */
    private fun mendelChapter() = BreedingChapter(
        id = "mendel",
        titleEs = "Las leyes de Mendel",
        summaryEs = "Cómo se reparte un rasgo entre la descendencia y por qué el cruce de dos heterocigotos da siempre los mismos números.",
        bodyEs = listOf(
            "Cada rasgo depende de un gen, y cada planta recibe dos copias de ese gen: una de la madre y otra del padre. El par de copias es el genotipo; lo que se ve, aroma, estructura, color o tricomas, es el fenotipo.",
            "Un alelo puede ser dominante (A) o recesivo (a). Con un solo alelo dominante el rasgo se expresa, así que un heterocigoto (Aa) se parece a un homocigoto dominante (AA). El alelo recesivo queda enmascarado, pero sigue ahí y se transmite.",
            "Mendel observó que los dos alelos de un heterocigoto se separan al formar los gametos: la mitad de sus células sexuales llevan A y la mitad llevan a. Por eso el cruce de dos heterocigotos produce siempre la misma proporción, generación tras generación.",
            "Al cruzar Aa x Aa las cuatro combinaciones salen en la misma proporción. La relación observable es 3:1 a favor del rasgo dominante, y la relación entre genotipos es 1:2:1: un cuarto de homocigotos dominantes, la mitad de heterocigotos y un cuarto de homocigotos recesivos.",
            "El 3:1 es una media sobre una población. En una camada pequeña pueden no aparecer plantas recesivas, o aparecer más de las esperadas, sin que el cruce esté mal hecho: por eso hay que contar antes de concluir."
        ),
        keyPointsEs = listOf(
            "A es el alelo dominante y a el recesivo; el genotipo se escribe con el dominante primero.",
            "Aa se parece a AA, pero sigue llevando el alelo recesivo: por eso se llama portador.",
            "Aa x Aa da 3:1 en fenotipo y 1:2:1 en genotipo.",
            "Las proporciones son medias de población, no un resultado garantizado para cada planta."
        ),
        source = BreedingContentSource.Authored(
            "Texto redactado para la app: el asset de breeding no incluye las leyes de Mendel."
        ),
        ratioClaims = listOf(
            BreedingRatioClaim("AA", "AA", 1.0, 0.0, 0.0, "AA x AA"),
            BreedingRatioClaim("AA", "Aa", 1.0, 0.0, 0.5, "AA x Aa"),
            BreedingRatioClaim("Aa", "Aa", 0.75, 0.25, 0.5, "Aa x Aa"),
            BreedingRatioClaim("Aa", "aa", 0.5, 0.5, 0.5, "Aa x aa"),
            BreedingRatioClaim("aa", "aa", 0.0, 1.0, 0.0, "aa x aa")
        ),
        questions = listOf(
            BreedingChapterQuestion(
                promptEs = "Una planta tiene genotipo Aa. ¿Qué proporción de sus gametos lleva el alelo dominante?",
                optionsEs = listOf("Todos", "La mitad", "Ninguno", "Dos tercios"),
                correctIndex = 1,
                explanationEs = "Un heterocigoto separa sus dos alelos en partes iguales: 1:1."
            ),
            BreedingChapterQuestion(
                promptEs = "Al cruzar Aa x Aa, ¿qué proporción de la descendencia muestra el rasgo recesivo?",
                optionsEs = listOf("Ninguna", "Un cuarto", "La mitad", "Tres cuartos"),
                correctIndex = 1,
                explanationEs = "El 3:1 se mide sobre el fenotipo: una de cada cuatro plantas lo muestra."
            ),
            BreedingChapterQuestion(
                promptEs = "Un portador (Aa) se parece a un homocigoto dominante (AA). ¿En qué se diferencian?",
                optionsEs = listOf(
                    "En nada, son idénticos",
                    "El portador lleva además un alelo recesivo que puede transmitir",
                    "El portador es más débil",
                    "El portador no puede reproducirse"
                ),
                correctIndex = 1,
                explanationEs = "El alelo recesivo está enmascarado pero sigue en el genotipo y llega a la mitad de sus descendientes."
            ),
            BreedingChapterQuestion(
                promptEs = "Una camada de 20 plantas sale de Aa x Aa y no muestra ningún rasgo recesivo. ¿Qué ocurre?",
                optionsEs = listOf(
                    "El cruce no funcionó",
                    "Es una oscilación esperable: 3:1 es una media y 20 plantas son pocas",
                    "Las plantas se estropearon",
                    "Apareció una mutación"
                ),
                correctIndex = 1,
                explanationEs = "Con un cuarto recesivo esperado, 20 plantas dan unas cinco. Obtener cero es improbable pero posible; hay que contar más antes de concluir."
            )
        )
    )

    /* ── Chapter 2: from the asset ───────────────────────────────────────── */

    /** Chapter 2: `generations` in the asset, quoted verbatim. */
    private fun generationsChapter(generations: List<BreedingGeneration>): BreedingChapter {
        val f1 = generations.firstOrNull { it.id == "f1" }
        val f2 = generations.firstOrNull { it.id == "f2" }
        val ibl = generations.firstOrNull { it.id == "ibl" }
        return BreedingChapter(
            id = "generaciones",
            titleEs = "Generaciones y cruces",
            summaryEs = "Qué es cada generación filial, de la F1 a la F5, y en qué momento empieza la estabilización.",
            bodyEs = listOfNotNull(
                f1?.let { "${it.label} — ${it.descriptionEs}" },
                f2?.let { "${it.label} — ${it.descriptionEs}" },
                ibl?.let { "${it.label} — ${it.descriptionEs}" }
            ),
            keyPointsEs = listOfNotNull(
                "La F2 es la generación con más variación; a partir de la F3 empieza la estabilización.",
                "La IBL fija el genoma pero pierde vigor: es una herramienta de trabajo, no un producto final."
            ),
            source = BreedingContentSource.FromAsset(
                listOf(
                    BreedingAssetRef(BreedingContentCollection.GENERATIONS, "f1"),
                    BreedingAssetRef(BreedingContentCollection.GENERATIONS, "f2"),
                    BreedingAssetRef(BreedingContentCollection.GENERATIONS, "f3"),
                    BreedingAssetRef(BreedingContentCollection.GENERATIONS, "f4"),
                    BreedingAssetRef(BreedingContentCollection.GENERATIONS, "f5"),
                    BreedingAssetRef(BreedingContentCollection.GENERATIONS, "ibl")
                )
            ),
            questions = listOf(
                BreedingChapterQuestion(
                    promptEs = "¿En qué generación aparece la máxima variación genética?",
                    optionsEs = listOf("F1", "F2", "F3", "F5"),
                    correctIndex = 1,
                    explanationEs = "La F2 es la generación donde la segregación se revela y se ven todas las combinaciones de rasgos."
                ),
                BreedingChapterQuestion(
                    promptEs = "Cruzar dos F1 entre sí, de la misma camada, produce:",
                    optionsEs = listOf("Una F2", "Una F3", "Una IBL", "Un retrocruce"),
                    correctIndex = 0,
                    explanationEs = "El cruce entre hermanos de la misma F1 da una F2, la generación de máxima variación."
                ),
                BreedingChapterQuestion(
                    promptEs = "¿Qué es una línea IBL?",
                    optionsEs = listOf(
                        "Un cruce entre dos variedades distintas",
                        "Una población endogamizada hasta ser genéticamente homogénea",
                        "La primera generación de un retrocruce",
                        "Un protocolo de floración"
                    ),
                    correctIndex = 1,
                    explanationEs = "IBL es una línea consanguínea: endogamia controlada durante muchas generaciones hasta fijar el genoma."
                ),
                BreedingChapterQuestion(
                    promptEs = "¿Cuál es el coste de cruzar muchas veces dentro de una misma línea?",
                    optionsEs = listOf(
                        "Ninguno",
                        "Pérdida de vigor por consanguinidad",
                        "Más velocidad de floración",
                        "Más aroma siempre"
                    ),
                    correctIndex = 1,
                    explanationEs = "La endogamia fija los rasgos pero reduce el vigor, y la IBL lo recoge entre sus contras."
                )
            )
        )
    }

    /* ── Chapter 3: authored ─────────────────────────────────────────────── */

    /**
     * Chapter 3, written for the app.
     *
     * The asset has a `reversal` technique and a `feminized` generation, but
     * neither mentions ethylene or explains why STS works, and the request asked
     * for the mechanism specifically. So the biology is authored here and marked
     * as such, while the two asset entries it builds on stay credited.
     */
    private fun feminizationChapter(
        generations: List<BreedingGeneration>,
        techniques: List<BreedingTechnique>
    ): BreedingChapter {
        val feminized = generations.firstOrNull { it.id == "feminized" }
        val reversal = techniques.firstOrNull { it.id == "reversal" }
        return BreedingChapter(
            id = "feminizacion",
            titleEs = "Feminización y reversión de sexo con STS",
            summaryEs = "Cómo se consigue polen de una planta hembra y qué hace exactamente el tiosulfato de plata.",
            bodyEs = listOfNotNull(
                feminized?.let { "${it.label} — ${it.descriptionEs}" },
                reversal?.let { "${it.labelEs} — ${it.descriptionEs}" },
                "El tiosulfato de plata bloquea la síntesis de etileno en la flor, que es la hormona que sostiene el desarrollo de los estambres. Sin etileno la flor no forma los estambres, la planta deja de producir polen masculino y se vuelve hermafrodita. El polen que produce esa flor porta el cromosoma X, así que al polinizar otra hembra se obtienen semillas XX, es decir, plantas hembra.",
                "El protocolo habitual es aplicar STS por vía foliar en prefloración, durante varios días seguidos, y repetir aplicaciones puntuales hasta que aparezcan las flores masculinas. Conviene retirar el producto al confirmar la reversión y vigilar que la planta no se dane por sobreexposición.",
                "La reversión también se puede provocar con plata coloidal, más suave pero más lenta, o con estrés osmótico. El inconveniente es el mismo en todos los métodos: la planta madre queda predispuesta a mostrar flores masculinas si el estrés continúa, y el cruce usa una única planta, de modo que se pierde diversidad genética respecto a un cruce normal."
            ),
            keyPointsEs = listOf(
                "El objetivo de la reversión es conseguir polen XX de una hembra de buena genética.",
                "El ETS bloquea el etileno, la hormona que desarrolla los estambres.",
                "La contrapartida es la pérdida de diversidad genética al cruzar la misma línea dos veces."
            ),
            source = BreedingContentSource.Authored(
                "Texto redactado para la app: el asset describe el STS y la plata coloidal, pero no explica el mecanismo del etileno ni el polen XX."
            ),
            questions = listOf(
                BreedingChapterQuestion(
                    promptEs = "¿Qué hormona bloquea el tiosulfato de plata?",
                    optionsEs = listOf("Etileno", "Auxina", "Citoquinina", "Ácido abscísico"),
                    correctIndex = 0,
                    explanationEs = "El tiosulfato de plata inhibe la síntesis de etileno, y sin etileno la flor no desarrolla los estambres."
                ),
                BreedingChapterQuestion(
                    promptEs = "Una reversión usa una sola planta madre. ¿Cuál es la contrapartida?",
                    optionsEs = listOf(
                        "Se pierde diversidad genética",
                        "Aumenta la velocidad de floración",
                        "El aroma se vuelve más intenso",
                        "Deja de hacer falta la planta madre"
                    ),
                    correctIndex = 0,
                    explanationEs = "Cruzar una misma línea dos veces reduce la variación genética de la descendencia."
                ),
                BreedingChapterQuestion(
                    promptEs = "¿Qué condición es la más adecuada para el proceso?",
                    optionsEs = listOf("Cualquier planta", "Una planta sin genética", "Una planta sana y estable", "Una plántula muy joven"),
                    correctIndex = 2,
                    explanationEs = "Cuanto más sana y estable sea la planta madre, mejor responde la reversión y menos problemas da la descendencia."
                ),
                BreedingChapterQuestion(
                    promptEs = "Tras aplicar STS la planta amarillea o se daña. Lo más probable es:",
                    optionsEs = listOf(
                        "Que las condiciones sean correctas",
                        "Sobreexposición o mal uso del producto",
                        "Que la planta ya estuviera mal",
                        "Falta de luz"
                    ),
                    correctIndex = 1,
                    explanationEs = "El STS es fitotóxico si se aplica mal: hay que respetar dosis, horarios y retirada."
                )
            )
        )
    }

    /* ── Chapter 4: authored ─────────────────────────────────────────────── */

    /**
     * Chapter 4, written for the app.
     *
     * The asset has `regular_selection` and `phenotype_stabilization`, but nothing
     * on how a dominant trait appears sooner than a recessive one, or on what
     * makes a phenotype worth selecting. Those are the substance of the request,
     * so the chapter is authored and credits the two asset entries it builds on.
     */
    private fun phenotypeChapter(techniques: List<BreedingTechnique>): BreedingChapter {
        val selection = techniques.firstOrNull { it.id == "regular_selection" }
        val stabilization = techniques.firstOrNull { it.id == "phenotype_stabilization" }
        return BreedingChapter(
            id = "fenotipica",
            titleEs = "Fenotipado y estabilización",
            summaryEs = "Cómo se eligen las plantas que pasan a la siguiente generación y cómo se fija un fenotipo.",
            bodyEs = listOfNotNull(
                selection?.let { "${it.labelEs} — ${it.descriptionEs}" },
                stabilization?.let { "${it.labelEs} — ${it.descriptionEs}" },
                "Elegir una planta no es elegir la más bonita, sino la más representativa de lo que se quiere. Un buen criterio de fenotipado se escribe antes de sembrar: aroma, estructura, tiempo de floración, densidad, resistencia. Así la comparación es objetiva y dos personas seleccionarían lo mismo en la misma planta.",
                "La población necesaria depende de la genética del cruce y del rasgo buscado. Un rasgo dominante se ve pronto y con menos plantas; uno recesivo puede necesitar muchas más camadas para asomar. La regla práctica que da la biblioteca es trabajar entre 20 y 200 plantas y descartar progresivamente hasta quedarse con las tres o cuatro mejores.",
                "Estabilizar exige repetir el ciclo: seleccionar, cruzar las mejores entre sí, volver a sembrar y volver a seleccionar. Cada generación reduce la variación. Cuando una camada entera reproduce el mismo fenotipo, la línea está fijada; hasta entonces, una camada todavía puede sorprender con una planta fuera de tipo."
            ),
            keyPointsEs = listOf(
                "Escribe el criterio de selección antes de sembrar.",
                "La biblioteca sugiere sembrar entre 20 y 200 plantas y descartar progresivamente.",
                "Un rasgo recesivo necesita más población para aparecer que uno dominante.",
                "Una línea está fijada cuando toda la capa reproduce el mismo fenotipo."
            ),
            source = BreedingContentSource.Authored(
                "Texto redactado para la app: el asset describe el fenohunt y la estabilización, pero no explica el dominante frente al recesivo ni fija un criterio de selección."
            ),
            questions = listOf(
                BreedingChapterQuestion(
                    promptEs = "Un rasgo recesivo que no aparece en la F1, ¿cuándo se ve por primera vez?",
                    optionsEs = listOf("En la F1", "En la F2", "En la F5", "Nunca"),
                    correctIndex = 1,
                    explanationEs = "El alelo recesivo está enmascarado en la F1 heterocigota y se expresa al separarse en la F2."
                ),
                BreedingChapterQuestion(
                    promptEs = "Según la biblioteca, cuántas plantas se siembran en un fenohunt:",
                    optionsEs = listOf("Entre 20 y 200", "Entre 2 y 5", "Exactamente 500", "Una sola"),
                    correctIndex = 0,
                    explanationEs = "La técnica de fenohunt indica cultivar entre 20 y 200 plantas y descartar progresivamente."
                ),
                BreedingChapterQuestion(
                    promptEs = "¿Cuándo se considera que una línea está estabilizada?",
                    optionsEs = listOf(
                        "Después de la primera floración",
                        "Cuando toda la capa reproduce el mismo fenotipo",
                        "Cuando supera los 30 días",
                        "Nunca se estabiliza del todo"
                    ),
                    correctIndex = 1,
                    explanationEs = "La estabilización consiste en que la descendencia replique de forma consistente el fenotipo elegido."
                ),
                BreedingChapterQuestion(
                    promptEs = "Para seleccionar bien hace falta:",
                    optionsEs = listOf(
                        "Elegir la planta más alta",
                        "Un criterio de fenotipado definido antes de sembrar",
                        "Sembrar más de mil plantas",
                        "Elegir siempre la primera que florece"
                    ),
                    correctIndex = 1,
                    explanationEs = "Un criterio escrito de antemano hace que la comparación sea objetiva y repetible."
                )
            )
        )
    }

    /* ── Chapter 5: from the asset ───────────────────────────────────────── */

    /** Chapter 5: `glossary`, quoted verbatim. Every paragraph is one entry. */
    private fun glossaryChapter(glossary: List<BreedingTerm>): BreedingChapter =
        BreedingChapter(
            id = "glosario",
            titleEs = "Glosario de breeding",
            summaryEs = "Los términos que aparecen en los capítulos anteriores, con su definición.",
            bodyEs = glossary.map { "${it.term} — ${it.definitionEs}" },
            keyPointsEs = emptyList(),
            source = BreedingContentSource.FromAsset(
                glossary.map { BreedingAssetRef(BreedingContentCollection.GLOSSARY, it.term) }
            ),
            questions = listOf(
                BreedingChapterQuestion(
                    promptEs = "¿Qué es la heterosis?",
                    optionsEs = listOf(
                        "Vigor híbrido: la superioridad de la F1 frente a sus parentales",
                        "La pérdida de vigor en la F2",
                        "Una técnica de curado",
                        "El polen XX"
                    ),
                    correctIndex = 0,
                    explanationEs = "El glosario define la heterosis como vigor híbrido, la superioridad de la F1 frente a sus parentales."
                ),
                BreedingChapterQuestion(
                    promptEs = "Según el glosario, el pistilo es:",
                    optionsEs = listOf(
                        "El órgano masculino que produce el polen",
                        "El órgano femenino que recibe el polen y forma la semilla",
                        "Una técnica de floración",
                        "Un tipo de retrocruce"
                    ),
                    correctIndex = 1,
                    explanationEs = "El pistil, con los pelos blancos, recibe el polen y forma la semilla."
                ),
                BreedingChapterQuestion(
                    promptEs = "Un cubing consiste en:",
                    optionsEs = listOf(
                        "Congelar polen durante un año",
                        "Varias generaciones de retrocruce para reproducir un fenotipo élite",
                        "Sembrar el mismo cruce en distintas carpas",
                        "Cosechar flores masculinas"
                    ),
                    correctIndex = 1,
                    explanationEs = "El glosario lo define como varias generaciones de retrocruce para reproducir un fenotipo élite."
                ),
                BreedingChapterQuestion(
                    promptEs = "Un fenohunt es:",
                    optionsEs = listOf(
                        "Una búsqueda sistemática del mejor fenotipo dentro de una población",
                        "Un cruce entre dos variedades distintas",
                        "Una técnica de reversión",
                        "Un protocolo de floración"
                    ),
                    correctIndex = 0,
                    explanationEs = "El glosario lo define como la búsqueda sistemática del mejor fenotipo dentro de una población."
                )
            )
        )
}

/** Spanish label for a genotype, for the picker in the Punnett UI. */
fun genotypeLabelEs(genotype: Genotype): String = when (genotype.notation) {
    "AA" -> "AA — homocigoto dominante"
    "Aa" -> "Aa — heterocigoto"
    "aa" -> "aa — homocigoto recesivo"
    else -> genotype.notation
}

/** Minimum options a question must offer for [BreedingChapterQuestion] to be answerable. */
const val MIN_OPTIONS_PER_QUESTION = 2
