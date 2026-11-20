package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Locale
import kotlin.math.pow

enum class AssetClass(
    val label: String,
    val badge: String,
    val pointLabel: String,
    val unitSuffix: String
) {
    FOREX("Forex", "FX", "Pips", "p"),
    METALS("Metals", "METAL", "Pts", "pt"),
    CRYPTO("Crypto", "CRYPTO", "Pts", "pt"),
    STOCKS("Stocks", "STOCK", "Ticks", "t")
}

enum class ForexPair(
    val symbol: String,
    val assetClass: AssetClass,
    val displayName: String,
    val base: String,
    val quote: String,
    val pipSize: Double,
    val decimals: Int,
    val typicalSpreadPips: Double,
    val seedPrice: Double,
    val contractSize: Double
) {
    // Forex Majors
    EUR_USD("EUR/USD", AssetClass.FOREX, "Euro / US Dollar", "EUR", "USD", 0.0001, 5, 0.8, 1.08420, 100_000.0),
    GBP_USD("GBP/USD", AssetClass.FOREX, "British Pound / USD", "GBP", "USD", 0.0001, 5, 1.1, 1.26850, 100_000.0),
    USD_JPY("USD/JPY", AssetClass.FOREX, "US Dollar / Yen", "USD", "JPY", 0.01, 3, 0.9, 154.320, 100_000.0),
    USD_CHF("USD/CHF", AssetClass.FOREX, "US Dollar / Franc", "USD", "CHF", 0.0001, 5, 1.2, 0.88410, 100_000.0),
    AUD_USD("AUD/USD", AssetClass.FOREX, "Aussie Dollar / USD", "AUD", "USD", 0.0001, 5, 1.0, 0.65480, 100_000.0),
    USD_CAD("USD/CAD", AssetClass.FOREX, "US Dollar / Loonie", "USD", "CAD", 0.0001, 5, 1.3, 1.39620, 100_000.0),

    // Precious Metals
    XAU_USD("XAU/USD", AssetClass.METALS, "Gold Spot / USD", "XAU", "USD", 0.10, 2, 2.5, 2645.50, 100.0),
    XAG_USD("XAG/USD", AssetClass.METALS, "Silver Spot / USD", "XAG", "USD", 0.01, 3, 2.2, 31.450, 1_000.0),
    XPT_USD("XPT/USD", AssetClass.METALS, "Platinum Spot / USD", "XPT", "USD", 0.10, 2, 3.0, 968.40, 100.0),

    // Cryptocurrencies
    BTC_USD("BTC/USD", AssetClass.CRYPTO, "Bitcoin / USD", "BTC", "USD", 10.0, 2, 1.8, 91850.00, 1.0),
    ETH_USD("ETH/USD", AssetClass.CRYPTO, "Ethereum / USD", "ETH", "USD", 1.0, 2, 1.5, 3180.00, 10.0),
    SOL_USD("SOL/USD", AssetClass.CRYPTO, "Solana / USD", "SOL", "USD", 0.10, 2, 1.6, 238.40, 100.0),
    XRP_USD("XRP/USD", AssetClass.CRYPTO, "Ripple / USD", "XRP", "USD", 0.001, 4, 1.4, 1.1240, 10_000.0),

    // Global Equities / Stocks
    AAPL("AAPL", AssetClass.STOCKS, "Apple Inc.", "AAPL", "USD", 0.05, 2, 1.2, 228.45, 100.0),
    NVDA("NVDA", AssetClass.STOCKS, "NVIDIA Corp.", "NVDA", "USD", 0.05, 2, 1.4, 142.80, 100.0),
    TSLA("TSLA", AssetClass.STOCKS, "Tesla Inc.", "TSLA", "USD", 0.10, 2, 1.6, 338.60, 100.0),
    MSFT("MSFT", AssetClass.STOCKS, "Microsoft Corp.", "MSFT", "USD", 0.10, 2, 1.2, 418.20, 100.0);

    fun formatPrice(price: Double): String =
        String.format(Locale.US, "%.${decimals}f", price)

    fun pipDifference(fromPrice: Double, toPrice: Double): Double =
        (toPrice - fromPrice) / pipSize

    fun pointValueUsd(currentPrice: Double, lotSize: Double): Double {
        if (currentPrice <= 0.0) return 0.0
        val pipValuePerLot = if (assetClass == AssetClass.FOREX && quote != "USD") {
            (contractSize * pipSize) / currentPrice
        } else {
            contractSize * pipSize
        }
        return pipValuePerLot * lotSize
    }

    fun notionalUsd(currentPrice: Double, lotSize: Double): Double {
        val safePrice = currentPrice.coerceAtLeast(pipSize)
        return if (assetClass == AssetClass.FOREX && base == "USD") {
            contractSize * lotSize
        } else {
            safePrice * contractSize * lotSize
        }
    }

    fun pnlUsd(side: TradeSide, entryPrice: Double, currentPrice: Double, lotSize: Double): Double {
        if (currentPrice <= 0.0 || entryPrice <= 0.0) return 0.0
        val direction = if (side == TradeSide.BUY) 1.0 else -1.0
        val pips = ((currentPrice - entryPrice) / pipSize) * direction
        return pips * pointValueUsd(currentPrice, lotSize)
    }

    companion object {
        fun fromSymbol(symbol: String): ForexPair =
            entries.firstOrNull { it.symbol == symbol } ?: EUR_USD

        fun byAssetClass(assetClass: AssetClass?): List<ForexPair> =
            if (assetClass == null) entries else entries.filter { it.assetClass == assetClass }
    }
}

