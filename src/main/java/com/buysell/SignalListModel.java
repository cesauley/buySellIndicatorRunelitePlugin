package com.buysell;

import com.buysell.model.Signal;
import com.buysell.model.SignalResult;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * Partitions cached signal results into Buy / Sell / Hold lists for the sidebar.
 */
public final class SignalListModel
{
    @Value
    public static class ItemEntry
    {
        int itemId;
        String name;
        Signal signal;
        double confidence;
    }

    private SignalListModel()
    {
    }

    /**
     * Builds three sorted lists from a cache snapshot. FILTERED entries are excluded.
     * Duplicate item IDs keep the latest entry (last write wins in the input map iteration,
     * but a ConcurrentHashMap snapshot already de-duplicates by key).
     */
    public static Map<Signal, List<ItemEntry>> partition(
        Map<Integer, SignalResult> snapshot,
        IntFunction<String> nameLookup)
    {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(nameLookup, "nameLookup");

        Map<Signal, List<ItemEntry>> buckets = new EnumMap<>(Signal.class);
        buckets.put(Signal.BUY, new ArrayList<>());
        buckets.put(Signal.SELL, new ArrayList<>());
        buckets.put(Signal.HOLD, new ArrayList<>());

        // Defensive de-dupe if a caller passes a multi-entry collection
        Map<Integer, SignalResult> unique = new HashMap<>(snapshot);
        for (Map.Entry<Integer, SignalResult> e : unique.entrySet())
        {
            SignalResult result = e.getValue();
            if (result == null)
            {
                continue;
            }
            Signal signal = result.getSignal();
            if (signal != Signal.BUY && signal != Signal.SELL && signal != Signal.HOLD)
            {
                continue;
            }
            Integer itemId = e.getKey();
            if (itemId == null || itemId <= 0)
            {
                continue;
            }
            String name = nameLookup.apply(itemId);
            if (name == null || name.isEmpty())
            {
                name = "Item " + itemId;
            }
            buckets.get(signal).add(new ItemEntry(itemId, name, signal, result.getConfidence()));
        }

        Comparator<ItemEntry> order = Comparator
            .comparingDouble(ItemEntry::getConfidence).reversed()
            .thenComparing(ItemEntry::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(ItemEntry::getItemId);

        for (List<ItemEntry> list : buckets.values())
        {
            list.sort(order);
        }

        Map<Signal, List<ItemEntry>> immutable = new EnumMap<>(Signal.class);
        immutable.put(Signal.BUY, Collections.unmodifiableList(buckets.get(Signal.BUY)));
        immutable.put(Signal.SELL, Collections.unmodifiableList(buckets.get(Signal.SELL)));
        immutable.put(Signal.HOLD, Collections.unmodifiableList(buckets.get(Signal.HOLD)));
        return Collections.unmodifiableMap(immutable);
    }
}
