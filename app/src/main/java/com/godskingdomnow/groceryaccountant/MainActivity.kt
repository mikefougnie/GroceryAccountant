package com.godskingdomnow.groceryaccountant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.godskingdomnow.groceryaccountant.ui.AppNav
import com.godskingdomnow.groceryaccountant.ui.theme.GroceryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as GroceryApp).container
        setContent {
            GroceryTheme {
                AppNav(container)
            }
        }
    }
}
