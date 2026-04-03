package com.buysell;

import com.buysell.model.Signal;
import com.buysell.model.SignalResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
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
 * Fetches price timeseries from the OSRS Wiki Prices API and runs the analysis
 * model selected by {@link BuySellIndicatorConfig.AnalysisBundle}: flip-style
 * mean reversion, classic momentum TA, or rolling z-score. Bundles are grouped
 * into flipping (short horizons) vs merchanting (daily, long windows); signal
 * interpretation depends on the chosen bundle (see README).
 *
 * <p>The Wiki {@code /timeseries} endpoint returns at most 365 data points per request.
 */
@Slf4j
@Singleton
public class PriceAnalysisService
{
    /** OSRS Wiki Prices API caps timeseries responses at 365 data points. */
    private static final int WIKI_TIMESERIES_MAX_POINTS = 365;

    private static final int MIN_CANDLES_FOR_FLIP = 8;

    /** Classic TA: EMA fast/slow, RSI, VWAP short/long (aligned with prior plugin presets). */
    private static final int CLASSIC_EMA_FAST = 6;
    private static final int CLASSIC_EMA_SLOW = 24;
    private static final int CLASSIC_RSI_PERIOD = 14;
    private static final int CLASSIC_VWAP_SHORT = 6;
    private static final int CLASSIC_VWAP_LONG = 24;

    private static final int CLASSIC_MIN_CANDLES = Math.max(
        CLASSIC_RSI_PERIOD + 1,
        Math.max(CLASSIC_VWAP_LONG, CLASSIC_EMA_SLOW));

    private static final double ZSCORE_CAP = 2.0;

    private static final String BASE_URL = "https://prices.runescape.wiki/api/v1/osrs";

    private static final String USER_AGENT =
        "BuySellIndicator/1.0 (RuneLite plugin; OSRS Wiki GE timeseries; maintainer's discord: neonic1996)";

    private static final double WEIGHT_RANGE = 0.40;
    private static final double WEIGHT_VWAP_DEV = 0.35;
    private static final double WEIGHT_VELOCITY = 0.25;

    private static final double WEIGHT_EMA = 0.40;
    private static final double WEIGHT_RSI = 0.35;
    private static final double WEIGHT_VWAP_TREND = 0.25;

    private final OkHttpClient httpClient;
    private final BuySellIndicatorConfig config;

    private final Map<Integer, SignalResult> cache = new ConcurrentHashMap<>();

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

    public SignalResult getSignal(int itemId)
    {
        SignalResult cached = cache.get(itemId);
        long ttlMs = (long) config.cacheMinutes() * 60_000L;
        long now = System.currentTimeMillis();

        if (cached != null && (now - cached.getComputedAtMs()) < ttlMs)
        {
            log.trace("getSignal cache hit itemId={} ageMs={} ttlMs={}",
                itemId, now - cached.getComputedAtMs(), ttlMs);
            return cached;
        }

        if (inFlight.add(itemId))
        {
            log.trace("getSignal cache miss, scheduling fetch itemId={}", itemId);
            executor.submit(() -> fetchAndAnalyse(itemId));
        }
        else
        {
            log.trace("getSignal fetch already in-flight itemId={}", itemId);
        }

        return cached;
    }

    /**
     * Returns a cached analysis result without scheduling a fetch. Used by bank filtering so the
     * client thread never triggers network work from script callbacks.
     */
    public SignalResult getCachedSignal(int itemId)
    {
        return cache.get(itemId);
    }

    public void clearCache()
    {
        int n = cache.size();
        cache.clear();
        log.debug("clearCache removed {} entries", n);
    }

    private static int minCandlesRequired(BuySellIndicatorConfig.AnalysisBundle bundle)
    {
        switch (bundle.getModel())
        {
            case FLIP:
                return Math.min(MIN_CANDLES_FOR_FLIP, bundle.getMaxCandles());
            case CLASSIC_TA:
                return Math.min(CLASSIC_MIN_CANDLES, bundle.getMaxCandles());
            case ZSCORE:
                int w = bundle.getZScoreSmaPeriod();
                return Math.min(w + 2, bundle.getMaxCandles());
            default:
                return MIN_CANDLES_FOR_FLIP;
        }
    }

