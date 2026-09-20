package com.abc.expensetracker

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.abc.expensetracker.ui.common.BottomNavItem
import com.abc.expensetracker.ui.common.ReconcileBottomNavigation
import com.abc.expensetracker.ui.screens.ActivityScreen
import com.abc.expensetracker.ui.screens.AddTxnSheet
import com.abc.expensetracker.ui.screens.HomeScreen
import com.abc.expensetracker.ui.screens.InsightsScreen
import com.abc.expensetracker.ui.screens.PlanScreen
import com.abc.expensetracker.ui.screens.SettingsScreen
import com.abc.expensetracker.ui.screens.TransactionDetailScreen
import com.abc.expensetracker.ui.theme.KharchaTheme
import com.abc.expensetracker.ui.theme.ReconcileColors
import com.abc.expensetracker.ui.vm.BudgetsVm
import com.abc.expensetracker.ui.vm.HomeVm
import com.abc.expensetracker.ui.vm.MoreVm
import com.abc.expensetracker.ui.vm.StatsVm
import com.abc.expensetracker.ui.vm.TxnListVm
import com.abc.expensetracker.ui.vm.VmFactory

private const val ROUTE_HOME = "home"
private const val ROUTE_ACTIVITY = "activity"
private const val ROUTE_INSIGHTS = "insights"
private const val ROUTE_PLAN = "plan"
private const val ROUTE_PLAN_PATTERN = "plan/{section}"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_TRANSACTION_PATTERN = "transaction/{transactionId}"

private val bottomNavItems = listOf(
    BottomNavItem(ROUTE_HOME, "Home", Icons.Outlined.Home),
    BottomNavItem(ROUTE_ACTIVITY, "Activity", Icons.Outlined.ReceiptLong),
    BottomNavItem(ROUTE_INSIGHTS, "Insights", Icons.Outlined.BarChart),
    BottomNavItem(ROUTE_PLAN, "Plan", Icons.Outlined.Savings),
)

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_START_ROUTE = "startRoute"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val container = KharchaApp.from(this)
        val quickAdd = intent?.getBooleanExtra("quickAdd", false) == true
        val openCards = intent?.getBooleanExtra("openCards", false) == true
        val startRoute = intent?.getStringExtra(EXTRA_START_ROUTE)
        setContent {
            val themeMode by container.settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            KharchaTheme(themeMode) {
                ReconcileApp(
                    container = container,
                    quickAdd = quickAdd,
                    openCards = openCards,
                    startRoute = startRoute,
                )
            }
        }
    }
}

