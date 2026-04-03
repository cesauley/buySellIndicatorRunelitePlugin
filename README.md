# Buy/Sell Indicator – RuneLite External Plugin

Shows a **BUY / SELL / HOLD** signal with a confidence percentage on every item
in your inventory and bank, using technical analysis of real-time Grand Exchange
price data from the OSRS Wiki Prices API.

```
┌─────────────────────────┐
│  [item icon]  [item]    │
│  BUY          SELL      │
│  72%          58%       │
└─────────────────────────┘
```

---

## How the Signal Works

Choose an **Analysis bundle** in the plugin config. Each bundle fixes three things together: **playstyle** (flipping vs long-term merchanting), **Wiki API bar size and depth**, and **which model** runs on those candles.

### Flipping vs merchanting

| Playstyle | Typical use | How to read **BUY** / **SELL** on the overlay |
|-----------|-------------|-----------------------------------------------|
| **Flipping** | Short horizons (5m / 1h bars, about a day to a week of data) | **BUY** ≈ better to **buy now for a quick resell**; **SELL** ≈ better to **sell soon** (or skip buying) for a short-term exit |
| **Merchanting** | Daily bars over many weeks or up to a year | **BUY** ≈ **accumulation** vs long history; **SELL** ≈ **trim / take profit / avoid new buys** — not the same as “panic sell in the next hour” |

Labels stay **BUY / SELL / HOLD** for a compact UI; the bundle you pick sets the **time horizon** those words refer to.

### Bundles (default: *Flipping — week (1h), mean reversion*)

**Flipping**

| Bundle | Bars | Model |
|--------|------|--------|
| Flipping — day (5m), mean reversion | 5m, 288 | Mean reversion (range + VWAP vs window + contrarian velocity) |
| Flipping — week (1h), mean reversion | 1h, 168 | Same |
| Flipping — day (5m), classic TA | 5m, 288 | EMA crossover + RSI-14 + short vs long VWAP **trend** (momentum-style) |

**Merchanting**

| Bundle | Bars | Model |
|--------|------|--------|
| Merchanting — months (daily), mean reversion | 24h, 180 | Mean reversion on daily history |
| Merchanting — year (daily), mean reversion | 24h, 365 | Same, full API depth |
| Merchanting — z-score (daily, ~3 mo) | 24h, 95 | Rolling z-score vs 20-day SMA on mids (statistical distance from mean) |
| Merchanting — swing (daily), classic TA | 24h, 180 | EMA + RSI + VWAP trend on dailies |

### Models (detail)

**Mean reversion (flip model):** weighted combination — range position (40 %), full-window VWAP deviation (35 %), short-term velocity / fade the recent move (25 %).

**Classic TA:** EMA(6) vs EMA(24) (40 %), RSI-14 zones (35 %), VWAP(6) vs VWAP(24) trend (25 %) — momentum-oriented, not the same as mean reversion.

**Z-score:** single score from the latest mid vs mean and sample standard deviation of the last 20 dailies in the window; confidence scales with \|z\| (capped).

A **spread penalty** reduces confidence when the latest candle’s high–low gap is wide (illiquid items). Signals below **Minimum confidence** show as **HOLD**.

Price data is fetched from `prices.runescape.wiki/api/v1/osrs/timeseries` (timestep and point count come from the selected bundle; Wiki caps at 365 points per request) and cached for 5 minutes by default.

---

## Prerequisites

| Requirement | Version |
|-------------|---------|
| Java JDK    | 21 (Gradle 8.10 does not support JDK 25+) |
| IntelliJ IDEA | Community Edition 2023+ |
| Git         | Any recent version |
| Internet    | Required for the OSRS Wiki Prices API |
| Display     | RuneLite is a GUI app — must run on a machine with a screen (see note below) |

