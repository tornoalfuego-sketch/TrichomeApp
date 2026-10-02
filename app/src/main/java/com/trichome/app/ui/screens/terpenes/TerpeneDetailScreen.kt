package com.trichome.app.ui.screens.terpenes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.trichome.app.data.repository.Terpene
import com.trichome.app.model.BiosynthesisExplainer
import com.trichome.app.model.DataRowLayout
import com.trichome.app.model.EntourageFilters
import com.trichome.app.model.EntourageTab
import com.trichome.app.model.GrowOutGuides
import com.trichome.app.model.TerpeneAgronomyContent
import com.trichome.app.model.TerpeneAgronomyCopy
import com.trichome.app.model.TerpeneVolatility
import com.trichome.app.model.TerpeneVolatilityCopy
import com.trichome.app.model.VolatilityBar
import com.trichome.app.model.VolatilityCurve
import com.trichome.app.model.VolatilityCurveCopy
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.components.accentTextButtonColors
import com.trichome.app.ui.screens.entourage.entourageRoute
import com.trichome.app.ui.theme.LocalTertiaryText
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.TerpenesViewModel
import com.trichome.app.viewmodel.appViewModel
import kotlinx.coroutines.launch

/**
 * Full detail card for a single terpene.
 *
 * The catalog entry is resolved by id, then every optional field is rendered
 * only when it is populated, so a partially authored entry degrades to a
 * shorter card instead of showing empty headings.
 *
 * Opening the card is what registers the discovery in the progression, so the
 * reward is tied to actually reading the content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerpeneDetailScreen(
    terpeneId: String,
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val vm = appViewModel { TerpenesViewModel(it) }
    val scope = rememberCoroutineScope()
    val scheme = themeState.colorScheme()

    var terpene by remember(terpeneId) { mutableStateOf<Terpene?>(null) }
    var partners by remember(terpeneId) { mutableStateOf<List<Terpene>>(emptyList()) }
    var notFound by remember(terpeneId) { mutableStateOf(false) }
    var unlocked by remember(terpeneId) { mutableStateOf(false) }
    var isNewDiscovery by remember(terpeneId) { mutableStateOf(false) }

    // F2: one row per compound carrying the boiling point, the band and where
    // the band came from. Resolved through the ViewModel's single index so the
    // screen never assembles a temperature of its own.
    var volatility by remember(terpeneId) { mutableStateOf<TerpeneVolatility?>(null) }
    var curve by remember(terpeneId) { mutableStateOf(VolatilityCurve(emptyList())) }

    // F3: the agronomy block, beside the vapourisation card and not instead of
    // it. The copy arrives fully built from the model — every lever, every
    // evidence level and the sentence that says the catalog documents none for a
    // compound without an entry — so this file decides only where it goes.
    var agronomy by remember(terpeneId) { mutableStateOf<TerpeneAgronomyContent?>(null) }

    LaunchedEffect(terpeneId) {
        val found = vm.detail(terpeneId)
        if (found == null) {
            notFound = true
        } else {
            terpene = found
            partners = vm.partners(found)
            unlocked = found.id in vm.discovered
            if (!unlocked) {
                isNewDiscovery = vm.markDiscovered(found)
            }
            volatility = vm.volatilityOf(found)
            curve = vm.curveFor(found)
            agronomy = vm.agronomyFor(found)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // This screen draws its own header instead of a `Scaffold`, so it
                // gets no `contentWindowInsets` for free. Without the status bar
                // inset the back arrow and the title sit underneath the status
                // bar. The activity is already edge-to-edge, so this is not a
                // targetSdk 36 regression -- it is the pre-existing shape of a
                // header that never declared its insets.
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                terpene?.name ?: "Terpeno",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            terpene?.let { entry ->
                IconButton(onClick = { vm.toggleFavorite(entry) }) {
                    Icon(
                        imageVector = if (entry.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (entry.isFavorite) {
                            "Quitar de favoritos"
                        } else {
                            "Añadir a favoritos"
                        },
                        tint = if (entry.isFavorite) scheme.tertiary else scheme.onSurfaceVariant
                    )
                }
            }
        }

        if (notFound) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No se encontró ese terpeno.", color = scheme.onSurfaceVariant)
            }
            return@Column
        }

        val entry = terpene
        if (entry == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                TerpeneHeader(entry, themeState, isNewDiscovery)
            }

            // Identity: formula, mass, family, boiling point, richness.
            item {
                DetailCard("🧪 Identidad química") {
                    DataRow("Fórmula", entry.formula)
                    DataRow("Masa molar", entry.molarMass)
                    DataRow("Familia química", entry.family)
                    DataRow("Punto de ebullición", entry.boilingPoint)
                    DataRow("Riqueza en cannabis", entry.richness)
                }
            }

            // F2: the temperature behaviour, on all 158 pages and not only on
            // the ten the module measures. `Punto de ebullición` above is the
            // measured fact; everything below it says how far the compound is
            // useful once the element gets there, and marks whether that band
            // was measured or worked out here.
            val volatilityRow = volatility
            if (volatilityRow != null) {
                item {
                    VolatilityCard(volatilityRow, curve, entry.id, scheme)
                }
            }

            // F3. Rendered unconditionally once it exists, so a compound with no
            // documented lever still gets the sentence that says so, and a
            // documented one gets its basis lines with it.
            val agronomyBlock = agronomy
            if (agronomyBlock != null) {
                item {
                    AgronomyCard(agronomyBlock, scheme)
                }
            }

            item {
                DetailCard("👃 Perfil sensorial") {
                    DetailParagraph("Aroma", entry.aroma)
                    DetailParagraph("Sabor", entry.taste)
                }
            }

            if (entry.effects.isNotEmpty()) {
                item {
                    DetailCard("🧠 Efectos") {
                        ChipList(entry.effects, scheme.primary)
                    }
                }
            }

            if (entry.medicalProperties.isNotEmpty()) {
                item {
                    DetailCard("⚕️ Propiedades médicas") {
                        ChipList(entry.medicalProperties, scheme.tertiary)
                    }
                }
            }

            if (entry.mechanism.isNotBlank()) {
                item {
                    DetailCard("🎯 Mecanismo de acción") {
                        DetailParagraph("", entry.mechanism)
                    }
                }
            }

            if (entry.biosynthesis.isNotBlank()) {
                item {
                    DetailCard("🧬 Biosíntesis") {
                        DetailParagraph("", entry.biosynthesis)
                    }
                }
            }

            if (entry.toxicity.isNotBlank()) {
                item {
                    DetailCard("⚠️ Toxicidad y precauciones") {
                        DetailParagraph("", entry.toxicity)
                    }
                }
            }

            // The Séquito action, for the terpenes the module models. The join
            // from a `terpenes.json` id to an `EntourageTerpene` goes through
            // `EntourageFilters.terpeneForCatalogId`, which reads
            // `EntourageTerpene.catalogId` — so the two catalogs cannot disagree
            // about which id means which compound. A terpene the module does not
            // model gets no button rather than one that opens an empty filter.
            //
            // Not wrapped in `remember`: it is a linear scan over ten enum
            // entries, and the value only has to exist while this list is built.
            val entourageTerpene = EntourageFilters.terpeneForCatalogId(entry.id)
            if (entourageTerpene != null) {
                item {
                    DetailCard("🧬 Efecto Séquito") {
                        Text(
                            "Ver las sinergias documentadas que contienen ${entourageTerpene.labelEs}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        TextButton(
                            onClick = {
                                navController.navigate(
                                    entourageRoute(
                                        tab = EntourageTab.NETWORK,
                                        terpene = entourageTerpene
                                    )
                                )
                            },
                            colors = accentTextButtonColors(scheme, scheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ver sinergias del Efecto Séquito", maxLines = 1)
                        }
                    }
                }
            }

            if (partners.isNotEmpty()) {
                item {
                    DetailCard("🤝 Efecto entourage") {
                        Text(
                            "Estos compuestos actúan como sinergistas: " +
                                "modulan el receptor y amplifican o matizan el efecto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            partners.forEach { partner ->
                                PartnerRow(partner, scheme) {
                                    navController.navigate("terpene/${partner.id}")
                                }
                            }
                        }
                    }
                }
            }

            if (entry.foundIn.isNotEmpty()) {
                item {
                    DetailCard("🌍 También se encuentra en") {
                        ChipList(entry.foundIn, scheme.secondary)
                    }
                }
            }

            if (entry.strains.isNotEmpty()) {
                item {
                    DetailCard("🌿 Cepas con alto contenido") {
                        ChipList(entry.strains, scheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TerpeneHeader(
    entry: Terpene,
    themeState: TrichomeThemeState,
    isNew: Boolean
) {
    val scheme = themeState.colorScheme()
    SolidPanel {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(scheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Science,
                        contentDescription = null,
                        tint = scheme.primary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.headlineSmall)
                    if (entry.family.isNotBlank()) {
                        Text(
                            entry.family,
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (isNew) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = scheme.primary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "✨ Descubrimiento registrado · +20 XP",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    content: @Composable () -> Unit
) {
    SolidPanel {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/**
 * A label and its value on one row, for every row on the page.
 *
 * ## Why the value is weighted and the label is not
 *
 * This row shipped as `Arrangement.SpaceBetween` with two unweighted `Text`s,
 * which lays out each child with the *row's* width and then pushes them apart.
 * Nothing bounded the pair, so a value wider than the leftover space did not
 * wrap — it overflowed the row and landed on top of the label. `Origen de la
 * ventana` / `Medida en la tabla de Séquito` did exactly that: the two touched,
 * and the monospace value broke under itself.
 *
 * `Row` measures unweighted children **before** weighted ones, so leaving the
 * label unweighted keeps it on one line at its natural width and handing the
 * value `DataRowLayout.VALUE_WEIGHT` — all that is left — is what confines a
 * long value to its own column. The value stays right-aligned inside that
 * column, so every row whose value is short renders flush to the right edge
 * exactly as it did before.
 *
 * The fix is here rather than at the provenance call site on purpose: five other
 * rows on this page have the same shape and were only surviving on short values.
 * A long value must not be able to break the layout for any of them.
 *
 * The numbers are in [DataRowLayout], not here, because a value no JVM test can
 * reach is a value nobody can catch before it ships — the same lesson as the
 * zero-weight crash.
 */
