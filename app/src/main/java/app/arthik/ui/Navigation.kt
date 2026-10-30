package app.arthik.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.arthik.ui.components.ArthikIcons
import app.arthik.ui.components.glass
import app.arthik.ui.screens.AccountDetailScreen
import app.arthik.ui.screens.AccountsScreen
import app.arthik.ui.screens.BudgetsScreen
import app.arthik.ui.screens.CategoriesScreen
import app.arthik.ui.screens.DashboardScreen
import app.arthik.ui.screens.InsightsScreen
import app.arthik.ui.screens.SettingsScreen
import app.arthik.ui.screens.TransactionsScreen
import app.arthik.ui.screens.UnrecognisedScreen
import app.arthik.ui.theme.LocalGlass

object Routes {
    const val HOME = "home"
    const val TXNS = "transactions"
    const val ACCOUNTS = "accounts"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val ACCOUNT = "account/{id}"
    const val BUDGETS = "budgets"
    const val CATEGORIES = "categories"
    const val UNRECOGNISED = "unrecognised"
    fun account(id: Long) = "account/$id"
    fun transactions(category: String? = null, account: Long? = null) =
        "transactions?category=${category.orEmpty()}&account=${account ?: -1}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", ArthikIcons.Home),
    Tab(Routes.TXNS, "Transactions", ArthikIcons.List),
    Tab(Routes.ACCOUNTS, "Accounts", ArthikIcons.Wallet),
    Tab(Routes.INSIGHTS, "Insights", ArthikIcons.Insights),
    Tab(Routes.SETTINGS, "Settings", ArthikIcons.Settings),
)

@Composable
fun ArthikNavHost(nav: NavHostController = rememberNavController()) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route?.substringBefore('?')
    Box(Modifier.fillMaxSize()) {
        NavHost(
            nav, startDestination = Routes.HOME, modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(180)) }, exitTransition = { fadeOut(tween(120)) },
        ) {
            composable(Routes.HOME) { DashboardScreen(nav) }
            composable(
                "${Routes.TXNS}?category={category}&account={account}",
                arguments = listOf(
                    navArgument("category") { type = NavType.StringType; defaultValue = "" },
                    navArgument("account") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { e ->
                TransactionsScreen(
                    initialCategory = e.arguments?.getString("category")?.takeIf { it.isNotEmpty() },
                    initialAccount = e.arguments?.getLong("account")?.takeIf { it >= 0 },
                )
            }
            composable(Routes.ACCOUNTS) { AccountsScreen(nav) }
            composable(Routes.ACCOUNT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                AccountDetailScreen(e.arguments!!.getLong("id"), nav)
            }
            composable(Routes.INSIGHTS) { InsightsScreen(nav) }
            composable(Routes.BUDGETS) { BudgetsScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.CATEGORIES) { CategoriesScreen(nav) }
            composable(Routes.UNRECOGNISED) { UnrecognisedScreen(nav) }
        }
        if (route in tabs.map { it.route }) {
            BottomBar(route, Modifier.align(Alignment.BottomCenter)) { target ->
                val dest = if (target == Routes.TXNS) Routes.transactions() else target
                nav.navigate(dest) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }
}

@Composable
private fun BottomBar(current: String?, modifier: Modifier, onSelect: (String) -> Unit) {
    val glass = LocalGlass.current
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(28.dp), strong = true),
    ) {
        tabs.forEach { t ->
            NavigationBarItem(
                selected = current == t.route,
                onClick = { onSelect(t.route) },
                icon = { Icon(t.icon, contentDescription = t.label) },
                label = { Text(t.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = Color.White.copy(alpha = 0.08f),
                    unselectedIconColor = glass.textSecondary,
                    unselectedTextColor = glass.textSecondary,
                ),
            )
        }
    }
}

/** Bottom padding so content scrolls clear of the floating bottom bar. */
val BottomBarSpace = 110.dp