enum class Timeframe(val label: String, val secondsPerCandle: Long) {
    M1("1M", 60L),
    M5("5M", 300L),
    M15("15M", 900L),
    H1("1H", 3600L),
    H4("4H", 14400L),
    D1("1D", 86400L);

    companion object {
        fun fromLabel(label: String): Timeframe =
            entries.firstOrNull { it.label == label } ?: M5
    }
}

enum class BotStrategy(
    val title: String,
    val shortCode: String,
    val description: String,
    val expectedWinRate: Double
) {
    EMA_CROSSOVER(
        title = "EMA Ribbon Scalper",
        shortCode = "EMA 9/21",
        description = "Captures intraday momentum shifts when fast EMA(9) crosses slow EMA(21) with ATR volatility filter.",
        expectedWinRate = 64.5
    ),
    RSI_REVERSAL(
        title = "RSI Mean Reversion",
        shortCode = "RSI(14)",
        description = "Fades overextended Bollinger extremes when RSI(14) crosses back inside 30/70 institutional bands.",
        expectedWinRate = 68.2
    ),
    BREAKOUT_GRID(
        title = "Session Breakout Engine",
        shortCode = "BRK-GRID",
        description = "Detects range compression breakouts using 20-period high/low channels with dynamic risk sizing.",
        expectedWinRate = 58.9
    ),
    MACD_MOMENTUM(
        title = "MACD Trend Rider",
        shortCode = "MACD+EMA",
        description = "Rides sustained multi-candle directional trends aligned with MACD histogram expansion.",
        expectedWinRate = 61.4
    ),
    BOLLINGER_SQUEEZE(
        title = "Bollinger Squeeze Sniper",
        shortCode = "BB-SQZ",
        description = "Detects tight volatility compression bands and fires on explosive high-volume breakouts.",
        expectedWinRate = 66.1
    ),
    LONDON_OPEN_ORB(
        title = "Session Open ORB Hunter",
        shortCode = "ORB-HUNT",
        description = "Captures session opening range breakouts confirmed by fast EMA(9) slope.",
        expectedWinRate = 63.8
    ),
    ICHIMOKU_CLOUD(
        title = "Ichimoku Cloud Quant",
        shortCode = "ICHI-KUMO",
        description = "Combines Tenkan/Kijun momentum crosses with Kumo cloud support/resistance filtering.",
        expectedWinRate = 62.7
    ),
    FIBONACCI_SWING(
        title = "Fibonacci Pullback Swing",
        shortCode = "FIBO-618",
        description = "Buys/sells institutional 38.2%–61.8% golden retracements aligned with EMA(21) trend.",
        expectedWinRate = 65.4
    );

    companion object {
        fun fromName(raw: String): BotStrategy =
            entries.firstOrNull { it.name == raw } ?: EMA_CROSSOVER
    }
}

enum class BotStatus {
    ACTIVE,
    PAUSED;

    companion object {
        fun fromName(raw: String): BotStatus =
            entries.firstOrNull { it.name == raw } ?: PAUSED
    }
}

enum class TradeSide {
    BUY,
    SELL;

    companion object {
        fun fromName(raw: String): TradeSide =
            entries.firstOrNull { it.name == raw } ?: BUY
    }
}

enum class TradeStatus {
    OPEN,
    CLOSED;

    companion object {
        fun fromName(raw: String): TradeStatus =
            entries.firstOrNull { it.name == raw } ?: OPEN
    }
}

