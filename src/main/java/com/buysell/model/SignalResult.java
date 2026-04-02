package com.buysell.model;

import lombok.Value;

/**
 * Immutable result produced by PriceAnalysisService for a single item.
 *
 * confidence is a value in [0, 100] representing how strongly the
 * algorithm agrees on the given signal direction.
 */
@Value
public class SignalResult
{
    Signal signal;
    double confidence;
    long computedAtMs;

    public static SignalResult hold()
    {
        return new SignalResult(Signal.HOLD, 0.0, System.currentTimeMillis());
    }
}
