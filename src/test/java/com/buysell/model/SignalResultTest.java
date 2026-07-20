package com.buysell.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class SignalResultTest
{
    @Test
    public void hold_factory_returnsHoldZeroConfidence()
    {
        SignalResult r = SignalResult.hold();
        assertEquals(Signal.HOLD, r.getSignal());
        assertEquals(0.0, r.getConfidence(), 0.0);
        assertNotNull(r.getComputedAtMs());
    }

    @Test
    public void valueEquality()
    {
        SignalResult a = new SignalResult(Signal.BUY, 50, 100);
        SignalResult b = new SignalResult(Signal.BUY, 50, 100);
        SignalResult c = new SignalResult(Signal.SELL, 50, 100);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    public void signalEnum_values()
    {
        assertEquals(4, Signal.values().length);
        assertEquals(Signal.BUY, Signal.valueOf("BUY"));
        assertEquals(Signal.FILTERED, Signal.valueOf("FILTERED"));
    }
}
