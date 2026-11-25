package com.godskingdomnow.groceryaccountant.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.godskingdomnow.groceryaccountant.AppContainer
import kotlinx.coroutines.flow.filterNotNull

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    // only pop when there is somewhere to go back to (avoids a double pop during exit animations)
    val back: () -> Unit = { if (nav.previousBackStackEntry != null) nav.popBackStack() }
    val openImport: (Long?) -> Unit = { listId -> nav.navigate("import?list=${listId ?: -1L}") { launchSingleTop = true } }
    val openListAfterImport: (Long) -> Unit = { id ->
        nav.navigate("list/$id") { popUpTo("lists") { inclusive = false }; launchSingleTop = true }
    }

    // A text was shared into the app (from Messages, etc.) -> open the import screen.
    LaunchedEffect(Unit) {
        container.pendingImport.filterNotNull().collect {
            val route = nav.currentBackStackEntry?.destination?.route.orEmpty()
            if (!route.startsWith("import")) openImport(null)
        }
    }

    NavHost(navController = nav, startDestination = "lists") {
        composable("lists") {
            ListsScreen(
                vm = viewModel { ListsViewModel(container) },
                openList = { id -> nav.navigate("list/$id") { launchSingleTop = true } },
                openBenefits = { nav.navigate("benefits") { launchSingleTop = true } },
                openAccount = { nav.navigate("account") { launchSingleTop = true } },
                openImport = { openImport(null) },
            )
        }
        composable("list/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            ListScreen(
                vm = viewModel(key = "list-$id") { ListViewModel(container, id) },
                back = back,
                openBenefits = { nav.navigate("benefits") { launchSingleTop = true } },
                openImport = { openImport(id) },
            )
        }
        composable(
            "import?list={list}",
            arguments = listOf(navArgument("list") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val listId = entry.arguments?.getLong("list")?.takeIf { it > 0 }
            ImportScreen(
                vm = viewModel { ImportViewModel(container, listId) },
                back = back,
                openList = openListAfterImport,
            )
        }
        composable("benefits") { BenefitsScreen(viewModel { BenefitsViewModel(container) }, back) }
        composable("account") { AccountScreen(viewModel { AccountViewModel(container) }, back) }
    }
}