enum class CloseReason(val label: String) {
    OPEN("Active"),
    TP_HIT("Take Profit"),
    SL_HIT("Stop Loss"),
    BOT_EXIT("Bot Signal Exit"),
    MANUAL_CLOSE("Manual Close");

    companion object {
        fun fromName(raw: String): CloseReason =
            entries.firstOrNull { it.name == raw } ?: MANUAL_CLOSE
    }
}

data class Candle(
    val timestampMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Int
)

data class BotSignal(
    val botId: String,
    val botName: String,
    val strategy: BotStrategy,
    val pair: ForexPair,
    val direction: String, // "BULLISH", "BEARISH", "NEUTRAL"
    val confidencePct: Int,
    val summary: String
)

data class MarketSnapshot(
    val pair: ForexPair,
    val bid: Double,
    val ask: Double,
    val spreadPips: Double,
    val changePct: Double,
    val high24h: Double,
    val low24h: Double,
    val rsi14: Double,
    val ema9: Double,
    val ema21: Double,
    val macdHistogram: Double,
    val candles: List<Candle>,
    val ema9Series: List<Double>,
    val ema21Series: List<Double>,
    val bbUpperSeries: List<Double>,
    val bbLowerSeries: List<Double>,
    val rsiSeries: List<Double>,
    val isLiveSpotAnchored: Boolean
)

@Keep
@IgnoreExtraProperties
data class TraderAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Trader",
    val email: String = "",
    val balance: Double = 10000.0,
    val startingBalance: Double = 10000.0,
    val leverage: Long = 100L,
    val maxDailyDrawdownPct: Double = 5.0,
    val killSwitchTriggered: Boolean = false,
    val requireTradeConfirm: Boolean = false,
    @com.google.firebase.firestore.ServerTimestamp val createdAt: Timestamp? = null,
    @com.google.firebase.firestore.ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TradingBot(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val pair: String = ForexPair.EUR_USD.symbol,
    val strategy: String = BotStrategy.EMA_CROSSOVER.name,
    val timeframe: String = Timeframe.M5.label,
    val status: String = BotStatus.PAUSED.name,
    val lotSize: Double = 0.10,
    val stopLossPips: Long = 20L,
    val takeProfitPips: Long = 40L,
    val riskPercent: Double = 1.5,
    val totalTrades: Long = 0L,
    val winningTrades: Long = 0L,
    val realizedPnl: Double = 0.0,
    @com.google.firebase.firestore.ServerTimestamp val createdAt: Timestamp? = null,
    @com.google.firebase.firestore.ServerTimestamp val updatedAt: Timestamp? = null
) {
    val forexPair: ForexPair get() = ForexPair.fromSymbol(pair)
    val botStrategy: BotStrategy get() = BotStrategy.fromName(strategy)
    val botTimeframe: Timeframe get() = Timeframe.fromLabel(timeframe)
    val botStatus: BotStatus get() = BotStatus.fromName(status)
    val winRatePct: Double get() = if (totalTrades > 0L) (winningTrades.toDouble() / totalTrades.toDouble()) * 100.0 else 0.0
}

