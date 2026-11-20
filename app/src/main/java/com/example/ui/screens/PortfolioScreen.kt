package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AssetClass
import com.example.data.model.ForexPair
import com.example.data.model.MarketSnapshot
import com.example.data.model.TradeRecord
import com.example.data.model.TradeSide
import com.example.data.model.TradeStatus
import com.example.data.model.TraderAccount
import com.example.data.model.UiState
import com.example.data.model.roundTo
import com.example.ui.AccountTelemetry
import com.example.ui.components.EquityCurveChart
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private enum class LedgerFilter(val label: String) {
    OPEN("Open"),
    CLOSED("History"),
    BOTS_ONLY("Bots Only")
}

@Composable
fun PortfolioScreen(
    tradesState: UiState<List<TradeRecord>>,
    snapshots: Map<ForexPair, MarketSnapshot>,
    account: TraderAccount?,
    telemetry: AccountTelemetry,
    onClosePosition: (TradeRecord) -> Unit,
    onModifyTradeSlTp: (TradeRecord, Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeFilter by remember { mutableStateOf(LedgerFilter.OPEN) }
    var editingTrade by remember { mutableStateOf<TradeRecord?>(null) }

    when (tradesState) {
        is UiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EmeraldBull)
            }
        }
        is UiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tradesState.message, color = CrimsonBear)
            }
        }
        is UiState.Success -> {
            val allTrades = tradesState.data
            val openTrades = remember(allTrades) { allTrades.filter { it.tradeStatus == TradeStatus.OPEN } }
            val closedTrades = remember(allTrades) { allTrades.filter { it.tradeStatus == TradeStatus.CLOSED } }

            val displayedTrades = remember(activeFilter, allTrades, openTrades, closedTrades) {
                when (activeFilter) {
                    LedgerFilter.OPEN -> openTrades
                    LedgerFilter.CLOSED -> closedTrades
                    LedgerFilter.BOTS_ONLY -> allTrades.filter { it.botId.isNotBlank() }
                }
            }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    EquityCurveChart(
                        startingBalance = account?.startingBalance ?: 10000.0,
                        currentEquity = telemetry.equity,
                        closedTrades = closedTrades
                    )
                }

                // Primary Telemetry Grid
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TelemetryTile(
                                label = "BALANCE",
                                value = String.format(Locale.US, "$%,.2f", telemetry.balance),
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryTile(
                                label = "FREE MARGIN",
                                value = String.format(Locale.US, "$%,.2f", telemetry.freeMargin),
                                color = CyanAlgo,
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryTile(
                                label = "WIN RATE",
                                value = String.format(Locale.US, "%.1f%%", telemetry.winRatePct),
                                color = EmeraldBull,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TelemetryTile(
                                label = "PROFIT FACTOR",
                                value = String.format(Locale.US, "%.2f", telemetry.profitFactor),
                                color = if (telemetry.profitFactor >= 1.2) EmeraldBull else TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            val pipSign = if (telemetry.totalPips >= 0) "+" else ""
                            TelemetryTile(
                                label = "NET POINTS",
                                value = String.format(Locale.US, "%s%.1f pts", pipSign, telemetry.totalPips),
                                color = if (telemetry.totalPips >= 0) EmeraldBull else CrimsonBear,
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryTile(
                                label = "BEST TRADE",
                                value = String.format(Locale.US, "+$%.2f", telemetry.bestTradePnl.coerceAtLeast(0.0)),
                                color = AmberWarn,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LedgerFilter.entries.forEach { filter ->
                            val countSuffix = when (filter) {
                                LedgerFilter.OPEN -> " (${openTrades.size})"
                                LedgerFilter.CLOSED -> " (${closedTrades.size})"
                                LedgerFilter.BOTS_ONLY -> ""
                            }
                            FilterChip(
                                selected = activeFilter == filter,
                                onClick = { activeFilter = filter },
                                label = { Text("${filter.label}$countSuffix") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldBull.copy(alpha = 0.18f),
                                    selectedLabelColor = EmeraldBull,
                                    containerColor = SlateSurface,
                                    labelColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("ledger_filter_${filter.name.lowercase()}")
                            )
                        }
                    }
                }

                if (displayedTrades.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SlateSurface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = "Empty ledger",
                                    tint = TextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (activeFilter == LedgerFilter.OPEN) "No Open Positions" else "No Trades Recorded Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Active robots or manual executions on the Terminal desk will appear here in real time.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(displayedTrades, key = { it.id }) { trade ->
                        TradeRecordCard(
                            trade = trade,
                            snapshot = snapshots[trade.forexPair],
                            onEditSlTp = { editingTrade = trade },
                            onClose = { onClosePosition(trade) }
                        )
                    }
                }
            }
        }
    }

    editingTrade?.let { trade ->
        ModifySlTpDialog(
            trade = trade,
            onDismiss = { editingTrade = null },
            onSave = { sl, tp ->
                onModifyTradeSlTp(trade, sl, tp)
                editingTrade = null
            }
        )
    }
}

