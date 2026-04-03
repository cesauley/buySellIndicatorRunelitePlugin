package com.buysell;

import com.buysell.model.Signal;
import com.buysell.model.SignalResult;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for analysis, JSON parsing, cache behaviour, and {@link BuySellIndicatorConfig.FilterMode}.
 */
public class PriceAnalysisServiceTest
{
    private static final int MIN_CANDLES_FLIP = 8;
    private static final int CLASSIC_MIN_CANDLES = 24;

    private BuySellIndicatorConfig config;

    @Before
    public void setUp()
    {
        config = mock(BuySellIndicatorConfig.class);
        when(config.cacheMinutes()).thenReturn(5);
        when(config.minConfidence()).thenReturn(0);
        when(config.analysisBundle()).thenReturn(BuySellIndicatorConfig.AnalysisBundle.FLIPPING_DAY_FLIP);
    }

    private static PriceAnalysisService.Candle c(double high, double low)
    {
        return new PriceAnalysisService.Candle(high, low, 1L, 1L);
    }

    private static PriceAnalysisService.Candle c(double high, double low, long highVol, long lowVol)
    {
        return new PriceAnalysisService.Candle(high, low, highVol, lowVol);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, SignalResult> cacheMap(PriceAnalysisService svc) throws Exception
    {
        Field f = PriceAnalysisService.class.getDeclaredField("cache");
        f.setAccessible(true);
        return (Map<Integer, SignalResult>) f.get(svc);
    }

    private static String timeseriesJson(int n, double high, double low)
    {
        StringBuilder sb = new StringBuilder("{\"data\":[");
        for (int i = 0; i < n; i++)
        {
            if (i > 0)
            {
                sb.append(',');
            }
            sb.append("{\"avgHighPrice\":").append(high)
                .append(",\"avgLowPrice\":").append(low)
                .append(",\"highPriceVolume\":1,\"lowPriceVolume\":1}");
        }
        sb.append("]}");
        return sb.toString();
    }

    // --- Group A: FilterMode ---

    @Test
    public void filterMode_next_cyclesThroughAllFourModes()
    {
        BuySellIndicatorConfig.FilterMode m = BuySellIndicatorConfig.FilterMode.OFF;
        m = m.next();
        assertEquals(BuySellIndicatorConfig.FilterMode.BUY_ONLY, m);
        m = m.next();
        assertEquals(BuySellIndicatorConfig.FilterMode.SELL_ONLY, m);
        m = m.next();
        assertEquals(BuySellIndicatorConfig.FilterMode.BUY_AND_SELL, m);
        m = m.next();
        assertEquals(BuySellIndicatorConfig.FilterMode.OFF, m);
    }

    @Test
    public void filterMode_buttonLabels()
    {
        assertEquals("\u2013", BuySellIndicatorConfig.FilterMode.OFF.getButtonLabel());
        assertEquals("B", BuySellIndicatorConfig.FilterMode.BUY_ONLY.getButtonLabel());
        assertEquals("S", BuySellIndicatorConfig.FilterMode.SELL_ONLY.getButtonLabel());
        assertEquals("BS", BuySellIndicatorConfig.FilterMode.BUY_AND_SELL.getButtonLabel());
    }

    // --- Group B: analyseFlip ---

    @Test
    public void analyseFlip_flatMarket_lowConfidence() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        List<PriceAnalysisService.Candle> list = new ArrayList<>();
        for (int i = 0; i < 10; i++)
        {
            list.add(c(1000, 1000));
        }
        SignalResult r = svc.analyseFlip(list);
        assertNotNull(r);
        assertTrue("flat range should yield very low confidence", r.getConfidence() < 15.0);
    }

