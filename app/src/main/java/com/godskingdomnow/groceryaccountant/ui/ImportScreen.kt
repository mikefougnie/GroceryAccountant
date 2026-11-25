@file:OptIn(ExperimentalMaterial3Api::class)

package com.godskingdomnow.groceryaccountant.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.godskingdomnow.groceryaccountant.data.money

/**
 * Paste or share a text message, see what it found, untick anything that isn't an item,
 * pick a list, add. Opens automatically when text is shared to the app from Messages.
 */
@Composable
fun ImportScreen(vm: ImportViewModel, back: () -> Unit, openList: (Long) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var share by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add from a text") },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (ui.rows.isEmpty()) "No items found yet" else "${ui.count} of ${ui.rows.size} selected",
                        Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { vm.import(share && ui.session.signedIn) { id -> openList(id) } }, enabled = ui.count > 0) {
                        Text("Add ${ui.count} item${if (ui.count == 1) "" else "s"}")
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize().imePadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "text") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "In Messages, long-press a text and choose Share → Grocery Accountant. Or copy it and paste here.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        ui.text, vm::setText, Modifier.fillMaxWidth(),
                        label = { Text("The message") }, minLines = 3, maxLines = 8,
                        trailingIcon = {
                            IconButton(onClick = { clipboard.getText()?.text?.let { vm.setText(it) } }) {
                                Icon(Icons.Outlined.ContentPaste, "Paste")
                            }
                        },
                    )
                    if (ui.text.isBlank()) {
                        Button(onClick = { clipboard.getText()?.text?.let { vm.setText(it) } }, Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.ContentPaste, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Paste copied text")
                        }
                    }
                    TargetPicker(ui, vm)
                    if (ui.targetId == null) {
                        OutlinedTextField(ui.newName, vm::setNewName, Modifier.fillMaxWidth(), label = { Text("New list name") }, singleLine = true)
                        if (ui.session.signedIn) Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(share, { share = it })
                            Text("Share with ${ui.session.familyName.ifEmpty { "my family" }}")
                        }
                    }
                }
                if (ui.rows.isNotEmpty()) {
                    Text("Found", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall)
                    Text("Tap to include or leave out. You can fix names and prices after adding.",
                        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(ui.rows) { i, row ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.toggle(i) }.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(row.selected, { vm.toggle(i) })
                    Column(Modifier.weight(1f)) {
                        Text(
                            buildString {
                                if (row.p.qty > 1) append(row.p.qty).append(" × ")
                                append(row.p.name)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (row.selected) FontWeight.Medium else FontWeight.Normal,
                            color = if (row.selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val notes = listOfNotNull(
                            row.p.size.takeIf { it.isNotBlank() },
                            row.p.priceCents?.let { money(it) },
                            if (row.onList) "already on this list" else null,
                            if (!row.p.likely && !row.onList) "looks like chat, not an item" else null,
                        )
                        if (notes.isNotEmpty()) Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
            if (ui.rows.isEmpty() && ui.text.isNotBlank()) item(key = "none") {
                Text("Couldn’t find any items in that text. Put each item on its own line or separate them with commas.",
                    Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TargetPicker(ui: ImportUi, vm: ImportViewModel) {
    var open by remember { mutableStateOf(false) }
    val label = ui.lists.firstOrNull { it.id == ui.targetId }?.name ?: "A new list"
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = label, onValueChange = {}, readOnly = true, label = { Text("Add to") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem({ Text("A new list") }, { open = false; vm.setTarget(null) })
            ui.lists.forEach { l ->
                DropdownMenuItem({ Text(if (l.store.isBlank()) l.name else "${l.name} · ${l.store}") }, { open = false; vm.setTarget(l.id) })
            }
        }
    }
}
