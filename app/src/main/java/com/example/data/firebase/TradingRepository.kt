package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.BotStatus
import com.example.data.model.BotStrategy
import com.example.data.model.CloseReason
import com.example.data.model.ForexPair
import com.example.data.model.RobotTemplate
import com.example.data.model.Timeframe
import com.example.data.model.TradeRecord
import com.example.data.model.TradeSide
import com.example.data.model.TradeStatus
import com.example.data.model.TraderAccount
import com.example.data.model.TradingBot
import com.example.data.model.roundTo
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TradingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeAccount(userId: String): Flow<TraderAccount?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        TraderAccount::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeBots(userId: String): Flow<List<TradingBot>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val colRef = db.collection("users").document(uid).collection("bots")
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TradingBot::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeTrades(userId: String): Flow<List<TradeRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val colRef = db.collection("users").document(uid).collection("trades")
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TradeRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureAccountAndStarterBots(
        displayName: String?,
        email: String?
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val userDoc = db.collection("users").document(uid)
            path = userDoc.path
            val snapshot = userDoc.get().await()
            if (!snapshot.exists()) {
                val cleanName = displayName?.trim()?.takeIf { it.isNotEmpty() }?.take(80) ?: "Quant Trader"
                val cleanEmail = email?.trim()?.take(120) ?: ""
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "email" to cleanEmail,
                    "balance" to 10000.0,
                    "startingBalance" to 10000.0,
                    "leverage" to 100L,
                    "maxDailyDrawdownPct" to 5.0,
                    "killSwitchTriggered" to false,
                    "requireTradeConfirm" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDoc.set(payload).await()
            }

            // Seed multi-asset starter trading robots once if user has zero bots
            val botsCol = userDoc.collection("bots")
            val existingBots = botsCol.limit(1).get().await()
            if (existingBots.isEmpty) {
                val starterBots = listOf(
                    mapOf(
                        "userId" to uid,
                        "name" to "London Scalper Pro",
                        "pair" to ForexPair.EUR_USD.symbol,
                        "strategy" to BotStrategy.EMA_CROSSOVER.name,
                        "timeframe" to Timeframe.M5.label,
                        "status" to BotStatus.ACTIVE.name,
                        "lotSize" to 0.25,
                        "stopLossPips" to 16L,
                        "takeProfitPips" to 32L,
                        "riskPercent" to 1.5,
                        "totalTrades" to 0L,
                        "winningTrades" to 0L,
                        "realizedPnl" to 0.0,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "userId" to uid,
                        "name" to "XAU Gold London Blitz",
                        "pair" to ForexPair.XAU_USD.symbol,
                        "strategy" to BotStrategy.LONDON_OPEN_ORB.name,
                        "timeframe" to Timeframe.M5.label,
                        "status" to BotStatus.ACTIVE.name,
                        "lotSize" to 0.10,
                        "stopLossPips" to 35L,
                        "takeProfitPips" to 85L,
                        "riskPercent" to 1.5,
                        "totalTrades" to 0L,
                        "winningTrades" to 0L,
                        "realizedPnl" to 0.0,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "userId" to uid,
                        "name" to "Bitcoin Momentum Rider",
                        "pair" to ForexPair.BTC_USD.symbol,
                        "strategy" to BotStrategy.MACD_MOMENTUM.name,
                        "timeframe" to Timeframe.M15.label,
                        "status" to BotStatus.ACTIVE.name,
                        "lotSize" to 0.05,
                        "stopLossPips" to 40L,
                        "takeProfitPips" to 95L,
                        "riskPercent" to 1.75,
                        "totalTrades" to 0L,
                        "winningTrades" to 0L,
                        "realizedPnl" to 0.0,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    mapOf(
                        "userId" to uid,
                        "name" to "NVDA AI Trend Scalper",
                        "pair" to ForexPair.NVDA.symbol,
                        "strategy" to BotStrategy.EMA_CROSSOVER.name,
                        "timeframe" to Timeframe.M5.label,
                        "status" to BotStatus.PAUSED.name,
                        "lotSize" to 0.25,
                        "stopLossPips" to 20L,
                        "takeProfitPips" to 50L,
                        "riskPercent" to 1.5,
                        "totalTrades" to 0L,
                        "winningTrades" to 0L,
                        "realizedPnl" to 0.0,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                starterBots.forEach { payload ->
                    botsCol.document().set(payload).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun createBot(
        name: String,
        pair: ForexPair,
        strategy: BotStrategy,
        timeframe: Timeframe,
        lotSize: Double,
        stopLossPips: Long,
        takeProfitPips: Long,
        riskPercent: Double,
        startImmediately: Boolean
    ): Result<String> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/bots"
        return try {
            val uid = requireUserId()
            val trimmedName = name.trim()
            require(trimmedName.length in 2..50) { "Bot name must be 2–50 characters." }
            val cleanLot = lotSize.coerceIn(0.01, 10.0).roundTo(2)
            val cleanSl = stopLossPips.coerceIn(5L, 500L)
            val cleanTp = takeProfitPips.coerceIn(5L, 1000L)
            val cleanRisk = riskPercent.coerceIn(0.25, 10.0).roundTo(2)

            val docRef = db.collection("users").document(uid).collection("bots").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "name" to trimmedName,
                "pair" to pair.symbol,
                "strategy" to strategy.name,
                "timeframe" to timeframe.label,
                "status" to if (startImmediately) BotStatus.ACTIVE.name else BotStatus.PAUSED.name,
                "lotSize" to cleanLot,
                "stopLossPips" to cleanSl,
                "takeProfitPips" to cleanTp,
                "riskPercent" to cleanRisk,
                "totalTrades" to 0L,
                "winningTrades" to 0L,
                "realizedPnl" to 0.0,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateBotStatus(botId: String, status: BotStatus): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/bots/$botId"
        return try {
            val uid = requireUserId()
            require(botId.isNotBlank()) { "Bot ID required" }
            val docRef = db.collection("users").document(uid).collection("bots").document(botId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "status" to status.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteBot(botId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/bots/$botId"
        return try {
            val uid = requireUserId()
            require(botId.isNotBlank()) { "Bot ID required" }
            val docRef = db.collection("users").document(uid).collection("bots").document(botId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun openTrade(
        botId: String,
        botName: String,
        pair: ForexPair,
        side: TradeSide,
        lotSize: Double,
        entryPrice: Double,
        stopLoss: Double,
        takeProfit: Double
    ): Result<String> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trades"
        return try {
            val uid = requireUserId()
            val cleanBotName = botName.trim().take(60).ifEmpty { "Manual Execution" }
            val cleanLot = lotSize.coerceIn(0.01, 10.0).roundTo(2)
            require(entryPrice > 0.0 && entryPrice <= 10_000_000.0) { "Invalid entry price" }
            val docRef = db.collection("users").document(uid).collection("trades").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "botId" to botId.take(128),
                "botName" to cleanBotName,
                "pair" to pair.symbol,
                "side" to side.name,
                "status" to TradeStatus.OPEN.name,
                "lotSize" to cleanLot,
                "entryPrice" to entryPrice.roundTo(pair.decimals),
                "closePrice" to 0.0,
                "stopLoss" to stopLoss.coerceIn(0.0, 10_000_000.0).roundTo(pair.decimals),
                "takeProfit" to takeProfit.coerceIn(0.0, 10_000_000.0).roundTo(pair.decimals),
                "realizedPnl" to 0.0,
                "pips" to 0.0,
                "closeReason" to CloseReason.OPEN.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun closeTradeAndUpdateBalances(
        trade: TradeRecord,
        closePrice: Double,
        reason: CloseReason,
        currentAccountBalance: Double,
        associatedBot: TradingBot?
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trades/${trade.id}"
        return try {
            val uid = requireUserId()
            require(trade.id.isNotBlank()) { "Trade ID required" }
            val pair = trade.forexPair
            val side = trade.tradeSide
            val direction = if (side == TradeSide.BUY) 1.0 else -1.0
            val pips = (pair.pipDifference(trade.entryPrice, closePrice) * direction).roundTo(1)
            val pnl = pair.pnlUsd(side, trade.entryPrice, closePrice, trade.lotSize).roundTo(2)

            val userRef = db.collection("users").document(uid)
            val tradeRef = userRef.collection("trades").document(trade.id)
            val botRef = associatedBot?.id?.takeIf { it.isNotBlank() }?.let {
                userRef.collection("bots").document(it)
            }
            path = tradeRef.path

            db.runTransaction { tx ->
                val tradeSnap = tx.get(tradeRef)
                val currentStatus = tradeSnap.getString("status")
                if (currentStatus != TradeStatus.OPEN.name) {
                    return@runTransaction null
                }

                val userSnap = tx.get(userRef)
                val liveBalance = userSnap.getDouble("balance") ?: currentAccountBalance
                val newBalance = (liveBalance + pnl).coerceIn(0.0, 10_000_000.0).roundTo(2)

                val botSnap = botRef?.let { tx.get(it) }

                tx.update(
                    tradeRef,
                    mapOf(
                        "status" to TradeStatus.CLOSED.name,
                        "closePrice" to closePrice.roundTo(pair.decimals),
                        "realizedPnl" to pnl,
                        "pips" to pips,
                        "closeReason" to reason.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                tx.update(
                    userRef,
                    mapOf(
                        "balance" to newBalance,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                if (botRef != null && botSnap != null && botSnap.exists()) {
                    val prevTotal = botSnap.getLong("totalTrades") ?: 0L
                    val prevWins = botSnap.getLong("winningTrades") ?: 0L
                    val prevPnl = botSnap.getDouble("realizedPnl") ?: 0.0
                    tx.update(
                        botRef,
                        mapOf(
                            "totalTrades" to (prevTotal + 1L),
                            "winningTrades" to (prevWins + if (pnl >= 0.0) 1L else 0L),
                            "realizedPnl" to (prevPnl + pnl).roundTo(2),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateRiskSettings(
        leverage: Long,
        maxDailyDrawdownPct: Double,
        requireTradeConfirm: Boolean,
        killSwitchTriggered: Boolean
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val userRef = db.collection("users").document(uid)
            path = userRef.path
            userRef.update(
                mapOf(
                    "leverage" to leverage.coerceIn(10L, 500L),
                    "maxDailyDrawdownPct" to maxDailyDrawdownPct.coerceIn(1.0, 50.0).roundTo(1),
                    "requireTradeConfirm" to requireTradeConfirm,
                    "killSwitchTriggered" to killSwitchTriggered,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun pauseAllBotsAndTriggerKillSwitch(activeBots: List<TradingBot>): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val userRef = db.collection("users").document(uid)
            path = userRef.path
            val batch = db.batch()
            batch.update(
                userRef,
                mapOf(
                    "killSwitchTriggered" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            activeBots.forEach { bot ->
                if (bot.id.isNotBlank()) {
                    batch.update(
                        userRef.collection("bots").document(bot.id),
                        mapOf(
                            "status" to BotStatus.PAUSED.name,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun resetDemoEquity(trades: List<TradeRecord>): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val userRef = db.collection("users").document(uid)
            path = userRef.path
            val batch = db.batch()
            batch.update(
                userRef,
                mapOf(
                    "balance" to 10000.0,
                    "startingBalance" to 10000.0,
                    "killSwitchTriggered" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            trades.take(400).forEach { trade ->
                if (trade.id.isNotBlank()) {
                    batch.delete(userRef.collection("trades").document(trade.id))
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun setAllBotsStatus(bots: List<TradingBot>, targetStatus: BotStatus): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/bots"
        return try {
            val uid = requireUserId()
            val validBots = bots.filter { it.id.isNotBlank() }
            if (validBots.isEmpty()) return Result.success(Unit)
            val userRef = db.collection("users").document(uid)
            path = userRef.collection("bots").path
            val batch = db.batch()
            validBots.forEach { bot ->
                batch.update(
                    userRef.collection("bots").document(bot.id),
                    mapOf(
                        "status" to targetStatus.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateBotParameters(
        botId: String,
        lotSize: Double,
        stopLossPips: Long,
        takeProfitPips: Long
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/bots/$botId"
        return try {
            val uid = requireUserId()
            require(botId.isNotBlank()) { "Bot ID required" }
            val docRef = db.collection("users").document(uid).collection("bots").document(botId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "lotSize" to lotSize.coerceIn(0.01, 10.0).roundTo(2),
                    "stopLossPips" to stopLossPips.coerceIn(5L, 500L),
                    "takeProfitPips" to takeProfitPips.coerceIn(5L, 1000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTradeSlTp(
        trade: TradeRecord,
        newStopLoss: Double,
        newTakeProfit: Double
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/trades/${trade.id}"
        return try {
            val uid = requireUserId()
            require(trade.id.isNotBlank()) { "Trade ID required" }
            val pair = trade.forexPair
            val docRef = db.collection("users").document(uid).collection("trades").document(trade.id)
            path = docRef.path
            docRef.update(
                mapOf(
                    "stopLoss" to newStopLoss.coerceIn(0.0, 10_000_000.0).roundTo(pair.decimals),
                    "takeProfit" to newTakeProfit.coerceIn(0.0, 10_000_000.0).roundTo(pair.decimals),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun cloneRobotTemplates(
        templates: List<RobotTemplate>,
        activateImmediately: Boolean
    ): Result<Int> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/bots"
        return try {
            val uid = requireUserId()
            if (templates.isEmpty()) return Result.success(0)
            val botsCol = db.collection("users").document(uid).collection("bots")
            path = botsCol.path
            val batch = db.batch()
            val statusName = if (activateImmediately) BotStatus.ACTIVE.name else BotStatus.PAUSED.name
            templates.forEach { tpl ->
                val docRef = botsCol.document()
                val payload = mapOf(
                    "userId" to uid,
                    "name" to tpl.name.trim().take(50),
                    "pair" to tpl.pair.symbol,
                    "strategy" to tpl.strategy.name,
                    "timeframe" to tpl.timeframe.label,
                    "status" to statusName,
                    "lotSize" to tpl.lotSize.coerceIn(0.01, 10.0).roundTo(2),
                    "stopLossPips" to tpl.stopLossPips.coerceIn(5L, 500L),
                    "takeProfitPips" to tpl.takeProfitPips.coerceIn(5L, 1000L),
                    "riskPercent" to tpl.riskPercent.coerceIn(0.25, 10.0).roundTo(2),
                    "totalTrades" to 0L,
                    "winningTrades" to 0L,
                    "realizedPnl" to 0.0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(docRef, payload)
            }
            batch.commit().await()
            Result.success(templates.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}

