package com.buysell.event;

import com.buysell.model.Signal;
import com.buysell.model.SignalResult;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SignalEventTest
{
    @Test
    public void signalUpdated_holdsFields()
    {
        SignalResult result = new SignalResult(Signal.BUY, 80, 1);
        SignalUpdated event = new SignalUpdated(4151, result);
        assertEquals(4151, event.getItemId());
        assertEquals(result, event.getResult());
    }

    @Test
    public void signalsCleared_canConstruct()
    {
        assertNotNull(new SignalsCleared());
    }
}
