package com.trichome.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trichome.app.model.ClimateCardContent
import com.trichome.app.ui.theme.LocalTertiaryText
import com.trichome.app.ui.theme.metricValue

/**
 * The estimated outdoor climate card: temperature, humidity, VPD and its band.
 *
 * ## The number must never look like a measurement
 *
 * [AmbientClimate] returns a temperature, a humidity and a VPD from a cosine of the
 * day of year. Printed bare, `24 °C / 62 % / 0,84 kPa` is indistinguishable from a
 * $20 thermometer, and a grower comparing it against their own readings has no way to
 * tell the difference. So the estimate labelling is structural, not decorative:
 *
 *  - every value arrives from [ClimateCardContent] carrying an `≈` marker, so no call
 *    site can print a bare number;
 *  - [content.assumptionEs] states which latitude the model was handed, and says
 *    plainly that the app does not know the user's location when it assumed one;
 *  - [content.sourceEs] — "Sin sensores ni conexión" — sits at the bottom of the card.
 *
 * ## Colours
 *
 * Nothing here hardcodes a colour or reads the accent. The title is `onSurface` by
 * default, the captions are the third text level, and the band label is the only thing
 * that would tint — which it deliberately does not, so this card cannot repaint when
 * the user changes the accent and cannot fight the text it carries.
 */
@Composable
fun EstimatedClimateCard(
    content: ClimateCardContent,
    modifier: Modifier = Modifier,
    titleEs: String = "🌡️ Clima estimado"
) {
    SolidPanel(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(titleEs, style = MaterialTheme.typography.titleMedium)
            Text(
                text = content.bandLabelEs,
                style = MaterialTheme.typography.bodyLarge
            )

            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ClimateStat("Temp.", content.temperatureEs, Modifier.weight(1f))
                ClimateStat("Humedad", content.humidityEs, Modifier.weight(1f))
                ClimateStat("VPD", content.vpdEs, Modifier.weight(1f))
            }

            Text(
                text = content.bandAdviceEs,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(4.dp))

            // Two sentences, both load-bearing: what the estimate is, and what the app
            // is not doing to produce it.
            Text(
                text = content.assumptionEs,
                style = MaterialTheme.typography.labelSmall,
                color = LocalTertiaryText.current
            )
            Text(
                text = content.sourceEs,
                style = MaterialTheme.typography.labelSmall,
                color = LocalTertiaryText.current
            )
        }
    }
}

/** One label/value pair of the card. The label is furniture; the value is content. */
@Composable
private fun ClimateStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            // Three estimates side by side, and the whole point of the card is that
            // they are compared against each other and against the grower's own
            // readings — which is the value register's job. 14sp rather than the
            // `titleMedium` 16sp it replaces: monospace has a wider advance than
            // the prose face, and three of them share a row with `Arrangement.spacedBy(16.dp)`
            // between them, so the digits have to leave room for each other.
            style = metricValue(),
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalTertiaryText.current,
            textAlign = TextAlign.Center
        )
    }
}