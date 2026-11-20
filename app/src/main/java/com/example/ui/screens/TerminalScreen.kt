package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AssetClass
import com.example.data.model.BotSignal
import com.example.data.model.ForexPair
import com.example.data.model.MarketSnapshot
import com.example.data.model.Timeframe
import com.example.data.model.TradeRecord
import com.example.data.model.TradeSide
import com.example.data.model.TradeStatus
import com.example.data.model.TraderAccount
import com.example.data.model.activeMarketSessionLabel
import com.example.data.model.roundTo
import com.example.ui.AccountTelemetry
import com.example.ui.components.CandlestickChart
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.CyanAlgo
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MonoPriceStyle
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletIndicator
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun TerminalScreen(
    selectedPair: ForexPair,
    selectedTimeframe: Timeframe,
    snapshots: Map<ForexPair, MarketSnapshot>,
    botSignals: List<BotSignal>,
    trades: List<TradeRecord>,
    account: TraderAccount?,
    telemetry: AccountTelemetry,
    isBusy: Boolean,
    onSelectPair: (ForexPair) -> Unit,
    onSelectTimeframe: (Timeframe) -> Unit,
    onRefreshSpotRates: () -> Unit,
    onExecuteOrder: (ForexPair, TradeSide, Double, Long, Long) -> Unit,
    onClosePosition: (TradeRecord) -> Unit,
    onNavigateToBots: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSnapshot = snapshots[selectedPair]
    var showEma by remember { mutableStateOf(true) }
    var showBollinger by remember { mutableStateOf(true) }
    var showRsi by remember { mutableStateOf(true) }

    var lotSize by remember { mutableDoubleStateOf(0.25) }
    var slPips by remember { mutableLongStateOf(20L) }
    var tpPips by remember { mutableLongStateOf(40L) }
    var pendingConfirmSide by remember { mutableStateOf<TradeSide?>(null) }

    var selectedAssetClass by remember { mutableStateOf<AssetClass?>(null) }

    val openPairPositions = remember(trades, selectedPair) {
        trades.filter { it.tradeStatus == TradeStatus.OPEN && it.forexPair == selectedPair }
    }

    val pairSignals = remember(botSignals, selectedPair) {
        botSignals.filter { it.pair == selectedPair }
    }

    val sessionLabel = remember(selectedPair) {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        activeMarketSessionLabel(selectedPair.assetClass, utcCal.get(Calendar.HOUR_OF_DAY))
    }

    val filteredPairs = remember(selectedAssetClass) {
        ForexPair.byAssetClass(selectedAssetClass)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useSplitWorkstation = maxWidth >= 820.dp

        if (useSplitWorkstation) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1.45f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        TerminalHeader(
                            activeSnapshot = activeSnapshot,
                            telemetry = telemetry,
                            sessionLabel = sessionLabel,
                            onRefreshSpotRates = onRefreshSpotRates
                        )
                    }
                    item {
                        AssetClassStrip(
                            selectedAssetClass = selectedAssetClass,
                            onSelectAssetClass = { asset ->
                                selectedAssetClass = asset
                                if (asset != null && selectedPair.assetClass != asset) {
                                    onSelectPair(ForexPair.byAssetClass(asset).first())
                                }
                            }
                        )
                    }
                    item {
                        PairTickerStrip(
                            pairs = filteredPairs,
                            selectedPair = selectedPair,
                            snapshots = snapshots,
                            onSelectPair = onSelectPair
                        )
                    }
                    if (activeSnapshot != null) {
                        item {
                            PairQuoteAndControls(
                                selectedPair = selectedPair,
                                selectedTimeframe = selectedTimeframe,
                                activeSnapshot = activeSnapshot,
                                showEma = showEma,
                                showBollinger = showBollinger,
                                showRsi = showRsi,
                                onSelectTimeframe = onSelectTimeframe,
                                onToggleEma = { showEma = !showEma },
                                onToggleBollinger = { showBollinger = !showBollinger },
                                onToggleRsi = { showRsi = !showRsi }
                            )
                        }
                        item {
                            CandlestickChart(
                                snapshot = activeSnapshot,
                                openPositionsOnPair = openPairPositions,
                                showEmaOverlay = showEma,
                                showBollingerOverlay = showBollinger,
                                showRsiPane = showRsi,
                                modifier = Modifier.testTag("live_candlestick_chart")
                            )
                        }
                    }
                    if (openPairPositions.isNotEmpty() && activeSnapshot != null) {
                        item {
                            OpenPairPositionsCard(
                                pair = selectedPair,
                                positions = openPairPositions,
                                snapshot = activeSnapshot,
                                onClosePosition = onClosePosition
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (activeSnapshot != null) {
                        item {
                            ExecutionDeskCard(
                                selectedPair = selectedPair,
                                activeSnapshot = activeSnapshot,
                                account = account,
                                lotSize = lotSize,
                                slPips = slPips,
                                tpPips = tpPips,
                                isBusy = isBusy,
                                onAdjustLot = { lotSize = it },
                                onAdjustSl = { slPips = it },
                                onAdjustTp = { tpPips = it },
                                onOrderSideClick = { side ->
                                    if (account?.requireTradeConfirm == true) {
                                        pendingConfirmSide = side
                                    } else {
                                        onExecuteOrder(selectedPair, side, lotSize, slPips, tpPips)
                                    }
                                }
                            )
                        }
                    }
                    item {
                        RobotSignalsCard(
                            selectedPair = selectedPair,
                            pairSignals = pairSignals,
                            onNavigateToBots = onNavigateToBots
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    TerminalHeader(
                        activeSnapshot = activeSnapshot,
                        telemetry = telemetry,
                        sessionLabel = sessionLabel,
                        onRefreshSpotRates = onRefreshSpotRates
                    )
                }

                item {
                    AssetClassStrip(
                        selectedAssetClass = selectedAssetClass,
                        onSelectAssetClass = { asset ->
                            selectedAssetClass = asset
                            if (asset != null && selectedPair.assetClass != asset) {
                                onSelectPair(ForexPair.byAssetClass(asset).first())
                            }
                        }
                    )
                }

                item {
                    PairTickerStrip(
                        pairs = filteredPairs,
                        selectedPair = selectedPair,
                        snapshots = snapshots,
                        onSelectPair = onSelectPair
                    )
                }

                if (activeSnapshot != null) {
                    item {
                        PairQuoteAndControls(
                            selectedPair = selectedPair,
                            selectedTimeframe = selectedTimeframe,
                            activeSnapshot = activeSnapshot,
                            showEma = showEma,
                            showBollinger = showBollinger,
                            showRsi = showRsi,
                            onSelectTimeframe = onSelectTimeframe,
                            onToggleEma = { showEma = !showEma },
                            onToggleBollinger = { showBollinger = !showBollinger },
                            onToggleRsi = { showRsi = !showRsi }
                        )
                    }

                    item {
                        CandlestickChart(
                            snapshot = activeSnapshot,
                            openPositionsOnPair = openPairPositions,
                            showEmaOverlay = showEma,
                            showBollingerOverlay = showBollinger,
                            showRsiPane = showRsi,
                            modifier = Modifier.testTag("live_candlestick_chart")
                        )
                    }

                    if (openPairPositions.isNotEmpty()) {
                        item {
                            OpenPairPositionsCard(
                                pair = selectedPair,
                                positions = openPairPositions,
                                snapshot = activeSnapshot,
                                onClosePosition = onClosePosition
                            )
                        }
                    }

                    item {
                        ExecutionDeskCard(
                            selectedPair = selectedPair,
                            activeSnapshot = activeSnapshot,
                            account = account,
                            lotSize = lotSize,
                            slPips = slPips,
                            tpPips = tpPips,
                            isBusy = isBusy,
                            onAdjustLot = { lotSize = it },
                            onAdjustSl = { slPips = it },
                            onAdjustTp = { tpPips = it },
                            onOrderSideClick = { side ->
                                if (account?.requireTradeConfirm == true) {
                                    pendingConfirmSide = side
                                } else {
                                    onExecuteOrder(selectedPair, side, lotSize, slPips, tpPips)
                                }
                            }
                        )
                    }
                }

                item {
                    RobotSignalsCard(
                        selectedPair = selectedPair,
                        pairSignals = pairSignals,
                        onNavigateToBots = onNavigateToBots
                    )
                }
            }
        }
    }

    pendingConfirmSide?.let { side ->
        val snap = activeSnapshot
        val priceText = snap?.let {
            selectedPair.formatPrice(if (side == TradeSide.BUY) it.ask else it.bid)
        } ?: "--"
        AlertDialog(
            onDismissRequest = { pendingConfirmSide = null },
            containerColor = SlateElevated,
            title = {
                Text("Confirm ${side.name} Order", color = TextPrimary)
            },
            text = {
                Text(
                    "Execute ${side.name} $lotSize lots of ${selectedPair.symbol} @ $priceText with SL ${slPips}p / TP ${tpPips}p?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onExecuteOrder(selectedPair, side, lotSize, slPips, tpPips)
                        pendingConfirmSide = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (side == TradeSide.BUY) EmeraldBull else CrimsonBear,
                        contentColor = ObsidianCanvas
                    )
                ) {
                    Text("Confirm ${side.name}")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmSide = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun TerminalHeader(
    activeSnapshot: MarketSnapshot?,
    telemetry: AccountTelemetry,
    sessionLabel: String,
    onRefreshSpotRates: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (activeSnapshot?.isLiveSpotAnchored == true) EmeraldBull else CyanAlgo)
                )
                Text(
                    text = if (activeSnapshot?.isLiveSpotAnchored == true) {
                        "LIVE SPOT • $sessionLabel"
                    } else {
                        "MULTI-ASSET • $sessionLabel"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = String.format(Locale.US, "Equity $%,.2f", telemetry.equity),
                style = MonoPriceStyle.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val pnlColor = if (telemetry.floatingPnl >= 0) EmeraldBull else CrimsonBear
            val sign = if (telemetry.floatingPnl >= 0) "+" else "-"
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = pnlColor.copy(alpha = 0.14f)
            ) {
                Text(
                    text = String.format(Locale.US, "Float %s$%.2f", sign, kotlin.math.abs(telemetry.floatingPnl)),
                    style = MonoPriceStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = pnlColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            IconButton(
                onClick = onRefreshSpotRates,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("refresh_spot_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh live FX spot rates",
                    tint = CyanAlgo
                )
            }
        }
    }
}

@Composable
private fun AssetClassStrip(
    selectedAssetClass: AssetClass?,
    onSelectAssetClass: (AssetClass?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedAssetClass == null,
            onClick = { onSelectAssetClass(null) },
            label = { Text("All (${ForexPair.entries.size})") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = EmeraldBull.copy(alpha = 0.2f),
                selectedLabelColor = EmeraldBull,
                containerColor = SlateSurface,
                labelColor = TextSecondary
            ),
            modifier = Modifier.testTag("asset_filter_all")
        )
        AssetClass.entries.forEach { asset ->
            val accent = assetClassColor(asset)
            val count = ForexPair.byAssetClass(asset).size
            FilterChip(
                selected = selectedAssetClass == asset,
                onClick = { onSelectAssetClass(if (selectedAssetClass == asset) null else asset) },
                label = { Text("${asset.label} ($count)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.2f),
                    selectedLabelColor = accent,
                    containerColor = SlateSurface,
                    labelColor = TextSecondary
                ),
                modifier = Modifier.testTag("asset_filter_${asset.name.lowercase()}")
            )
        }
    }
}

private fun assetClassColor(assetClass: AssetClass): Color = when (assetClass) {
    AssetClass.FOREX -> EmeraldBull
    AssetClass.METALS -> AmberWarn
    AssetClass.CRYPTO -> CyanAlgo
    AssetClass.STOCKS -> VioletIndicator
}

@Composable
private fun PairTickerStrip(
    pairs: List<ForexPair>,
    selectedPair: ForexPair,
    snapshots: Map<ForexPair, MarketSnapshot>,
    onSelectPair: (ForexPair) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(pairs, key = { it.name }) { pair ->
            val snap = snapshots[pair]
            val isSelected = pair == selectedPair
            val up = (snap?.changePct ?: 0.0) >= 0.0
            val badgeAccent = assetClassColor(pair.assetClass)
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = if (isSelected) EmeraldBull else HairlineBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectPair(pair) }
                    .testTag("pair_chip_${pair.name.lowercase()}"),
                color = if (isSelected) EmeraldBull.copy(alpha = 0.12f) else SlateSurface
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pair.symbol,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) EmeraldBull else TextPrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = badgeAccent.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = pair.assetClass.badge,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = badgeAccent,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = snap?.let { pair.formatPrice(it.bid) } ?: pair.formatPrice(pair.seedPrice),
                            style = MonoPriceStyle.copy(fontSize = 11.sp),
                            color = TextSecondary
                        )
                        val pct = snap?.changePct ?: 0.0
                        Text(
                            text = String.format(Locale.US, "%+.2f%%", pct),
                            style = MonoPriceStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            color = if (up) EmeraldBull else CrimsonBear
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PairQuoteAndControls(
    selectedPair: ForexPair,
    selectedTimeframe: Timeframe,
    activeSnapshot: MarketSnapshot,
    showEma: Boolean,
    showBollinger: Boolean,
    showRsi: Boolean,
    onSelectTimeframe: (Timeframe) -> Unit,
    onToggleEma: () -> Unit,
    onToggleBollinger: () -> Unit,
    onToggleRsi: () -> Unit
) {
    val accent = assetClassColor(selectedPair.assetClass)
    val u = selectedPair.assetClass.unitSuffix
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
                .background(SlateSurface, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = selectedPair.symbol,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accent.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = selectedPair.assetClass.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "${selectedPair.displayName} • Spread ${activeSnapshot.spreadPips}$u",
                    style = MonoPriceStyle.copy(fontSize = 11.sp),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = selectedPair.formatPrice(activeSnapshot.bid),
                    style = MonoPriceStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    color = if (activeSnapshot.changePct >= 0.0) EmeraldBull else CrimsonBear
                )
                Text(
                    text = "Ask ${selectedPair.formatPrice(activeSnapshot.ask)}",
                    style = MonoPriceStyle.copy(fontSize = 11.sp),
                    color = TextMuted
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Timeframe.entries.forEach { tf ->
                val selected = tf == selectedTimeframe
                FilterChip(
                    selected = selected,
                    onClick = { onSelectTimeframe(tf) },
                    label = {
                        Text(
                            text = tf.label,
                            style = MonoPriceStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanAlgo.copy(alpha = 0.2f),
                        selectedLabelColor = CyanAlgo,
                        containerColor = SlateSurface,
                        labelColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("tf_chip_${tf.label.lowercase()}")
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IndicatorToggleChip("EMA", showEma, AmberWarn, onToggleEma)
            IndicatorToggleChip("BB", showBollinger, CyanAlgo, onToggleBollinger)
            IndicatorToggleChip("RSI", showRsi, VioletIndicator, onToggleRsi)
        }
    }
}

@Composable
private fun OpenPairPositionsCard(
    pair: ForexPair,
    positions: List<TradeRecord>,
    snapshot: MarketSnapshot,
    onClosePosition: (TradeRecord) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, EmeraldBull.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ACTIVE POSITIONS ON ${pair.symbol} (${positions.size})",
                style = MaterialTheme.typography.labelSmall,
                color = CyanAlgo
            )
            positions.forEach { pos ->
                val mark = if (pos.tradeSide == TradeSide.BUY) snapshot.bid else snapshot.ask
                val pnl = pair.pnlUsd(pos.tradeSide, pos.entryPrice, mark, pos.lotSize).roundTo(2)
                val pnlColor = if (pnl >= 0.0) EmeraldBull else CrimsonBear
                val sign = if (pnl >= 0.0) "+" else "-"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateElevated)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${pos.side} ${pos.lotSize}L @ ${pair.formatPrice(pos.entryPrice)}",
                            style = MonoPriceStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = TextPrimary
                        )
                        Text(
                            text = pos.botName,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%s$%.2f", sign, kotlin.math.abs(pnl)),
                            style = MonoPriceStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            color = pnlColor
                        )
                        OutlinedButton(
                            onClick = { onClosePosition(pos) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonBear),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close position", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Close", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionDeskCard(
    selectedPair: ForexPair,
    activeSnapshot: MarketSnapshot,
    account: TraderAccount?,
    lotSize: Double,
    slPips: Long,
    tpPips: Long,
    isBusy: Boolean,
    onAdjustLot: (Double) -> Unit,
    onAdjustSl: (Long) -> Unit,
    onAdjustTp: (Long) -> Unit,
    onOrderSideClick: (TradeSide) -> Unit
) {
    val leverage = (account?.leverage ?: 100L).coerceAtLeast(10L)
    val estMargin = (selectedPair.notionalUsd(activeSnapshot.ask, lotSize) / leverage.toDouble()).roundTo(2)
    val rrRatio = (tpPips.toDouble() / slPips.coerceAtLeast(1L).toDouble()).roundTo(1)
    val pipValue = selectedPair.pointValueUsd(activeSnapshot.bid, lotSize)
    val unitLabel = selectedPair.assetClass.pointLabel.uppercase()
    val unitSuffix = selectedPair.assetClass.unitSuffix

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INSTANT EXECUTION DESK",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Text(
                    text = "1:$leverage • R:R 1:$rrRatio",
                    style = MonoPriceStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = CyanAlgo
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StepperBox(
                    label = "LOT SIZE",
                    valueText = String.format(Locale.US, "%.2f", lotSize),
                    onMinus = { onAdjustLot((lotSize - 0.05).coerceAtLeast(0.01).roundTo(2)) },
                    onPlus = { onAdjustLot((lotSize + 0.05).coerceAtMost(5.0).roundTo(2)) },
                    tagPrefix = "lot",
                    modifier = Modifier.weight(1.1f)
                )
                StepperBox(
                    label = "SL ($unitLabel)",
                    valueText = "${slPips}$unitSuffix",
                    onMinus = { onAdjustSl((slPips - 5L).coerceAtLeast(5L)) },
                    onPlus = { onAdjustSl((slPips + 5L).coerceAtMost(200L)) },
                    tagPrefix = "sl",
                    modifier = Modifier.weight(1f)
                )
                StepperBox(
                    label = "TP ($unitLabel)",
                    valueText = "${tpPips}$unitSuffix",
                    onMinus = { onAdjustTp((tpPips - 5L).coerceAtLeast(5L)) },
                    onPlus = { onAdjustTp((tpPips + 5L).coerceAtMost(400L)) },
                    tagPrefix = "tp",
                    modifier = Modifier.weight(1f)
                )
            }

            // Margin & Point Value Risk Summary Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianCanvas.copy(alpha = 0.7f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format(Locale.US, "Margin $%,.2f", estMargin),
                    style = MonoPriceStyle.copy(fontSize = 11.sp),
                    color = TextSecondary
                )
                Text(
                    text = String.format(Locale.US, "Val/$unitSuffix $%.2f", pipValue),
                    style = MonoPriceStyle.copy(fontSize = 11.sp),
                    color = TextSecondary
                )
                Text(
                    text = String.format(Locale.US, "Risk $%.2f", pipValue * slPips),
                    style = MonoPriceStyle.copy(fontSize = 11.sp),
                    color = AmberWarn
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onOrderSideClick(TradeSide.SELL) },
                    enabled = !isBusy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonBear,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("sell_order_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = "Sell",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "SELL",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedPair.formatPrice(activeSnapshot.bid),
                            style = MonoPriceStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Button(
                    onClick = { onOrderSideClick(TradeSide.BUY) },
                    enabled = !isBusy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldBull,
                        contentColor = ObsidianCanvas
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("buy_order_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = "Buy",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "BUY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedPair.formatPrice(activeSnapshot.ask),
                            style = MonoPriceStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RobotSignalsCard(
    selectedPair: ForexPair,
    pairSignals: List<BotSignal>,
    onNavigateToBots: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = "Bot signals",
                        tint = AmberWarn,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "ROBOT SIGNALS (${selectedPair.symbol})",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
                TextButton(
                    onClick = onNavigateToBots,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Manage Bots", color = CyanAlgo)
                }
            }

            if (pairSignals.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SmartToy,
                        contentDescription = "No active bots on pair",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "No trading robot assigned to ${selectedPair.symbol}. Deploy one in Robots.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            } else {
                pairSignals.forEach { sig ->
                    val sigColor = when (sig.direction) {
                        "BULLISH" -> EmeraldBull
                        "BEARISH" -> CrimsonBear
                        else -> TextSecondary
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateElevated)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sig.botName,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = sig.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = sigColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "${sig.direction} ${sig.confidencePct}%",
                                style = MonoPriceStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                color = sigColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IndicatorToggleChip(
    label: String,
    active: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    FilterChip(
        selected = active,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MonoPriceStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.18f),
            selectedLabelColor = color,
            containerColor = SlateSurface,
            labelColor = TextMuted
        )
    )
}

@Composable
private fun StepperBox(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SlateElevated)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onMinus,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${tagPrefix}_minus_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "Decrease $label",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = valueText,
                style = MonoPriceStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            IconButton(
                onClick = onPlus,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${tagPrefix}_plus_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Increase $label",
                    tint = EmeraldBull,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