    private void fetchAndAnalyse(int itemId)
    {
        try
        {
            List<Candle> candles = fetchTimeseries(itemId);
            SignalResult result;
            BuySellIndicatorConfig.AnalysisBundle bundle = config.analysisBundle();
            int minCandles = minCandlesRequired(bundle);

            if (candles == null || candles.size() < minCandles)
            {
                log.debug("Not enough candles for item {} (got {}, need {})", itemId,
                    candles == null ? 0 : candles.size(), minCandles);
                result = SignalResult.hold();
            }
            else
            {
                Candle last = candles.get(candles.size() - 1);
                double latestMid = (last.getHigh() + last.getLow()) / 2.0;
                int minPrice = config.minItemPrice();
                int maxPrice = config.maxItemPrice();
                boolean belowMin = minPrice > 0 && latestMid < minPrice;
                boolean aboveMax = maxPrice > 0 && latestMid > maxPrice;

                if (belowMin || aboveMax)
                {
                    log.debug("Item {} outside price threshold latestMid={} minPrice={} maxPrice={}",
                        itemId, latestMid, minPrice, maxPrice);
                    result = new SignalResult(Signal.FILTERED, 0.0, System.currentTimeMillis());
                }
                else
                {
                    result = analyse(candles, bundle);
                    log.debug("Analysis result itemId={} signal={} confidence={} bundle={}",
                        itemId, result.getSignal(), result.getConfidence(), bundle);
                    if (result.getConfidence() < config.minConfidence())
                    {
                        log.debug("Downgrading to HOLD (below minConfidence) itemId={} confidence={} minConfidence={}",
                            itemId, result.getConfidence(), config.minConfidence());
                        result = new SignalResult(Signal.HOLD, result.getConfidence(),
                            result.getComputedAtMs());
                    }
                }
            }

            cache.put(itemId, result);
            log.debug("Cached signal itemId={} signal={} confidence={}",
                itemId, result.getSignal(), result.getConfidence());
        }
        catch (Exception e)
        {
            log.warn("Failed to fetch/analyse item {}: {}", itemId, e.getMessage());
            cache.put(itemId, SignalResult.hold());
        }
        finally
        {
            inFlight.remove(itemId);
        }
    }

