package com.buysell;

import com.buysell.model.Signal;
import com.buysell.model.SignalResult;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SignalListModelTest
{
    @Test
    public void partition_emptySnapshot_returnsEmptyBuckets()
    {
        Map<Signal, List<SignalListModel.ItemEntry>> result =
            SignalListModel.partition(Collections.emptyMap(), id -> "x");
        assertTrue(result.get(Signal.BUY).isEmpty());
        assertTrue(result.get(Signal.SELL).isEmpty());
        assertTrue(result.get(Signal.HOLD).isEmpty());
    }

    @Test
    public void partition_groupsBySignal_andExcludesFiltered()
    {
        Map<Integer, SignalResult> snap = new HashMap<>();
        snap.put(1, new SignalResult(Signal.BUY, 80, 1));
        snap.put(2, new SignalResult(Signal.SELL, 70, 1));
        snap.put(3, new SignalResult(Signal.HOLD, 10, 1));
        snap.put(4, new SignalResult(Signal.FILTERED, 0, 1));

        Map<Signal, List<SignalListModel.ItemEntry>> result =
            SignalListModel.partition(snap, id -> "Item" + id);

        assertEquals(1, result.get(Signal.BUY).size());
        assertEquals(1, result.get(Signal.SELL).size());
        assertEquals(1, result.get(Signal.HOLD).size());
        assertEquals(1, result.get(Signal.BUY).get(0).getItemId());
    }

    @Test
    public void partition_sortsByConfidenceDescThenName()
    {
        Map<Integer, SignalResult> snap = new HashMap<>();
        snap.put(10, new SignalResult(Signal.BUY, 50, 1));
        snap.put(11, new SignalResult(Signal.BUY, 90, 1));
        snap.put(12, new SignalResult(Signal.BUY, 50, 1));

        Map<Signal, List<SignalListModel.ItemEntry>> result =
            SignalListModel.partition(snap, id ->
            {
                if (id == 10) return "Zebra";
                if (id == 11) return "Alpha";
                return "Apple";
            });

        List<SignalListModel.ItemEntry> buy = result.get(Signal.BUY);
        assertEquals(11, buy.get(0).getItemId());
        assertEquals(12, buy.get(1).getItemId());
        assertEquals(10, buy.get(2).getItemId());
    }

    @Test
    public void partition_nullResult_skipped()
    {
        Map<Integer, SignalResult> snap = new HashMap<>();
        snap.put(1, null);
        snap.put(2, new SignalResult(Signal.HOLD, 1, 1));
        Map<Signal, List<SignalListModel.ItemEntry>> result =
            SignalListModel.partition(snap, id -> "n");
        assertEquals(1, result.get(Signal.HOLD).size());
    }

    @Test
    public void partition_invalidItemId_skipped()
    {
        Map<Integer, SignalResult> snap = new HashMap<>();
        snap.put(0, new SignalResult(Signal.BUY, 50, 1));
        snap.put(-1, new SignalResult(Signal.BUY, 50, 1));
        Map<Signal, List<SignalListModel.ItemEntry>> result =
            SignalListModel.partition(snap, id -> "n");
        assertTrue(result.get(Signal.BUY).isEmpty());
    }

    @Test
    public void partition_nullOrEmptyName_usesFallback()
    {
        Map<Integer, SignalResult> snap = new HashMap<>();
        snap.put(5, new SignalResult(Signal.SELL, 40, 1));
        Map<Signal, List<SignalListModel.ItemEntry>> result =
            SignalListModel.partition(snap, id -> null);
        assertEquals("Item 5", result.get(Signal.SELL).get(0).getName());

        snap.clear();
        snap.put(6, new SignalResult(Signal.SELL, 40, 1));
        result = SignalListModel.partition(snap, id -> "");
        assertEquals("Item 6", result.get(Signal.SELL).get(0).getName());
    }

    @Test(expected = NullPointerException.class)
    public void partition_nullSnapshot_throws()
    {
        SignalListModel.partition(null, id -> "x");
    }

    @Test(expected = NullPointerException.class)
    public void partition_nullNameLookup_throws()
    {
        SignalListModel.partition(Collections.emptyMap(), null);
    }
}
