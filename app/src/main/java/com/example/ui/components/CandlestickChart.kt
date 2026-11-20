package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MarketSnapshot
import com.example.data.model.TradeRecord
import com.example.data.model.TradeSide
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CrimsonBear
import com.example.ui.theme.CyanAlgo
import com.example.ui.theme.EmeraldBull
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MonoPriceStyle
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CandlestickChart(
    snapshot: MarketSnapshot,
    openPositionsOnPair: List<TradeRecord>,
    showEmaOverlay: Boolean,
    showBollingerOverlay: Boolean,
    showRsiPane: Boolean,
    modifier: Modifier = Modifier
) {
    var candleWindow by remember { mutableIntStateOf(52) }
    val candles = remember(snapshot.candles, candleWindow) {
        snapshot.candles.takeLast(candleWindow.coerceIn(16, snapshot.candles.size.coerceAtLeast(16)))
    }
    val visibleCount = candles.size
    if (visibleCount == 0) return

    val ema9 = remember(snapshot.ema9Series, visibleCount) { snapshot.ema9Series.takeLast(visibleCount) }
    val ema21 = remember(snapshot.ema21Series, visibleCount) { snapshot.ema21Series.takeLast(visibleCount) }
    val bbUpper = remember(snapshot.bbUpperSeries, visibleCount) { snapshot.bbUpperSeries.takeLast(visibleCount) }
    val bbLower = remember(snapshot.bbLowerSeries, visibleCount) { snapshot.bbLowerSeries.takeLast(visibleCount) }
    val rsiSeries = remember(snapshot.rsiSeries, visibleCount) { snapshot.rsiSeries.takeLast(visibleCount) }

    var inspectedIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val density = LocalDensity.current
    val axisWidthPx = with(density) { 74.dp.toPx() }

    val pulseTransition = rememberInfiniteTransition(label = "live_bid_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val inspectedCandle = inspectedIndex?.let { candles.getOrNull(it) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
            .background(SlateSurface, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Crosshair / OHLC Readout Strip + Zoom Controls
        val displayCandle = inspectedCandle ?: candles.last()
        val isBull = displayCandle.close >= displayCandle.open
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OhlcTag("O", snapshot.pair.formatPrice(displayCandle.open), TextSecondary)
                OhlcTag("H", snapshot.pair.formatPrice(displayCandle.high), EmeraldBull)
                OhlcTag("L", snapshot.pair.formatPrice(displayCandle.low), CrimsonBear)
                OhlcTag("C", snapshot.pair.formatPrice(displayCandle.close), if (isBull) EmeraldBull else CrimsonBear)
            }
            val barPips = snapshot.pair.pipDifference(displayCandle.open, displayCandle.close)
            val unit = snapshot.pair.assetClass.unitSuffix
            Text(
                text = if (inspectedCandle != null) {
                    "${timeFormatter.format(Date(inspectedCandle.timestampMillis))} (${String.format(Locale.US, "%+.1f%s", barPips, unit)})"
                } else {
                    "RSI ${snapshot.rsi14}"
                },
                style = MonoPriceStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                color = if (inspectedCandle != null) CyanAlgo else VioletIndicator
            )
        }

        // Main Interactive Canvas
        val chartHeight = if (showRsiPane) 288.dp else 232.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("candlestick_canvas")
                    .pointerInput(visibleCount, axisWidthPx) {
                        detectTapGestures(
                            onTap = { offset ->
                                val plotW = (size.width - axisWidthPx).coerceAtLeast(40f)
                                val idx = ((offset.x / plotW) * (visibleCount - 1))
                                    .roundToInt()
                                    .coerceIn(0, visibleCount - 1)
                                inspectedIndex = if (inspectedIndex == idx) null else idx
                            }
                        )
                    }
                    .pointerInput(visibleCount, axisWidthPx) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val plotW = (size.width - axisWidthPx).coerceAtLeast(40f)
                                inspectedIndex = ((offset.x / plotW) * (visibleCount - 1))
                                    .roundToInt()
                                    .coerceIn(0, visibleCount - 1)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val plotW = (size.width - axisWidthPx).coerceAtLeast(40f)
                                inspectedIndex = ((change.position.x / plotW) * (visibleCount - 1))
                                    .roundToInt()
                                    .coerceIn(0, visibleCount - 1)
                            },
                            onDragEnd = {
                                inspectedIndex = null
                            },
                            onDragCancel = {
                                inspectedIndex = null
                            }
                        )
                    }
            ) {
                val plotWidth = (size.width - axisWidthPx).coerceAtLeast(80f)
                val timeAxisHeight = 18.dp.toPx()
                val rsiHeight = if (showRsiPane) (size.height - timeAxisHeight) * 0.24f else 0f
                val rsiGap = if (showRsiPane) 10.dp.toPx() else 0f
                val mainHeight = size.height - timeAxisHeight - rsiHeight - rsiGap
                val volumeHeight = mainHeight * 0.16f
                val topPad = 8.dp.toPx()
                val pricePlotHeight = (mainHeight - topPad * 2f).coerceAtLeast(40f)

                var rawMin = candles.minOf { it.low }
                var rawMax = candles.maxOf { it.high }
                if (showBollingerOverlay && bbUpper.isNotEmpty() && bbLower.isNotEmpty()) {
                    rawMin = min(rawMin, bbLower.min())
                    rawMax = max(rawMax, bbUpper.max())
                }
                openPositionsOnPair.forEach { pos ->
                    rawMin = min(rawMin, pos.entryPrice)
                    rawMax = max(rawMax, pos.entryPrice)
                    if (pos.stopLoss > 0.0) rawMin = min(rawMin, pos.stopLoss)
                    if (pos.takeProfit > 0.0) rawMax = max(rawMax, pos.takeProfit)
                }
                val pad = ((rawMax - rawMin) * 0.10).coerceAtLeast(snapshot.pair.pipSize * 4)
                val minPrice = rawMin - pad
                val maxPrice = rawMax + pad
                val priceRange = (maxPrice - minPrice).coerceAtLeast(snapshot.pair.pipSize)

                fun yForPrice(price: Double): Float {
                    val norm = ((price - minPrice) / priceRange).toFloat().coerceIn(0f, 1f)
                    return topPad + pricePlotHeight * (1f - norm)
                }

                val stepX = plotWidth / visibleCount.toFloat()
                val bodyWidth = (stepX * 0.62f).coerceIn(2.dp.toPx(), 10.dp.toPx())
                val wickStroke = 1.2.dp.toPx().coerceAtLeast(1.5f)
                val axisTextOffset = 5.dp.toPx()

                val axisLabelStyle = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 9.sp,
                    color = TextMuted
                )

                // Horizontal Price Grid + Right Axis Labels
                val gridLines = 4
                for (g in 0..gridLines) {
                    val frac = g / gridLines.toFloat()
                    val y = topPad + pricePlotHeight * frac
                    val gridPrice = maxPrice - (priceRange * frac)
                    drawLine(
                        color = HairlineBorder.copy(alpha = 0.55f),
                        start = Offset(0f, y),
                        end = Offset(plotWidth, y),
                        strokeWidth = 0.8.dp.toPx()
                    )
                    val labelLayout = textMeasurer.measure(
                        text = snapshot.pair.formatPrice(gridPrice),
                        style = axisLabelStyle
                    )
                    drawText(
                        textLayoutResult = labelLayout,
                        topLeft = Offset(
                            plotWidth + axisTextOffset,
                            (y - labelLayout.size.height / 2f).coerceIn(0f, mainHeight - labelLayout.size.height)
                        )
                    )
                }

                // Vertical Time Grid + Bottom X-Axis Timestamps
                val labelStep = max(1, visibleCount / 4)
                for (i in 0 until visibleCount step labelStep) {
                    val x = i * stepX + stepX / 2f
                    drawLine(
                        color = HairlineBorder.copy(alpha = 0.35f),
                        start = Offset(x, 0f),
                        end = Offset(x, mainHeight),
                        strokeWidth = 0.8.dp.toPx()
                    )
                    val tLayout = textMeasurer.measure(
                        text = timeFormatter.format(Date(candles[i].timestampMillis)),
                        style = axisLabelStyle
                    )
                    drawText(
                        textLayoutResult = tLayout,
                        topLeft = Offset(
                            (x - tLayout.size.width / 2f).coerceIn(0f, plotWidth - tLayout.size.width),
                            mainHeight + 2.dp.toPx()
                        )
                    )
                }

                // Bollinger Band Fill & Strokes
                if (showBollingerOverlay && bbUpper.size == visibleCount && bbLower.size == visibleCount) {
                    val bandPath = Path()
                    val upperPath = Path()
                    val lowerPath = Path()
                    for (i in 0 until visibleCount) {
                        val x = i * stepX + stepX / 2f
                        val yU = yForPrice(bbUpper[i])
                        val yL = yForPrice(bbLower[i])
                        if (i == 0) {
                            upperPath.moveTo(x, yU)
                            lowerPath.moveTo(x, yL)
                            bandPath.moveTo(x, yU)
                        } else {
                            upperPath.lineTo(x, yU)
                            lowerPath.lineTo(x, yL)
                            bandPath.lineTo(x, yU)
                        }
                    }
                    for (i in visibleCount - 1 downTo 0) {
                        val x = i * stepX + stepX / 2f
                        bandPath.lineTo(x, yForPrice(bbLower[i]))
                    }
                    bandPath.close()
                    drawPath(bandPath, color = CyanAlgo.copy(alpha = 0.08f))
                    drawPath(upperPath, color = CyanAlgo.copy(alpha = 0.45f), style = Stroke(width = 1.1.dp.toPx()))
                    drawPath(lowerPath, color = CyanAlgo.copy(alpha = 0.45f), style = Stroke(width = 1.1.dp.toPx()))
                }

                // Volume Bars at base of main chart
                val maxVol = (candles.maxOf { it.volume }).coerceAtLeast(1)
                for (i in 0 until visibleCount) {
                    val c = candles[i]
                    val x = i * stepX + stepX / 2f
                    val volH = (c.volume.toFloat() / maxVol) * volumeHeight
                    val barColor = if (c.close >= c.open) EmeraldBull.copy(alpha = 0.16f) else CrimsonBear.copy(alpha = 0.16f)
                    drawRect(
                        color = barColor,
                        topLeft = Offset(x - bodyWidth / 2f, mainHeight - volH),
                        size = Size(bodyWidth, volH)
                    )
                }

                // Candlesticks (Wick + Body)
                val minBodyH = 1.5.dp.toPx()
                for (i in 0 until visibleCount) {
                    val c = candles[i]
                    val cx = i * stepX + stepX / 2f
                    val bull = c.close >= c.open
                    val color = if (bull) EmeraldBull else CrimsonBear

                    val yHigh = yForPrice(c.high)
                    val yLow = yForPrice(c.low)
                    val yOpen = yForPrice(c.open)
                    val yClose = yForPrice(c.close)

                    drawLine(
                        color = color,
                        start = Offset(cx, yHigh),
                        end = Offset(cx, yLow),
                        strokeWidth = wickStroke,
                        cap = StrokeCap.Round
                    )

                    val topY = min(yOpen, yClose)
                    val bodyH = abs(yClose - yOpen).coerceAtLeast(minBodyH)
                    drawRect(
                        color = color,
                        topLeft = Offset(cx - bodyWidth / 2f, topY),
                        size = Size(bodyWidth, bodyH)
                    )
                }

                // EMA 9 & EMA 21 Curves
                if (showEmaOverlay && ema9.size == visibleCount && ema21.size == visibleCount) {
                    val ema9Path = Path()
                    val ema21Path = Path()
                    for (i in 0 until visibleCount) {
                        val x = i * stepX + stepX / 2f
                        val y9 = yForPrice(ema9[i])
                        val y21 = yForPrice(ema21[i])
                        if (i == 0) {
                            ema9Path.moveTo(x, y9)
                            ema21Path.moveTo(x, y21)
                        } else {
                            ema9Path.lineTo(x, y9)
                            ema21Path.lineTo(x, y21)
                        }
                    }
                    val emaStroke = 1.6.dp.toPx()
                    drawPath(ema9Path, color = AmberWarn, style = Stroke(width = emaStroke, cap = StrokeCap.Round))
                    drawPath(ema21Path, color = CyanAlgo, style = Stroke(width = emaStroke, cap = StrokeCap.Round))
                }

                // Open Position Entry / SL / TP Overlay Lines with Tags
                val dashPx = 5.dp.toPx()
                val gapPx = 4.dp.toPx()
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, gapPx), 0f)
                val tagLabelStyle = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = ObsidianCanvas
                )
                openPositionsOnPair.take(2).forEach { pos ->
                    val entryY = yForPrice(pos.entryPrice)
                    val sideColor = if (pos.tradeSide == TradeSide.BUY) EmeraldBull else CrimsonBear
                    drawLine(
                        color = sideColor.copy(alpha = 0.85f),
                        start = Offset(0f, entryY),
                        end = Offset(plotWidth, entryY),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = dashEffect
                    )
                    val entryBadge = textMeasurer.measure("${pos.side} ${pos.lotSize}L", tagLabelStyle)
                    drawRoundRect(
                        color = sideColor.copy(alpha = 0.9f),
                        topLeft = Offset(4.dp.toPx(), (entryY - entryBadge.size.height - 4.dp.toPx()).coerceAtLeast(0f)),
                        size = Size(entryBadge.size.width + 8.dp.toPx(), entryBadge.size.height + 3.dp.toPx()),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                    drawText(
                        textLayoutResult = entryBadge,
                        topLeft = Offset(8.dp.toPx(), (entryY - entryBadge.size.height - 2.5.dp.toPx()).coerceAtLeast(1.dp.toPx()))
                    )
                    if (pos.takeProfit > 0.0) {
                        val tpY = yForPrice(pos.takeProfit)
                        drawLine(
                            color = EmeraldBull.copy(alpha = 0.65f),
                            start = Offset(0f, tpY),
                            end = Offset(plotWidth, tpY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                        val tpBadge = textMeasurer.measure("TP", tagLabelStyle)
                        drawRoundRect(
                            color = EmeraldBull.copy(alpha = 0.85f),
                            topLeft = Offset(4.dp.toPx(), (tpY - tpBadge.size.height / 2f).coerceIn(0f, mainHeight - tpBadge.size.height)),
                            size = Size(tpBadge.size.width + 6.dp.toPx(), tpBadge.size.height + 2.dp.toPx()),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                        drawText(
                            textLayoutResult = tpBadge,
                            topLeft = Offset(7.dp.toPx(), (tpY - tpBadge.size.height / 2f + 1.dp.toPx()).coerceIn(0f, mainHeight - tpBadge.size.height))
                        )
                    }
                    if (pos.stopLoss > 0.0) {
                        val slY = yForPrice(pos.stopLoss)
                        drawLine(
                            color = CrimsonBear.copy(alpha = 0.65f),
                            start = Offset(0f, slY),
                            end = Offset(plotWidth, slY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                        val slBadge = textMeasurer.measure("SL", tagLabelStyle)
                        drawRoundRect(
                            color = CrimsonBear.copy(alpha = 0.85f),
                            topLeft = Offset(4.dp.toPx(), (slY - slBadge.size.height / 2f).coerceIn(0f, mainHeight - slBadge.size.height)),
                            size = Size(slBadge.size.width + 6.dp.toPx(), slBadge.size.height + 2.dp.toPx()),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                        drawText(
                            textLayoutResult = slBadge,
                            topLeft = Offset(7.dp.toPx(), (slY - slBadge.size.height / 2f + 1.dp.toPx()).coerceIn(0f, mainHeight - slBadge.size.height))
                        )
                    }
                }

                // Live Bid Price Horizontal Tag + Pulsing Beacon
                val liveY = yForPrice(snapshot.bid)
                val liveBull = candles.last().close >= candles.last().open
                val liveColor = if (liveBull) EmeraldBull else CrimsonBear
                val lastCandleX = (visibleCount - 1) * stepX + stepX / 2f

                drawCircle(
                    color = liveColor.copy(alpha = pulseAlpha * 0.35f),
                    radius = 7.dp.toPx(),
                    center = Offset(lastCandleX, liveY)
                )
                drawCircle(
                    color = liveColor,
                    radius = 2.8.dp.toPx(),
                    center = Offset(lastCandleX, liveY)
                )

                drawLine(
                    color = liveColor.copy(alpha = 0.75f),
                    start = Offset(0f, liveY),
                    end = Offset(plotWidth, liveY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()), 0f)
                )
                val liveLabel = textMeasurer.measure(
                    text = snapshot.pair.formatPrice(snapshot.bid),
                    style = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = ObsidianCanvas
                    )
                )
                val tagH = liveLabel.size.height + 6.dp.toPx()
                val tagTop = (liveY - tagH / 2f).coerceIn(0f, mainHeight - tagH)
                drawRoundRect(
                    color = liveColor,
                    topLeft = Offset(plotWidth + 2.dp.toPx(), tagTop),
                    size = Size(axisWidthPx - 4.dp.toPx(), tagH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawText(
                    textLayoutResult = liveLabel,
                    topLeft = Offset(plotWidth + axisTextOffset, tagTop + 3.dp.toPx())
                )

                // Interactive Crosshair
                inspectedIndex?.let { idx ->
                    val cx = idx * stepX + stepX / 2f
                    val cy = yForPrice(candles[idx].close)
                    drawLine(
                        color = TextSecondary.copy(alpha = 0.8f),
                        start = Offset(cx, 0f),
                        end = Offset(cx, mainHeight),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )
                    drawLine(
                        color = TextSecondary.copy(alpha = 0.8f),
                        start = Offset(0f, cy),
                        end = Offset(plotWidth, cy),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )
                    drawCircle(color = CyanAlgo, radius = 3.5.dp.toPx(), center = Offset(cx, cy))
                }

                // RSI(14) Oscillator Sub-Pane
                if (showRsiPane && rsiSeries.size == visibleCount) {
                    val rsiTop = mainHeight + timeAxisHeight + rsiGap
                    drawLine(
                        color = HairlineBorder,
                        start = Offset(0f, rsiTop),
                        end = Offset(plotWidth, rsiTop),
                        strokeWidth = 0.8.dp.toPx()
                    )
                    fun yForRsi(v: Double): Float {
                        val norm = ((v - 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
                        return rsiTop + rsiHeight * (1f - norm)
                    }
                    drawLine(
                        color = CrimsonBear.copy(alpha = 0.35f),
                        start = Offset(0f, yForRsi(70.0)),
                        end = Offset(plotWidth, yForRsi(70.0)),
                        strokeWidth = 0.8.dp.toPx(),
                        pathEffect = dashEffect
                    )
                    drawLine(
                        color = EmeraldBull.copy(alpha = 0.35f),
                        start = Offset(0f, yForRsi(30.0)),
                        end = Offset(plotWidth, yForRsi(30.0)),
                        strokeWidth = 0.8.dp.toPx(),
                        pathEffect = dashEffect
                    )
                    val rsiPath = Path()
                    val rsiFill = Path()
                    for (i in 0 until visibleCount) {
                        val x = i * stepX + stepX / 2f
                        val y = yForRsi(rsiSeries[i])
                        if (i == 0) {
                            rsiPath.moveTo(x, y)
                            rsiFill.moveTo(x, rsiTop + rsiHeight)
                            rsiFill.lineTo(x, y)
                        } else {
                            rsiPath.lineTo(x, y)
                            rsiFill.lineTo(x, y)
                        }
                    }
                    rsiFill.lineTo((visibleCount - 1) * stepX + stepX / 2f, rsiTop + rsiHeight)
                    rsiFill.close()
                    drawPath(
                        rsiFill,
                        brush = Brush.verticalGradient(
                            colors = listOf(VioletIndicator.copy(alpha = 0.22f), Color.Transparent),
                            startY = rsiTop,
                            endY = rsiTop + rsiHeight
                        )
                    )
                    drawPath(rsiPath, color = VioletIndicator, style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round))

                    drawText(
                        textMeasurer = textMeasurer,
                        text = "RSI ${snapshot.rsi14}",
                        topLeft = Offset(plotWidth + axisTextOffset, rsiTop + 2.dp.toPx()),
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VioletIndicator
                        )
                    )
                }
            }
        }

        // Bottom Legend & Zoom Controls Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showEmaOverlay) {
                    LegendPill("EMA 9", AmberWarn)
                    LegendPill("EMA 21", CyanAlgo)
                }
                if (showBollingerOverlay) {
                    LegendPill("BB", CyanAlgo.copy(alpha = 0.7f))
                }
                if (showRsiPane) {
                    LegendPill("RSI", VioletIndicator)
                }
                if (openPositionsOnPair.isNotEmpty()) {
                    LegendPill("${openPositionsOnPair.size} Pos", EmeraldBull)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { candleWindow = (candleWindow - 12).coerceAtLeast(24) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chart_zoom_in_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ZoomIn,
                            contentDescription = "Zoom in chart",
                            tint = CyanAlgo,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { candleWindow = (candleWindow + 12).coerceAtMost(90) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chart_zoom_out_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ZoomOut,
                            contentDescription = "Zoom out chart",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OhlcTag(label: String, value: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Text(
            text = value,
            style = MonoPriceStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = color
        )
    }
}

@Composable
private fun LegendPill(text: String, dotColor: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = ObsidianCanvas
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = dotColor
            )
        }
    }
}