@Composable
private fun TelemetryTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
            .background(SlateSurface, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(
            text = value,
            style = MonoPriceStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
private fun TradeRecordCard(
    trade: TradeRecord,
    snapshot: MarketSnapshot?,
    onEditSlTp: () -> Unit,
    onClose: () -> Unit
) {
    val pair = trade.forexPair
    val isOpen = trade.tradeStatus == TradeStatus.OPEN
    val sideColor = if (trade.tradeSide == TradeSide.BUY) EmeraldBull else CrimsonBear

    val liveMarkPrice = snapshot?.let {
        if (trade.tradeSide == TradeSide.BUY) it.bid else it.ask
    } ?: trade.entryPrice

    val displayPnl = if (isOpen) {
        pair.pnlUsd(trade.tradeSide, trade.entryPrice, liveMarkPrice, trade.lotSize)
    } else {
        trade.realizedPnl
    }
    val pnlColor = if (displayPnl >= 0.0) EmeraldBull else CrimsonBear
    val pnlSign = if (displayPnl >= 0.0) "+" else "-"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
            .testTag("trade_card_${trade.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = sideColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = trade.side,
                            style = MonoPriceStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = sideColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    val assetBadgeColor = when (pair.assetClass) {
                        AssetClass.FOREX -> EmeraldBull
                        AssetClass.METALS -> AmberWarn
                        AssetClass.CRYPTO -> CyanAlgo
                        AssetClass.STOCKS -> VioletIndicator
                    }
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = assetBadgeColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = pair.assetClass.badge,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = assetBadgeColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = trade.pair,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "${trade.lotSize}L",
                        style = MonoPriceStyle.copy(fontSize = 12.sp),
                        color = TextSecondary
                    )
                }

                Text(
                    text = String.format(Locale.US, "%s$%.2f", pnlSign, abs(displayPnl)),
                    style = MonoPriceStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    color = pnlColor
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SlateElevated)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val exitOrMark = if (isOpen) liveMarkPrice else trade.closePrice
                Text(
                    text = "${pair.formatPrice(trade.entryPrice)} → ${pair.formatPrice(exitOrMark)}",
                    style = MonoPriceStyle.copy(fontSize = 12.sp),
                    color = TextPrimary
                )
                Text(
                    text = trade.botName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (trade.botId.isNotBlank()) CyanAlgo else AmberWarn
                )
            }

            if (isOpen) {
                Text(
                    text = "SL ${pair.formatPrice(trade.stopLoss)} • TP ${pair.formatPrice(trade.takeProfit)}",
                    style = MonoPriceStyle.copy(fontSize = 11.sp),
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onEditSlTp,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAlgo),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("edit_trade_${trade.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Modify SL/TP",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Modify SL/TP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onClose,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonBear),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("close_trade_${trade.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close position",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Close Trade", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(
                    text = "${trade.reason.label} • ${String.format(Locale.US, "%+.1f %s", trade.pips, pair.assetClass.pointLabel.lowercase())}",
                    style = MonoPriceStyle.copy(fontSize = 11.sp),
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ModifySlTpDialog(
    trade: TradeRecord,
    onDismiss: () -> Unit,
    onSave: (Double, Double) -> Unit
) {
    val pair = trade.forexPair
    val initialSlPips = abs(pair.pipDifference(trade.entryPrice, trade.stopLoss)).roundToLong().coerceIn(5L, 250L)
    val initialTpPips = abs(pair.pipDifference(trade.entryPrice, trade.takeProfit)).roundToLong().coerceIn(5L, 400L)

    var slPips by remember(trade.id) { mutableLongStateOf(initialSlPips) }
    var tpPips by remember(trade.id) { mutableLongStateOf(initialTpPips) }

    val previewSl = if (trade.tradeSide == TradeSide.BUY) {
        (trade.entryPrice - slPips * pair.pipSize).roundTo(pair.decimals)
    } else {
        (trade.entryPrice + slPips * pair.pipSize).roundTo(pair.decimals)
    }
    val previewTp = if (trade.tradeSide == TradeSide.BUY) {
        (trade.entryPrice + tpPips * pair.pipSize).roundTo(pair.decimals)
    } else {
        (trade.entryPrice - tpPips * pair.pipSize).roundTo(pair.decimals)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateElevated,
        title = {
            Text("Modify ${trade.pair} Brackets", color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Entry ${pair.formatPrice(trade.entryPrice)} (${trade.side} ${trade.lotSize}L)",
                    style = MonoPriceStyle.copy(fontSize = 12.sp),
                    color = CyanAlgo
                )
                val unit = pair.assetClass.unitSuffix
                Column {
                    Text(
                        text = "Stop Loss: ${slPips}$unit (${pair.formatPrice(previewSl)})",
                        style = MonoPriceStyle.copy(fontSize = 12.sp),
                        color = CrimsonBear
                    )
                    Slider(
                        value = slPips.toFloat(),
                        onValueChange = { slPips = it.roundToLong().coerceIn(5L, 250L) },
                        valueRange = 5f..250f,
                        colors = SliderDefaults.colors(thumbColor = CrimsonBear, activeTrackColor = CrimsonBear)
                    )
                }
                Column {
                    Text(
                        text = "Take Profit: ${tpPips}$unit (${pair.formatPrice(previewTp)})",
                        style = MonoPriceStyle.copy(fontSize = 12.sp),
                        color = EmeraldBull
                    )
                    Slider(
                        value = tpPips.toFloat(),
                        onValueChange = { tpPips = it.roundToLong().coerceIn(5L, 400L) },
                        valueRange = 5f..400f,
                        colors = SliderDefaults.colors(thumbColor = EmeraldBull, activeTrackColor = EmeraldBull)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(previewSl, previewTp) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldBull, contentColor = ObsidianCanvas)
            ) {
                Text("Save Brackets", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
