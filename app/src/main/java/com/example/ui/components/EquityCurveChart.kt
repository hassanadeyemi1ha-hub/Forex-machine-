package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TradeRecord
import com.example.data.model.TradeStatus
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MonoPriceStyle
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun EquityCurveChart(
    startingBalance: Double,
    currentEquity: Double,
    closedTrades: List<TradeRecord>,
    modifier: Modifier = Modifier
) {
    val points = remember(startingBalance, currentEquity, closedTrades) {
        val chronological = closedTrades
            .filter { it.tradeStatus == TradeStatus.CLOSED }
            .reversed()
        val series = mutableListOf(startingBalance)
        var running = startingBalance
        chronological.forEach { t ->
            running += t.realizedPnl
            series.add(running)
        }
        if (series.size == 1) {
            series.add(currentEquity)
        } else {
            series[series.lastIndex] = currentEquity
        }
        series.toList()
    }

    val netDelta = currentEquity - startingBalance
    val isPositive = netDelta >= 0.0
    val curveColor = if (isPositive) EmeraldBull else CrimsonBear

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
            .background(SlateSurface, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "EQUITY CURVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Text(
                    text = String.format(Locale.US, "$%,.2f", currentEquity),
                    style = MonoPriceStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }
            val sign = if (isPositive) "+" else "-"
            Text(
                text = String.format(Locale.US, "%s$%,.2f", sign, kotlin.math.abs(netDelta)),
                style = MonoPriceStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = curveColor
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
        ) {
            val minVal = min(points.min(), startingBalance) - 25.0
            val maxVal = max(points.max(), startingBalance) + 25.0
            val range = (maxVal - minVal).coerceAtLeast(1.0)
            val padY = 6.dp.toPx()
            val plotH = (size.height - padY * 2f).coerceAtLeast(20f)

            fun yFor(v: Double): Float {
                val norm = ((v - minVal) / range).toFloat().coerceIn(0f, 1f)
                return padY + plotH * (1f - norm)
            }

            // Subtle horizontal gridlines
            for (g in 1..3) {
                val gy = padY + plotH * (g / 4f)
                drawLine(
                    color = HairlineBorder.copy(alpha = 0.45f),
                    start = Offset(0f, gy),
                    end = Offset(size.width, gy),
                    strokeWidth = 0.8.dp.toPx()
                )
            }

            // Baseline starting equity line
            val baseY = yFor(startingBalance)
            val dashPx = 4.dp.toPx()
            val gapPx = 3.dp.toPx()
            drawLine(
                color = TextMuted.copy(alpha = 0.55f),
                start = Offset(0f, baseY),
                end = Offset(size.width, baseY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, gapPx), 0f)
            )

            val strokePath = Path()
            val fillPath = Path()
            val stepX = if (points.size > 1) size.width / (points.size - 1).toFloat() else size.width

            points.forEachIndexed { idx, value ->
                val x = idx * stepX
                val y = yFor(value)
                if (idx == 0) {
                    strokePath.moveTo(x, y)
                    fillPath.moveTo(x, size.height)
                    fillPath.lineTo(x, y)
                } else {
                    strokePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(size.width, size.height)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(curveColor.copy(alpha = 0.28f), Color.Transparent)
                )
            )
            drawPath(
                path = strokePath,
                color = curveColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Final pulsing endpoint
            val lastY = yFor(points.last())
            val endX = (size.width - 4.dp.toPx()).coerceAtLeast(0f)
            drawCircle(
                color = curveColor.copy(alpha = 0.25f),
                radius = 6.dp.toPx(),
                center = Offset(endX, lastY)
            )
            drawCircle(
                color = curveColor,
                radius = 3.dp.toPx(),
                center = Offset(endX, lastY)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Baseline: $${String.format(Locale.US, "%,.0f", startingBalance)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = "${closedTrades.size} settled trades",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}
