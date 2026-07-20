package com.buysell;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable draft of plugin settings for the in-panel editor. Changes are not
 * persisted until {@link #toConfigValues()} is saved via ConfigManager.
 */
@Data
public class SettingsDraft
{
    private boolean showOnInventory;
    private boolean showOnBank;
    private int minConfidence;
    private int minItemPrice;
    private int maxItemPrice;
    private BuySellIndicatorConfig.FontSize fontSize;
    private int cacheMinutes;
    private BuySellIndicatorConfig.AnalysisBundle analysisBundle;
    private boolean enableBankSignalFilter;
    private String blacklistedItems;

    public static SettingsDraft fromConfig(BuySellIndicatorConfig config)
    {
        Objects.requireNonNull(config, "config");
        SettingsDraft d = new SettingsDraft();
        d.showOnInventory = config.showOnInventory();
        d.showOnBank = config.showOnBank();
        d.minConfidence = config.minConfidence();
        d.minItemPrice = config.minItemPrice();
        d.maxItemPrice = config.maxItemPrice();
        d.fontSize = config.fontSize();
        d.cacheMinutes = config.cacheMinutes();
        d.analysisBundle = config.analysisBundle();
        d.enableBankSignalFilter = config.enableBankSignalFilter();
        d.blacklistedItems = config.blacklistedItems() == null ? "" : config.blacklistedItems();
        return d;
    }

    /**
     * @return null if valid, otherwise an error message
     */
    public String validate()
    {
        if (minConfidence < 0 || minConfidence > 90)
        {
            return "Minimum Confidence must be between 0 and 90.";
        }
        if (minItemPrice < 0)
        {
            return "Min Item Price cannot be negative.";
        }
        if (maxItemPrice < 0)
        {
            return "Max Item Price cannot be negative.";
        }
        if (maxItemPrice > 0 && minItemPrice > 0 && minItemPrice > maxItemPrice)
        {
            return "Min Item Price cannot exceed Max Item Price.";
        }
        if (cacheMinutes < 1 || cacheMinutes > 60)
        {
            return "Cache Duration must be between 1 and 60 minutes.";
        }
        if (fontSize == null)
        {
            return "Font Size is required.";
        }
        if (analysisBundle == null)
        {
            return "Analysis bundle is required.";
        }
        if (blacklistedItems == null)
        {
            blacklistedItems = "";
        }
        return null;
    }

    public boolean isDirty(BuySellIndicatorConfig config)
    {
        Objects.requireNonNull(config, "config");
        String blacklist = config.blacklistedItems() == null ? "" : config.blacklistedItems();
        String draftBlacklist = blacklistedItems == null ? "" : blacklistedItems;
        return showOnInventory != config.showOnInventory()
            || showOnBank != config.showOnBank()
            || minConfidence != config.minConfidence()
            || minItemPrice != config.minItemPrice()
            || maxItemPrice != config.maxItemPrice()
            || fontSize != config.fontSize()
            || cacheMinutes != config.cacheMinutes()
            || analysisBundle != config.analysisBundle()
            || enableBankSignalFilter != config.enableBankSignalFilter()
            || !draftBlacklist.equals(blacklist);
    }

    /**
     * Key/value map matching BuySellIndicatorConfig keyNames for ConfigManager.
     */
    public Map<String, String> toConfigValues()
    {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("showOnInventory", Boolean.toString(showOnInventory));
        values.put("showOnBank", Boolean.toString(showOnBank));
        values.put("minConfidence", Integer.toString(minConfidence));
        values.put("minItemPrice", Integer.toString(minItemPrice));
        values.put("maxItemPrice", Integer.toString(maxItemPrice));
        values.put("fontSize", fontSize.name());
        values.put("cacheMinutes", Integer.toString(cacheMinutes));
        values.put("analysisBundle", analysisBundle.name());
        values.put("enableBankSignalFilter", Boolean.toString(enableBankSignalFilter));
        values.put("blacklistedItems", blacklistedItems == null ? "" : blacklistedItems);
        return values;
    }
}
