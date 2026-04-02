package com.buysell;

import com.buysell.model.Signal;
import com.buysell.model.SignalResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches hourly price timeseries from the OSRS Wiki Prices API and runs a
 * three-factor technical analysis model (EMA crossover, RSI-14, VWAP trend)
 * to produce a BUY / SELL / HOLD signal with a confidence percentage.
 *
 * Results are cached per item for a configurable number of minutes to avoid
 * hammering the API. All network I/O and computation runs on a background
 * thread pool; the overlay reads results non-blocking via the cache map.
 */
@Slf4j
@Singleton
public class PriceAnalysisService
{
    private static final String BASE_URL = "https://prices.runescape.wiki/api/v1/osrs";
    private static final String USER_AGENT = "BuySellIndicatorPlugin - RuneLite external plugin";

    // Weights must sum to 1.0
    private static final double WEIGHT_EMA = 0.40;
    private static final double WEIGHT_RSI = 0.35;
    private static final double WEIGHT_VWAP = 0.25;

    private static final int EMA_FAST = 6;
    private static final int EMA_SLOW = 24;
    private static final int RSI_PERIOD = 14;
    private static final int VWAP_SHORT = 6;
    private static final int VWAP_LONG = 24;

    /** Minimum candles needed to compute all indicators. */
    private static final int MIN_CANDLES = 28;

    private final OkHttpClient httpClient;
    private final BuySellIndicatorConfig config;

    /** Thread-safe result cache: itemId → SignalResult */
    private final Map<Integer, SignalResult> cache = new ConcurrentHashMap<>();

    /** Items currently being fetched to avoid duplicate in-flight requests. */
    private final Set<Integer> inFlight = ConcurrentHashMap.newKeySet();

    private final ExecutorService executor = Executors.newCachedThreadPool(r ->
    {
        Thread t = new Thread(r, "buysell-analysis");
        t.setDaemon(true);
        return t;
    });

    @Inject
    public PriceAnalysisService(OkHttpClient httpClient, BuySellIndicatorConfig config)
    {
        this.httpClient = httpClient;
        this.config = config;
    }

    /**
     * Returns a cached SignalResult if one is fresh, otherwise triggers a
     * background fetch and returns null (the overlay should show a loading state).
     */
    public SignalResult getSignal(int itemId)
    {
        SignalResult cached = cache.get(itemId);
        long ttlMs = (long) config.cacheMinutes() * 60_000L;

        if (cached != null && (System.currentTimeMillis() - cached.getComputedAtMs()) < ttlMs)
        {
            return cached;
        }

        // Trigger async fetch only once per item
        if (inFlight.add(itemId))
        {
            executor.submit(() -> fetchAndAnalyse(itemId));
        }

        // Return stale data while refreshing, or null if never loaded
        return cached;
    }

    /** Evicts all cached entries, forcing a fresh fetch on next access. */
    public void clearCache()
    {
        cache.clear();
    }

    // -------------------------------------------------------------------------
    // Private: network + analysis
    // -------------------------------------------------------------------------

    private void fetchAndAnalyse(int itemId)
    {
        try
        {
            List<Candle> candles = fetchTimeseries(itemId);
            SignalResult result;

            if (candles == null || candles.size() < MIN_CANDLES)
            {
                log.debug("Not enough candles for item {} (got {})", itemId,
                    candles == null ? 0 : candles.size());
                result = SignalResult.hold();
            }
            else
            {
                result = analyse(candles);
                // Apply minimum confidence threshold from config
                if (result.getConfidence() < config.minConfidence())
                {
                    result = new SignalResult(Signal.HOLD, result.getConfidence(),
                        result.getComputedAtMs());
                }
            }

            cache.put(itemId, result);
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch/analyse item {}: {}", itemId, e.getMessage());
            // Cache a HOLD so we don't spam the API on every render frame
            cache.put(itemId, SignalResult.hold());
        }
        finally
        {
            inFlight.remove(itemId);
        }
    }

    private List<Candle> fetchTimeseries(int itemId) throws IOException
    {
        String url = BASE_URL + "/timeseries?id=" + itemId + "&timestep=1h";
        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                log.warn("Wiki API returned {} for item {}", response.code(), itemId);
                return null;
            }

