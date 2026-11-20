package com.example

import com.example.data.market.MarketDataEngine
import com.example.data.model.BotStatus
import com.example.data.model.BotStrategy
import com.example.data.model.Candle
import com.example.data.model.ForexPair
import com.example.data.model.MarketSnapshot
import com.example.data.model.Timeframe
import com.example.data.model.TradeSide
import com.example.data.model.TradingBot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantFXUnitTest {

    @Test
    fun eurUsd_buyPnlCalculation_isAccurate() {
        // 1.00 standard lot, +25 pips on EUR/USD = +$250.00
        val pnl = ForexPair.EUR_USD.pnlUsd(
            side = TradeSide.BUY,
            entryPrice = 1.08000,
            currentPrice = 1.08250,
            lotSize = 1.0
        )
        assertEquals(250.0, pnl, 0.01)
    }

    @Test
    fun usdJpy_sellPnlCalculation_isAccurate() {
        // 0.50 lot SELL USD/JPY from 150.50 to 150.00 (+50 pips)
        val pnl = ForexPair.USD_JPY.pnlUsd(
            side = TradeSide.SELL,
            entryPrice = 150.500,
            currentPrice = 150.000,
            lotSize = 0.50
        )
        // 50 pips * ((100_000 * 0.01) / 150.0) * 0.50 = 50 * 6.6667 * 0.50 = $166.67
        assertEquals(166.67, pnl, 0.2)
    }

    @Test
    fun botSignalEvaluation_triggersExpectedDirection() {
        val engine = MarketDataEngine()
        val bot = TradingBot(
            id = "bot_1",
            userId = "user_1",
            name = "Alpha EMA",
            pair = ForexPair.EUR_USD.symbol,
            strategy = BotStrategy.EMA_CROSSOVER.name,
            timeframe = Timeframe.M5.label,
            status = BotStatus.ACTIVE.name,
            lotSize = 0.25,
            stopLossPips = 18L,
            takeProfitPips = 36L
        )
        val snapshot = MarketSnapshot(
            pair = ForexPair.EUR_USD,
            bid = 1.08550,
            ask = 1.08560,
            spreadPips = 1.0,
            changePct = 0.42,
            high24h = 1.08700,
            low24h = 1.08100,
            rsi14 = 58.0,
            ema9 = 1.08520,
            ema21 = 1.08460, // +6.0 pips EMA spread -> strong BULLISH signal
            macdHistogram = 0.0002,
            candles = listOf(Candle(1000L, 1.0840, 1.0858, 1.0838, 1.0855, 300)),
            ema9Series = listOf(1.08520),
            ema21Series = listOf(1.08460),
            bbUpperSeries = listOf(1.08700),
            bbLowerSeries = listOf(1.08200),
            rsiSeries = listOf(58.0),
            isLiveSpotAnchored = true
        )

        val signal = engine.evaluateBotSignal(bot, snapshot)
        assertEquals("BULLISH", signal.direction)
        assertTrue(signal.confidencePct >= 66)
        assertEquals(TradeSide.BUY, engine.shouldBotTriggerEntry(signal))
        assertNotNull(signal.summary)
    }

    @Test
    fun activeForexSessions_detectsLondonNewYorkOverlap() {
        val overlap = com.example.data.model.activeForexSessions(14)
        assertTrue(overlap.contains("LONDON"))
        assertTrue(overlap.contains("NEW YORK"))
    }

    @Test
    fun bestRobotCatalog_allTopRobotsMatchRuleBoundsAndEvaluateSignals() {
        val engine = MarketDataEngine()
        val now = 1_700_000_000_000L
        val candles = List(30) { i ->
            val p = 1.08000 + i * 0.00015
            Candle(now + i * 60_000L, p - 0.00005, p + 0.0002, p - 0.0001, p, 300)
        }
        val closes = candles.map { it.close }

        assertEquals(18, com.example.data.model.BestRobotCatalog.topRobots.size)
        com.example.data.model.BestRobotCatalog.topRobots.forEach { tpl ->
            assertTrue(tpl.name.length in 2..50)
            assertTrue(tpl.lotSize in 0.01..10.0)
            assertTrue(tpl.stopLossPips in 5L..500L)
            assertTrue(tpl.takeProfitPips in 5L..1000L)
            assertTrue(tpl.riskPercent in 0.25..10.0)

            val bot = TradingBot(
                id = tpl.id,
                userId = "user_1",
                name = tpl.name,
                pair = tpl.pair.symbol,
                strategy = tpl.strategy.name,
                timeframe = tpl.timeframe.label,
                status = BotStatus.ACTIVE.name,
                lotSize = tpl.lotSize,
                stopLossPips = tpl.stopLossPips,
                takeProfitPips = tpl.takeProfitPips,
                riskPercent = tpl.riskPercent
            )
            val snap = MarketSnapshot(
                pair = tpl.pair,
                bid = closes.last(),
                ask = closes.last() + tpl.pair.pipSize,
                spreadPips = 1.0,
                changePct = 0.2,
                high24h = candles.maxOf { it.high },
                low24h = candles.minOf { it.low },
                rsi14 = 62.0,
                ema9 = closes.last(),
                ema21 = closes.first(),
                macdHistogram = 0.00015,
                candles = candles,
                ema9Series = closes,
                ema21Series = closes.map { it - 0.0002 },
                bbUpperSeries = closes.map { it + 0.0004 },
                bbLowerSeries = closes.map { it - 0.0004 },
                rsiSeries = List(30) { 62.0 },
                isLiveSpotAnchored = true
            )
            val sig = engine.evaluateBotSignal(bot, snap)
            assertTrue(sig.summary.isNotBlank())
        }
    }

    @Test
    fun multiAsset_goldBitcoinStockPnlAndNotional_areAccurate() {
        // Gold XAU/USD: 0.50 lot (50 oz) rising +$12.00 = +$600.00
        val goldPnl = ForexPair.XAU_USD.pnlUsd(TradeSide.BUY, 2640.00, 2652.00, 0.50)
        assertEquals(600.0, goldPnl, 0.01)

        // Bitcoin BTC/USD: 0.25 lot (0.25 BTC) rising +$800.00 = +$200.00
        val btcPnl = ForexPair.BTC_USD.pnlUsd(TradeSide.BUY, 91000.00, 91800.00, 0.25)
        assertEquals(200.0, btcPnl, 0.01)

        // Stock NVDA: 1.0 lot (100 shares) falling -$3.50 on SELL = +$350.00
        val nvdaPnl = ForexPair.NVDA.pnlUsd(TradeSide.SELL, 145.00, 141.50, 1.0)
        assertEquals(350.0, nvdaPnl, 0.01)

        // Notional value for 0.10 BTC @ $90,000 = $9,000
        assertEquals(9000.0, ForexPair.BTC_USD.notionalUsd(90000.0, 0.10), 0.01)

        // All 4 asset classes present in catalog
        val classes = com.example.data.model.BestRobotCatalog.topRobots.map { it.pair.assetClass }.toSet()
        assertEquals(com.example.data.model.AssetClass.entries.toSet(), classes)
    }
}


