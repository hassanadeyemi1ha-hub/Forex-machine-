package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Candle
import com.example.data.model.ForexPair
import com.example.data.model.MarketSnapshot
import com.example.data.model.TradeRecord
import com.example.data.model.TradeSide
import com.example.data.model.TradeStatus
import com.example.ui.components.CandlestickChart
import com.example.ui.theme.QuantFXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readAppNameFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("QuantFX", appName)
    }

    @Test
    fun candlestickChart_rendersOhlcOverlaysAndZoomControls() {
        val now = 1_700_000_000_000L
        val candles = List(30) { idx ->
            val base = 1.08400 + idx * 0.00010
            Candle(
                timestampMillis = now + idx * 300_000L,
                open = base,
                high = base + 0.00025,
                low = base - 0.00015,
                close = base + 0.00015,
                volume = 250 + idx * 10
            )
        }
        val closes = candles.map { it.close }
        val snapshot = MarketSnapshot(
            pair = ForexPair.EUR_USD,
            bid = closes.last(),
            ask = closes.last() + 0.00010,
            spreadPips = 1.0,
            changePct = 0.28,
            high24h = candles.maxOf { it.high },
            low24h = candles.minOf { it.low },
            rsi14 = 61.4,
            ema9 = closes.last(),
            ema21 = closes.first(),
            macdHistogram = 0.00012,
            candles = candles,
            ema9Series = closes,
            ema21Series = closes.map { it - 0.0003 },
            bbUpperSeries = closes.map { it + 0.0006 },
            bbLowerSeries = closes.map { it - 0.0006 },
            rsiSeries = List(30) { 61.4 },
            isLiveSpotAnchored = true
        )
        val openPos = listOf(
            TradeRecord(
                id = "t1",
                pair = ForexPair.EUR_USD.symbol,
                side = TradeSide.BUY.name,
                status = TradeStatus.OPEN.name,
                lotSize = 0.25,
                entryPrice = 1.08500,
                stopLoss = 1.08300,
                takeProfit = 1.08900
            )
        )

        composeRule.setContent {
            QuantFXTheme {
                CandlestickChart(
                    snapshot = snapshot,
                    openPositionsOnPair = openPos,
                    showEmaOverlay = true,
                    showBollingerOverlay = true,
                    showRsiPane = true
                )
            }
        }

        composeRule.onNodeWithTag("candlestick_canvas").assertIsDisplayed()
        composeRule.onNodeWithText("EMA 9").assertIsDisplayed()
        composeRule.onNodeWithText("EMA 21").assertIsDisplayed()
        composeRule.onNodeWithText("1 Pos").assertIsDisplayed()
        composeRule.onNodeWithTag("chart_zoom_in_button").performClick()
        composeRule.onNodeWithTag("chart_zoom_out_button").performClick()
    }
}