            String body = response.body().string();
            return parseCandles(body);
        }
    }

    private List<Candle> parseCandles(String json)
    {
        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        JsonArray data = root.getAsJsonArray("data");

        if (data == null || data.size() == 0)
        {
            return null;
        }

        List<Candle> candles = new ArrayList<>(data.size());
        for (JsonElement el : data)
        {
            JsonObject obj = el.getAsJsonObject();

            // API may return null for either price if no trades occurred in that period
            JsonElement highEl = obj.get("avgHighPrice");
            JsonElement lowEl = obj.get("avgLowPrice");
            JsonElement highVolEl = obj.get("highPriceVolume");
            JsonElement lowVolEl = obj.get("lowPriceVolume");

            if (highEl == null || highEl.isJsonNull()
                || lowEl == null || lowEl.isJsonNull())
            {
                continue;
            }

            double high = highEl.getAsDouble();
            double low = lowEl.getAsDouble();
            long highVol = (highVolEl != null && !highVolEl.isJsonNull())
                ? highVolEl.getAsLong() : 0L;
            long lowVol = (lowVolEl != null && !lowVolEl.isJsonNull())
                ? lowVolEl.getAsLong() : 0L;

            if (high > 0 && low > 0)
            {
                candles.add(new Candle(high, low, highVol, lowVol));
            }
        }

        return candles.isEmpty() ? null : candles;
    }

    // -------------------------------------------------------------------------
    // Private: technical analysis
    // -------------------------------------------------------------------------

    private SignalResult analyse(List<Candle> candles)
    {
        double[] mid = midPrices(candles);

        // ── EMA Crossover ────────────────────────────────────────────────────
        double emaFast = ema(mid, EMA_FAST);
        double emaSlow = ema(mid, EMA_SLOW);
        Signal emaSignal = emaFast > emaSlow ? Signal.BUY : Signal.SELL;
        // Confidence: how far apart the EMAs are relative to slow EMA, capped at 1.0
        double emaConf = Math.min(Math.abs(emaFast - emaSlow) / emaSlow * 20.0, 1.0);

        // ── RSI-14 ───────────────────────────────────────────────────────────
        double rsi = rsi(mid, RSI_PERIOD);
        Signal rsiSignal;
        double rsiConf;
        if (rsi <= 30)
        {
            rsiSignal = Signal.BUY;
            rsiConf = (30.0 - rsi) / 30.0; // 0→30 RSI = 0→100% conf
        }
        else if (rsi >= 70)
        {
            rsiSignal = Signal.SELL;
            rsiConf = (rsi - 70.0) / 30.0;
        }
        else if (rsi < 50)
        {
            rsiSignal = Signal.BUY;
            rsiConf = (50.0 - rsi) / 20.0 * 0.5; // weaker, max 50%
        }
        else
        {
            rsiSignal = Signal.SELL;
            rsiConf = (rsi - 50.0) / 20.0 * 0.5;
        }

        // ── VWAP Trend ───────────────────────────────────────────────────────
        double vwapShort = vwap(candles, VWAP_SHORT);
        double vwapLong = vwap(candles, VWAP_LONG);
        Signal vwapSignal = vwapShort > vwapLong ? Signal.BUY : Signal.SELL;
        double vwapConf = Math.min(Math.abs(vwapShort - vwapLong) / vwapLong * 15.0, 1.0);

        // ── Weighted combination ─────────────────────────────────────────────
        double buyScore = 0.0;
        double sellScore = 0.0;

        buyScore  += (emaSignal  == Signal.BUY)  ? WEIGHT_EMA  * emaConf  : 0;
        sellScore += (emaSignal  == Signal.SELL) ? WEIGHT_EMA  * emaConf  : 0;
        buyScore  += (rsiSignal  == Signal.BUY)  ? WEIGHT_RSI  * rsiConf  : 0;
        sellScore += (rsiSignal  == Signal.SELL) ? WEIGHT_RSI  * rsiConf  : 0;
        buyScore  += (vwapSignal == Signal.BUY)  ? WEIGHT_VWAP * vwapConf : 0;
        sellScore += (vwapSignal == Signal.SELL) ? WEIGHT_VWAP * vwapConf : 0;

        Signal dominant;
        double rawConf;
        if (buyScore >= sellScore)
        {
            dominant = Signal.BUY;
            rawConf = buyScore;
        }
        else
        {
            dominant = Signal.SELL;
            rawConf = sellScore;
        }

        // ── Spread penalty ───────────────────────────────────────────────────
        // Items with a very wide bid/ask spread have less reliable signals
        double latestMid = mid[mid.length - 1];
        Candle latest = candles.get(candles.size() - 1);
        double spread = (latest.getHigh() - latest.getLow()) / latestMid;
        // Penalise up to 50% for a 10%+ spread
        double spreadPenalty = Math.min(spread * 5.0, 0.5);

        double finalConf = rawConf * (1.0 - spreadPenalty) * 100.0;
        finalConf = Math.max(0, Math.min(100, finalConf));

        return new SignalResult(dominant, finalConf, System.currentTimeMillis());
    }

    /** Compute mid prices array from candle list. */
    private double[] midPrices(List<Candle> candles)
    {
        double[] mid = new double[candles.size()];
        for (int i = 0; i < candles.size(); i++)
        {
            Candle c = candles.get(i);
            mid[i] = (c.getHigh() + c.getLow()) / 2.0;
        }
        return mid;
    }

    /**
     * Exponential Moving Average of the last {@code period} values.
     * Uses a standard smoothing factor k = 2 / (period + 1).
     */
    private double ema(double[] prices, int period)
    {
        int start = Math.max(0, prices.length - period * 3); // warm-up window
        double k = 2.0 / (period + 1);
        double emaVal = prices[start];
        for (int i = start + 1; i < prices.length; i++)
        {
            emaVal = prices[i] * k + emaVal * (1.0 - k);
        }
        return emaVal;
    }

    /**
     * Wilder's RSI over the last {@code period + 1} values.
     * Returns a value in [0, 100].
     */
    private double rsi(double[] prices, int period)
    {
        int len = prices.length;
        int start = Math.max(0, len - period - 1);

        double avgGain = 0;
        double avgLoss = 0;

        // Initial averages over first 'period' changes
        int initEnd = Math.min(start + period, len - 1);
        for (int i = start + 1; i <= initEnd; i++)
        {
            double change = prices[i] - prices[i - 1];
            if (change > 0)
            {
                avgGain += change;
            }
            else
            {
                avgLoss += Math.abs(change);
            }
        }
        avgGain /= period;
        avgLoss /= period;

        // Wilder smoothing for remaining values
        for (int i = initEnd + 1; i < len; i++)
        {
            double change = prices[i] - prices[i - 1];
            double gain = change > 0 ? change : 0;
            double loss = change < 0 ? Math.abs(change) : 0;
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }

        if (avgLoss == 0)
        {
            return 100;
        }
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    /**
     * Volume-Weighted Average Price over the last {@code window} candles.
     * Uses (high+low)/2 as the typical price proxy.
     */
    private double vwap(List<Candle> candles, int window)
    {
        int start = Math.max(0, candles.size() - window);
        double sumPV = 0;
        long sumVol = 0;

        for (int i = start; i < candles.size(); i++)
        {
            Candle c = candles.get(i);
            long vol = c.getHighVol() + c.getLowVol();
            double typicalPrice = (c.getHigh() + c.getLow()) / 2.0;
            sumPV += typicalPrice * vol;
            sumVol += vol;
        }

        if (sumVol == 0)
        {
            // Fall back to simple average if no volume data
            double sum = 0;
            for (int i = start; i < candles.size(); i++)
            {
                Candle c = candles.get(i);
                sum += (c.getHigh() + c.getLow()) / 2.0;
            }
            return sum / (candles.size() - start);
        }

        return sumPV / sumVol;
    }

    // -------------------------------------------------------------------------
    // Inner data class
    // -------------------------------------------------------------------------

    @lombok.Value
    private static class Candle
    {
        double high;
        double low;
        long highVol;
        long lowVol;
    }
}