@Composable
private fun DataRow(label: String, value: String) {
    if (value.isBlank()) return
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        // Top, not centred: the value wraps to as many lines as it needs and the
        // label belongs beside its first line.
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.width(DataRowLayout.GAP_DP.dp))
        Text(
            value,
            modifier = Modifier.weight(DataRowLayout.VALUE_WEIGHT),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            ),
            color = scheme.onSurface,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun DetailParagraph(label: String, value: String) {
    if (value.isBlank()) return
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(vertical = 4.dp)) {
        if (label.isNotBlank()) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface
        )
    }
}

/**
 * F2: the vapourisation section, on every catalog page.
 *
 * ## Why the derived band is visible and not behind a disclosure
 *
 * A user browsing 148 terpenes with no temperature and ten with one reads as a
 * broken database. Filling the gap with a band that looks measured would be a
 * worse defect than the gap. So the section is built the way [EstimatedClimateCard]
 * is built: the estimated number arrives carrying `≈`, and the sentence saying
 * where it came from arrives **with** it, as a field of the model, so no call
 * site can render the number without the evidence.
 *
 * ## The evidence line is not optional
 *
 * `evidenceEs` and `limitsEs` are rendered unconditionally, straight after the
 * numbers, with no `if`, no `AnimatedVisibility` and no "ver más". That is the
 * same rule the Séquito module holds its own card to, and it is asserted from
 * `TerpeneDetailVolatilityTest` by reading this file's source.
 *
 * ## One scroll owner
 *
 * A plain `Column`. The page's `LazyColumn` owns the scroll, so this section
 * must not declare one of its own.
 */
