package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.TradingRepository
import com.example.data.market.MarketDataEngine
import com.example.data.model.BestRobotCatalog
import com.example.data.model.BotSignal
import com.example.data.model.BotStatus
import com.example.data.model.BotStrategy
import com.example.data.model.CloseReason
import com.example.data.model.ForexPair
import com.example.data.model.MarketSnapshot
import com.example.data.model.RobotTemplate
import com.example.data.model.Timeframe
import com.example.data.model.TradeRecord
import com.example.data.model.TradeSide
import com.example.data.model.TradeStatus
import com.example.data.model.TraderAccount
import com.example.data.model.TradingBot
import com.example.data.model.UiState
import com.example.data.model.roundTo
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountTelemetry(
    val balance: Double = 10000.0,
    val floatingPnl: Double = 0.0,
    val equity: Double = 10000.0,
    val usedMargin: Double = 0.0,
    val freeMargin: Double = 10000.0,
    val marginLevelPct: Double = 0.0,
    val totalRealizedPnl: Double = 0.0,
    val winRatePct: Double = 0.0,
    val openCount: Int = 0,
    val closedCount: Int = 0,
    val drawdownPct: Double = 0.0,
    val profitFactor: Double = 0.0,
    val totalPips: Double = 0.0,
    val bestTradePnl: Double = 0.0
)

