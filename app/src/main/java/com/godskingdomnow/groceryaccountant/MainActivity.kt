package com.godskingdomnow.groceryaccountant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.godskingdomnow.groceryaccountant.ui.AppNav
import com.godskingdomnow.groceryaccountant.ui.theme.GroceryTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as GroceryApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) takeShared(intent)
        setContent {
            GroceryTheme {
                AppNav(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeShared(intent)
    }

    /** "Share" from Messages (or any app) sends the text here. */
    private fun takeShared(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
        if (!text.isNullOrBlank()) container.pendingImport.value = text
    }
}
