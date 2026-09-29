package com.trichome.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Shared confirmation for an irreversible action.
 *
 * Two rules the six delete sites all depend on:
 * - [message] must name the entity, so the user can see they confirmed the row
 *   they meant to tap and not a neighbouring one.
 * - the confirm button wears `colorScheme.error`, because a cancel and a
 *   confirm must never look equally safe.
 *
 * @param dismissLabel defaults to the app-wide "Cancelar".
 */
@Composable
fun ConfirmDestructiveDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancelar"
) {
    val error = MaterialTheme.colorScheme.error
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    )
}

/**
 * Wires one screen's delete icons to [ConfirmDestructiveDialog].
 *
 * The dialog's visibility is derived from `pending`, so [DestructiveConfirmation.confirm]
 * is reachable only from the dialog's confirm button. Call sites therefore pass
 * their rows to `request` and stop there.
 *
 * @param title dialog title for a given row.
 * @param message dialog body for a given row; name the entity.
 * @param confirmLabel label of the destructive button for a given row.
 * @param onConfirmed the existing delete call, invoked once, after confirmation.
 */
@Composable
fun <T> rememberDestructiveConfirmation(
    title: (T) -> String,
    message: (T) -> String,
    confirmLabel: (T) -> String,
    onConfirmed: (T) -> Unit
): DestructiveConfirmation<T> {
    // `rememberUpdatedState` keeps the latest lambda without recreating the
    // holder, so the pending item survives a recomposition with a new closure.
    val latestOnConfirmed by rememberUpdatedState(onConfirmed)
    val confirmation = remember { DestructiveConfirmation<T> { latestOnConfirmed(it) } }

    val pending = confirmation.pending
    if (pending != null) {
        ConfirmDestructiveDialog(
            title = title(pending),
            message = message(pending),
            confirmLabel = confirmLabel(pending),
            onConfirm = confirmation::confirm,
            onDismiss = confirmation::dismiss
        )
    }
    return confirmation
}
