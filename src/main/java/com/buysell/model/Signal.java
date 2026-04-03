package com.buysell.model;

/**
 * Trading signal direction produced by the price analysis algorithm.
 */
public enum Signal
{
    BUY,
    SELL,
    HOLD,
    /** Item price is outside the configured min/max threshold; overlay is skipped. */
    FILTERED
}