@Composable
private fun ReconcileApp(
    container: AppContainer,
    quickAdd: Boolean,
    openCards: Boolean,
    startRoute: String?,
) {
    val factory = remember { VmFactory(container) }
    val nav: NavHostController = rememberNavController()
    var showAdd by remember { mutableStateOf(quickAdd) }

    // One instance per activity keeps filter and edit state stable across tab changes.
    val homeVm: HomeVm = viewModel(factory = factory)
    val txnVm: TxnListVm = viewModel(factory = factory)
    val statsVm: StatsVm = viewModel(factory = factory)
    val budgetsVm: BudgetsVm = viewModel(factory = factory)
    val moreVm: MoreVm = viewModel(factory = factory)

    // SMS permissions: RECEIVE_SMS = realtime capture, READ_SMS = one-time
    // historical import. On grant, the full-inbox import runs exactly once.
    val importDone by homeVm.importDone.collectAsState()
    var permissionsRequested by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.READ_SMS] == true && importDone == false) {
            homeVm.runHistoricalImport()
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Wait for DataStore (importDone != null) so the one-time import can't be
    // skipped by racing the initial value.
    LaunchedEffect(importDone) {
        if (importDone == null) return@LaunchedEffect
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        val needed = buildList {
            if (!granted(Manifest.permission.RECEIVE_SMS)) add(Manifest.permission.RECEIVE_SMS)
            if (!granted(Manifest.permission.READ_SMS)) add(Manifest.permission.READ_SMS)
            if (
                android.os.Build.VERSION.SDK_INT >= 33 &&
                !granted(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        when {
            needed.isNotEmpty() && !permissionsRequested -> {
                permissionsRequested = true
                permissionLauncher.launch(needed.toTypedArray())
            }
            needed.isEmpty() && importDone == false -> homeVm.runHistoricalImport()
        }
    }

    // Preserve destinations used by bill reminders, shortcuts, and older widgets.
    LaunchedEffect(openCards, startRoute) {
        val destination = when {
            openCards -> "plan/bills"
            startRoute == "txns" || startRoute == ROUTE_ACTIVITY -> ROUTE_ACTIVITY
            startRoute == "stats" || startRoute == ROUTE_INSIGHTS -> ROUTE_INSIGHTS
            startRoute == "budgets" || startRoute == ROUTE_PLAN -> "plan/budget"
            startRoute == "more" || startRoute == ROUTE_SETTINGS -> ROUTE_SETTINGS
            startRoute?.startsWith("transaction/") == true -> startRoute
            startRoute?.startsWith("plan/") == true -> startRoute
            else -> null
        }
        destination?.let { nav.navigate(it) { launchSingleTop = true } }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val selectedTab = when (currentRoute) {
        ROUTE_HOME -> ROUTE_HOME
        ROUTE_ACTIVITY -> ROUTE_ACTIVITY
        ROUTE_INSIGHTS -> ROUTE_INSIGHTS
        ROUTE_PLAN_PATTERN -> ROUTE_PLAN
        else -> null
    }
    val showAppNavigation = selectedTab != null

    fun openTransaction(id: Long) {
        nav.navigate("transaction/$id")
    }

    Scaffold(
        containerColor = ReconcileColors.Ink,
        bottomBar = {
            if (showAppNavigation) {
                ReconcileBottomNavigation(
                    items = bottomNavItems,
                    selectedRoute = selectedTab,
                    onSelect = { item ->
                        val destination = if (item.route == ROUTE_PLAN) "plan/budget" else item.route
                        nav.navigate(destination) {
                            popUpTo(nav.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (currentRoute == ROUTE_HOME || currentRoute == ROUTE_ACTIVITY) {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = ReconcileColors.Mint,
                    contentColor = ReconcileColors.Ink,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add transaction")
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = ROUTE_HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    vm = homeVm,
                    planVm = budgetsVm,
                    dataVm = moreVm,
                    onOpenSettings = { nav.navigate(ROUTE_SETTINGS) },
                    onTransactionClick = ::openTransaction,
                    onSeeActivity = { nav.navigate(ROUTE_ACTIVITY) },
                    onSeeInsights = { nav.navigate(ROUTE_INSIGHTS) },
                    onOpenPlan = { section -> nav.navigate("plan/$section") },
                    onOpenDataQuality = { nav.navigate(ROUTE_SETTINGS) },
                )
            }
            composable(ROUTE_ACTIVITY) {
                ActivityScreen(vm = txnVm, onTransactionClick = ::openTransaction)
            }
            composable(ROUTE_INSIGHTS) {
                InsightsScreen(vm = statsVm)
            }
            composable(
                route = ROUTE_PLAN_PATTERN,
                arguments = listOf(navArgument("section") { defaultValue = "budget" }),
            ) { entry ->
                PlanScreen(
                    vm = budgetsVm,
                    dataVm = moreVm,
                    initialSection = entry.arguments?.getString("section") ?: "budget",
                    onTransactionClick = ::openTransaction,
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    vm = moreVm,
                    planVm = budgetsVm,
                    onBack = { nav.popBackStack() },
                    onOpenBills = { nav.navigate("plan/bills") },
                    onOpenUncategorized = { categoryId ->
                        txnVm.showUncategorized(categoryId)
                        nav.navigate(ROUTE_ACTIVITY)
                    },
                    onTransactionClick = ::openTransaction,
                )
            }
            composable(
                route = ROUTE_TRANSACTION_PATTERN,
                arguments = listOf(navArgument("transactionId") { type = NavType.LongType }),
            ) { entry ->
                val transactionId = entry.arguments?.getLong("transactionId")
                if (transactionId != null) {
                    TransactionDetailScreen(
                        transactionId = transactionId,
                        vm = txnVm,
                        onClose = { nav.popBackStack() },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddTxnSheet(vm = txnVm, onDismiss = { showAdd = false })
    }
}