@Keep
@IgnoreExtraProperties
data class TradeRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val botId: String = "",
    val botName: String = "Manual Execution",
    val pair: String = ForexPair.EUR_USD.symbol,
    val side: String = TradeSide.BUY.name,
    val status: String = TradeStatus.OPEN.name,
    val lotSize: Double = 0.10,
    val entryPrice: Double = 1.0,
    val closePrice: Double = 0.0,
    val stopLoss: Double = 0.0,
    val takeProfit: Double = 0.0,
    val realizedPnl: Double = 0.0,
    val pips: Double = 0.0,
    val closeReason: String = CloseReason.OPEN.name,
    @com.google.firebase.firestore.ServerTimestamp val createdAt: Timestamp? = null,
    @com.google.firebase.firestore.ServerTimestamp val updatedAt: Timestamp? = null
) {
    val forexPair: ForexPair get() = ForexPair.fromSymbol(pair)
    val tradeSide: TradeSide get() = TradeSide.fromName(side)
    val tradeStatus: TradeStatus get() = TradeStatus.fromName(status)
    val reason: CloseReason get() = CloseReason.fromName(closeReason)
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

fun Double.roundTo(decimals: Int): Double {
    val factor = 10.0.pow(decimals)
    return kotlin.math.round(this * factor) / factor
}

fun activeForexSessions(utcHour: Int): List<String> {
    val sessions = mutableListOf<String>()
    if (utcHour in 22..23 || utcHour in 0..6) sessions.add("SYDNEY")
    if (utcHour in 0..8) sessions.add("TOKYO")
    if (utcHour in 8..16) sessions.add("LONDON")
    if (utcHour in 13..21) sessions.add("NEW YORK")
    if (sessions.isEmpty()) sessions.add("INTERBANK")
    return sessions
}

fun activeMarketSessionLabel(assetClass: AssetClass, utcHour: Int): String = when (assetClass) {
    AssetClass.FOREX, AssetClass.METALS -> activeForexSessions(utcHour).joinToString(" + ")
    AssetClass.CRYPTO -> "24/7 SPOT"
    AssetClass.STOCKS -> when (utcHour) {
        in 14..20 -> "NYSE / NASDAQ"
        in 9..13, in 21..23 -> "EXTENDED HOURS"
        else -> "OVERNIGHT CFD"
    }
}

data class RobotTemplate(
    val id: String,
    val name: String,
    val badge: String,
    val pair: ForexPair,
    val strategy: BotStrategy,
    val timeframe: Timeframe,
    val lotSize: Double,
    val stopLossPips: Long,
    val takeProfitPips: Long,
    val riskPercent: Double,
    val backtestWinRate: Double,
    val profitFactor: Double,
    val maxDrawdownPct: Double,
    val summary: String
)

object BestRobotCatalog {
    val topRobots: List<RobotTemplate> = listOf(
        // FOREX
        RobotTemplate(
            id = "ea_london_scalper_pro",
            name = "London Scalper Pro",
            badge = "INSTITUTIONAL #1",
            pair = ForexPair.EUR_USD,
            strategy = BotStrategy.EMA_CROSSOVER,
            timeframe = Timeframe.M5,
            lotSize = 0.25,
            stopLossPips = 16L,
            takeProfitPips = 32L,
            riskPercent = 1.5,
            backtestWinRate = 66.8,
            profitFactor = 2.14,
            maxDrawdownPct = 3.2,
            summary = "High-frequency EMA(9/21) ribbon scalper tuned for tight EUR/USD London liquidity."
        ),
        RobotTemplate(
            id = "ea_cable_reversion",
            name = "Cable Reversion Guard",
            badge = "MEAN REVERSION",
            pair = ForexPair.GBP_USD,
            strategy = BotStrategy.RSI_REVERSAL,
            timeframe = Timeframe.M15,
            lotSize = 0.20,
            stopLossPips = 22L,
            takeProfitPips = 44L,
            riskPercent = 1.25,
            backtestWinRate = 68.4,
            profitFactor = 2.26,
            maxDrawdownPct = 2.9,
            summary = "Fades GBP/USD Bollinger + RSI(14) exhaustion spikes back toward institutional VWAP."
        ),
        RobotTemplate(
            id = "ea_tokyo_breakout",
            name = "Tokyo Yen Breakout",
            badge = "ASIAN SESSION",
            pair = ForexPair.USD_JPY,
            strategy = BotStrategy.BREAKOUT_GRID,
            timeframe = Timeframe.H1,
            lotSize = 0.30,
            stopLossPips = 24L,
            takeProfitPips = 56L,
            riskPercent = 1.75,
            backtestWinRate = 60.2,
            profitFactor = 2.08,
            maxDrawdownPct = 4.1,
            summary = "Captures USD/JPY 12-bar volatility channel breakouts with asymmetric 1:2.3 R:R."
        ),
        RobotTemplate(
            id = "ea_aussie_bb_squeeze",
            name = "Aussie BB Squeeze EA",
            badge = "VOLATILITY SNIPER",
            pair = ForexPair.AUD_USD,
            strategy = BotStrategy.BOLLINGER_SQUEEZE,
            timeframe = Timeframe.M5,
            lotSize = 0.25,
            stopLossPips = 15L,
            takeProfitPips = 36L,
            riskPercent = 1.5,
            backtestWinRate = 67.1,
            profitFactor = 2.31,
            maxDrawdownPct = 2.7,
            summary = "Waits for Bollinger bandwidth compression on AUD/USD before entering momentum expansion."
        ),
        RobotTemplate(
            id = "ea_london_open_blitz",
            name = "London Open Blitz",
            badge = "SESSION ORB",
            pair = ForexPair.GBP_USD,
            strategy = BotStrategy.LONDON_OPEN_ORB,
            timeframe = Timeframe.M5,
            lotSize = 0.30,
            stopLossPips = 18L,
            takeProfitPips = 42L,
            riskPercent = 1.5,
            backtestWinRate = 64.5,
            profitFactor = 2.11,
            maxDrawdownPct = 3.6,
            summary = "Trades GBP/USD opening-range expansion aligned with fast EMA directional slope."
        ),
        RobotTemplate(
            id = "ea_swiss_ichimoku",
            name = "Swiss Ichimoku Quant",
            badge = "SAFE-HAVEN TREND",
            pair = ForexPair.USD_CHF,
            strategy = BotStrategy.ICHIMOKU_CLOUD,
            timeframe = Timeframe.H1,
            lotSize = 0.20,
            stopLossPips = 26L,
            takeProfitPips = 62L,
            riskPercent = 1.25,
            backtestWinRate = 63.9,
            profitFactor = 2.22,
            maxDrawdownPct = 3.1,
            summary = "Tenkan/Kijun cloud crossover system built for steady USD/CHF macro trend runs."
        ),
        RobotTemplate(
            id = "ea_loonie_fibo_swing",
            name = "Loonie Fibo Swing",
            badge = "GOLDEN RATIO",
            pair = ForexPair.USD_CAD,
            strategy = BotStrategy.FIBONACCI_SWING,
            timeframe = Timeframe.H4,
            lotSize = 0.25,
            stopLossPips = 28L,
            takeProfitPips = 68L,
            riskPercent = 1.5,
            backtestWinRate = 65.8,
            profitFactor = 2.38,
            maxDrawdownPct = 2.8,
            summary = "Enters USD/CAD pullbacks inside the 38.2%–61.8% Fibonacci zone with trend alignment."
        ),
        RobotTemplate(
            id = "ea_fiber_macd_rider",
            name = "Fiber MACD Rider",
            badge = "MOMENTUM PRO",
            pair = ForexPair.EUR_USD,
            strategy = BotStrategy.MACD_MOMENTUM,
            timeframe = Timeframe.M15,
            lotSize = 0.30,
            stopLossPips = 22L,
            takeProfitPips = 50L,
            riskPercent = 1.75,
            backtestWinRate = 62.4,
            profitFactor = 1.96,
            maxDrawdownPct = 3.8,
            summary = "Rides sustained EUR/USD institutional order flow confirmed by expanding MACD histograms."
        ),
        RobotTemplate(
            id = "ea_night_owl_sniper",
            name = "Night Owl Sniper",
            badge = "LOW DRAWDOWN",
            pair = ForexPair.EUR_USD,
            strategy = BotStrategy.RSI_REVERSAL,
            timeframe = Timeframe.H1,
            lotSize = 0.15,
            stopLossPips = 20L,
            takeProfitPips = 40L,
            riskPercent = 1.0,
            backtestWinRate = 69.5,
            profitFactor = 2.42,
            maxDrawdownPct = 2.1,
            summary = "Conservative hourly RSI + Bollinger band mean-reversion engine with ultra-low drawdown."
        ),
        RobotTemplate(
            id = "ea_ninja_momentum_ea",
            name = "Ninja Momentum EA",
            badge = "JPY SCALPER",
            pair = ForexPair.USD_JPY,
            strategy = BotStrategy.MACD_MOMENTUM,
            timeframe = Timeframe.M15,
            lotSize = 0.25,
            stopLossPips = 20L,
            takeProfitPips = 48L,
            riskPercent = 1.5,
            backtestWinRate = 63.1,
            profitFactor = 2.04,
            maxDrawdownPct = 3.5,
            summary = "Captures rapid USD/JPY yield-driven momentum impulses with tight trailing stop targets."
        ),

        // METALS
        RobotTemplate(
            id = "ea_gold_london_blitz",
            name = "XAU Gold London Blitz",
            badge = "GOLD #1 EA",
            pair = ForexPair.XAU_USD,
            strategy = BotStrategy.LONDON_OPEN_ORB,
            timeframe = Timeframe.M5,
            lotSize = 0.10,
            stopLossPips = 35L,
            takeProfitPips = 85L,
            riskPercent = 1.5,
            backtestWinRate = 67.4,
            profitFactor = 2.34,
            maxDrawdownPct = 3.3,
            summary = "Precision XAU/USD opening-range breakout engine calibrated for bullion liquidity sweeps."
        ),
        RobotTemplate(
            id = "ea_gold_ichimoku_macro",
            name = "Gold Ichimoku Macro",
            badge = "BULLION TREND",
            pair = ForexPair.XAU_USD,
            strategy = BotStrategy.ICHIMOKU_CLOUD,
            timeframe = Timeframe.H1,
            lotSize = 0.10,
            stopLossPips = 45L,
            takeProfitPips = 110L,
            riskPercent = 1.5,
            backtestWinRate = 65.2,
            profitFactor = 2.28,
            maxDrawdownPct = 3.0,
            summary = "Captures multi-session Gold macro rallies using Tenkan/Kijun crosses above Kumo."
        ),
        RobotTemplate(
            id = "ea_silver_squeeze_hunter",
            name = "Silver Squeeze Hunter",
            badge = "METALS SNIPER",
            pair = ForexPair.XAG_USD,
            strategy = BotStrategy.BOLLINGER_SQUEEZE,
            timeframe = Timeframe.M15,
            lotSize = 0.15,
            stopLossPips = 25L,
            takeProfitPips = 60L,
            riskPercent = 1.25,
            backtestWinRate = 64.0,
            profitFactor = 2.15,
            maxDrawdownPct = 3.4,
            summary = "Tracks XAG/USD Bollinger volatility squeezes to catch explosive silver breakouts."
        ),

        // CRYPTO
        RobotTemplate(
            id = "ea_btc_momentum_rider",
            name = "Bitcoin Momentum Rider",
            badge = "CRYPTO #1 EA",
            pair = ForexPair.BTC_USD,
            strategy = BotStrategy.MACD_MOMENTUM,
            timeframe = Timeframe.M15,
            lotSize = 0.05,
            stopLossPips = 40L,
            takeProfitPips = 95L,
            riskPercent = 1.75,
            backtestWinRate = 63.8,
            profitFactor = 2.19,
            maxDrawdownPct = 4.2,
            summary = "24/7 BTC/USD momentum rider engineered to capture institutional spot ETF trend waves."
        ),
        RobotTemplate(
            id = "ea_eth_bb_breakout",
            name = "ETH Bollinger Breakout",
            badge = "ALTCOIN VOLATILITY",
            pair = ForexPair.ETH_USD,
            strategy = BotStrategy.BOLLINGER_SQUEEZE,
            timeframe = Timeframe.M5,
            lotSize = 0.15,
            stopLossPips = 30L,
            takeProfitPips = 75L,
            riskPercent = 1.5,
            backtestWinRate = 66.5,
            profitFactor = 2.30,
            maxDrawdownPct = 3.7,
            summary = "Monitors ETH/USD compression bands and fires on confirmed upside/downside expansion."
        ),
        RobotTemplate(
            id = "ea_sol_fibo_pullback",
            name = "Solana Fibo Pullback",
            badge = "L1 SWING EA",
            pair = ForexPair.SOL_USD,
            strategy = BotStrategy.FIBONACCI_SWING,
            timeframe = Timeframe.H1,
            lotSize = 0.15,
            stopLossPips = 28L,
            takeProfitPips = 70L,
            riskPercent = 1.5,
            backtestWinRate = 64.9,
            profitFactor = 2.24,
            maxDrawdownPct = 3.9,
            summary = "Buys SOL/USD golden-ratio retracements (38.2%–61.8%) with EMA(21) trend confirmation."
        ),

        // STOCKS
        RobotTemplate(
            id = "ea_nvda_ai_scalper",
            name = "NVDA AI Trend Scalper",
            badge = "EQUITIES #1",
            pair = ForexPair.NVDA,
            strategy = BotStrategy.EMA_CROSSOVER,
            timeframe = Timeframe.M5,
            lotSize = 0.25,
            stopLossPips = 20L,
            takeProfitPips = 50L,
            riskPercent = 1.5,
            backtestWinRate = 68.1,
            profitFactor = 2.41,
            maxDrawdownPct = 2.8,
            summary = "High-momentum NVIDIA stock scalper executing on EMA(9/21) institutional flow alignment."
        ),
        RobotTemplate(
            id = "ea_tsla_volatility_reversion",
            name = "TSLA Volatility Reversion",
            badge = "NASDAQ ALPHA",
            pair = ForexPair.TSLA,
            strategy = BotStrategy.RSI_REVERSAL,
            timeframe = Timeframe.M15,
            lotSize = 0.20,
            stopLossPips = 24L,
            takeProfitPips = 54L,
            riskPercent = 1.25,
            backtestWinRate = 65.6,
            profitFactor = 2.18,
            maxDrawdownPct = 3.5,
            summary = "Fades extreme TSLA intraday RSI(14) + Bollinger Band overextensions back to mean."
        )
    )
}