@Composable
private fun VolatilityCard(
    volatility: TerpeneVolatility,
    curve: VolatilityCurve,
    subjectCatalogId: String,
    scheme: androidx.compose.material3.ColorScheme
) {
    val tertiary = LocalTertiaryText.current
    val content = TerpeneVolatilityCopy.contentOf(volatility)
    val partnerCount = (curve.steps.size - 1).coerceAtLeast(0)
    val curveContent = VolatilityCurveCopy.contentOf(
        curve = curve,
        subjectLabelEs = volatility.labelEs,
        partnerCount = partnerCount
    )

    DetailCard("🌡️ Vaporización") {
        DataRow("Ventana de vaporización", content.windowEs)
        DataRow("Origen de la ventana", content.provenanceLabelEs)

        if (content.noteEs.isNotBlank()) {
            DetailParagraph("", content.noteEs)
        }

        // The evidence line. Always rendered, never behind an interaction.
        DetailParagraph("", content.evidenceEs)
        DetailParagraph("", content.limitsEs)

        if (curve.isNotEmpty && curveContent.isDrawable) {
            Spacer(Modifier.height(12.dp))
            Text(
                curveContent.titleEs,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                curveContent.scopeEs,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            curve.stages.forEach { stage ->
                val isSubject = stage.step.catalogId == subjectCatalogId
                val bar = curve.barFor(stage.step)
                Text(
                    stage.step.window.formatEs() + " · " + stage.step.labelEs,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSubject) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isSubject) scheme.onSurface else scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(3.dp))
                VolatilityBarTrack(bar, isSubject, scheme, tertiary)
                if (stage.pending.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Después siguen: " + stage.pending.joinToString(", ") { it.labelEs },
                        style = MaterialTheme.typography.labelSmall,
                        color = tertiary
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            if (curveContent.derivedWarningEs.isNotBlank()) {
                Text(
                    curveContent.derivedWarningEs,
                    style = MaterialTheme.typography.labelSmall,
                    color = tertiary
                )
                Spacer(Modifier.height(6.dp))
            }

            if (curveContent.contradictionEs.isNotBlank()) {
                Text(
                    curveContent.contradictionEs,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error
                )
            } else {
                Text(
                    curveContent.aggregateEs,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface
                )
            }
        }
    }
}

/**
 * F3: the agronomy section, beside the vapourisation card and not instead of it.
 *
 * ## What this composable decides and what it does not
 *
 * It decides placement and nothing else. Every sentence, every lever and every
 * show-or-hide decision arrives inside [TerpeneAgronomyContent], built by
 * `TerpeneAgronomyCopy` in `model/` — the rule the `weight(0f)` crash taught,
 * written down: Compose has no JVM unit-test runtime in this project, so a
 * number or a choice only a composable can reach is a choice nobody can catch
 * before it ships.
 *
 * ## The gap is stated, not left blank
 *
 * A compound the catalog documents no lever for gets
 * [TerpeneAgronomyCopy.NOT_DOCUMENTED_ES] — a sentence — instead of an empty
 * block. Silence reads as "nothing to add here"; only a sentence can say "the
 * catalog is silent about this one".
 *
 * ## The evidence level is rendered, never collapsed
 *
 * Each lever's `detailEs` is followed by its `basisEs` under a label that names
 * [AgronomyEvidence.labelEs]. No `if` wraps either line, no `AnimatedVisibility`,
 * no "ver más" — the same rule `TerpeneDetailVolatilityTest` holds for the
 * volatility evidence line and `EntourageScrollOwnershipTest` holds for the
 * synergy card.
 *
 * ## One scroll owner
 *
 * A plain [Column]. The page's `LazyColumn` owns the scroll, so this section
 * declares none of its own — a nested `verticalScroll` inside it is measured
 * with an infinite maximum height and throws.
 *
 * ## No hardcoded colour
 *
 * `scheme`, `tertiary` and nothing else. No literal, and no `Color.Unspecified`
 * published into a `Surface`.
 */
@Composable
private fun AgronomyCard(
    content: TerpeneAgronomyContent,
    scheme: androidx.compose.material3.ColorScheme
) {
    val tertiary = LocalTertiaryText.current
    val biosynthesis = BiosynthesisExplainer.contentFor(content.family)

    DetailCard(content.titleEs) {
        // Either the documented response or the sentence saying the catalog is
        // silent. One of the two is always printed.
        if (content.isDocumented) {
            DetailParagraph(TerpeneAgronomyCopy.RESPONSE_LABEL_ES, content.responseEs)
        } else {
            DetailParagraph("", content.notDocumentedEs)
        }

        // Levers, each with its basis immediately under it.
        content.levers.forEach { lever ->
            DetailParagraph(lever.titleEs, lever.detailEs)
            DetailParagraph(
                TerpeneAgronomyCopy.basisLabelEs(lever.evidenceLabelEs),
                lever.basisEs
            )
        }

        // The compound's route, always: it is a fact about the compound's size,
        // not an agronomic claim, so it survives a compound with no levers.
        DetailParagraph("", content.routeEs)

        Spacer(Modifier.height(12.dp))
        Text(
            biosynthesis.titleEs,
            style = MaterialTheme.typography.titleSmall,
            color = tertiary
        )
        DetailParagraph("", biosynthesis.introEs)
        biosynthesis.steps.forEach { step ->
            DetailParagraph(step.titleEs, step.bodyEs)
        }
        DetailParagraph("", biosynthesis.sizeRuleEs)
        DetailParagraph("", biosynthesis.trichomeEs)
        // The caveat is rendered unconditionally, like the volatility evidence
        // line: an explainer that can show its steps without saying what it
        // cannot settle is half a statement.
        DetailParagraph("", biosynthesis.caveatEs)

        Spacer(Modifier.height(12.dp))
        Text(
            TerpeneAgronomyCopy.SHARED_GUIDES_HEADING_ES,
            style = MaterialTheme.typography.titleSmall,
            color = tertiary
        )
        DetailParagraph("", TerpeneAgronomyCopy.SHARED_GUIDES_SCOPE_ES)
        GrowOutGuides.all.forEach { guide ->
            DetailParagraph(guide.titleEs, guide.whatEs)
            DetailParagraph(
                TerpeneAgronomyCopy.basisLabelEs(guide.evidence.labelEs),
                guide.basisEs
            )
        }

        Text(
            TerpeneAgronomyCopy.NOT_A_DIRECTIVE_ES,
            style = MaterialTheme.typography.labelSmall,
            color = tertiary
        )
    }
}

/**
 * One rung of the curve as a bar on the selection's shared temperature scale.
 *
 * The geometry is [VolatilityCurve.barFor]'s job and lives in the model, because
 * Compose has no unit-test runtime here — only device-only `androidTest`. What is
 * left here is layout only: three weighted boxes on one row.
 *
 * A derived band is drawn in the tertiary text colour and a measured one in the
 * accent, so the difference between "the app worked this out" and "the table
 * says so" is visible in the picture and not only in the text. No literal
 * colour anywhere: the scheme and the third text role only.
 */
@Composable
private fun VolatilityBarTrack(
    bar: VolatilityBar,
    isSubject: Boolean,
    scheme: androidx.compose.material3.ColorScheme,
    tertiary: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        // The weights come straight from `VolatilityBar`, which requires both to
        // be strictly positive because `RowScope.weight` throws on zero. The
        // defensive `coerceAtLeast` that used to sit here was the bug's hiding
        // place: it papered over the model's zero instead of surfacing it, and
        // the first bar of every curve is exactly the case that crashed.
        Box(Modifier.weight(bar.startFraction))
        Box(
            Modifier
                .weight(bar.widthFraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(if (isSubject) scheme.primary else tertiary)
        )
        Box(Modifier.weight(1f))
    }
}

@Composable
private fun ChipList(items: List<String>, color: androidx.compose.ui.graphics.Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PartnerRow(
    partner: Terpene,
    scheme: androidx.compose.material3.ColorScheme,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(partner.name, style = MaterialTheme.typography.titleSmall)
            Text(
                partner.family,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
        Text("→", color = scheme.primary)
    }
}