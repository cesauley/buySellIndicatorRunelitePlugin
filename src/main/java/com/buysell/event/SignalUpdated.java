package com.buysell.event;

import com.buysell.model.SignalResult;
import lombok.Value;

/**
 * Fired when a single item's analysis result is written to the cache.
 */
@Value
public class SignalUpdated
{
    int itemId;
    SignalResult result;
}
