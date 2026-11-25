package com.godskingdomnow.groceryaccountant.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.godskingdomnow.groceryaccountant.AppContainer

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    // only pop when there is somewhere to go back to (avoids a double pop during exit animations)
    val back: () -> Unit = { if (nav.previousBackStackEntry != null) nav.popBackStack() }

    NavHost(navController = nav, startDestination = "lists") {
        composable("lists") {
            ListsScreen(
                vm = viewModel { ListsViewModel(container) },
                openList = { id -> nav.navigate("list/$id") { launchSingleTop = true } },
                openBenefits = { nav.navigate("benefits") { launchSingleTop = true } },
                openAccount = { nav.navigate("account") { launchSingleTop = true } },
            )
        }
        composable("list/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            ListScreen(
                vm = viewModel(key = "list-$id") { ListViewModel(container, id) },
                back = back,
                openBenefits = { nav.navigate("benefits") { launchSingleTop = true } },
            )
        }
        composable("benefits") { BenefitsScreen(viewModel { BenefitsViewModel(container) }, back) }
        composable("account") { AccountScreen(viewModel { AccountViewModel(container) }, back) }
    }
}
