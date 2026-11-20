package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firebase.TradingRepository
import com.example.data.market.MarketDataEngine
import com.example.data.model.UiState
import com.example.ui.TradingViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.screens.PortfolioScreen
import com.example.ui.screens.RobotsScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.QuantFXTheme
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class QuantDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TERMINAL("Terminal", Icons.Filled.CandlestickChart, Icons.Outlined.CandlestickChart),
    ROBOTS("Robots", Icons.Filled.SmartToy, Icons.Outlined.SmartToy),
    PORTFOLIO("Ledger", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    VAULT("Vault", Icons.Filled.Security, Icons.Outlined.Security)
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            QuantFXTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateListener updates currentUser reactively */ })
    } else {
        val uid = user.uid
        key(uid) {
            QuantFXAuthenticatedShell(
                currentUserId = uid,
                userDisplayName = user.displayName,
                userEmail = user.email
            )
        }
    }
}

@Composable
fun QuantFXAuthenticatedShell(
    currentUserId: String,
    userDisplayName: String?,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: TradingViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                TradingViewModel(
                    repository = TradingRepository(app),
                    marketEngine = MarketDataEngine(),
                    currentUserId = currentUserId,
                    displayName = userDisplayName,
                    email = userEmail
                )
            }
        }
    )

    val accountState by viewModel.accountState.collectAsStateWithLifecycle()
    val botsState by viewModel.botsState.collectAsStateWithLifecycle()
    val tradesState by viewModel.tradesState.collectAsStateWithLifecycle()
    val selectedPair by viewModel.selectedPair.collectAsStateWithLifecycle()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsStateWithLifecycle()
    val snapshots by viewModel.marketSnapshots.collectAsStateWithLifecycle()
    val botSignals by viewModel.botSignals.collectAsStateWithLifecycle()
    val telemetry by viewModel.accountTelemetry.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val account = (accountState as? UiState.Success)?.data
    val trades = (tradesState as? UiState.Success)?.data ?: emptyList()

    var currentDestination by rememberSaveable { mutableStateOf(QuantDestination.TERMINAL) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 700.dp

        Scaffold(
            containerColor = ObsidianCanvas,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = SlateSurface,
                        contentColor = TextPrimary
                    ) {
                        QuantDestination.entries.forEach { dest ->
                            val selected = currentDestination == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentDestination = dest },
                                icon = {
                                    NavIconWithBadge(
                                        dest = dest,
                                        selected = selected,
                                        openCount = telemetry.openCount
                                    )
                                },
                                label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = EmeraldBull,
                                    selectedTextColor = EmeraldBull,
                                    indicatorColor = EmeraldBull.copy(alpha = 0.16f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("nav_${dest.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(ObsidianCanvas)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = SlateSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        QuantDestination.entries.forEach { dest ->
                            val selected = currentDestination == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentDestination = dest },
                                icon = {
                                    NavIconWithBadge(
                                        dest = dest,
                                        selected = selected,
                                        openCount = telemetry.openCount
                                    )
                                },
                                label = { Text(dest.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = EmeraldBull,
                                    selectedTextColor = EmeraldBull,
                                    indicatorColor = EmeraldBull.copy(alpha = 0.16f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag("rail_${dest.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 1160.dp)
                    ) {
                        when (currentDestination) {
                            QuantDestination.TERMINAL -> TerminalScreen(
                                selectedPair = selectedPair,
                                selectedTimeframe = selectedTimeframe,
                                snapshots = snapshots,
                                botSignals = botSignals,
                                trades = trades,
                                account = account,
                                telemetry = telemetry,
                                isBusy = isBusy,
                                onSelectPair = viewModel::selectPair,
                                onSelectTimeframe = viewModel::selectTimeframe,
                                onRefreshSpotRates = viewModel::refreshSpotRates,
                                onExecuteOrder = viewModel::executeManualOrder,
                                onClosePosition = viewModel::closePositionManually,
                                onNavigateToBots = { currentDestination = QuantDestination.ROBOTS }
                            )
                            QuantDestination.ROBOTS -> RobotsScreen(
                                botsState = botsState,
                                signals = botSignals,
                                account = account,
                                isBusy = isBusy,
                                onToggleBot = viewModel::toggleBot,
                                onSetAllBotsStatus = viewModel::setAllBotsStatus,
                                onUpdateBotParameters = viewModel::updateBotParameters,
                                onDeleteBot = viewModel::deleteBot,
                                onCreateBot = viewModel::createBot,
                                onCloneTemplate = viewModel::cloneBotTemplate,
                                onCloneAllBestRobots = viewModel::cloneAllBestRobots,
                                onSelectPairOnTerminal = { pair ->
                                    viewModel.selectPair(pair)
                                    currentDestination = QuantDestination.TERMINAL
                                }
                            )
                            QuantDestination.PORTFOLIO -> PortfolioScreen(
                                tradesState = tradesState,
                                snapshots = snapshots,
                                account = account,
                                telemetry = telemetry,
                                onClosePosition = viewModel::closePositionManually,
                                onModifyTradeSlTp = viewModel::modifyTradeSlTp
                            )
                            QuantDestination.VAULT -> VaultScreen(
                                userId = currentUserId,
                                userDisplayName = userDisplayName,
                                userEmail = userEmail,
                                account = account,
                                telemetry = telemetry,
                                onSaveRiskSettings = viewModel::updateRiskSettings,
                                onResetDemoEquity = viewModel::resetDemoAccount,
                                onSignOut = {
                                    signOut(
                                        credentialManager = credentialManager,
                                        scope = scope
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavIconWithBadge(
    dest: QuantDestination,
    selected: Boolean,
    openCount: Int
) {
    val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
    if (dest == QuantDestination.PORTFOLIO && openCount > 0) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = EmeraldBull,
                    contentColor = ObsidianCanvas
                ) {
                    Text(openCount.toString())
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = dest.label)
        }
    } else {
        Icon(imageVector = icon, contentDescription = dest.label)
    }
}
