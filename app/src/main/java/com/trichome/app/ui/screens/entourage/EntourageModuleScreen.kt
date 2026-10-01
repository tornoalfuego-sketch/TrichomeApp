package com.trichome.app.ui.screens.entourage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.trichome.app.model.*
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.EntourageViewModel
import com.trichome.app.viewmodel.appViewModel

/**
 * Builds the Séquito module route.
 *
 * One builder instead of a hand-written route string at each call site: the
 * argument names and the encoding of an [EntourageTerpene] key live here and
 * nowhere else, so a renamed argument cannot leave one caller navigating to a
 * route the graph does not declare — which is an `IllegalArgumentException` at
 * the moment of the tap, not a compile error.
 *
 * @param tab the section to open on. Null leaves the graph's default.
 * @param terpene pre-selects the network filter.
 */
fun entourageRoute(
    tab: EntourageTab? = null,
    terpene: EntourageTerpene? = null
): String {
    val query = listOfNotNull(
        tab?.let { "tab=${it.key}" },
        terpene?.let { "terpene=${it.key}" }
    ).joinToString("&")
    return if (query.isEmpty()) ENTOURAGE_ROUTE else "$ENTOURAGE_ROUTE?$query"
}

/**
 * The path the `NavHost` declares, without its optional query arguments.
 *
 * Declared in the navigation package so the graph and the builder quote one
 * string; a second literal here would be a route that can drift from the
 * destination the builder navigates to.
 */
const val ENTOURAGE_ROUTE: String = "entourage"

/** Query argument naming the section to open on. */
const val ENTOURAGE_TAB_ARG: String = "tab"

/** Query argument naming the terpene the network filters to. */
const val ENTOURAGE_TERPENE_ARG: String = "terpene"


