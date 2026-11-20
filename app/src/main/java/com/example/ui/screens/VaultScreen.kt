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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TraderAccount
import com.example.data.model.roundTo
import com.example.ui.AccountTelemetry
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
import java.util.Locale

@Composable
fun VaultScreen(
    userId: String,
    userDisplayName: String?,
    userEmail: String?,
    account: TraderAccount?,
    telemetry: AccountTelemetry,
    onSaveRiskSettings: (Long, Double, Boolean, Boolean) -> Unit,
    onResetDemoEquity: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var leverage by remember(account?.leverage) { mutableLongStateOf(account?.leverage ?: 100L) }
    var maxDrawdown by remember(account?.maxDailyDrawdownPct) {
        mutableDoubleStateOf(account?.maxDailyDrawdownPct ?: 5.0)
    }
    var requireConfirm by remember(account?.requireTradeConfirm) {
        mutableStateOf(account?.requireTradeConfirm ?: false)
    }
    var showResetConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Authenticated Identity Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EmeraldBull.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(EmeraldBull.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = "Verified Google identity",
                                tint = EmeraldBull,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userDisplayName ?: account?.displayName ?: "Authenticated Trader",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = userEmail ?: account?.email ?: "Google Auth Active",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyanAlgo
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EmeraldBull.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "OAUTH 2.0",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldBull,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateElevated)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("UID Fingerprint", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(
                            text = userId.take(12) + "…",
                            style = MonoPriceStyle.copy(fontSize = 11.sp),
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // 1b. Margin & Capital Utilization Card
        item {
            val utilization = if (telemetry.equity > 0.0) {
                (telemetry.usedMargin / telemetry.equity).toFloat().coerceIn(0f, 1f)
            } else 0f
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CAPITAL & MARGIN UTILIZATION",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanAlgo
                        )
                        Text(
                            text = if (telemetry.usedMargin > 0.0) {
                                String.format(Locale.US, "Level %.0f%%", telemetry.marginLevelPct)
                            } else {
                                "0% Used"
                            },
                            style = MonoPriceStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            color = EmeraldBull
                        )
                    }
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { utilization },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = if (utilization > 0.7f) CrimsonBear else EmeraldBull,
                        trackColor = SlateElevated
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = String.format(Locale.US, "Used $%,.2f", telemetry.usedMargin),
                            style = MonoPriceStyle.copy(fontSize = 11.sp),
                            color = TextSecondary
                        )
                        Text(
                            text = String.format(Locale.US, "Free $%,.2f", telemetry.freeMargin),
                            style = MonoPriceStyle.copy(fontSize = 11.sp),
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // 2. Risk Guardrails & Kill-Switch Controls
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Risk guardrails",
                            tint = CyanAlgo,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "RISK GUARDRAILS & KILL-SWITCH",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }

                    // Leverage Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Account Leverage", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(30L, 50L, 100L, 500L).forEach { lev ->
                                FilterChip(
                                    selected = leverage == lev,
                                    onClick = {
                                        leverage = lev
                                        onSaveRiskSettings(lev, maxDrawdown, requireConfirm, false)
                                    },
                                    label = { Text("1:$lev", style = MonoPriceStyle.copy(fontSize = 11.sp)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanAlgo.copy(alpha = 0.2f),
                                        selectedLabelColor = CyanAlgo
                                    ),
                                    modifier = Modifier.testTag("leverage_chip_$lev")
                                )
                            }
                        }
                    }

                    // Max Daily Drawdown Kill-Switch
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Max Drawdown Kill-Switch", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(
                                text = String.format(Locale.US, "%.1f%% (Now %.2f%%)", maxDrawdown, telemetry.drawdownPct),
                                style = MonoPriceStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                color = if (account?.killSwitchTriggered == true) CrimsonBear else AmberWarn
                            )
                        }
                        Slider(
                            value = maxDrawdown.toFloat(),
                            onValueChange = { maxDrawdown = it.toDouble().roundTo(1).coerceIn(1.0, 25.0) },
                            onValueChangeFinished = {
                                onSaveRiskSettings(leverage, maxDrawdown, requireConfirm, false)
                            },
                            valueRange = 1f..25f,
                            colors = SliderDefaults.colors(thumbColor = AmberWarn, activeTrackColor = AmberWarn)
                        )
                    }

                    if (account?.killSwitchTriggered == true) {
                        Button(
                            onClick = { onSaveRiskSettings(leverage, maxDrawdown, requireConfirm, true) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarn, contentColor = ObsidianCanvas),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("clear_kill_switch_button")
                        ) {
                            Text("Clear Kill-Switch & Unlock Bots", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Order Confirmation Guard
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Trade Confirmation Guard", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(
                                "Require modal confirmation before manual BUY/SELL fills.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = requireConfirm,
                            onCheckedChange = {
                                requireConfirm = it
                                onSaveRiskSettings(leverage, maxDrawdown, it, false)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianCanvas,
                                checkedTrackColor = EmeraldBull
                            ),
                            modifier = Modifier.testTag("trade_confirm_switch")
                        )
                    }
                }
            }
        }

        // 3. Session & Demo Account Controls
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SlateSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = "Session controls",
                            tint = EmeraldBull,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "VAULT & SESSION CONTROLS",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }

                    OutlinedButton(
                        onClick = { showResetConfirm = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAlgo),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("reset_demo_button")
                    ) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = "Reset demo equity", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Simulated Equity ($10,000)")
                    }

                    Button(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonBear, contentColor = TextPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out Securely", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = SlateElevated,
            title = { Text("Reset Simulated Equity?", color = TextPrimary) },
            text = {
                Text(
                    "This resets your account balance to $10,000.00, clears your trade history, and resets the drawdown kill-switch.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetDemoEquity()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAlgo, contentColor = ObsidianCanvas)
                ) {
                    Text("Reset Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
