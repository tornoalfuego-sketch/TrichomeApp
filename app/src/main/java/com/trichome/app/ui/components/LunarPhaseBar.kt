package com.trichome.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trichome.app.model.LunarCardContent
import com.trichome.app.ui.theme.LocalTertiaryText

/**
 * The calendar's lunar button and the panel it expands into.
 *
 * ## Scroll discipline
 *
 * This lives **above** the calendar's `verticalScroll`, not inside it, and it is not
 * scrollable itself. `ScrollOwnershipTest` exists because a nested scroll inside
 * another scrollable measured its child with an infinite maximum height and killed
 * the process on a tap. Two rules keep that bug out of this component:
 *
 *  1. no `verticalScroll` here, ever — the panel is a fixed, bounded amount of text
 *     and the calendar's own scroll is the only scroll on the screen;
 *  2. the panel is composed *above* the scrolling `Column`, so it takes vertical
 *     space from the content rather than growing inside the scroll.
 *
 * `Scaffold` handles the reduced height: the `padding` it hands the content already
 * accounts for whatever the top bar measures, so the calendar shrinks and scrolls
 * instead of overflowing.
 *
 * ## Why there is no reduced-motion branch
 *
 * The project has no reduced-motion convention: no `LocalMotionDurationScale`, no
 * `animator_duration_scale` read, nothing in the theme that toggles animation. Adding
 * one here would be inventing a global mechanism from a single component, and it
 * would have to be threaded through the theme to be any use. `AnimatedVisibility` is
 * used instead, which is what `TerpenesScreen` and `MainActivity` already do, and
 * Compose's own duration scale is respected by it when the platform asks for less.
 *
 * ## Accessibility
 *
 * The button is a real `IconButton`, so it is reachable by tab and operable by enter
 * or tap — not hover-only. Its `contentDescription` comes from
 * [LunarCardContent.toggleDescriptionEs], which changes with the expanded state and
 * names what the press will do. The glyph itself is an emoji with no drawable
 * behind it, so the `Icon` is decorative and the text carries the meaning.
 */
@Composable
fun LunarPhaseBar(
    content: LunarCardContent,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The glyph is decorative: it is an emoji Text, not an image with an
            // accessible name, and the phase name is right next to it.
            Text(
                text = content.glyph,
                fontSize = 20.sp,
                modifier = Modifier.size(28.dp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(2.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = content.phaseLabelEs,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "${content.illuminationEs} ilumina · ${content.trendLabelEs}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalTertiaryText.current
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    // Announced label, from the pure copy so it is testable. The
                    // state changes with `expanded`, which is what makes this an
                    // operable disclosure rather than a static glyph.
                    contentDescription = content.toggleDescriptionEs
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            SolidPanel(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                cornerRadius = 12
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = content.tipHeadlineEs,
                        style = MaterialTheme.typography.titleSmall
                    )
                    // The point of the feature. A phase name alone teaches nothing,
                    // so the advice sentence is what the panel is actually for.
                    Text(
                        text = content.adviceEs,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = content.disclaimerEs,
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalTertiaryText.current
                    )
                }
            }
        }
    }
}