/**
 * The Séquito (entourage) module: the cannabinoid x terpene network, the
 * booster, the Lab minigame and the quiz.
 *
 * ## One scroll owner, four tabs
 *
 * The tab content is *emitted into* a single [LazyColumn] rather than living
 * inside four of them or four scrollable columns. A `verticalScroll` nested in
 * an already-scrolling parent is measured with an infinite maximum height and
 * throws at runtime — the crash `ScrollOwnershipTest` exists for — and that
 * failure is invisible to the compiler, to lint and to every behavioural test of
 * the chemistry underneath. Each section therefore contributes `item {}` blocks
 * through a `LazyListScope` extension ([entourageNetworkItems] and friends)
 * instead of owning a scroll.
 *
 * @param initialTabKey the section to open on, from the route.
 * @param initialTerpeneKey when present, the network opens filtered to the
 *   synergies containing that terpene. Resolved by
 *   [EntourageFilters.terpeneForKey] rather than by a second inline match, so an
 *   unknown key opens the unfiltered library instead of an empty screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntourageModuleScreen(
    initialTabKey: String?,
    initialTerpeneKey: String?,
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val vm = appViewModel { EntourageViewModel(it) }
    val scheme = themeState.colorScheme()
    val library = vm.library

    var tab by remember { mutableStateOf(EntourageTab.fromKey(initialTabKey)) }
    var filterKey by remember {
        mutableStateOf(EntourageFilters.terpeneForKey(initialTerpeneKey)?.key.orEmpty())
    }

    // Booster state. Cannabinoids and terpenes are one shared selection: the
    // planner reads the terpenes for the percentage and the cannabinoids for the
    // matched synergy, so presenting them as two independent questions would
    // invite a combination the planner cannot explain.
    var cannabinoids by remember { mutableStateOf(emptySet<Cannabinoid>()) }
    var terpenes by remember { mutableStateOf(emptySet<EntourageTerpene>()) }
    var target by remember { mutableStateOf<PharmacologicalProfile?>(null) }

    // Lab state.
    var caseIndex by remember { mutableIntStateOf(0) }
    var labDials by remember { mutableStateOf(emptyMap<Cannabinoid, Float>()) }
    var labTerpenes by remember { mutableStateOf(emptySet<EntourageTerpene>()) }
    var labResult by remember { mutableStateOf<LabResult?>(null) }

    // Quiz state. The lifecycle lives in the model machine; this only renders it.
    val questions = library?.questions ?: emptyList()
    val quiz = remember(questions) { EntourageQuiz(questions) }
    var quizState by remember(quiz) { mutableStateOf(quiz.state) }

    // A finished run pays through the `achievements` table. Keyed on the state,
    // so it fires exactly once per completion — and `EntourageRewards.pending`
    // makes a second run of the same badge a no-op anyway.
    LaunchedEffect(quizState) {
        val outcome = EntourageQuizUi.outcomeOf(quizState)
        if (outcome.finished) vm.grant(outcome.rewards)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "🧬 Efecto Séquito",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    "Terpeno × Cannabinoide",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }

        TabRow(
            selectedTabIndex = tab.ordinal,
            containerColor = scheme.surface
        ) {
            EntourageTab.entries.forEach { entry ->
                Tab(
                    selected = entry == tab,
                    onClick = { tab = entry },
                    text = { Text(entry.labelEs, maxLines = 1) }
                )
            }
        }

        if (library == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (vm.loadFailed) {
                        // A broken asset is reportable, not a spinner that never
                        // resolves: the app looking busy while it has nothing is
                        // the version of this the user cannot act on.
                        Text(
                            "No se pudo cargar el contenido de Séquito.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "El archivo data/entourage_data.json no está disponible " +
                                "o no se pudo interpretar.",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant
                        )
                    } else {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Cargando la biblioteca de Séquito…",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }
            return@Column
        }

        // The empty-content case is stated rather than left as a blank screen:
        // an unreadable asset is a failure the user should be able to report.
        if (library.isEmpty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No se pudo cargar el contenido de Séquito.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
            return@Column
        }

        val disclaimer = remember(library) { EntourageIntegrity.disclaimerFor(library.disclaimerEs) }
        val notice = remember(library) {
            EntourageIntegrity.noticeFor(library.unresolvedReferences)
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // First on every tab, above every section: the module's limits are
            // stated before its claims, and never relegated to a footer.
            item { EntourageDisclaimerPanel(disclaimer, themeState) }

            if (notice != null) {
                item { EntourageIntegrityPanel(notice) }
            }

            // One item per tab. The section is a plain Column, so the list stays
            // the only scroll owner; see EntourageNetworkSection for why a
            // nested vertical scroll is not an option here.
            item {
                when (tab) {
                    EntourageTab.NETWORK -> EntourageNetworkSection(
                        library = library,
                        filterKey = filterKey,
                        onFilter = { key -> filterKey = key },
                        cannabinoids = cannabinoids,
                        terpenes = terpenes,
                        onCannabinoids = { cannabinoids = it },
                        onTerpenes = { terpenes = it },
                        themeState = themeState
                    )

                    EntourageTab.BOOSTER -> EntourageBoosterSection(
                        library = library,
                        target = target,
                        onTarget = { target = it },
                        cannabinoids = cannabinoids,
                        terpenes = terpenes,
                        onCannabinoids = { cannabinoids = it },
                        onTerpenes = { terpenes = it },
                        themeState = themeState
                    )

                    EntourageTab.LAB -> EntourageLabSection(
                        library = library,
                        caseIndex = caseIndex,
                        onCaseIndex = { index ->
                            caseIndex = index
                            // A new case starts from its own dials: keeping the
                            // previous case's numbers would score the wrong
                            // puzzle.
                            labDials = emptyMap()
                            labTerpenes = emptySet()
                            labResult = null
                        },
                        dials = labDials,
                        onDials = { labDials = it },
                        labTerpenes = labTerpenes,
                        onLabTerpenes = { labTerpenes = it },
                        result = labResult,
                        onEvaluate = { case, selection ->
                            val solved = EntourageLab.solve(case, selection, library.profiles)
                            labResult = solved
                            // One payment per case, ever: `pending` drops the
                            // reward once the row is in the table, so retrying a
                            // case is free and replaying a Lab cannot inflate
                            // the total.
                            vm.grant(
                                EntourageRewards.forLabVerdict(
                                    case.id,
                                    case.titleEs,
                                    solved.verdict
                                )
                            )
                        },
                        themeState = themeState
                    )

                    EntourageTab.QUIZ -> EntourageQuizSection(
                        state = quizState,
                        onAnswer = { index -> quizState = quiz.answer(index) },
                        onNext = { quizState = quiz.next() },
                        onRestart = { quizState = quiz.restart() },
                        themeState = themeState
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            item {
                SolidPanel {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Sobre este módulo",
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "El porcentaje del Impulsor mide la distancia entre tus " +
                                "terpenos y las proporciones de un perfil de referencia. " +
                                "El Laboratorio resuelve casos clínicos con techos que el " +
                                "propio caso declara. Ninguna cifra de este módulo es una " +
                                "estimación de potencia ni un consejo médico.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
