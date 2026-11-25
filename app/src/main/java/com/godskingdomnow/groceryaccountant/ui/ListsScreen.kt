package com.godskingdomnow.groceryaccountant.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.godskingdomnow.groceryaccountant.data.money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    vm: ListsViewModel,
    openList: (Long) -> Unit,
    openBenefits: () -> Unit,
    openAccount: () -> Unit,
    openImport: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grocery Accountant") },
                actions = {
                    IconButton(onClick = openImport) { Icon(Icons.Outlined.Sms, "Add from a text message") }
                    IconButton(onClick = openBenefits) { Icon(Icons.Outlined.Savings, "WIC, SNAP and coupons") }
                    IconButton(onClick = openAccount) { Icon(Icons.Outlined.AccountCircle, "Family sign-in and sync") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New list") })
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (ui.syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (ui.session.signedIn || ui.session.lastError.isNotEmpty()) SyncLine(ui, openAccount)
            if (ui.loaded && ui.cards.isEmpty()) {
                EmptyLists(onNew = { creating = true }, onImport = openImport)
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ui.cards, key = { it.list.id }) { card -> ListCardView(card) { openList(card.list.id) } }
                }
            }
        }
    }

    if (creating) {
        NewListDialog(
            signedIn = ui.session.signedIn,
            familyName = ui.session.familyName,
            onDismiss = { creating = false },
            onCreate = { name, store, shared ->
                creating = false
                vm.createList(name, store, shared) { id -> openList(id) }
            },
        )
    }
}

@Composable
private fun SyncLine(ui: ListsUi, openAccount: () -> Unit) {
    val s = ui.session
    val text = when {
        s.lastError.isNotEmpty() -> s.lastError
        s.lastSyncAt > 0 -> "Synced with ${s.familyName.ifEmpty { "your family" }} · " +
            DateUtils.getRelativeTimeSpanString(s.lastSyncAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        else -> "Signed in — syncing…"
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = openAccount).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (s.lastError.isEmpty()) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff, null, Modifier.size(18.dp),
            tint = if (s.lastError.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Text(text, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ListCardView(card: ListCard, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(card.list.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (card.list.shared) Icon(Icons.Outlined.Groups, "Shared with family", Modifier.padding(start = 6.dp).size(18.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
                val bits = listOfNotNull(
                    card.list.store.takeIf { it.isNotBlank() },
                    if (card.itemCount == 0) "empty" else "${card.left} of ${card.itemCount} to get",
                )
                Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("You pay", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money(card.youPay), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun EmptyLists(onNew: () -> Unit, onImport: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.ShoppingBasket, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Know what you’ll pay before the register", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Make a list, add prices as you shop, and see your total — with coupons, sales tax, WIC and SNAP worked out for you. Everything stays on this phone.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onNew) { Text("Make your first list") }
        TextButton(onClick = onImport) { Text("Or add one from a text message") }
    }
}

@Composable
fun NewListDialog(signedIn: Boolean, familyName: String, onDismiss: () -> Unit, onCreate: (String, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var store by remember { mutableStateOf("") }
    var shared by remember { mutableStateOf(signedIn) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name, like “Taco Night”") }, singleLine = true)
                OutlinedTextField(store, { store = it }, label = { Text("Store (optional)") }, singleLine = true)
                if (signedIn) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(shared, { shared = it })
                    Text("Share with ${familyName.ifEmpty { "my family" }}")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, store, shared) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
