package com.abc.expensetracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.abc.expensetracker.ui.screens.AddTxnSheet
import com.abc.expensetracker.ui.screens.BudgetsScreen
import com.abc.expensetracker.ui.screens.HomeScreen
import com.abc.expensetracker.ui.screens.MoreScreen
import com.abc.expensetracker.ui.screens.StatsScreen
import com.abc.expensetracker.ui.screens.TransactionsScreen
import com.abc.expensetracker.ui.theme.KharchaTheme
import com.abc.expensetracker.ui.vm.BudgetsVm
import com.abc.expensetracker.ui.vm.HomeVm
import com.abc.expensetracker.ui.vm.MoreVm
import com.abc.expensetracker.ui.vm.StatsVm
import com.abc.expensetracker.ui.vm.TxnListVm
import com.abc.expensetracker.ui.vm.VmFactory

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Default.Home),
    Tab("txns", "History", Icons.Default.ReceiptLong),
    Tab("stats", "Stats", Icons.Default.BarChart),
    Tab("budgets", "Budgets", Icons.Default.Savings),
    Tab("more", "More", Icons.Default.MoreHoriz),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = KharchaApp.from(this)
        val quickAdd = intent?.getBooleanExtra("quickAdd", false) == true
        val openCards = intent?.getBooleanExtra("openCards", false) == true
        setContent {
            val themeMode by container.settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            KharchaTheme(themeMode) {
                ReconcileApp(container, quickAdd = quickAdd, openCards = openCards)
            }
        }
    }
}

@Composable
private fun ReconcileApp(container: AppContainer, quickAdd: Boolean, openCards: Boolean) {
    val factory = remember { VmFactory(container) }
    val nav: NavHostController = rememberNavController()
    var showAdd by remember { mutableStateOf(quickAdd) }

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
        fun granted(p: String) =
            ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        val needed = buildList {
            if (!granted(Manifest.permission.RECEIVE_SMS)) add(Manifest.permission.RECEIVE_SMS)
            if (!granted(Manifest.permission.READ_SMS)) add(Manifest.permission.READ_SMS)
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                !granted(Manifest.permission.POST_NOTIFICATIONS)
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        when {
            needed.isNotEmpty() -> if (!permissionsRequested) {
                permissionsRequested = true
                permissionLauncher.launch(needed.toTypedArray())
            }
            importDone == false -> homeVm.runHistoricalImport()
        }
    }
    // Bill notification tap / app shortcut land on the right place.
    LaunchedEffect(Unit) {
        if (openCards) nav.navigate("budgets") { launchSingleTop = true }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == "home" || currentRoute == "txns") {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add transaction")
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                HomeScreen(
                    vm = homeVm,
                    onTxnClick = { nav.navigate("txns") },
                    onSeeAll = { nav.navigate("txns") },
                )
            }
            composable("txns") { TransactionsScreen(txnVm) }
            composable("stats") { StatsScreen(statsVm) }
            composable("budgets") { BudgetsScreen(budgetsVm) }
            composable("more") { MoreScreen(moreVm) }
        }
    }

    if (showAdd) {
        AddTxnSheet(vm = txnVm, onDismiss = { showAdd = false })
    }
}