    @Test
    public void analyseFlip_lastAtPeriodMin_isBuy() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        List<PriceAnalysisService.Candle> list = new ArrayList<>();
        for (int i = 0; i < 19; i++)
        {
            list.add(c(200, 200));
        }
        list.add(c(100, 100));
        SignalResult r = svc.analyseFlip(list);
        assertEquals(Signal.BUY, r.getSignal());
    }

    @Test
    public void analyseFlip_lastAtPeriodMax_isSell() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        List<PriceAnalysisService.Candle> list = new ArrayList<>();
        for (int i = 0; i < 19; i++)
        {
            list.add(c(100, 100));
        }
        list.add(c(200, 200));
        SignalResult r = svc.analyseFlip(list);
        assertEquals(Signal.SELL, r.getSignal());
    }

    @Test
    public void analyseFlip_exactlyMinCandles_doesNotThrow() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        List<PriceAnalysisService.Candle> list = new ArrayList<>();
        for (int i = 0; i < MIN_CANDLES_FLIP; i++)
        {
            list.add(c(100 + i, 100 + i));
        }
        SignalResult r = svc.analyseFlip(list);
        assertNotNull(r);
    }

    @Test
    public void fetchAndAnalyse_oneCandle_returnsHold() throws Exception
    {
        OkHttpClient http = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        Response response = mock(Response.class);
        ResponseBody body = mock(ResponseBody.class);
        when(http.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(body);
        when(body.string()).thenReturn(timeseriesJson(1, 100, 100));

        PriceAnalysisService svc = new PriceAnalysisService(http, config);
        java.lang.reflect.Method m = PriceAnalysisService.class.getDeclaredMethod("fetchAndAnalyse", int.class);
        m.setAccessible(true);
        m.invoke(svc, 4151);

        Thread.sleep(300);
        SignalResult cached = svc.getCachedSignal(4151);
        assertNotNull(cached);
        assertEquals(Signal.HOLD, cached.getSignal());
    }

    @Test
    public void analyseFlip_zeroVolume_usesVwapFallback() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        List<PriceAnalysisService.Candle> list = new ArrayList<>();
        for (int i = 0; i < 10; i++)
        {
            list.add(c(100 + i * 2, 100 + i * 2, 0L, 0L));
        }
        SignalResult r = svc.analyseFlip(list);
        assertNotNull(r);
    }

    @Test
    public void analyseFlip_highSpread_reducesConfidenceVsTightSpread() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        List<PriceAnalysisService.Candle> tight = new ArrayList<>();
        List<PriceAnalysisService.Candle> wide = new ArrayList<>();
        for (int i = 0; i < 15; i++)
        {
            double base = 1000 + i * 10;
            tight.add(c(base + 1, base - 1));
            wide.add(c(base * 1.25, base * 0.75));
        }
        double tightConf = svc.analyseFlip(tight).getConfidence();
        double wideConf = svc.analyseFlip(wide).getConfidence();
        assertTrue("wide spread should not increase confidence vs tight", wideConf <= tightConf + 1.0);
    }

    // --- Group C: analyseClassicTa ---

    @Test
    public void analyseClassicTa_risingPrices_tendsBuy() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        List<PriceAnalysisService.Candle> list = new ArrayList<>();
        for (int i = 0; i < 30; i++)
        {
            double p = 100 + i * 5;
            list.add(c(p, p));
        }
        SignalResult r = svc.analyseClassicTa(list);
        assertEquals(Signal.BUY, r.getSignal());
    }

    @Test
    public void analyseClassicTa_fallingPrices_tendsSell() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        List<PriceAnalysisService.Candle> list = new ArrayList<>();
        for (int i = 0; i < 30; i++)
        {
            double p = 300 - i * 5;
            list.add(c(p, p));
        }
        SignalResult r = svc.analyseClassicTa(list);
        assertEquals(Signal.SELL, r.getSignal());
    }

    @Test
    public void analyseClassicTa_allEqualPrices_doesNotThrow() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        List<PriceAnalysisService.Candle> list = new ArrayList<>();
        for (int i = 0; i < 30; i++)
        {
            list.add(c(100, 100));
        }
        SignalResult r = svc.analyseClassicTa(list);
        assertNotNull(r);
    }

    @Test
    public void analyseClassicTa_exactlyMinCandles_doesNotThrow() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        List<PriceAnalysisService.Candle> list = new ArrayList<>();
        for (int i = 0; i < CLASSIC_MIN_CANDLES; i++)
        {
            list.add(c(100 + i, 100 + i));
        }
        assertNotNull(svc.analyseClassicTa(list));
    }

    // --- Group D: analyseZScore ---

    @Test
    public void analyseZScore_priceBelowMean_isBuy() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        List<PriceAnalysisService.Candle> list = new ArrayList<>();
        for (int i = 0; i < 19; i++)
        {
            list.add(c(200, 200));
        }
        list.add(c(50, 50));
        SignalResult r = svc.analyseZScore(list, 20);
        assertEquals(Signal.BUY, r.getSignal());
    }

    @Test
    public void analyseZScore_priceAboveMean_isSell() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        List<PriceAnalysisService.Candle> list = new ArrayList<>();
        for (int i = 0; i < 19; i++)
        {
            list.add(c(100, 100));
        }
        list.add(c(500, 500));
        SignalResult r = svc.analyseZScore(list, 20);
        assertEquals(Signal.SELL, r.getSignal());
    }

    @Test
    public void analyseZScore_allEqual_stdZero_returnsHold() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        List<PriceAnalysisService.Candle> list = new ArrayList<>();
        for (int i = 0; i < 20; i++)
        {
            list.add(c(100, 100));
        }
        SignalResult r = svc.analyseZScore(list, 20);
        assertEquals(Signal.HOLD, r.getSignal());
        assertEquals(0.0, r.getConfidence(), 0.001);
    }

    @Test
    public void analyseZScore_windowTooSmall_returnsHold() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        List<PriceAnalysisService.Candle> list = new ArrayList<>();
        for (int i = 0; i < 10; i++)
        {
            list.add(c(100, 100));
        }
        SignalResult r = svc.analyseZScore(list, 1);
        assertEquals(Signal.HOLD, r.getSignal());
    }

    @Test
    public void analyseZScore_notEnoughCandles_returnsHold() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        List<PriceAnalysisService.Candle> list = new ArrayList<>();
        for (int i = 0; i < 5; i++)
        {
            list.add(c(100, 100));
        }
        SignalResult r = svc.analyseZScore(list, 20);
        assertEquals(Signal.HOLD, r.getSignal());
    }

    @Test
    public void analyseZScore_extremeZ_confidenceCapped() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        List<PriceAnalysisService.Candle> list = new ArrayList<>();
        for (int i = 0; i < 19; i++)
        {
            list.add(c(100, 100));
        }
        list.add(c(10000, 10000));
        SignalResult r = svc.analyseZScore(list, 20);
        assertTrue(r.getConfidence() <= 100.0 + 1e-6);
    }

    // --- Group E: parseCandles ---

    @Test
    public void parseCandles_wellFormed() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        String json = "{\"data\":[{\"avgHighPrice\":120,\"avgLowPrice\":80,\"highPriceVolume\":1,\"lowPriceVolume\":1}]}";
        List<PriceAnalysisService.Candle> list = svc.parseCandles(json);
        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals(120.0, list.get(0).getHigh(), 0.001);
        assertEquals(80.0, list.get(0).getLow(), 0.001);
    }

    @Test
    public void parseCandles_missingDataKey_returnsNull() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        assertNull(svc.parseCandles("{}"));
    }

    @Test
    public void parseCandles_emptyArray_returnsNull() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        assertNull(svc.parseCandles("{\"data\":[]}"));
    }

    @Test
    public void parseCandles_allNullHighLow_returnsNull() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        String json = "{\"data\":["
            + "{\"avgHighPrice\":null,\"avgLowPrice\":100},"
            + "{\"avgHighPrice\":100,\"avgLowPrice\":null}"
            + "]}";
        assertNull(svc.parseCandles(json));
    }

    @Test
    public void parseCandles_mixedValidAndInvalid_keepsValidOnly() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        String json = "{\"data\":["
            + "{\"avgHighPrice\":null,\"avgLowPrice\":100},"
            + "{\"avgHighPrice\":10,\"avgLowPrice\":10},"
            + "{\"avgHighPrice\":20,\"avgLowPrice\":20},"
            + "{\"avgHighPrice\":30,\"avgLowPrice\":30}"
            + "]}";
        List<PriceAnalysisService.Candle> list = svc.parseCandles(json);
        assertNotNull(list);
        assertEquals(3, list.size());
    }

    @Test
    public void parseCandles_nonPositiveHigh_skipped() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        String json = "{\"data\":[{\"avgHighPrice\":0,\"avgLowPrice\":10}]}";
        assertNull(svc.parseCandles(json));
    }

    @Test
    public void parseCandles_dataNotArray_returnsNull() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        assertNull(svc.parseCandles("{\"data\":\"oops\"}"));
    }

    // --- Group F: getSignal cache / HTTP ---

    @Test
    public void getSignal_cacheMiss_schedulesFetch() throws Exception
    {
        OkHttpClient http = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        Response response = mock(Response.class);
        ResponseBody body = mock(ResponseBody.class);
        when(http.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(body);
        when(body.string()).thenReturn(timeseriesJson(20, 100, 100));

        PriceAnalysisService svc = new PriceAnalysisService(http, config);
        assertNull(svc.getSignal(12345));
        Thread.sleep(400);
        verify(http, times(1)).newCall(any(Request.class));
    }

    @Test
    public void getSignal_freshCacheHit_noHttpCall() throws Exception
    {
        OkHttpClient http = mock(OkHttpClient.class);
        PriceAnalysisService svc = new PriceAnalysisService(http, config);
        long now = System.currentTimeMillis();
        cacheMap(svc).put(99, new SignalResult(Signal.BUY, 80.0, now));
        SignalResult r = svc.getSignal(99);
        assertNotNull(r);
        assertEquals(Signal.BUY, r.getSignal());
        verify(http, never()).newCall(any(Request.class));
    }

    @Test
    public void getSignal_expiredCache_schedulesRefetch() throws Exception
    {
        OkHttpClient http = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        Response response = mock(Response.class);
        ResponseBody body = mock(ResponseBody.class);
        when(http.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(body);
        when(body.string()).thenReturn(timeseriesJson(20, 100, 100));

        PriceAnalysisService svc = new PriceAnalysisService(http, config);
        long old = System.currentTimeMillis() - 60L * 60_000L;
        cacheMap(svc).put(77, new SignalResult(Signal.SELL, 70.0, old));
        SignalResult r = svc.getSignal(77);
        assertNotNull(r);
        assertEquals(Signal.SELL, r.getSignal());
        Thread.sleep(400);
        verify(http, times(1)).newCall(any(Request.class));
    }

    @Test
    public void getSignal_secondCallWhileInFlight_doesNotDuplicateSubmit() throws Exception
    {
        CountDownLatch blocker = new CountDownLatch(1);
        OkHttpClient http = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        when(http.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenAnswer(invocation ->
        {
            assertTrue(blocker.await(5, TimeUnit.SECONDS));
            Response response = mock(Response.class);
            ResponseBody body = mock(ResponseBody.class);
            when(response.isSuccessful()).thenReturn(true);
            when(response.body()).thenReturn(body);
            when(body.string()).thenReturn(timeseriesJson(20, 100, 100));
            return response;
        });

        PriceAnalysisService svc = new PriceAnalysisService(http, config);
        assertNull(svc.getSignal(555));
        assertNull(svc.getSignal(555));
        blocker.countDown();
        Thread.sleep(600);
        verify(http, times(1)).newCall(any(Request.class));
    }

    @Test
    public void clearCache_emptiesGetCachedSignal() throws Exception
    {
        PriceAnalysisService svc = new PriceAnalysisService(mock(OkHttpClient.class), config);
        cacheMap(svc).put(1, SignalResult.hold());
        assertNotNull(svc.getCachedSignal(1));
        svc.clearCache();
        assertNull(svc.getCachedSignal(1));
    }
}
