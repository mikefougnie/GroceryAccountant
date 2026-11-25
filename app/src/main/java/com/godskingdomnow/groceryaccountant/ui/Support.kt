package com.godskingdomnow.groceryaccountant.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.godskingdomnow.groceryaccountant.BuildConfig

/** "Buy me a coffee" support links, set in gradle.properties (supportPaypal, supportVenmo). Blank hides them. */
object Support {
    val links: List<Pair<String, String>> = listOfNotNull(
        BuildConfig.SUPPORT_PAYPAL.trim().takeIf { it.isNotEmpty() }?.let { "PayPal" to "https://paypal.me/${it.removePrefix("@")}" },
        BuildConfig.SUPPORT_VENMO.trim().takeIf { it.isNotEmpty() }?.let { "Venmo" to "https://venmo.com/u/${it.removePrefix("@")}" },
    )
    val enabled get() = links.isNotEmpty()

    fun open(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(ctx, url, Toast.LENGTH_LONG).show()
        }
    }
}

const val COFFEE_MESSAGE = "Grocery Accountant is free, with no ads.\nIf it's helped you stretch your grocery budget, you can buy me a coffee. Thank you!"

val CoffeeYellow = Color(0xFFFFC400)

/** The big yellow button for a menu or settings screen. */
@Composable
fun CoffeeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (!Support.enabled) return
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = CoffeeYellow, contentColor = Color(0xFF231A00)),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) { Text("☕  Buy me a coffee", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
}

@Composable
fun CoffeeDialog(message: String, fromMenu: Boolean, onDismiss: () -> Unit, onNever: () -> Unit = {}) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("☕", fontSize = 40.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(message, textAlign = TextAlign.Center, fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Support.links.forEach { (label, url) ->
                        Button(onClick = { onDismiss(); Support.open(ctx, url) }, modifier = Modifier.weight(1f).height(52.dp)) {
                            Text(label, fontSize = 16.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (fromMenu) "Close" else "Not now") } },
        dismissButton = { if (!fromMenu) TextButton(onClick = onNever) { Text("Don't ask again") } },
    )
}

/**
 * Friendly, rare prompt on opening: the first time, then at most once a week.
 * "Don't ask again" is final. Waits until [isReady] (e.g. no permission prompt or scan in progress).
 */
@Composable
fun CoffeePrompt(message: String, isReady: () -> Boolean = { true }) {
    if (!Support.enabled) return
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("support", Context.MODE_PRIVATE) }
    val ready by rememberUpdatedState(isReady)
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(5_000)
        var tries = 0
        while (!ready() && tries++ < 12) delay(10_000)
        if (!ready()) return@LaunchedEffect
        val week = 7L * 24 * 60 * 60 * 1000
        if (prefs.getBoolean("coffeeNever", false) || System.currentTimeMillis() - prefs.getLong("coffeeShownAt", 0L) < week) return@LaunchedEffect
        prefs.edit().putLong("coffeeShownAt", System.currentTimeMillis()).apply()
        show = true
    }
    if (show) CoffeeDialog(message, fromMenu = false, onDismiss = { show = false },
        onNever = { prefs.edit().putBoolean("coffeeNever", true).apply(); show = false })
}
