package com.trichome.app.model

import java.text.Normalizer

/**
 * Live search over the Lab's case catalogue.
 *
 * ## Why the catalogue needed a filter before it needed more cases
 *
 * F5 shipped two modes and three pharmacological cases; F11 ships twelve cases
 * split across both. Twelve rows is the point at which "scroll to find the one
 * about storage" stops being reasonable and starts being the reason a grower
 * gives up on the Lab, and a minigame nobody can find is a minigame that does
 * not get played.
 *
 * ## What is searched
 *
 * Four fields, all shipped text: the case's **title**, its **brief** (the
 * paragraph that sets up the situation), its **mode**, and the **goal** it aims
 * at. The goal is included because the fastest way to find a case is to know you
 * want one about a cannabinoid ratio and not remember the title that asks for it.
 *
 * The mode is searched by its Spanish [LabMode.labelEs] rather than its key, so
 * typing "procesado" finds the agricultural cases and typing "handling" does not
 * — a key is an implementation detail and this is a Spanish picker.
 *
 * ## Accents and case
 *
 * Folded with the same [foldForSearch] rule [DiagnosisSearch] uses, and for the
 * same reason: a grower typing "acaro" has to find "Ácaro", and a hand-maintained
 * list of accented letters is wrong the first time a word outside it ships. The
 * **stored** text is never altered — only the comparison key is folded, so the
 * displayed title is the asset's title, byte for byte.
 */
object EntourageCaseSearch {

    /** Heading over the search field. */
    const val FIELD_LABEL_ES: String = "Buscar caso"

    /** Placeholder inside the empty field. */
    const val FIELD_HINT_ES: String = "Escribe un cultivo, un problema o una cosecha"

    /** The clear button's label. */
    const val CLEAR_LABEL_ES: String = "Borrar la búsqueda del caso"

    /** What the picker says when nothing matches. */
    const val NO_RESULTS_ES: String =
        "Ningún caso coincide con esa búsqueda. Prueba con un cultivo, una plaga o una cosecha."

    /** The result count, e.g. "4 de 12 casos". */
    const val COUNT_FORMAT_ES: String = "%d de %d casos"

    /** Row label for the mode filter. */
    const val MODE_FILTER_ES: String = "Modo"

    /** The "every mode" chip. */
    const val ALL_MODES_ES: String = "Todos"

    /** Every mode the catalogue filter offers, enum order. */
    val modes: List<LabMode> = LabMode.entries

    /**
     * One case, as the filter needs it.
     *
     * The folded haystack is built once per case rather than per keystroke, for
     * the same reason `DiagnosisSearch` builds its index once: a filter that
     * re-normalises twelve briefs on every character is doing per-keystroke work
     * that buys nothing.
     */
    data class Entry(
        val case: EntourageCase,
        /** The Spanish label of the case's own mode. */
        val modeLabelEs: String,
        /** The Spanish label of the goal, when the case has one. */
        val goalLabelEs: String,
        val haystack: String
    )

    /**
     * Folds accents and case out of [text] for comparison only.
     *
     * NFD plus the combining-mark range, the same rule as
     * [DiagnosisSearch.foldForSearch]. Duplicated rather than shared so the Lab
     * does not acquire a dependency on the diagnosis package for one function;
     * the rule is one line and both copies carry the same KDoc reason.
     */
    fun foldForSearch(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return buildString(decomposed.length) {
            decomposed.forEach { character ->
                if (character.code in COMBINING_MARKS) return@forEach
                append(character.lowercaseChar())
            }
        }
    }

    private val COMBINING_MARKS = 0x0300..0x036F

    /** Every Unicode whitespace character, so a blank field is blank however it is blank. */
    private val WHITESPACE = Regex("\\s+")

    /**
     * The whole catalogue, folded.
     *
     * @param cases the shipped case list.
     * @param profiles the shipped profile list, so a pharmacological case's goal
     *   resolves to a label instead of a raw enum key.
     */
    fun index(
        cases: List<EntourageCase>,
        profiles: List<EntourageProfile> = emptyList()
    ): List<Entry> {
        val labelsByProfile = profiles.associate { it.key to it.labelEs }
        return cases.map { case ->
            val modeLabel = case.mode.labelEs
            val goalLabel = case.goal?.let { labelsByProfile[it] }.orEmpty()
            Entry(
                case = case,
                modeLabelEs = modeLabel,
                goalLabelEs = goalLabel,
                haystack = foldForSearch(
                    listOf(
                        case.titleEs,
                        case.briefEs,
                        case.explanationEs,
                        modeLabel,
                        goalLabel,
                        case.handlingGoal?.labelEs.orEmpty()
                    ).joinToString(" ")
                )
            )
        }
    }

    /**
     * The entries in [mode], or all of them when it is null.
     *
     * Kept separate from [filter] so the mode row and the text field are two
     * independent inputs: a grower narrowing to "decisión de procesado" and then
     * typing a word gets the intersection without either rule knowing about the
     * other.
     */
    fun inMode(entries: List<Entry>, mode: LabMode?): List<Entry> =
        if (mode == null) entries else entries.filter { it.case.mode == mode }

    /**
     * The entries matching [query], in the index's own order.
     *
     * A blank query returns everything, because an empty field is not a filter
     * that matches nothing. Every whitespace-separated token has to match, which
     * is what makes "moho cosecha" find the cases that are about both.
     */
    fun filter(entries: List<Entry>, query: String): List<Entry> {
        // Split on any whitespace, not on a single space: a field holding a tab or
        // a newline is still blank, and a filter that emptied the whole picker
        // because of a stray tab is the classic live-search bug.
        val tokens = foldForSearch(query).split(WHITESPACE).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return entries
        return entries.filter { entry -> tokens.all { it in entry.haystack } }
    }

    /** `"4 de 12 casos"`, for the count line under the field. */
    fun countEs(shown: Int, total: Int): String = COUNT_FORMAT_ES.format(shown, total)
}