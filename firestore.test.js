const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_trader_123";
const BOB_UID = "bob_trader_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validAccountPayload(uid, ts) {
  return {
    userId: uid,
    displayName: "Alice Quant",
    email: "alice@example.com",
    balance: 10000.0,
    startingBalance: 10000.0,
    leverage: 100,
    maxDailyDrawdownPct: 5.0,
    killSwitchTriggered: false,
    requireTradeConfirm: false,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validBotPayload(uid, ts) {
  return {
    userId: uid,
    name: "Alpha EMA Scalper",
    pair: "EUR/USD",
    strategy: "EMA_CROSSOVER",
    timeframe: "5M",
    status: "ACTIVE",
    lotSize: 0.25,
    stopLossPips: 18,
    takeProfitPips: 36,
    riskPercent: 1.5,
    totalTrades: 0,
    winningTrades: 0,
    realizedPnl: 0.0,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validOpenTradePayload(uid, botId, ts) {
  return {
    userId: uid,
    botId,
    botName: "Alpha EMA Scalper",
    pair: "EUR/USD",
    side: "BUY",
    status: "OPEN",
    lotSize: 0.25,
    entryPrice: 1.0845,
    closePrice: 0.0,
    stopLoss: 1.0827,
    takeProfit: 1.0881,
    realizedPnl: 0.0,
    pips: 0.0,
    closeReason: "OPEN",
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated access: denies reads and writes across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validAccountPayload(ALICE_UID, ts)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("bots").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("bots").doc("bot_1").set(validBotPayload(ALICE_UID, ts)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("trades").get());
});

test("TraderAccount: owner create, partial update, and rejects ghost fields & future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validAccountPayload(ALICE_UID, ts))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      balance: 10240.5,
      leverage: 200,
      killSwitchTriggered: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable email mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      email: "spoofed@example.com",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("bots").doc("bot_ghost").set({
      ...validBotPayload(ALICE_UID, ts),
      id: "bot_ghost",
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("bots").doc("bot_future").set(
      validBotPayload(ALICE_UID, new Date(Date.now() + 120000))
    )
  );
});

test("TradingBot: owner CRUD, list query, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("bots").doc("bot_1").set(validBotPayload(ALICE_UID, ts))
  );

  // Empty or populated subcollection list works for owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("bots").get());

  // Partial status & stats update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("bots").doc("bot_1").update({
      status: "PAUSED",
      totalTrades: 3,
      winningTrades: 2,
      realizedPnl: 145.75,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject winningTrades > totalTrades
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("bots").doc("bot_1").update({
      totalTrades: 2,
      winningTrades: 5,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user isolation: Bob cannot read, list, or modify Alice's bots
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("bots").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("bots").doc("bot_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("bots").doc("bot_1").update({
      status: "PAUSED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Expanded BotStrategy enums succeed; arbitrary unknown strategy fails
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("bots").doc("bot_ichi").set({
      ...validBotPayload(ALICE_UID, ts),
      name: "Swiss Ichimoku Quant",
      pair: "USD/CHF",
      strategy: "ICHIMOKU_CLOUD",
      timeframe: "1H",
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("bots").doc("bot_bad_strat").set({
      ...validBotPayload(ALICE_UID, ts),
      strategy: "UNKNOWN_STRATEGY",
    })
  );
});

test("TradeRecord: create OPEN, close once, and enforce terminal state lock on CLOSED", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trades").doc("trade_1").set(
      validOpenTradePayload(ALICE_UID, "bot_1", ts)
    )
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trades").get()
  );

  // Transition OPEN -> CLOSED
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trades").doc("trade_1").update({
      status: "CLOSED",
      closePrice: 1.0881,
      realizedPnl: 90.0,
      pips: 36.0,
      closeReason: "TP_HIT",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state lock: CLOSED trade cannot be reopened or altered
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("trades").doc("trade_1").update({
      realizedPnl: 9999.0,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Multi-asset instruments: allows Metals, Crypto, and Stocks and rejects unknown symbols", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("bots").doc("bot_gold").set({
      ...validBotPayload(ALICE_UID, ts),
      name: "XAU Gold London Blitz",
      pair: "XAU/USD",
      strategy: "LONDON_OPEN_ORB",
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trades").doc("trade_btc").set({
      ...validOpenTradePayload(ALICE_UID, "bot_btc", ts),
      pair: "BTC/USD",
      entryPrice: 91850.0,
      stopLoss: 91600.0,
      takeProfit: 92450.0,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trades").doc("trade_nvda").set({
      ...validOpenTradePayload(ALICE_UID, "bot_nvda", ts),
      pair: "NVDA",
      entryPrice: 142.80,
      stopLoss: 141.80,
      takeProfit: 145.30,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("bots").doc("bot_bad_sym").set({
      ...validBotPayload(ALICE_UID, ts),
      pair: "DOGE/MOON",
    })
  );
});
