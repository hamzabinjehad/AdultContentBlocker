package app.hisn.android.nativeshell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.hisn.android.PersonBlur
import app.hisn.android.PolicyStore
import app.hisn.android.R

private val PeopleModes = listOf(PersonBlur.OFF, PersonBlur.ALL, PersonBlur.WOMEN, PersonBlur.MEN)
private val PeopleResponses = listOf(PersonBlur.FACES, PersonBlur.BACK, PersonBlur.COVER)

/**
 * The opt-in people cover: which faces (off, all, women or men, estimated) and what happens when
 * one is found. It lived on the classic screen until that was retired; the rules are the same.
 * Images and video must be on first, a protection lock freezes the choice, and VisualGuard picks a
 * change up on its next sample (it reloads the face models for the new mode).
 */
@Composable
fun PeopleCoverCard(imagesOn: Boolean) {
    val context = LocalContext.current
    val haptics = rememberHisnHaptics()
    val snackbar = LocalHisnSnackbar.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    var mode by remember { mutableStateOf(PersonBlur.mode(context)) }
    var response by remember { mutableStateOf(PersonBlur.response(context)) }
    var choosing by remember { mutableStateOf<String?>(null) }
    val peopleOn = imagesOn && PersonBlur.on(mode)

    fun locked(): Boolean = runCatching { PolicyStore.get(context).remainingMillis() > 0L }.getOrDefault(false)

    fun open(which: String) {
        when {
            locked() -> snackbar(HisnMessage(resources.getString(R.string.rules_locked)))
            !imagesOn -> snackbar(HisnMessage(resources.getString(R.string.person_blur_need_visual)))
            which == "response" && !PersonBlur.on(mode) ->
                snackbar(HisnMessage(resources.getString(R.string.person_response_need_people)))
            else -> choosing = which
        }
    }

    HisnCard {
        Column(Modifier.alpha(if (imagesOn) 1f else 0.6f)) {
            // The value leads the description, as on the layer rows ("Off · …"), so a long
            // choice never squeezes the title.
            SettingRow(
                icon = Icons.Rounded.Face,
                title = stringResource(R.string.person_blur_title),
                supporting = stringResource(PersonBlur.label(if (imagesOn) mode else PersonBlur.OFF)) +
                    " · " + stringResource(R.string.person_blur_summary),
                onClick = { open("mode") }
            )
            InsetDivider()
            Column(Modifier.alpha(if (peopleOn || !imagesOn) 1f else 0.6f)) {
                SettingRow(
                    icon = Icons.Rounded.VisibilityOff,
                    title = stringResource(R.string.person_response_title),
                    // The summary repeats both choices, so the chosen one alone is clearer here.
                    supporting = stringResource(PersonBlur.responseLabel(response)),
                    onClick = { open("response") }
                )
            }
        }
    }

    when (choosing) {
        "mode" -> ChoiceDialog(
            title = stringResource(R.string.person_blur_title),
            options = PeopleModes.map { it to stringResource(PersonBlur.label(it)) },
            selected = mode,
            onDismiss = { choosing = null },
            onChoose = { chosen ->
                choosing = null
                // Re-check at the moment of writing: a lock may have started while the dialog was open.
                if (locked()) {
                    snackbar(HisnMessage(resources.getString(R.string.rules_locked)))
                } else {
                    PersonBlur.set(context, chosen)
                    mode = chosen
                    haptics.confirm()
                }
            }
        )
        "response" -> ChoiceDialog(
            title = stringResource(R.string.person_response_title),
            options = PeopleResponses.map { it to stringResource(PersonBlur.responseLabel(it)) },
            selected = response,
            onDismiss = { choosing = null },
            onChoose = { chosen ->
                choosing = null
                if (locked()) {
                    snackbar(HisnMessage(resources.getString(R.string.rules_locked)))
                } else {
                    PersonBlur.setResponse(context, chosen)
                    response = chosen
                    haptics.confirm()
                }
            }
        )
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = key == selected, role = Role.RadioButton) { onChoose(key) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = key == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}
