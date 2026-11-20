package com.example.data.market

import com.example.data.model.BotSignal
import com.example.data.model.BotStrategy
import com.example.data.model.Candle
import com.example.data.model.ForexPair
import com.example.data.model.MarketSnapshot
import com.example.data.model.Timeframe
import com.example.data.model.TradeSide
import com.example.data.model.TradingBot
import com.example.data.model.roundTo
import com.example.data.remote.CryptoSpotService
import com.example.data.remote.FrankfurterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class MarketDataEngine(
    private val fxService: FrankfurterService = FrankfurterService.create(),
    private val cryptoService: CryptoSpotService = CryptoSpotService.create()
) {
    private val random = Random(System.currentTimeMillis())
    private val candleBuffers = mutableMapOf<Pair<ForexPair, Timeframe>, MutableList<Candle>>()
    private val anchorPrices = mutableMapOf<ForexPair, Double>()
    private var liveSpotAnchored = false
    private var tickStep = 0L

    suspend fun fetchLiveSpotAnchors(): Boolean = withContext(Dispatchers.IO) {
        coroutineScope {
            val fxJob = async {
                try {
                    val r = fxService.getLatestRates().rates
                    r["EUR"]?.takeIf { it > 0.0 }?.let { anchorPrices[ForexPair.EUR_USD] = 1.0 / it }
                    r["GBP"]?.takeIf { it > 0.0 }?.let { anchorPrices[ForexPair.GBP_USD] = 1.0 / it }
                    r["AUD"]?.takeIf { it > 0.0 }?.let { anchorPrices[ForexPair.AUD_USD] = 1.0 / it }
                    r["JPY"]?.takeIf { it > 0.0 }?.let { anchorPrices[ForexPair.USD_JPY] = it }
                    r["CHF"]?.takeIf { it > 0.0 }?.let { anchorPrices[ForexPair.USD_CHF] = it }
                    r["CAD"]?.takeIf { it > 0.0 }?.let { anchorPrices[ForexPair.USD_CAD] = it }
                    r.isNotEmpty()
                } catch (_: Exception) {
                    false
                }
            }
            val cryptoJob = async {
                try {
                    val c = cryptoService.getSpotPrices()
                    c["bitcoin"]?.get("usd")?.takeIf { it > 0.0 }?.let { anchorPrices[ForexPair.BTC_USD] = it }
                    c["ethereum"]?.get("usd")?.takeIf { it > 0.0 }?.let { anchorPrices[ForexPair.ETH_USD] = it }
                    c["solana"]?.get("usd")?.takeIf { it > 0.0 }?.let { anchorPrices[ForexPair.SOL_USD] = it }
                    c["ripple"]?.get("usd")?.takeIf { it > 0.0 }?.let { anchorPrices[ForexPair.XRP_USD] = it }
                    c.isNotEmpty()
                } catch (_: Exception) {
                    false
                }
            }
            val fxOk = fxJob.await()
            val cryptoOk = cryptoJob.await()
            liveSpotAnchored = fxOk || cryptoOk
            liveSpotAnchored
        }
    }

    fun streamMarket(timeframeProvider: () -> Timeframe): Flow<Map<ForexPair, MarketSnapshot>> = flow {
        // Initialize candle buffers for all pairs
        ForexPair.entries.forEach { pair ->
            val anchor = anchorPrices[pair] ?: pair.seedPrice
            anchorPrices.putIfAbsent(pair, anchor)
        }

        while (true) {
            tickStep++
            val activeTimeframe = timeframeProvider()
            val now = System.currentTimeMillis()
            val snapshots = mutableMapOf<ForexPair, MarketSnapshot>()

            for (pair in ForexPair.entries) {
                val key = pair to activeTimeframe
                val buffer = candleBuffers.getOrPut(key) {
                    buildInitialHistory(pair, activeTimeframe, now)
                }

                // Micro-tick price movement (mean-reverting Brownian + cycle wave)
                val lastCandle = buffer.last()
                val anchor = anchorPrices[pair] ?: pair.seedPrice
                val waveBias = sin((tickStep + pair.ordinal * 7) * 0.14) * pair.pipSize * 1.15
                val meanRevert = (anchor - lastCandle.close) * 0.015
                val noise = (random.nextDouble() - 0.5) * pair.pipSize * 4.4
                val nextClose = (lastCandle.close + waveBias + meanRevert + noise)
                    .coerceAtLeast(pair.pipSize * 10)
                    .roundTo(pair.decimals)

                // Every 8 ticks (~6s), roll into a fresh candle so charts feel alive in real time
                if (tickStep % 8L == 0L) {
                    val newCandle = Candle(
                        timestampMillis = now,
                        open = lastCandle.close,
                        high = max(lastCandle.close, nextClose),
                        low = min(lastCandle.close, nextClose),
                        close = nextClose,
                        volume = 120 + random.nextInt(480)
                    )
                    buffer.add(newCandle)
                    if (buffer.size > 90) {
                        buffer.removeAt(0)
                    }
                } else {
                    val updated = lastCandle.copy(
                        high = max(lastCandle.high, nextClose),
                        low = min(lastCandle.low, nextClose),
                        close = nextClose,
                        volume = lastCandle.volume + random.nextInt(12, 48)
                    )
                    buffer[buffer.lastIndex] = updated
                }

                snapshots[pair] = buildSnapshot(pair, buffer.toList())
            }

            emit(snapshots)
            delay(750L)
        }
    }

    private fun buildInitialHistory(
        pair: ForexPair,
        timeframe: Timeframe,
        nowMillis: Long
    ): MutableList<Candle> {
        val count = 72
        val stepMillis = timeframe.secondsPerCandle * 1000L
        val targetEnd = anchorPrices[pair] ?: pair.seedPrice
        val volatilityPips = when (timeframe) {
            Timeframe.M1 -> 3.5
            Timeframe.M5 -> 6.0
            Timeframe.M15 -> 10.0
            Timeframe.H1 -> 16.0
            Timeframe.H4 -> 26.0
            Timeframe.D1 -> 42.0
        }

        val closes = MutableList(count) { 0.0 }
        var price = targetEnd - (random.nextDouble(-12.0, 12.0) * pair.pipSize)
        for (i in 0 until count) {
            val drift = (targetEnd - price) * 0.06
            val wave = sin(i * 0.25 + pair.ordinal) * pair.pipSize * (volatilityPips * 0.35)
            val shock = random.nextDouble(-1.0, 1.0) * pair.pipSize * volatilityPips * 0.65
            price = (price + drift + wave + shock).coerceAtLeast(pair.pipSize * 10)
            closes[i] = price
        }
        // Smoothly pull final close near real anchor price
        val offset = targetEnd - closes.last()
        val list = mutableListOf<Candle>()
        var prevClose = (closes.first() + offset * 0.2).roundTo(pair.decimals)
        for (i in 0 until count) {
            val t = nowMillis - (count - 1 - i) * stepMillis
            val close = (closes[i] + offset * (i.toDouble() / count)).roundTo(pair.decimals)
            val open = prevClose
            val wickUp = random.nextDouble(0.2, 1.0) * pair.pipSize * (volatilityPips * 0.45)
            val wickDown = random.nextDouble(0.2, 1.0) * pair.pipSize * (volatilityPips * 0.45)
            val high = (max(open, close) + wickUp).roundTo(pair.decimals)
            val low = (min(open, close) - wickDown).roundTo(pair.decimals)
            list.add(
                Candle(
                    timestampMillis = t,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = 180 + random.nextInt(900)
                )
            )
            prevClose = close
        }
        return list
    }

    private fun buildSnapshot(pair: ForexPair, candles: List<Candle>): MarketSnapshot {
        val closes = candles.map { it.close }
        val ema9 = computeEma(closes, 9)
        val ema21 = computeEma(closes, 21)
        val ema12 = computeEma(closes, 12)
        val ema26 = computeEma(closes, 26)
        val macdLine = ema12.zip(ema26) { a, b -> a - b }
        val macdSignal = computeEma(macdLine, 9)
        val macdHist = (macdLine.lastOrNull() ?: 0.0) - (macdSignal.lastOrNull() ?: 0.0)
        val (bbUpper, bbLower) = computeBollinger(closes, 20, 2.0)
        val rsiSeries = computeRsiSeries(closes, 14)

        val currentBid = candles.last().close
        val spreadJitter = (random.nextDouble(-0.15, 0.2) + pair.typicalSpreadPips).coerceAtLeast(0.4)
        val currentAsk = (currentBid + spreadJitter * pair.pipSize).roundTo(pair.decimals)
        val openRef = candles.first().open
        val changePct = if (openRef > 0.0) ((currentBid - openRef) / openRef) * 100.0 else 0.0

        return MarketSnapshot(
            pair = pair,
            bid = currentBid,
            ask = currentAsk,
            spreadPips = spreadJitter.roundTo(1),
            changePct = changePct.roundTo(2),
            high24h = candles.maxOf { it.high },
            low24h = candles.minOf { it.low },
            rsi14 = (rsiSeries.lastOrNull() ?: 50.0).roundTo(1),
            ema9 = (ema9.lastOrNull() ?: currentBid).roundTo(pair.decimals),
            ema21 = (ema21.lastOrNull() ?: currentBid).roundTo(pair.decimals),
            macdHistogram = macdHist,
            candles = candles,
            ema9Series = ema9,
            ema21Series = ema21,
            bbUpperSeries = bbUpper,
            bbLowerSeries = bbLower,
            rsiSeries = rsiSeries,
            isLiveSpotAnchored = liveSpotAnchored
        )
    }

    fun evaluateBotSignal(bot: TradingBot, snapshot: MarketSnapshot): BotSignal {
        val u = snapshot.pair.assetClass.unitSuffix
        val emaDiffPips = (snapshot.ema9 - snapshot.ema21) / snapshot.pair.pipSize
        val rsi = snapshot.rsi14
        val lastCandle = snapshot.candles.lastOrNull()
        val prevUpper = snapshot.bbUpperSeries.lastOrNull() ?: snapshot.bid
        val prevLower = snapshot.bbLowerSeries.lastOrNull() ?: snapshot.bid

        return when (bot.botStrategy) {
            BotStrategy.EMA_CROSSOVER -> {
                val conf = (55 + min(40, abs(emaDiffPips * 6.5).toInt())).coerceIn(50, 96)
                when {
                    emaDiffPips > 1.2 -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "BULLISH", conf, "EMA(9) +${String.format(Locale.US, "%.1f", emaDiffPips)}$u above EMA(21)"
                    )
                    emaDiffPips < -1.2 -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "BEARISH", conf, "EMA(9) ${String.format(Locale.US, "%.1f", emaDiffPips)}$u below EMA(21)"
                    )
                    else -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "NEUTRAL", 48, "EMAs converging (${String.format(Locale.US, "%.1f", emaDiffPips)}$u)"
                    )
                }
            }
            BotStrategy.RSI_REVERSAL -> {
                when {
                    rsi <= 38.0 || snapshot.bid <= prevLower -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "BULLISH", (68 + ((40.0 - rsi).coerceAtLeast(0.0) * 1.1).toInt()).coerceIn(60, 95),
                        "RSI(${rsi}) oversold rebound zone"
                    )
                    rsi >= 62.0 || snapshot.bid >= prevUpper -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "BEARISH", (68 + ((rsi - 60.0).coerceAtLeast(0.0) * 1.1).toInt()).coerceIn(60, 95),
                        "RSI(${rsi}) overbought fade zone"
                    )
                    else -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "NEUTRAL", 45, "RSI(${rsi}) inside equilibrium band"
                    )
                }
            }
            BotStrategy.BREAKOUT_GRID -> {
                val lookback = snapshot.candles.dropLast(1).takeLast(12)
                val rangeHigh = lookback.maxOfOrNull { it.high } ?: snapshot.bid
                val rangeLow = lookback.minOfOrNull { it.low } ?: snapshot.bid
                when {
                    snapshot.bid > rangeHigh -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "BULLISH", 86, "Upside 12-bar breakout above ${snapshot.pair.formatPrice(rangeHigh)}"
                    )
                    snapshot.bid < rangeLow -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "BEARISH", 86, "Downside 12-bar breakdown below ${snapshot.pair.formatPrice(rangeLow)}"
                    )
                    else -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "NEUTRAL", 52, "Coiling inside ${snapshot.pair.formatPrice(rangeLow)}–${snapshot.pair.formatPrice(rangeHigh)}"
                    )
                }
            }
            BotStrategy.MACD_MOMENTUM -> {
                val histPips = snapshot.macdHistogram / snapshot.pair.pipSize
                val conf = (58 + min(36, abs(histPips * 9.0).toInt())).coerceIn(52, 94)
                when {
                    histPips > 0.6 && (lastCandle?.close ?: 0.0) >= (lastCandle?.open ?: 0.0) -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "BULLISH", conf, "MACD histogram expanding (+${String.format(Locale.US, "%.2f", histPips)}$u)"
                    )
                    histPips < -0.6 && (lastCandle?.close ?: 0.0) <= (lastCandle?.open ?: 0.0) -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "BEARISH", conf, "MACD bearish momentum (${String.format(Locale.US, "%.2f", histPips)}$u)"
                    )
                    else -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "NEUTRAL", 49, "MACD momentum flat"
                    )
                }
            }
            BotStrategy.BOLLINGER_SQUEEZE -> {
                val bandWidthPips = (prevUpper - prevLower) / snapshot.pair.pipSize
                val isCompressed = bandWidthPips < 18.0
                when {
                    snapshot.bid >= prevUpper && rsi >= 54.0 -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "BULLISH", if (isCompressed) 89 else 74,
                        "BB squeeze upper breakout (width ${String.format(Locale.US, "%.1f", bandWidthPips)}$u)"
                    )
                    snapshot.bid <= prevLower && rsi <= 46.0 -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "BEARISH", if (isCompressed) 89 else 74,
                        "BB squeeze lower breakdown (width ${String.format(Locale.US, "%.1f", bandWidthPips)}$u)"
                    )
                    else -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "NEUTRAL", 53, "Volatility coiling inside BB (${String.format(Locale.US, "%.1f", bandWidthPips)}$u)"
                    )
                }
            }
            BotStrategy.LONDON_OPEN_ORB -> {
                val orbLookback = snapshot.candles.dropLast(1).takeLast(6)
                val orbHigh = orbLookback.maxOfOrNull { it.high } ?: snapshot.bid
                val orbLow = orbLookback.minOfOrNull { it.low } ?: snapshot.bid
                when {
                    snapshot.bid > orbHigh && snapshot.ema9 >= snapshot.ema21 -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "BULLISH", 82, "ORB upside break > ${snapshot.pair.formatPrice(orbHigh)}"
                    )
                    snapshot.bid < orbLow && snapshot.ema9 <= snapshot.ema21 -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "BEARISH", 82, "ORB downside break < ${snapshot.pair.formatPrice(orbLow)}"
                    )
                    else -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "NEUTRAL", 50, "Holding inside 6-bar opening range"
                    )
                }
            }
            BotStrategy.ICHIMOKU_CLOUD -> {
                val tenkanSlice = snapshot.candles.takeLast(9)
                val kijunSlice = snapshot.candles.takeLast(26)
                val tenkan = ((tenkanSlice.maxOfOrNull { it.high } ?: snapshot.bid) +
                    (tenkanSlice.minOfOrNull { it.low } ?: snapshot.bid)) / 2.0
                val kijun = ((kijunSlice.maxOfOrNull { it.high } ?: snapshot.bid) +
                    (kijunSlice.minOfOrNull { it.low } ?: snapshot.bid)) / 2.0
                val tkDiffPips = (tenkan - kijun) / snapshot.pair.pipSize
                val conf = (60 + min(34, abs(tkDiffPips * 5.0).toInt())).coerceIn(55, 94)
                when {
                    tkDiffPips > 0.8 && snapshot.bid > kijun -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "BULLISH", conf, "Tenkan above Kijun (+${String.format(Locale.US, "%.1f", tkDiffPips)}$u) over Kumo"
                    )
                    tkDiffPips < -0.8 && snapshot.bid < kijun -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "BEARISH", conf, "Tenkan below Kijun (${String.format(Locale.US, "%.1f", tkDiffPips)}$u) under Kumo"
                    )
                    else -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "NEUTRAL", 47, "Price consolidating near Kijun-sen"
                    )
                }
            }
            BotStrategy.FIBONACCI_SWING -> {
                val swing = snapshot.candles.takeLast(24)
                val swingHigh = swing.maxOfOrNull { it.high } ?: snapshot.bid
                val swingLow = swing.minOfOrNull { it.low } ?: snapshot.bid
                val span = (swingHigh - swingLow).coerceAtLeast(snapshot.pair.pipSize * 4)
                val retraceFromHigh = (swingHigh - snapshot.bid) / span
                val retraceFromLow = (snapshot.bid - swingLow) / span
                when {
                    snapshot.ema9 > snapshot.ema21 && retraceFromHigh in 0.32..0.68 -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "BULLISH", 78, "Golden Fibo pullback (${(retraceFromHigh * 100).toInt()}%) in uptrend"
                    )
                    snapshot.ema9 < snapshot.ema21 && retraceFromLow in 0.32..0.68 -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "BEARISH", 78, "Golden Fibo bounce (${(retraceFromLow * 100).toInt()}%) in downtrend"
                    )
                    else -> BotSignal(
                        bot.id, bot.name, bot.botStrategy, snapshot.pair,
                        "NEUTRAL", 48, "Awaiting 38.2%–61.8% swing retracement"
                    )
                }
            }
        }
    }

    fun shouldBotTriggerEntry(signal: BotSignal): TradeSide? {
        if (signal.confidencePct < 66) return null
        return when (signal.direction) {
            "BULLISH" -> TradeSide.BUY
            "BEARISH" -> TradeSide.SELL
            else -> null
        }
    }

    private fun computeEma(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val k = 2.0 / (period + 1)
        val result = MutableList(values.size) { values[0] }
        var ema = values[0]
        for (i in 1 until values.size) {
            ema = values[i] * k + ema * (1.0 - k)
            result[i] = ema
        }
        return result
    }

    private fun computeBollinger(values: List<Double>, period: Int, stdMult: Double): Pair<List<Double>, List<Double>> {
        val upper = MutableList(values.size) { 0.0 }
        val lower = MutableList(values.size) { 0.0 }
        for (i in values.indices) {
            val start = max(0, i - period + 1)
            val window = values.subList(start, i + 1)
            val mean = window.average()
            val variance = window.sumOf { (it - mean).pow(2) } / window.size
            val sd = sqrt(variance)
            upper[i] = mean + stdMult * sd
            lower[i] = mean - stdMult * sd
        }
        return upper to lower
    }

    private fun computeRsiSeries(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val rsi = MutableList(values.size) { 50.0 }
        var avgGain = 0.0
        var avgLoss = 0.0
        for (i in 1 until values.size) {
            val diff = values[i] - values[i - 1]
            val gain = max(0.0, diff)
            val loss = max(0.0, -diff)
            if (i <= period) {
                avgGain += gain / period
                avgLoss += loss / period
            } else {
                avgGain = ((avgGain * (period - 1)) + gain) / period
                avgLoss = ((avgLoss * (period - 1)) + loss) / period
            }
            if (i >= period) {
                val rs = if (avgLoss == 0.0) 100.0 else avgGain / avgLoss
                rsi[i] = (100.0 - (100.0 / (1.0 + rs))).coerceIn(5.0, 95.0)
            }
        }
        return rsi
    }
}