> **Running over SSH?** RuneLite requires a graphical display. If you connect to
> a headless Linux server via SSH you will get `HeadlessException: No X11 DISPLAY`.
> See [Running over SSH or a headless server](#running-over-ssh-or-a-headless-server) below.

**Install JDK 21 on Ubuntu/Debian (required — Gradle 8.10 does not support JDK 25+):**
```bash
sudo apt update && sudo apt install -y openjdk-21-jdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
java -version   # should print "openjdk 21..."
```

If you have multiple JDKs installed and your system default is JDK 25+, prefix all Gradle commands with the correct `JAVA_HOME`:
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew run
```

**Install IntelliJ IDEA Community:**
Download from https://www.jetbrains.com/idea/download/ or via JetBrains Toolbox.

---

## Local Testing — Step-by-Step

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/buySellIndicatorPlugin.git
cd buySellIndicatorPlugin
```

### 2. Open in IntelliJ IDEA

1. Launch IntelliJ IDEA.
2. Choose **File → Open** and select the `buySellIndicatorPlugin` folder.
3. IntelliJ will detect the `build.gradle` and ask to import as a Gradle project — click **Trust Project** and wait for Gradle to sync (it will download RuneLite and its dependencies; this may take 2–5 minutes on first run).

### 3. Enable Annotation Processing

The plugin uses Lombok for boilerplate reduction. You must enable annotation
processing or the project will not compile:

1. Go to **File → Settings** (or **IntelliJ IDEA → Preferences** on macOS).
2. Navigate to **Build, Execution, Deployment → Compiler → Annotation Processors**.
3. Check **Enable annotation processing**.
4. Click **OK** and let IntelliJ rebuild.

### 4. Set the JDK

1. Go to **File → Project Structure → Project**.
2. Under **SDK**, select **JDK 21** (required — JDK 25 is not supported by Gradle 8.10).
3. Set **Language level** to **11** (matching `build.gradle`).
4. Click **OK**.

### 5. Run the development client

#### Option A — Gradle (recommended, works without IntelliJ open):

```bash
./gradlew run
```

On Windows:
```cmd
gradlew.bat run
```

#### Option B — IntelliJ run configuration:

1. Open `src/test/java/com/buysell/BuySellPluginTest.java`.
2. Click the green **Run** arrow next to the `main` method.

Either way, the RuneLite client will launch in **developer mode** with the
Buy/Sell Indicator plugin already loaded.

### 6. Log in and test

1. Log in to **Old School RuneScape** (regular account or the official RuneLite
   test account – see RuneLite wiki for beta server options).
2. **Open your inventory** – after a few seconds you will see BUY / SELL / HOLD
   labels appear on each item.
3. **Open your bank** – the same signals appear on all banked items.

> First-time signals may take a few seconds per item while the API fetches
> timeseries data (depth depends on your **Analysis bundle**). A small `…` dot
> indicates a pending fetch.

### 7. Adjust settings

In the RuneLite sidebar (wrench icon → search "Buy/Sell"), you can configure:

| Setting | Default | Description |
|---------|---------|-------------|
| Show on Inventory | ✓ | Toggle overlay in inventory |
| Show on Bank | ✓ | Toggle overlay in bank |
| Minimum Confidence (%) | 40 | Signals below this show as HOLD |
| Font Size | Small | Small / Medium / Large |
| Cache Duration (minutes) | 5 | How long to cache price data |
| Analysis bundle | Flipping — week (1h), mean reversion | Playstyle + bar size + model (see above) |

---

## Troubleshooting

**"Could not resolve net.runelite:client:latest.release"**
- Make sure you have an internet connection and that the RuneLite Maven repo
  (`https://repo.runelite.net`) is not blocked by a firewall or proxy.

**No signals appear at all**
- Check the IntelliJ / terminal log for `WARN` lines from `PriceAnalysisService`.
- The OSRS Wiki API may be temporarily down. Wait a few minutes and reload.
- Make sure annotation processing is enabled (Lombok is required).

**Signals appear but seem wrong**
- The plugin uses technical analysis on GE prices, not game knowledge. Items
  with very low trade volume or unusual price spikes may produce noisy signals.
  Increase the Minimum Confidence threshold to filter out low-conviction signals.

**Client crashes on startup**
- Ensure your JDK is version 11 or higher. JDK 8 is not supported by RuneLite.
- Run `./gradlew run --info` to see verbose output and identify the root cause.

---

## Project Structure

```
buySellIndicatorPlugin/
├── build.gradle                        Gradle build + RuneLite dependency
├── runelite-plugin.properties          Plugin metadata (name, description, tags)
├── README.md                           This file
└── src/
    ├── main/java/com/buysell/
    │   ├── BuySellIndicatorPlugin.java  @PluginDescriptor entry point
    │   ├── BuySellIndicatorConfig.java  Config panel interface
    │   ├── BuySellIndicatorOverlay.java WidgetItemOverlay renderer
    │   ├── PriceAnalysisService.java    API + bundle dispatch + cache
    │   └── model/
    │       ├── Signal.java              BUY / SELL / HOLD enum
    │       └── SignalResult.java        Signal + confidence data class
    └── test/java/com/buysell/
        └── BuySellPluginTest.java       Dev client launcher
```

---

## Architecture Overview

```
Game Thread
    │  item IDs
    ▼
BuySellIndicatorOverlay (WidgetItemOverlay)
    │  cache lookup
    ▼
ConcurrentHashMap (5-min TTL)
    │  cache miss → async submit
    ▼
PriceAnalysisService (background thread)
    │  HTTP GET /timeseries?id=X&timestep from bundle
    ▼
OSRS Wiki Prices API  →  trimmed candle list
    │
    ▼
Mean reversion OR classic TA OR z-score (per bundle)
    │  spread penalty applied
    ▼
SignalResult { signal, confidence }  →  cache  →  overlay  →  screen
```

---

## License

MIT — feel free to fork, modify, and submit to the RuneLite Plugin Hub.
