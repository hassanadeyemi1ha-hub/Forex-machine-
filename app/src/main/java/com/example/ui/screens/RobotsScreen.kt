package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.data.model.BestRobotCatalog
import com.example.data.model.BotSignal
import com.example.data.model.BotStatus
import com.example.data.model.BotStrategy
import com.example.data.model.ForexPair
import com.example.data.model.RobotTemplate
import com.example.data.model.Timeframe
import com.example.data.model.TraderAccount
import com.example.data.model.TradingBot
import com.example.data.model.UiState
import com.example.data.model.roundTo
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
import kotlin.math.roundToLong

private enum class RobotsTab {
    MY_FLEET,
    TOP_EA_LIBRARY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RobotsScreen(
    botsState: UiState<List<TradingBot>>,
    signals: List<BotSignal>,
    account: TraderAccount?,
    isBusy: Boolean,
    onToggleBot: (TradingBot) -> Unit,
    onSetAllBotsStatus: (Boolean) -> Unit,
    onUpdateBotParameters: (TradingBot, Double, Long, Long) -> Unit,
    onDeleteBot: (TradingBot) -> Unit,
    onCreateBot: (String, ForexPair, BotStrategy, Timeframe, Double, Long, Long, Double, Boolean) -> Unit,
    onCloneTemplate: (RobotTemplate) -> Unit,
    onCloneAllBestRobots: () -> Unit,
    onSelectPairOnTerminal: (ForexPair) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(RobotsTab.MY_FLEET) }
    var libraryAssetFilter by remember { mutableStateOf<AssetClass?>(null) }
    var libraryPairFilter by remember { mutableStateOf<ForexPair?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var tuningBot by remember { mutableStateOf<TradingBot?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        when (botsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EmeraldBull)
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = botsState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CrimsonBear
                    )
                }
            }
            is UiState.Success -> {
                val bots = botsState.data
                val activeCount = bots.count { it.botStatus == BotStatus.ACTIVE }
                val totalBotPnl = bots.sumOf { it.realizedPnl }
                val installedNames = remember(bots) { bots.map { it.name.lowercase() }.toSet() }
                val missingTopCount = remember(installedNames) {
                    BestRobotCatalog.topRobots.count { it.name.lowercase() !in installedNames }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header Summary & Mode Switcher
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "MULTI-ASSET ALGO ROBOTS",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CyanAlgo
                                    )
                                    Text(
                                        text = "$activeCount / ${bots.size} Engines Running",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = TextPrimary
                                    )
                                }
                                val pnlColor = if (totalBotPnl >= 0) EmeraldBull else CrimsonBear
                                val sign = if (totalBotPnl >= 0) "+" else "-"
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = pnlColor.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%s$%.2f", sign, kotlin.math.abs(totalBotPnl)),
                                        style = MonoPriceStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                        color = pnlColor,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            // Segmented Tab Switcher: My Fleet vs Top EA Library
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SlateSurface)
                                    .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                RobotTabPill(
                                    label = "My Fleet (${bots.size})",
                                    selected = activeTab == RobotsTab.MY_FLEET,
                                    accent = EmeraldBull,
                                    onClick = { activeTab = RobotsTab.MY_FLEET },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("tab_my_fleet")
                                )
                                RobotTabPill(
                                    label = "Top EA Vault (${BestRobotCatalog.topRobots.size})",
                                    selected = activeTab == RobotsTab.TOP_EA_LIBRARY,
                                    accent = CyanAlgo,
                                    onClick = { activeTab = RobotsTab.TOP_EA_LIBRARY },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("tab_top_ea_library")
                                )
                            }

                            if (account?.killSwitchTriggered == true) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, CrimsonBear.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    color = CrimsonBear.copy(alpha = 0.14f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Warning,
                                            contentDescription = "Kill switch triggered",
                                            tint = CrimsonBear
                                        )
                                        Text(
                                            text = "Drawdown kill-switch is active. Reset it in Vault to resume automated execution.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (activeTab == RobotsTab.MY_FLEET) {
                        if (bots.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onSetAllBotsStatus(true) },
                                        enabled = activeCount < bots.size && account?.killSwitchTriggered != true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldBull),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("start_all_bots_button")
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = "Start all bots", modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Start All", style = MaterialTheme.typography.labelLarge)
                                    }
                                    OutlinedButton(
                                        onClick = { onSetAllBotsStatus(false) },
                                        enabled = activeCount > 0,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarn),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("pause_all_bots_button")
                                    ) {
                                        Icon(Icons.Filled.Pause, contentDescription = "Pause all bots", modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pause All", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }

                        if (missingTopCount > 0) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.dp, CyanAlgo.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                        .clickable { activeTab = RobotsTab.TOP_EA_LIBRARY },
                                    color = CyanAlgo.copy(alpha = 0.10f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.AutoAwesome, contentDescription = "Top EA Vault", tint = CyanAlgo)
                                            Column {
                                                Text(
                                                    text = "$missingTopCount Proven EAs Ready to Clone",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = "Tap to browse & clone top FX, Metal, Crypto & Stock robots.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                        Text("Browse", style = MaterialTheme.typography.labelLarge, color = CyanAlgo)
                                    }
                                }
                            }
                        }

                        if (bots.isEmpty()) {
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
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.SmartToy,
                                            contentDescription = "No robots",
                                            tint = CyanAlgo,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Text(
                                            text = "No Trading Robots Deployed",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Clone proven institutional strategies from the Top EA Vault or build your own.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                        Button(
                                            onClick = onCloneAllBestRobots,
                                            enabled = !isBusy,
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldBull, contentColor = ObsidianCanvas),
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = "Clone all top robots", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Clone All Top Robots", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        } else {
                            items(bots, key = { it.id }) { bot ->
                                val signal = signals.firstOrNull { it.botId == bot.id }
                                TradingBotCard(
                                    bot = bot,
                                    signal = signal,
                                    onToggle = { onToggleBot(bot) },
                                    onTune = { tuningBot = bot },
                                    onDelete = { onDeleteBot(bot) },
                                    onOpenChart = { onSelectPairOnTerminal(bot.forexPair) }
                                )
                            }
                        }
                    } else {
                        // TOP EA MARKETPLACE / CLONE LIBRARY
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, EmeraldBull.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SlateSurface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Top EA suite", tint = EmeraldBull)
                                        Text(
                                            text = "INSTITUTIONAL MULTI-ASSET ROBOTS",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = EmeraldBull
                                        )
                                    }
                                    Text(
                                        text = "Clone verified quantitative EAs across Forex, Metals (Gold/Silver), Crypto (BTC/ETH/SOL), and Stocks (NVDA/TSLA) directly into your fleet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                    Button(
                                        onClick = onCloneAllBestRobots,
                                        enabled = !isBusy && missingTopCount > 0,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = EmeraldBull,
                                            contentColor = ObsidianCanvas
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("clone_all_top_robots_button")
                                    ) {
                                        Icon(
                                            imageVector = if (missingTopCount > 0) Icons.Filled.ContentCopy else Icons.Filled.CheckCircle,
                                            contentDescription = "Clone all top robots",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (missingTopCount > 0) {
                                                "Clone All $missingTopCount Missing Top Robots"
                                            } else {
                                                "All ${BestRobotCatalog.topRobots.size} Top Robots Cloned"
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Asset Class + Instrument Filter Strips
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = libraryAssetFilter == null,
                                        onClick = {
                                            libraryAssetFilter = null
                                            libraryPairFilter = null
                                        },
                                        label = { Text("All Markets") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = EmeraldBull.copy(alpha = 0.2f),
                                            selectedLabelColor = EmeraldBull
                                        )
                                    )
                                    AssetClass.entries.forEach { asset ->
                                        val accent = when (asset) {
                                            AssetClass.FOREX -> EmeraldBull
                                            AssetClass.METALS -> AmberWarn
                                            AssetClass.CRYPTO -> CyanAlgo
                                            AssetClass.STOCKS -> VioletIndicator
                                        }
                                        FilterChip(
                                            selected = libraryAssetFilter == asset,
                                            onClick = {
                                                val next = if (libraryAssetFilter == asset) null else asset
                                                libraryAssetFilter = next
                                                if (next != null && libraryPairFilter?.assetClass != next) {
                                                    libraryPairFilter = null
                                                }
                                            },
                                            label = { Text(asset.label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = accent.copy(alpha = 0.2f),
                                                selectedLabelColor = accent
                                            )
                                        )
                                    }
                                }

                                val instrumentChoices = ForexPair.byAssetClass(libraryAssetFilter).filter { pair ->
                                    BestRobotCatalog.topRobots.any { it.pair == pair }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = libraryPairFilter == null,
                                        onClick = { libraryPairFilter = null },
                                        label = { Text("All Symbols") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyanAlgo.copy(alpha = 0.2f),
                                            selectedLabelColor = CyanAlgo
                                        )
                                    )
                                    instrumentChoices.forEach { pair ->
                                        FilterChip(
                                            selected = libraryPairFilter == pair,
                                            onClick = { libraryPairFilter = if (libraryPairFilter == pair) null else pair },
                                            label = { Text(pair.symbol) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = EmeraldBull.copy(alpha = 0.2f),
                                                selectedLabelColor = EmeraldBull
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        val filteredTemplates = BestRobotCatalog.topRobots.filter { tpl ->
                            (libraryAssetFilter == null || tpl.pair.assetClass == libraryAssetFilter) &&
                                (libraryPairFilter == null || tpl.pair == libraryPairFilter)
                        }
                        items(filteredTemplates, key = { it.id }) { tpl ->
                            val alreadyCloned = tpl.name.lowercase() in installedNames
                            RobotTemplateCard(
                                template = tpl,
                                alreadyCloned = alreadyCloned,
                                isBusy = isBusy,
                                onClone = { onCloneTemplate(tpl) }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateSheet = true },
            containerColor = EmeraldBull,
            contentColor = ObsidianCanvas,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("create_bot_fab")
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Deploy new trading robot"
            )
        }
    }

    if (showCreateSheet) {
        CreateBotBottomSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { name, pair, strategy, tf, lot, sl, tp, risk, startNow ->
                onCreateBot(name, pair, strategy, tf, lot, sl, tp, risk, startNow)
                showCreateSheet = false
            }
        )
    }

    tuningBot?.let { bot ->
        TuneBotDialog(
            bot = bot,
            onDismiss = { tuningBot = null },
            onSave = { lot, sl, tp ->
                onUpdateBotParameters(bot, lot, sl, tp)
                tuningBot = null
            }
        )
    }
}

@Composable
private fun RobotTabPill(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accent else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RobotTemplateCard(
    template: RobotTemplate,
    alreadyCloned: Boolean,
    isBusy: Boolean,
    onClone: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (alreadyCloned) EmeraldBull.copy(alpha = 0.4f) else HairlineBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("template_card_${template.id}"),
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
                Column(modifier = Modifier.weight(1f)) {
                    val assetColor = when (template.pair.assetClass) {
                        AssetClass.FOREX -> EmeraldBull
                        AssetClass.METALS -> AmberWarn
                        AssetClass.CRYPTO -> CyanAlgo
                        AssetClass.STOCKS -> VioletIndicator
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = assetColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = template.pair.assetClass.badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = assetColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = VioletIndicator.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = template.badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = VioletIndicator,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "${template.pair.symbol} • ${template.timeframe.label}",
                            style = MonoPriceStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = CyanAlgo
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SlateElevated
                ) {
                    Text(
                        text = template.strategy.shortCode,
                        style = MonoPriceStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = EmeraldBull,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }

            Text(
                text = template.summary,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateElevated)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BotMetric("WIN RATE", "${template.backtestWinRate}%", EmeraldBull)
                BotMetric("PROFIT FACTOR", "${template.profitFactor}", CyanAlgo)
                BotMetric("MAX DD", "${template.maxDrawdownPct}%", AmberWarn)
                BotMetric("LOT / SL / TP", "${template.lotSize}/${template.stopLossPips}/${template.takeProfitPips}", TextPrimary)
            }

            Button(
                onClick = onClone,
                enabled = !isBusy,
                shape = RoundedCornerShape(12.dp),
                colors = if (alreadyCloned) {
                    ButtonDefaults.buttonColors(containerColor = SlateElevated, contentColor = CyanAlgo)
                } else {
                    ButtonDefaults.buttonColors(containerColor = EmeraldBull, contentColor = ObsidianCanvas)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("clone_template_${template.id}")
            ) {
                Icon(
                    imageVector = if (alreadyCloned) Icons.Filled.CheckCircle else Icons.Filled.ContentCopy,
                    contentDescription = "Clone ${template.name}",
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (alreadyCloned) "Cloned • Clone Another Copy" else "Clone & Deploy Robot",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TradingBotCard(
    bot: TradingBot,
    signal: BotSignal?,
    onToggle: () -> Unit,
    onTune: () -> Unit,
    onDelete: () -> Unit,
    onOpenChart: () -> Unit
) {
    val isActive = bot.botStatus == BotStatus.ACTIVE
    val statusColor = if (isActive) EmeraldBull else TextMuted

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isActive) EmeraldBull.copy(alpha = 0.45f) else HairlineBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onOpenChart() }
            .testTag("bot_card_${bot.id}"),
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isActive) EmeraldBull.copy(alpha = 0.14f) else SlateElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SmartToy,
                            contentDescription = bot.name,
                            tint = statusColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = bot.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val botAssetColor = when (bot.forexPair.assetClass) {
                                AssetClass.FOREX -> EmeraldBull
                                AssetClass.METALS -> AmberWarn
                                AssetClass.CRYPTO -> CyanAlgo
                                AssetClass.STOCKS -> VioletIndicator
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = botAssetColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = bot.forexPair.assetClass.badge,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = botAssetColor,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                            Text(
                                text = "${bot.pair} • ${bot.timeframe}",
                                style = MonoPriceStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                color = CyanAlgo
                            )
                            Text(
                                text = bot.botStrategy.shortCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("toggle_bot_${bot.id}")
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isActive) "Pause ${bot.name}" else "Start ${bot.name}",
                            tint = if (isActive) AmberWarn else EmeraldBull
                        )
                    }
                    IconButton(
                        onClick = onTune,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("tune_bot_${bot.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Tune ${bot.name}",
                            tint = CyanAlgo
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_bot_${bot.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete ${bot.name}",
                            tint = TextMuted
                        )
                    }
                }
            }

            if (signal != null) {
                val sigColor = when (signal.direction) {
                    "BULLISH" -> EmeraldBull
                    "BEARISH" -> CrimsonBear
                    else -> TextSecondary
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateElevated)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isActive) sigColor else TextMuted)
                        )
                        Text(
                            text = if (isActive) signal.summary else "Engine paused • ${bot.botStrategy.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isActive) {
                        Text(
                            text = "${signal.direction} ${signal.confidencePct}%",
                            style = MonoPriceStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = sigColor
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val unit = bot.forexPair.assetClass.unitSuffix
                BotMetric("LOT / SL / TP", "${bot.lotSize} / ${bot.stopLossPips}$unit / ${bot.takeProfitPips}$unit", TextPrimary)
                BotMetric("TRADES (WIN%)", "${bot.totalTrades} (${String.format(Locale.US, "%.0f%%", bot.winRatePct)})", CyanAlgo)
                val pnlColor = if (bot.realizedPnl >= 0) EmeraldBull else CrimsonBear
                val sign = if (bot.realizedPnl >= 0) "+" else "-"
                BotMetric("NET PNL", String.format(Locale.US, "%s$%.2f", sign, kotlin.math.abs(bot.realizedPnl)), pnlColor)
            }
        }
    }
}