class TradingViewModel(
    private val repository: TradingRepository,
    private val marketEngine: MarketDataEngine,
    private val currentUserId: String,
    private val displayName: String?,
    private val email: String?
) : ViewModel() {

    private val _selectedPair = MutableStateFlow(ForexPair.EUR_USD)
    val selectedPair: StateFlow<ForexPair> = _selectedPair.asStateFlow()

    private val _selectedTimeframe = MutableStateFlow(Timeframe.M5)
    val selectedTimeframe: StateFlow<Timeframe> = _selectedTimeframe.asStateFlow()

    private val _marketSnapshots = MutableStateFlow<Map<ForexPair, MarketSnapshot>>(emptyMap())
    val marketSnapshots: StateFlow<Map<ForexPair, MarketSnapshot>> = _marketSnapshots.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val closingTradeIds = mutableSetOf<String>()
    private val openingBotIds = mutableSetOf<String>()
    private val lastBotTradeMillis = mutableMapOf<String, Long>()

    val accountState: StateFlow<UiState<TraderAccount?>> = repository.observeAccount(currentUserId)
        .map<TraderAccount?, UiState<TraderAccount?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing account", root)
            emit(UiState.Error(root.message ?: "Failed to load account"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val botsState: StateFlow<UiState<List<TradingBot>>> = repository.observeBots(currentUserId)
        .map<List<TradingBot>, UiState<List<TradingBot>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing bots", root)
            emit(UiState.Error(root.message ?: "Failed to load trading bots"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val tradesState: StateFlow<UiState<List<TradeRecord>>> = repository.observeTrades(currentUserId)
        .map<List<TradeRecord>, UiState<List<TradeRecord>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing trades", root)
            emit(UiState.Error(root.message ?: "Failed to load trade ledger"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val botSignals: StateFlow<List<BotSignal>> = combine(
        botsState,
        marketSnapshots
    ) { botsUi, snapshots ->
        val bots = (botsUi as? UiState.Success)?.data ?: emptyList()
        bots.mapNotNull { bot ->
            val snap = snapshots[bot.forexPair] ?: return@mapNotNull null
            marketEngine.evaluateBotSignal(bot, snap)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = emptyList()
    )

    val accountTelemetry: StateFlow<AccountTelemetry> = combine(
        accountState,
        tradesState,
        marketSnapshots
    ) { accountUi, tradesUi, snapshots ->
        val account = (accountUi as? UiState.Success)?.data
        val balance = account?.balance ?: 10000.0
        val starting = account?.startingBalance ?: 10000.0
        val leverage = (account?.leverage ?: 100L).coerceAtLeast(10L)
        val trades = (tradesUi as? UiState.Success)?.data ?: emptyList()
        val openTrades = trades.filter { it.tradeStatus == TradeStatus.OPEN }
        val closedTrades = trades.filter { it.tradeStatus == TradeStatus.CLOSED }

        val floatingPnl = openTrades.sumOf { trade ->
            val snap = snapshots[trade.forexPair]
            if (snap == null) 0.0
            else {
                val markPrice = if (trade.tradeSide == TradeSide.BUY) snap.bid else snap.ask
                trade.forexPair.pnlUsd(trade.tradeSide, trade.entryPrice, markPrice, trade.lotSize)
            }
        }
        val equity = balance + floatingPnl
        val usedMargin = openTrades.sumOf { trade ->
            trade.forexPair.notionalUsd(trade.entryPrice, trade.lotSize) / leverage.toDouble()
        }
        val freeMargin = (equity - usedMargin).coerceAtLeast(0.0)
        val marginLevel = if (usedMargin > 0.0) (equity / usedMargin) * 100.0 else 0.0
        val totalRealized = closedTrades.sumOf { it.realizedPnl }
        val wins = closedTrades.count { it.realizedPnl >= 0.0 }
        val winRate = if (closedTrades.isNotEmpty()) (wins.toDouble() / closedTrades.size) * 100.0 else 0.0
        val grossProfit = closedTrades.filter { it.realizedPnl > 0.0 }.sumOf { it.realizedPnl }
        val grossLoss = kotlin.math.abs(closedTrades.filter { it.realizedPnl < 0.0 }.sumOf { it.realizedPnl })
        val profitFactor = when {
            grossLoss > 0.0 -> (grossProfit / grossLoss).roundTo(2)
            grossProfit > 0.0 -> 9.99
            else -> 0.0
        }
        val totalPips = closedTrades.sumOf { it.pips }.roundTo(1)
        val bestTrade = closedTrades.maxOfOrNull { it.realizedPnl } ?: 0.0
        val drawdownPct = if (starting > 0.0 && equity < starting) {
            ((starting - equity) / starting) * 100.0
        } else 0.0

        AccountTelemetry(
            balance = balance.roundTo(2),
            floatingPnl = floatingPnl.roundTo(2),
            equity = equity.roundTo(2),
            usedMargin = usedMargin.roundTo(2),
            freeMargin = freeMargin.roundTo(2),
            marginLevelPct = marginLevel.roundTo(1),
            totalRealizedPnl = totalRealized.roundTo(2),
            winRatePct = winRate.roundTo(1),
            openCount = openTrades.size,
            closedCount = closedTrades.size,
            drawdownPct = drawdownPct.roundTo(2),
            profitFactor = profitFactor,
            totalPips = totalPips,
            bestTradePnl = bestTrade.roundTo(2)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = AccountTelemetry()
    )

    init {
        viewModelScope.launch {
            val initRes = repository.ensureAccountAndStarterBots(displayName, email)
            if (initRes.isFailure) {
                _statusMessage.value = initRes.exceptionOrNull()?.localizedMessage
                    ?: "Failed to initialize trader profile"
            }
        }
        viewModelScope.launch {
            marketEngine.fetchLiveSpotAnchors()
        }
        viewModelScope.launch {
            marketEngine.streamMarket { _selectedTimeframe.value }
                .collect { snapshots ->
                    _marketSnapshots.value = snapshots
                    processRealtimeBotAndRiskTick(snapshots)
                }
        }
    }

    fun selectPair(pair: ForexPair) {
        _selectedPair.value = pair
    }

    fun selectTimeframe(timeframe: Timeframe) {
        _selectedTimeframe.value = timeframe
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun refreshSpotRates() {
        viewModelScope.launch {
            val ok = marketEngine.fetchLiveSpotAnchors()
            _statusMessage.value = if (ok) {
                "Synced live FX & Crypto spot reference rates"
            } else {
                "Using multi-asset tick stream (spot API unreachable)"
            }
        }
    }

    fun executeManualOrder(
        pair: ForexPair,
        side: TradeSide,
        lotSize: Double,
        stopLossPips: Long,
        takeProfitPips: Long
    ) {
        val snap = _marketSnapshots.value[pair] ?: return
        val entry = if (side == TradeSide.BUY) snap.ask else snap.bid
        val sl = if (side == TradeSide.BUY) {
            entry - stopLossPips * pair.pipSize
        } else {
            entry + stopLossPips * pair.pipSize
        }
        val tp = if (side == TradeSide.BUY) {
            entry + takeProfitPips * pair.pipSize
        } else {
            entry - takeProfitPips * pair.pipSize
        }

        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.openTrade(
                botId = "",
                botName = "Manual Execution",
                pair = pair,
                side = side,
                lotSize = lotSize,
                entryPrice = entry,
                stopLoss = sl,
                takeProfit = tp
            )
            _isBusy.value = false
            _statusMessage.value = if (res.isSuccess) {
                "${side.name} ${lotSize}L ${pair.symbol} filled @ ${pair.formatPrice(entry)}"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Order rejected"
            }
        }
    }

    fun closePositionManually(trade: TradeRecord) {
        val snap = _marketSnapshots.value[trade.forexPair] ?: return
        val closePrice = if (trade.tradeSide == TradeSide.BUY) snap.bid else snap.ask
        val account = (accountState.value as? UiState.Success)?.data
        val bots = (botsState.value as? UiState.Success)?.data ?: emptyList()
        val bot = bots.firstOrNull { it.id == trade.botId }

        if (!closingTradeIds.add(trade.id)) return
        viewModelScope.launch {
            try {
                val res = repository.closeTradeAndUpdateBalances(
                    trade = trade,
                    closePrice = closePrice,
                    reason = CloseReason.MANUAL_CLOSE,
                    currentAccountBalance = account?.balance ?: 10000.0,
                    associatedBot = bot
                )
                if (res.isFailure) {
                    _statusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to close position"
                } else {
                    val pnl = trade.forexPair.pnlUsd(trade.tradeSide, trade.entryPrice, closePrice, trade.lotSize).roundTo(2)
                    val sign = if (pnl >= 0) "+" else ""
                    _statusMessage.value = "Closed ${trade.pair} (${sign}$$pnl)"
                }
            } finally {
                closingTradeIds.remove(trade.id)
            }
        }
    }

    fun toggleBot(bot: TradingBot) {
        val account = (accountState.value as? UiState.Success)?.data
        if (bot.botStatus == BotStatus.PAUSED && account?.killSwitchTriggered == true) {
            _statusMessage.value = "Drawdown kill-switch active. Reset it in Vault before resuming bots."
            return
        }
        val nextStatus = if (bot.botStatus == BotStatus.ACTIVE) BotStatus.PAUSED else BotStatus.ACTIVE
        viewModelScope.launch {
            val res = repository.updateBotStatus(bot.id, nextStatus)
            if (res.isFailure) {
                _statusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update bot"
            } else {
                _statusMessage.value = "${bot.name} is now ${nextStatus.name}"
            }
        }
    }

    fun createBot(
        name: String,
        pair: ForexPair,
        strategy: BotStrategy,
        timeframe: Timeframe,
        lotSize: Double,
        stopLossPips: Long,
        takeProfitPips: Long,
        riskPercent: Double,
        startImmediately: Boolean
    ) {
        val account = (accountState.value as? UiState.Success)?.data
        val effectiveStart = startImmediately && account?.killSwitchTriggered != true
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createBot(
                name = name,
                pair = pair,
                strategy = strategy,
                timeframe = timeframe,
                lotSize = lotSize,
                stopLossPips = stopLossPips,
                takeProfitPips = takeProfitPips,
                riskPercent = riskPercent,
                startImmediately = effectiveStart
            )
            _isBusy.value = false
            _statusMessage.value = if (res.isSuccess) {
                "Deployed robot \"$name\" on ${pair.symbol}"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to create robot"
            }
        }
    }

    fun deleteBot(bot: TradingBot) {
        viewModelScope.launch {
            val res = repository.deleteBot(bot.id)
            _statusMessage.value = if (res.isSuccess) {
                "Removed ${bot.name}"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to remove bot"
            }
        }
    }

    fun setAllBotsStatus(activate: Boolean) {
        val account = (accountState.value as? UiState.Success)?.data
        if (activate && account?.killSwitchTriggered == true) {
            _statusMessage.value = "Drawdown kill-switch active. Reset it in Vault before resuming bots."
            return
        }
        val bots = (botsState.value as? UiState.Success)?.data ?: return
        val target = if (activate) BotStatus.ACTIVE else BotStatus.PAUSED
        viewModelScope.launch {
            val res = repository.setAllBotsStatus(bots, target)
            _statusMessage.value = if (res.isSuccess) {
                if (activate) "All ${bots.size} trading robots activated" else "All trading robots paused"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to update robots"
            }
        }
    }

    fun updateBotParameters(
        bot: TradingBot,
        lotSize: Double,
        stopLossPips: Long,
        takeProfitPips: Long
    ) {
        viewModelScope.launch {
            val res = repository.updateBotParameters(bot.id, lotSize, stopLossPips, takeProfitPips)
            _statusMessage.value = if (res.isSuccess) {
                "Updated ${bot.name} (${lotSize}L, SL ${stopLossPips}p, TP ${takeProfitPips}p)"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to update robot parameters"
            }
        }
    }

    fun modifyTradeSlTp(trade: TradeRecord, newStopLoss: Double, newTakeProfit: Double) {
        viewModelScope.launch {
            val res = repository.updateTradeSlTp(trade, newStopLoss, newTakeProfit)
            _statusMessage.value = if (res.isSuccess) {
                "Updated SL/TP for ${trade.pair}"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to update SL/TP"
            }
        }
    }

    fun cloneBotTemplate(template: RobotTemplate) {
        val account = (accountState.value as? UiState.Success)?.data
        val existingNames = ((botsState.value as? UiState.Success)?.data ?: emptyList()).map { it.name }.toSet()
        val uniqueName = if (template.name in existingNames) {
            var idx = 2
            while ("${template.name} #$idx" in existingNames) idx++
            "${template.name} #$idx".take(50)
        } else {
            template.name
        }
        val activate = account?.killSwitchTriggered != true
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.cloneRobotTemplates(
                templates = listOf(template.copy(name = uniqueName)),
                activateImmediately = activate
            )
            _isBusy.value = false
            _statusMessage.value = if (res.isSuccess) {
                "Cloned & deployed \"$uniqueName\" (${template.pair.symbol})"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to clone trading robot"
            }
        }
    }

    fun cloneAllBestRobots() {
        val account = (accountState.value as? UiState.Success)?.data
        val existingBots = (botsState.value as? UiState.Success)?.data ?: emptyList()
        val existingNames = existingBots.map { it.name.lowercase() }.toSet()
        val missingTemplates = BestRobotCatalog.topRobots.filter { it.name.lowercase() !in existingNames }
        if (missingTemplates.isEmpty()) {
            _statusMessage.value = "All ${BestRobotCatalog.topRobots.size} multi-asset robots are already in your fleet"
            return
        }
        val activate = account?.killSwitchTriggered != true
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.cloneRobotTemplates(
                templates = missingTemplates,
                activateImmediately = activate
            )
            _isBusy.value = false
            _statusMessage.value = if (res.isSuccess) {
                "Cloned ${missingTemplates.size} top multi-asset robots into your fleet"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to clone top robots"
            }
        }
    }

    fun updateRiskSettings(
        leverage: Long,
        maxDailyDrawdownPct: Double,
        requireTradeConfirm: Boolean,
        resetKillSwitch: Boolean = false
    ) {
        val account = (accountState.value as? UiState.Success)?.data
        val nextKillSwitch = if (resetKillSwitch) false else (account?.killSwitchTriggered ?: false)
        viewModelScope.launch {
            val res = repository.updateRiskSettings(
                leverage = leverage,
                maxDailyDrawdownPct = maxDailyDrawdownPct,
                requireTradeConfirm = requireTradeConfirm,
                killSwitchTriggered = nextKillSwitch
            )
            if (res.isFailure) {
                _statusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to save guardrails"
            } else if (resetKillSwitch) {
                _statusMessage.value = "Kill-switch cleared. Robots can now be resumed."
            }
        }
    }

    fun resetDemoAccount() {
        val trades = (tradesState.value as? UiState.Success)?.data ?: emptyList()
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.resetDemoEquity(trades)
            _isBusy.value = false
            _statusMessage.value = if (res.isSuccess) {
                "Demo account reset to $10,000.00"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to reset account"
            }
        }
    }

    private fun processRealtimeBotAndRiskTick(snapshots: Map<ForexPair, MarketSnapshot>) {
        val account = (accountState.value as? UiState.Success)?.data ?: return
        val bots = (botsState.value as? UiState.Success)?.data ?: return
        val trades = (tradesState.value as? UiState.Success)?.data ?: return

        val openTrades = trades.filter { it.tradeStatus == TradeStatus.OPEN }

        // 1. Evaluate Stop-Loss & Take-Profit targets on all OPEN positions
        for (trade in openTrades) {
            if (trade.id.isBlank() || closingTradeIds.contains(trade.id)) continue
            val snap = snapshots[trade.forexPair] ?: continue
            val currentPrice = if (trade.tradeSide == TradeSide.BUY) snap.bid else snap.ask

            val hitReason = when (trade.tradeSide) {
                TradeSide.BUY -> when {
                    trade.takeProfit > 0.0 && currentPrice >= trade.takeProfit -> CloseReason.TP_HIT
                    trade.stopLoss > 0.0 && currentPrice <= trade.stopLoss -> CloseReason.SL_HIT
                    else -> null
                }
                TradeSide.SELL -> when {
                    trade.takeProfit > 0.0 && currentPrice <= trade.takeProfit -> CloseReason.TP_HIT
                    trade.stopLoss > 0.0 && currentPrice >= trade.stopLoss -> CloseReason.SL_HIT
                    else -> null
                }
            }

            if (hitReason != null) {
                closingTradeIds.add(trade.id)
                val bot = bots.firstOrNull { it.id == trade.botId }
                viewModelScope.launch {
                    try {
                        repository.closeTradeAndUpdateBalances(
                            trade = trade,
                            closePrice = currentPrice,
                            reason = hitReason,
                            currentAccountBalance = account.balance,
                            associatedBot = bot
                        )
                    } finally {
                        closingTradeIds.remove(trade.id)
                    }
                }
            }
        }

        // 2. Check Drawdown Kill-Switch
        val telemetry = accountTelemetry.value
        val activeBots = bots.filter { it.botStatus == BotStatus.ACTIVE }
        if (!account.killSwitchTriggered && activeBots.isNotEmpty() && telemetry.drawdownPct >= account.maxDailyDrawdownPct) {
            viewModelScope.launch {
                val res = repository.pauseAllBotsAndTriggerKillSwitch(activeBots)
                if (res.isSuccess) {
                    _statusMessage.value = "Kill-Switch Triggered: Drawdown hit ${telemetry.drawdownPct}%"
                }
            }
            return
        }

        // 3. Evaluate Active Algorithmic Bots for New Signal Entries
        if (account.killSwitchTriggered || openTrades.size >= 8) return

        val now = System.currentTimeMillis()
        for (bot in activeBots) {
            if (bot.id.isBlank() || openingBotIds.contains(bot.id)) continue
            val hasOpenPosition = openTrades.any { it.botId == bot.id }
            if (hasOpenPosition) continue

            val lastTime = lastBotTradeMillis[bot.id] ?: 0L
            if (now - lastTime < 14_000L) continue

            val snap = snapshots[bot.forexPair] ?: continue
            val signal = marketEngine.evaluateBotSignal(bot, snap)
            val triggerSide = marketEngine.shouldBotTriggerEntry(signal) ?: continue

            val entry = if (triggerSide == TradeSide.BUY) snap.ask else snap.bid
            val sl = if (triggerSide == TradeSide.BUY) {
                entry - bot.stopLossPips * bot.forexPair.pipSize
            } else {
                entry + bot.stopLossPips * bot.forexPair.pipSize
            }
            val tp = if (triggerSide == TradeSide.BUY) {
                entry + bot.takeProfitPips * bot.forexPair.pipSize
            } else {
                entry - bot.takeProfitPips * bot.forexPair.pipSize
            }

            openingBotIds.add(bot.id)
            lastBotTradeMillis[bot.id] = now
            viewModelScope.launch {
                try {
                    repository.openTrade(
                        botId = bot.id,
                        botName = bot.name,
                        pair = bot.forexPair,
                        side = triggerSide,
                        lotSize = bot.lotSize,
                        entryPrice = entry,
                        stopLoss = sl,
                        takeProfit = tp
                    )
                } finally {
                    openingBotIds.remove(bot.id)
                }
            }
        }
    }

    private companion object {
        const val TAG = "TradingViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
