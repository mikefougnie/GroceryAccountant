@file:OptIn(ExperimentalMaterial3Api::class)

package com.godskingdomnow.groceryaccountant.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.godskingdomnow.groceryaccountant.sync.Session

@Composable
fun AccountScreen(vm: AccountViewModel, back: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family sync") },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.session.signedIn) SignedIn(ui, vm) else SignInForm(ui.session, vm)
            if (Support.enabled) {
                var coffee by remember { mutableStateOf(false) }
                CoffeeButton(onClick = { coffee = true }, modifier = Modifier.padding(top = 12.dp))
                Text("Grocery Accountant is free with no ads. Coffee keeps it that way. Thank you!",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (coffee) CoffeeDialog(COFFEE_MESSAGE, fromMenu = true, onDismiss = { coffee = false })
            }
        }
    }
}

@Composable
private fun SignInForm(s: Session, vm: AccountViewModel) {
    var url by rememberSaveable { mutableStateOf(s.baseUrl.ifEmpty { Session.DEFAULT_BASE_URL }) }
    var user by rememberSaveable { mutableStateOf(s.username) }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showUrl by remember { mutableStateOf(s.baseUrl.isNotEmpty() && s.baseUrl != Session.DEFAULT_BASE_URL) }
    var offerShare by remember { mutableStateOf(0) }

    Text("Optional", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text("Grocery Accountant works fully on its own. If your family uses the Family app on GodsKingdomNow.com, sign in to share lists, WIC and SNAP balances with everyone.",
        style = MaterialTheme.typography.bodyMedium)

    OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
    OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
    if (showUrl) {
        OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Family app address") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            supportingText = { Text("Only change this if your family app lives somewhere else.") })
    } else {
        TextButton(onClick = { showUrl = true }) { Text("Use a different website address") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

    Button(
        onClick = {
            busy = true; error = null
            vm.signIn(url, user.trim(), pass) { err, unshared ->
                busy = false
                if (err != null) error = err else { pass = ""; offerShare = unshared }
            }
        },
        enabled = !busy && user.isNotBlank() && pass.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Sign in")
    }
    Text("Your password is sent only to sign in. The phone keeps a private key the website can cancel any time by signing out.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    if (offerShare > 0) ShareAllDialog(offerShare, onShare = { offerShare = 0; vm.shareAllThenSync() }, onSkip = { offerShare = 0 })
}

@Composable
private fun ShareAllDialog(count: Int, onShare: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Share your lists?") },
        text = { Text("You have $count list${if (count == 1) "" else "s"} on this phone. Share ${if (count == 1) "it" else "them"} with your family too? You can also share lists one at a time later from each list’s menu.") },
        confirmButton = { TextButton(onShare) { Text("Share all") } },
        dismissButton = { TextButton(onSkip) { Text("Keep private") } },
    )
}

@Composable
private fun SignedIn(ui: AccountUi, vm: AccountViewModel) {
    val s = ui.session
    var confirmOut by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Groups, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Column(Modifier.padding(start = 12.dp)) {
                Text(s.familyName.ifEmpty { "Your family" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("${s.displayName.ifEmpty { s.username }} · ${if (s.isParent) "parent" else "family member"}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }

    val status = when {
        ui.syncing -> "Syncing…"
        s.lastError.isNotEmpty() -> s.lastError
        s.lastSyncAt > 0 -> "Last synced " + DateUtils.getRelativeTimeSpanString(s.lastSyncAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        else -> "Not synced yet"
    }
    Text(status, color = if (s.lastError.isNotEmpty() && !ui.syncing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    if (s.lastConflicts > 0 && s.lastError.isEmpty())
        Text("${s.lastConflicts} change${if (s.lastConflicts == 1L) " was" else "s were"} also edited on the website, so the website’s version was kept.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    Button(onClick = { vm.syncNow() }, enabled = !ui.syncing, modifier = Modifier.fillMaxWidth()) { Text("Sync now") }

    Text("How sharing works", style = MaterialTheme.typography.titleSmall)
    Text(
        "• Lists marked “shared” match the Shopping page in your Family app.\n" +
            "• Lists you don’t share stay only on this phone.\n" +
            "• Changes sync a few seconds after you make them, and whenever you open the app.\n" +
            "• No signal at the store? Keep going — it catches up later.\n" +
            "• WIC and SNAP balances are shared; only parents can change them.",
        style = MaterialTheme.typography.bodyMedium,
    )

    OutlinedButton(onClick = { confirmOut = true }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }

    if (confirmOut) {
        AlertDialog(
            onDismissRequest = { confirmOut = false },
            title = { Text("Sign out?") },
            text = { Text("Do you want to keep copies of your family’s shared lists on this phone? Kept lists become private to this phone.") },
            confirmButton = { TextButton({ confirmOut = false; vm.signOut(keepFamilyLists = true) }) { Text("Keep copies") } },
            dismissButton = { TextButton({ confirmOut = false; vm.signOut(keepFamilyLists = false) }) { Text("Remove them") } },
        )
    }
}