@Composable
private fun BotMetric(label: String, value: String, valueColor: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Text(
            text = value,
            style = MonoPriceStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = valueColor
        )
    }
}

@Composable
private fun TuneBotDialog(
    bot: TradingBot,
    onDismiss: () -> Unit,
    onSave: (Double, Long, Long) -> Unit
) {
    var lotSize by remember(bot.id) { mutableDoubleStateOf(bot.lotSize) }
    var slPips by remember(bot.id) { mutableLongStateOf(bot.stopLossPips) }
    var tpPips by remember(bot.id) { mutableLongStateOf(bot.takeProfitPips) }
    val rrRatio = (tpPips.toDouble() / slPips.coerceAtLeast(1L).toDouble()).roundTo(1)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateElevated,
        title = {
            Column {
                Text("Tune ${bot.name}", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                Text("${bot.pair} • R:R 1:$rrRatio", color = CyanAlgo, style = MonoPriceStyle.copy(fontSize = 12.sp))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Lot Size", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Text(String.format(Locale.US, "%.2f Lots", lotSize), style = MonoPriceStyle, color = EmeraldBull)
                    }
                    Slider(
                        value = lotSize.toFloat(),
                        onValueChange = { lotSize = it.toDouble().roundTo(2).coerceIn(0.05, 2.0) },
                        valueRange = 0.05f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = EmeraldBull, activeTrackColor = EmeraldBull)
                    )
                }
                Column {
                    Text("Stop Loss: ${slPips}p", style = MonoPriceStyle.copy(fontSize = 12.sp), color = CrimsonBear)
                    Slider(
                        value = slPips.toFloat(),
                        onValueChange = { slPips = it.roundToLong().coerceIn(8L, 150L) },
                        valueRange = 8f..150f,
                        colors = SliderDefaults.colors(thumbColor = CrimsonBear, activeTrackColor = CrimsonBear)
                    )
                }
                Column {
                    Text("Take Profit: ${tpPips}p", style = MonoPriceStyle.copy(fontSize = 12.sp), color = EmeraldBull)
                    Slider(
                        value = tpPips.toFloat(),
                        onValueChange = { tpPips = it.roundToLong().coerceIn(10L, 300L) },
                        valueRange = 10f..300f,
                        colors = SliderDefaults.colors(thumbColor = EmeraldBull, activeTrackColor = EmeraldBull)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(lotSize, slPips, tpPips) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldBull, contentColor = ObsidianCanvas)
            ) {
                Text("Apply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateBotBottomSheet(
    onDismiss: () -> Unit,
    onCreate: (String, ForexPair, BotStrategy, Timeframe, Double, Long, Long, Double, Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("Apex Quant Bot") }
    var selectedAssetClass by remember { mutableStateOf(AssetClass.FOREX) }
    var selectedPair by remember { mutableStateOf(ForexPair.EUR_USD) }
    var selectedStrategy by remember { mutableStateOf(BotStrategy.EMA_CROSSOVER) }
    var selectedTimeframe by remember { mutableStateOf(Timeframe.M5) }
    var lotSize by remember { mutableDoubleStateOf(0.20) }
    var slPips by remember { mutableLongStateOf(20L) }
    var tpPips by remember { mutableLongStateOf(40L) }
    var startNow by remember { mutableStateOf(true) }
    val rrRatio = (tpPips.toDouble() / slPips.coerceAtLeast(1L).toDouble()).roundTo(1)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Deploy New Trading Robot",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Text(
                    text = "R:R 1:$rrRatio",
                    style = MonoPriceStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    color = CyanAlgo
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 50) name = it },
                label = { Text("Robot Name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldBull,
                    unfocusedBorderColor = HairlineBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bot_name_input")
            )

            Text("Market Class", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssetClass.entries.forEach { asset ->
                    FilterChip(
                        selected = asset == selectedAssetClass,
                        onClick = {
                            selectedAssetClass = asset
                            selectedPair = ForexPair.byAssetClass(asset).first()
                        },
                        label = { Text(asset.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanAlgo.copy(alpha = 0.2f),
                            selectedLabelColor = CyanAlgo
                        )
                    )
                }
            }

            Text("Instrument (${selectedAssetClass.label})", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ForexPair.byAssetClass(selectedAssetClass).forEach { pair ->
                    FilterChip(
                        selected = pair == selectedPair,
                        onClick = { selectedPair = pair },
                        label = { Text(pair.symbol) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldBull.copy(alpha = 0.2f),
                            selectedLabelColor = EmeraldBull
                        )
                    )
                }
            }

            Text("Algorithmic Engine", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BotStrategy.entries.forEach { strat ->
                    val chosen = strat == selectedStrategy
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (chosen) CyanAlgo else HairlineBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedStrategy = strat },
                        shape = RoundedCornerShape(12.dp),
                        color = if (chosen) CyanAlgo.copy(alpha = 0.12f) else SlateElevated
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strat.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(
                                    "Backtest WR ${strat.expectedWinRate}%",
                                    style = MonoPriceStyle.copy(fontSize = 11.sp),
                                    color = EmeraldBull
                                )
                            }
                            Text(strat.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }

            Text("Signal Timeframe", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Timeframe.entries.forEach { tf ->
                    FilterChip(
                        selected = tf == selectedTimeframe,
                        onClick = { selectedTimeframe = tf },
                        label = { Text(tf.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanAlgo.copy(alpha = 0.2f),
                            selectedLabelColor = CyanAlgo
                        )
                    )
                }
            }

            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Position Lot Size", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text(String.format(Locale.US, "%.2f Lots", lotSize), style = MonoPriceStyle, color = EmeraldBull)
                }
                Slider(
                    value = lotSize.toFloat(),
                    onValueChange = { lotSize = it.toDouble().roundTo(2).coerceIn(0.05, 2.0) },
                    valueRange = 0.05f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = EmeraldBull, activeTrackColor = EmeraldBull)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val unit = selectedPair.assetClass.unitSuffix
                Column(modifier = Modifier.weight(1f)) {
                    Text("Stop Loss: ${slPips}$unit", style = MonoPriceStyle.copy(fontSize = 12.sp), color = CrimsonBear)
                    Slider(
                        value = slPips.toFloat(),
                        onValueChange = { slPips = it.roundToLong().coerceIn(8L, 100L) },
                        valueRange = 8f..100f,
                        colors = SliderDefaults.colors(thumbColor = CrimsonBear, activeTrackColor = CrimsonBear)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Take Profit: ${tpPips}$unit", style = MonoPriceStyle.copy(fontSize = 12.sp), color = EmeraldBull)
                    Slider(
                        value = tpPips.toFloat(),
                        onValueChange = { tpPips = it.roundToLong().coerceIn(10L, 200L) },
                        valueRange = 10f..200f,
                        colors = SliderDefaults.colors(thumbColor = EmeraldBull, activeTrackColor = EmeraldBull)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Activate Robot Immediately", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Switch(
                    checked = startNow,
                    onCheckedChange = { startNow = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = ObsidianCanvas, checkedTrackColor = EmeraldBull)
                )
            }

            Button(
                onClick = {
                    val finalName = name.trim().ifEmpty { "Quant Bot" }
                    onCreate(finalName, selectedPair, selectedStrategy, selectedTimeframe, lotSize, slPips, tpPips, 1.5, startNow)
                },
                enabled = name.trim().length >= 2,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldBull, contentColor = ObsidianCanvas),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("deploy_bot_submit_button")
            ) {
                Text("Deploy Trading Robot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}