    private List<Candle> fetchTimeseries(int itemId) throws IOException
    {
        String timestep = config.analysisBundle().getApiTimestep();
        String url = BASE_URL + "/timeseries?id=" + itemId + "&timestep=" + timestep;
        log.debug("fetchTimeseries GET {}", url);
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

    List<Candle> parseCandles(String json)
    {
        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        JsonElement dataEl = root.get("data");
        if (dataEl == null || !dataEl.isJsonArray())
        {
            return null;
        }
        JsonArray data = dataEl.getAsJsonArray();
        log.trace("parseCandles raw data array size={}", data.size());

        if (data.size() == 0)
        {
            return null;
        }

        List<Candle> candles = new ArrayList<>(data.size());
        int skipped = 0;
        for (JsonElement el : data)
        {
            JsonObject obj = el.getAsJsonObject();

            JsonElement highEl = obj.get("avgHighPrice");
            JsonElement lowEl = obj.get("avgLowPrice");
            JsonElement highVolEl = obj.get("highPriceVolume");
            JsonElement lowVolEl = obj.get("lowPriceVolume");

            if (highEl == null || highEl.isJsonNull()
                || lowEl == null || lowEl.isJsonNull())
            {
                skipped++;
                log.trace("parseCandles skipped candle (null high/low) skipCount={}", skipped);
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
            else
            {
                skipped++;
                log.trace("parseCandles skipped candle (non-positive price) high={} low={} skipCount={}",
                    high, low, skipped);
            }
        }

        log.trace("parseCandles parsed {} candles (skipped {})", candles.size(), skipped);
        return candles.isEmpty() ? null : candles;
    }

    SignalResult analyse(List<Candle> candles, BuySellIndicatorConfig.AnalysisBundle bundle)
    {
        int effectiveMax = Math.min(WIKI_TIMESERIES_MAX_POINTS, bundle.getMaxCandles());
        if (candles.size() > effectiveMax)
        {
            candles = new ArrayList<>(candles.subList(candles.size() - effectiveMax, candles.size()));
        }

        log.debug("analyse candleCount={} effectiveMax={} bundle={} model={}",
            candles.size(), effectiveMax, bundle, bundle.getModel());

        switch (bundle.getModel())
        {
            case FLIP:
                return analyseFlip(candles);
            case CLASSIC_TA:
                return analyseClassicTa(candles);
            case ZSCORE:
                return analyseZScore(candles, bundle.getZScoreSmaPeriod());
            default:
                log.error("Unhandled analysis model: {}", bundle.getModel());
                throw new IllegalStateException("Unhandled model: " + bundle.getModel());
        }
    }

    SignalResult analyseFlip(List<Candle> candles)
    {
        double[] mid = midPrices(candles);
        int n = mid.length;
        double currentPrice = mid[n - 1];

        double periodMin = minMid(mid);
        double periodMax = maxMid(mid);
        double rangePos;
        if (periodMax <= periodMin)
        {
            rangePos = 0.5;
        }
        else
        {
            rangePos = (currentPrice - periodMin) / (periodMax - periodMin);
        }
        Signal rangeSignal = rangePos < 0.5 ? Signal.BUY : Signal.SELL;
        double rangeConf = Math.abs(rangePos - 0.5) / 0.5;

        double vwapFull = vwap(candles, n);
        double vwapDev = vwapFull > 0 ? (currentPrice - vwapFull) / vwapFull : 0.0;
        Signal vwapDevSignal;
        if (vwapDev < 0)
        {
            vwapDevSignal = Signal.BUY;
        }
        else if (vwapDev > 0)
        {
            vwapDevSignal = Signal.SELL;
        }
        else
        {
            vwapDevSignal = null;
        }
        double vwapDevConf = Math.min(Math.abs(vwapDev) * 10.0, 1.0);

        VelocityResult vel = priceVelocity(mid);
        Signal velSignal = vel.getSignal();
        double velConf = vel.getConfidence();

        double buyScore = 0.0;
        double sellScore = 0.0;

        buyScore += (rangeSignal == Signal.BUY) ? WEIGHT_RANGE * rangeConf : 0;
        sellScore += (rangeSignal == Signal.SELL) ? WEIGHT_RANGE * rangeConf : 0;

        if (vwapDevSignal != null)
        {
            buyScore += (vwapDevSignal == Signal.BUY) ? WEIGHT_VWAP_DEV * vwapDevConf : 0;
            sellScore += (vwapDevSignal == Signal.SELL) ? WEIGHT_VWAP_DEV * vwapDevConf : 0;
        }

        if (velSignal != null)
        {
            buyScore += (velSignal == Signal.BUY) ? WEIGHT_VELOCITY * velConf : 0;
            sellScore += (velSignal == Signal.SELL) ? WEIGHT_VELOCITY * velConf : 0;
        }

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

        log.trace("analyseFlip rangePos={} rangeConf={} vwapDev={} velSignal={} velConf={} buyScore={} sellScore={} dominant={} rawConf={}",
            rangePos, rangeConf, vwapDev, velSignal, velConf, buyScore, sellScore, dominant, rawConf);
        return finalizeWithSpreadPenalty(dominant, rawConf, candles, currentPrice);
    }

    SignalResult analyseClassicTa(List<Candle> candles)
    {
        double[] mid = midPrices(candles);

        double emaFast = ema(mid, CLASSIC_EMA_FAST);
        double emaSlow = ema(mid, CLASSIC_EMA_SLOW);
        Signal emaSignal = emaFast > emaSlow ? Signal.BUY : Signal.SELL;
        double emaConf = emaSlow > 0
            ? Math.min(Math.abs(emaFast - emaSlow) / emaSlow * 20.0, 1.0) : 0.0;

        double rsi = rsi(mid, CLASSIC_RSI_PERIOD);
        Signal rsiSignal;
        double rsiConf;
        if (rsi <= 30)
        {
            rsiSignal = Signal.BUY;
            rsiConf = (30.0 - rsi) / 30.0;
        }
        else if (rsi >= 70)
        {
            rsiSignal = Signal.SELL;
            rsiConf = (rsi - 70.0) / 30.0;
        }
        else if (rsi < 50)
        {
            rsiSignal = Signal.BUY;
            rsiConf = (50.0 - rsi) / 20.0 * 0.5;
        }
        else
        {
            rsiSignal = Signal.SELL;
            rsiConf = (rsi - 50.0) / 20.0 * 0.5;
        }

        double vwapShort = vwap(candles, CLASSIC_VWAP_SHORT);
        double vwapLong = vwap(candles, CLASSIC_VWAP_LONG);
        Signal vwapSignal = null;
        double vwapConf = 0.0;
        if (vwapLong > 0)
        {
            vwapSignal = vwapShort > vwapLong ? Signal.BUY : Signal.SELL;
            vwapConf = Math.min(Math.abs(vwapShort - vwapLong) / vwapLong * 15.0, 1.0);
        }

        double buyScore = 0.0;
        double sellScore = 0.0;

        buyScore += (emaSignal == Signal.BUY) ? WEIGHT_EMA * emaConf : 0;
        sellScore += (emaSignal == Signal.SELL) ? WEIGHT_EMA * emaConf : 0;
        buyScore += (rsiSignal == Signal.BUY) ? WEIGHT_RSI * rsiConf : 0;
        sellScore += (rsiSignal == Signal.SELL) ? WEIGHT_RSI * rsiConf : 0;
        if (vwapSignal != null)
        {
            buyScore += (vwapSignal == Signal.BUY) ? WEIGHT_VWAP_TREND * vwapConf : 0;
            sellScore += (vwapSignal == Signal.SELL) ? WEIGHT_VWAP_TREND * vwapConf : 0;
        }

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

        double currentPrice = mid[mid.length - 1];
        log.trace("analyseClassicTa emaFast={} emaSlow={} emaSignal={} emaConf={} rsi={} rsiSignal={} rsiConf={} vwapShort={} vwapLong={} vwapSignal={} vwapConf={} buyScore={} sellScore={} dominant={} rawConf={}",
            emaFast, emaSlow, emaSignal, emaConf, rsi, rsiSignal, rsiConf, vwapShort, vwapLong, vwapSignal, vwapConf, buyScore, sellScore, dominant, rawConf);
        return finalizeWithSpreadPenalty(dominant, rawConf, candles, currentPrice);
    }

    SignalResult analyseZScore(List<Candle> candles, int window)
    {
        double[] mid = midPrices(candles);
        int n = mid.length;
        if (window < 2 || n < window)
        {
            return new SignalResult(Signal.HOLD, 0.0, System.currentTimeMillis());
        }

        int start = n - window;
        double sum = 0.0;
        for (int i = start; i < n; i++)
        {
            sum += mid[i];
        }
        double mean = sum / window;

        double varSum = 0.0;
        for (int i = start; i < n; i++)
        {
            double d = mid[i] - mean;
            varSum += d * d;
        }
        double std = Math.sqrt(varSum / (window - 1));
        double currentPrice = mid[n - 1];

        if (std <= 0 || Double.isNaN(std))
        {
            return finalizeWithSpreadPenalty(Signal.HOLD, 0.0, candles, currentPrice);
        }

        double z = (currentPrice - mean) / std;
        Signal dominant;
        if (z < 0)
        {
            dominant = Signal.BUY;
        }
        else if (z > 0)
        {
            dominant = Signal.SELL;
        }
        else
        {
            dominant = Signal.HOLD;
        }

        double rawConf = Math.min(Math.abs(z) / ZSCORE_CAP, 1.0);
        log.trace("analyseZScore z={} mean={} std={} window={} dominant={} rawConf={}",
            z, mean, std, window, dominant, rawConf);
        return finalizeWithSpreadPenalty(dominant, rawConf, candles, currentPrice);
    }

    private SignalResult finalizeWithSpreadPenalty(
        Signal dominant, double rawConf01, List<Candle> candles, double latestMid)
    {
        Candle latest = candles.get(candles.size() - 1);
        double spread = latestMid > 0 ? (latest.getHigh() - latest.getLow()) / latestMid : 0.0;
        double spreadPenalty = Math.min(spread * 5.0, 0.5);

        double finalConf = rawConf01 * (1.0 - spreadPenalty) * 100.0;
        finalConf = Math.max(0, Math.min(100, finalConf));

        log.trace("finalizeWithSpreadPenalty dominant={} rawConf01={} latestMid={} spread={} spreadPenalty={} finalConf={}",
            dominant, rawConf01, latestMid, spread, spreadPenalty, finalConf);
        return new SignalResult(dominant, finalConf, System.currentTimeMillis());
    }

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

    private static double minMid(double[] mid)
    {
        double m = mid[0];
        for (int i = 1; i < mid.length; i++)
        {
            if (mid[i] < m)
            {
                m = mid[i];
            }
        }
        return m;
    }

    private static double maxMid(double[] mid)
    {
        double m = mid[0];
        for (int i = 1; i < mid.length; i++)
        {
            if (mid[i] > m)
            {
                m = mid[i];
            }
        }
        return m;
    }

    private static VelocityResult priceVelocity(double[] mid)
    {
        int n = mid.length;
        int recentLen = Math.max(1, n / 4);
        int recentStart = n - recentLen;
        double recentSum = 0.0;
        for (int i = recentStart; i < n; i++)
        {
            recentSum += mid[i];
        }
        double recentAvg = recentSum / recentLen;

        int olderStart = n / 2;
        int olderEnd = (3 * n) / 4;
        double olderAvg;
        if (olderEnd <= olderStart)
        {
            olderAvg = recentAvg;
        }
        else
        {
            double olderSum = 0.0;
            int count = 0;
            for (int i = olderStart; i < olderEnd; i++)
            {
                olderSum += mid[i];
                count++;
            }
            olderAvg = count > 0 ? olderSum / count : recentAvg;
        }

        if (olderAvg <= 0)
        {
            return VelocityResult.neutral();
        }
        double velocity = (recentAvg - olderAvg) / olderAvg;
        if (velocity < 0)
        {
            return new VelocityResult(Signal.BUY, Math.min(Math.abs(velocity) * 8.0, 1.0));
        }
        if (velocity > 0)
        {
            return new VelocityResult(Signal.SELL, Math.min(Math.abs(velocity) * 8.0, 1.0));
        }
        return VelocityResult.neutral();
    }

    private double ema(double[] prices, int period)
    {
        int start = Math.max(0, prices.length - period * 3);
        double k = 2.0 / (period + 1);
        double emaVal = prices[start];
        for (int i = start + 1; i < prices.length; i++)
        {
            emaVal = prices[i] * k + emaVal * (1.0 - k);
        }
        return emaVal;
    }

    private double rsi(double[] prices, int period)
    {
        int len = prices.length;
        int start = Math.max(0, len - period - 1);

        double avgGain = 0;
        double avgLoss = 0;

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

    private static final class VelocityResult
    {
        private final Signal signal;
        private final double confidence;

        private VelocityResult(Signal signal, double confidence)
        {
            this.signal = signal;
            this.confidence = confidence;
        }

        static VelocityResult neutral()
        {
            return new VelocityResult(null, 0.0);
        }

        Signal getSignal()
        {
            return signal;
        }

        double getConfidence()
        {
            return confidence;
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PACKAGE)
    static class Candle
    {
        double high;
        double low;
        long highVol;
        long lowVol;
    }
}
