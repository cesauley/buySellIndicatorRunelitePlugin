package com.buysell;

import com.buysell.model.Signal;
import com.buysell.model.SignalResult;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.bank.BankSearch;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Injects a bank button that cycles filter modes (OFF / BUY / SELL / BUY+SELL) and uses
 * {@code bankSearchFilter} to show only matching items.
 */
@Slf4j
@Singleton
public class BankFilterManager
{
    private static final String FILTER_WIDGET_NAME = "buysell-signal-filter";

    /** Layout next to top-right bank chrome (see Quest Helper quest tab at x=408 on UNIVERSE). */
    private static final int GAP_PX = 6;
    private static final int RIGHT_MARGIN_FALLBACK = 48;
    private static final int TOP_MARGIN = 6;
    private static final int MIN_LEFT_MARGIN = 8;
    /** Quest Helper bank icon starts here on UNIVERSE; keep our label to the left. */
    private static final int QUEST_HELPER_BANK_BUTTON_X = 408;

    /** Match Quest Helper: {@code UNKNOWN_BUTTON_SQUARE_SMALL} renders correctly at 25×25 only. */
    private static final int FILTER_BG_W = 25;
    private static final int FILTER_BG_H = 25;

    /**
     * Run after Bank plugin / Bank Tags so our include/exclude is the final decision.
     */
    private static final float CALLBACK_PRIORITY_LAST = -100f;

    private final Client client;
    private final ClientThread clientThread;
    private final BankSearch bankSearch;
    private final PriceAnalysisService analysisService;
    private final ItemManager itemManager;
    private final BuySellIndicatorConfig config;

    private BuySellIndicatorConfig.FilterMode activeMode = BuySellIndicatorConfig.FilterMode.OFF;
    /** Click target; vanilla bank tab square sprite at {@link #FILTER_BG_W}×{@link #FILTER_BG_H}. */
    private Widget filterBackground;
    /** White label centered on {@link #filterBackground}; no listener. */
    private Widget filterLabel;

    @Inject
    BankFilterManager(
        Client client,
        ClientThread clientThread,
        BankSearch bankSearch,
        PriceAnalysisService analysisService,
        ItemManager itemManager,
        BuySellIndicatorConfig config)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.bankSearch = bankSearch;
        this.analysisService = analysisService;
        this.itemManager = itemManager;
        this.config = config;
    }

    public void reset()
    {
        log.debug("reset clearing bank filter state");
        activeMode = BuySellIndicatorConfig.FilterMode.OFF;
        if (filterBackground != null)
        {
            filterBackground.setHidden(true);
        }
        if (filterLabel != null)
        {
            filterLabel.setHidden(true);
        }
        filterBackground = null;
        filterLabel = null;
        clientThread.invokeLater(() ->
        {
            if (client.getWidget(InterfaceID.Bankmain.UNIVERSE) != null)
            {
                bankSearch.reset(true);
            }
        });
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() != InterfaceID.BANKMAIN)
        {
            return;
        }
        log.debug("onWidgetLoaded BANKMAIN; scheduling filter button inject");
        clientThread.invokeLater(this::injectOrRefreshButton);
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event)
    {
        if (event.getGroupId() != InterfaceID.BANKMAIN)
        {
            return;
        }
        log.debug("onWidgetClosed BANKMAIN; clearing filter widget references");
        filterBackground = null;
        filterLabel = null;
    }

    /**
     * If the plugin loads while the bank is already open, {@link WidgetLoaded} may not fire again.
     */
    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (!config.enableBankSignalFilter())
        {
            return;
        }
        Widget universe = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
        if (universe == null || universe.isHidden())
        {
            return;
        }
        if (filterBackground == null || filterBackground.getParent() != universe)
        {
            log.debug("onGameTick filter button missing or wrong parent; re-injecting");
            injectOrRefreshButton();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged ev)
    {
        if (!"buysell".equals(ev.getGroup()))
        {
            return;
        }
        if ("enableBankSignalFilter".equals(ev.getKey()) || "minConfidence".equals(ev.getKey()))
        {
            log.debug("onConfigChanged key={} oldValue={} newValue={}", ev.getKey(), ev.getOldValue(), ev.getNewValue());
            clientThread.invokeLater(() ->
            {
                if (!config.enableBankSignalFilter() && activeMode != BuySellIndicatorConfig.FilterMode.OFF)
                {
                    cycleModeToOff();
                }
                injectOrRefreshButton();
                if (activeMode != BuySellIndicatorConfig.FilterMode.OFF)
                {
                    bankSearch.layoutBank();
                }
            });
        }
    }

    private void cycleModeToOff()
    {
        log.debug("cycleModeToOff resetting filter mode to OFF");
        activeMode = BuySellIndicatorConfig.FilterMode.OFF;
        if (filterBackground != null && filterLabel != null)
        {
            applyFilterVisuals();
            repositionFilterButtonAfterBankLayout();
        }
        bankSearch.reset(true);
    }

    @Subscribe(priority = CALLBACK_PRIORITY_LAST)
    public void onScriptCallbackEvent(ScriptCallbackEvent event)
    {
        if (!config.enableBankSignalFilter())
        {
            return;
        }

        String name = event.getEventName();
        log.trace("onScriptCallbackEvent name={} activeMode={}", name, activeMode);
        if ("getSearchingTagTab".equals(name))
        {
            if (activeMode != BuySellIndicatorConfig.FilterMode.OFF)
            {
                int[] intStack = client.getIntStack();
                int sz = client.getIntStackSize();
                intStack[sz - 1] = 1;
            }
            return;
        }

        if (!"bankSearchFilter".equals(name))
        {
            return;
        }

        if (activeMode == BuySellIndicatorConfig.FilterMode.OFF)
        {
            return;
        }

        int[] intStack = client.getIntStack();
        int intStackSize = client.getIntStackSize();
        int rawId = intStack[intStackSize - 1];
        log.trace("onScriptCallbackEvent bankSearchFilter rawId={}", rawId);
        if (rawId < 0)
        {
            return;
        }

        int canonicalId = itemManager.canonicalize(rawId);
        boolean include = matchesActiveFilter(canonicalId);
        log.trace("onScriptCallbackEvent bankSearchFilter canonicalId={} include={}", canonicalId, include);
        intStack[intStackSize - 2] = include ? 1 : 0;
    }

    @Subscribe(priority = CALLBACK_PRIORITY_LAST)
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (!config.enableBankSignalFilter())
        {
            return;
        }

        int scriptId = event.getScriptId();
        if (scriptId == ScriptID.BANKMAIN_SEARCHING || scriptId == ScriptID.BANKMAIN_FINISHBUILDING)
        {
            log.trace("onScriptPostFired scriptId={}", scriptId);
        }
        if (scriptId == ScriptID.BANKMAIN_SEARCHING)
        {
            if (activeMode != BuySellIndicatorConfig.FilterMode.OFF)
            {
                int[] intStack = client.getIntStack();
                int sz = client.getIntStackSize();
                intStack[sz - 1] = 1;
            }
            return;
        }

        if (scriptId != ScriptID.BANKMAIN_FINISHBUILDING)
        {
            return;
        }

        clientThread.invokeAtTickEnd(this::repositionFilterButtonAfterBankLayout);
        if (activeMode != BuySellIndicatorConfig.FilterMode.OFF)
        {
            clientThread.invokeAtTickEnd(this::sortVisibleItemsByConfidence);
        }
    }

    /**
     * Places the filter label near the top-right (close/header) area: left of {@link
     * InterfaceID.Bankmain#POPUP_BUTTON_OUT} when present, else right-aligned on UNIVERSE, with a
     * clamp so it stays left of Quest Helper's bank icon (x=408).
     */
    private void positionFilterButton(Widget universe, Widget filterBackground)
    {
        int fw = filterBackground.getOriginalWidth();
        int fh = filterBackground.getOriginalHeight();

        int x;
        int y;

        Widget anchor = client.getWidget(InterfaceID.Bankmain.POPUP_BUTTON_OUT);
        if (anchor != null && !anchor.isHidden())
        {
            int[] origin = widgetOriginInAncestor(anchor, universe);
            if (origin != null)
            {
                int anchorH = anchor.getOriginalHeight();
                x = origin[0] - fw - GAP_PX;
                y = origin[1] + Math.max(0, (anchorH - fh) / 2);
            }
            else
            {
                x = computeFallbackX(universe, fw);
                y = TOP_MARGIN;
            }
        }
        else
        {
            x = computeFallbackX(universe, fw);
            y = TOP_MARGIN;
        }

        x = Math.max(MIN_LEFT_MARGIN, x);
        if (x + fw > QUEST_HELPER_BANK_BUTTON_X - GAP_PX)
        {
            x = QUEST_HELPER_BANK_BUTTON_X - fw - GAP_PX;
        }
        x = Math.max(MIN_LEFT_MARGIN, x);

        filterBackground.setOriginalX(x);
        filterBackground.setOriginalY(y);
        if (filterLabel != null)
        {
            filterLabel.setOriginalX(x);
            filterLabel.setOriginalY(y);
        }
    }

    private static int computeFallbackX(Widget universe, int filterWidth)
    {
        int uw = universe.getOriginalWidth();
        if (uw <= 0)
        {
            uw = universe.getWidth();
        }
        return uw - filterWidth - RIGHT_MARGIN_FALLBACK;
    }

    /**
     * @return top-left of {@code w} in {@code ancestor} coordinates, or null if {@code w} is not under {@code ancestor}
     */
    private static int[] widgetOriginInAncestor(Widget w, Widget ancestor)
    {
        if (w == null || ancestor == null)
        {
            return null;
        }
        int x = 0;
        int y = 0;
        Widget cur = w;
        while (cur != null && cur != ancestor)
        {
            x += cur.getOriginalX();
            y += cur.getOriginalY();
            cur = cur.getParent();
        }
        if (cur != ancestor)
        {
            return null;
        }
        return new int[]{x, y};
    }

    private void repositionFilterButtonAfterBankLayout()
    {
        if (!config.enableBankSignalFilter() || filterBackground == null || filterBackground.isHidden())
        {
            return;
        }
        Widget universe = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
        if (universe == null || universe.isHidden() || filterBackground.getParent() != universe)
        {
            return;
        }
        positionFilterButton(universe, filterBackground);
        filterBackground.revalidate();
        if (filterLabel != null)
        {
            filterLabel.revalidate();
        }
    }

    private void injectOrRefreshButton()
    {
        if (!config.enableBankSignalFilter())
        {
            log.debug("injectOrRefreshButton bank signal filter disabled; hiding widgets");
            if (filterBackground != null)
            {
                filterBackground.setHidden(true);
            }
            if (filterLabel != null)
            {
                filterLabel.setHidden(true);
            }
            return;
        }

        Widget universe = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
        if (universe == null || universe.isHidden())
        {
            if (filterBackground != null && !filterBackground.isHidden())
            {
                log.error("injectOrRefreshButton UNIVERSE null or hidden but filter widget still visible; inconsistent state");
            }
            return;
        }

        if (filterBackground == null || filterLabel == null
            || filterBackground.getParent() != universe || filterLabel.getParent() != universe)
        {
            log.debug("injectOrRefreshButton creating new filter widgets on UNIVERSE");
            filterBackground = universe.createChild(-1, WidgetType.GRAPHIC);
            filterBackground.setName(FILTER_WIDGET_NAME);
            filterBackground.setOriginalWidth(FILTER_BG_W);
            filterBackground.setOriginalHeight(FILTER_BG_H);
            filterBackground.setAction(1, "Cycle filter");
            filterBackground.setOnOpListener((JavaScriptCallback) this::onFilterButtonOp);
            filterBackground.setHasListener(true);

            filterLabel = universe.createChild(-1, WidgetType.TEXT);
            filterLabel.setOriginalWidth(FILTER_BG_W);
            filterLabel.setOriginalHeight(FILTER_BG_H);
            filterLabel.setFontId(FontID.BOLD_12);
            filterLabel.setTextShadowed(true);
            filterLabel.setTextColor(0xffffff);
            filterLabel.setXTextAlignment(1);
            filterLabel.setYTextAlignment(1);
            filterLabel.setHasListener(false);

            applyFilterVisuals();
            filterBackground.revalidate();
            filterLabel.revalidate();
        }
        else
        {
            log.debug("injectOrRefreshButton refreshing existing filter widgets");
        }

        filterBackground.setHidden(false);
        filterLabel.setHidden(false);
        applyFilterVisuals();
        positionFilterButton(universe, filterBackground);
        filterBackground.revalidate();
        filterLabel.revalidate();
    }

    private void applyFilterVisuals()
    {
        if (filterBackground == null || filterLabel == null)
        {
            return;
        }
        filterBackground.setSpriteId(
            activeMode == BuySellIndicatorConfig.FilterMode.OFF
                ? SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL
                : SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED);
        filterLabel.setText(activeMode.getButtonLabel());
    }

    private void onFilterButtonOp(ScriptEvent ev)
    {
        if (ev.getOp() == 1 || ev.getOp() == 2)
        {
            clientThread.invokeLater(this::cycleMode);
        }
    }

    private void cycleMode()
    {
        if (!config.enableBankSignalFilter())
        {
            return;
        }

        BuySellIndicatorConfig.FilterMode previous = activeMode;
        activeMode = activeMode.next();
        log.debug("cycleMode {} -> {}", previous, activeMode);
        if (filterBackground != null && filterLabel != null)
        {
            applyFilterVisuals();
            Widget u = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
            if (u != null)
            {
                positionFilterButton(u, filterBackground);
            }
            filterBackground.revalidate();
            filterLabel.revalidate();
        }

        if (activeMode == BuySellIndicatorConfig.FilterMode.OFF)
        {
            bankSearch.reset(true);
        }
        else
        {
            if (client.getVarbitValue(VarbitID.BANK_CURRENTTAB) == 15)
            {
                client.menuAction(-1, InterfaceID.Bankmain.POTIONSTORE_BUTTON,
                    MenuAction.CC_OP, 1, -1, "Potion store", "");
            }

            bankSearch.reset(true);
            Widget searchBg = client.getWidget(InterfaceID.Bankmain.SEARCH);
            if (searchBg != null)
            {
                searchBg.setOnTimerListener((Object[]) null);
                searchBg.setSpriteId(SpriteID.Miscgraphics.EQUIPMENT_SLOT_TILE);
            }
            bankSearch.layoutBank();
        }

        client.playSoundEffect(SoundEffectID.UI_BOOP);
    }

    /**
     * After the bank layout script runs, reorder visible item widgets so highest-confidence signals
     * appear first (top-left grid order). Each widget keeps its item id; only positions change, so
     * sprites and overlays stay aligned.
     */
    private void sortVisibleItemsByConfidence()
    {
        if (!config.enableBankSignalFilter() || activeMode == BuySellIndicatorConfig.FilterMode.OFF)
        {
            return;
        }

        Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
        if (container == null || container.isHidden())
        {
            return;
        }

        Widget[] children = container.getDynamicChildren();
        if (children == null || children.length == 0)
        {
            return;
        }

        List<Widget> items = new ArrayList<>();
        for (Widget w : children)
        {
            if (w != null && !w.isHidden() && w.getItemId() > 0)
            {
                items.add(w);
            }
        }

        if (items.size() < 2)
        {
            return;
        }

        // Collect and sort slot positions in reading order (top→bottom, left→right) so that
        // even if this method runs multiple times the same canonical slots are always assigned,
        // regardless of the current DOM child order.
        int[] xs = new int[items.size()];
        int[] ys = new int[items.size()];
        for (int i = 0; i < items.size(); i++)
        {
            xs[i] = items.get(i).getOriginalX();
            ys[i] = items.get(i).getOriginalY();
        }
        // Sort the slot arrays into reading order independently of DOM child order.
        // We build index array sorted by (y asc, x asc) then reorder xs/ys.
        Integer[] slotOrder = new Integer[items.size()];
        for (int i = 0; i < slotOrder.length; i++) slotOrder[i] = i;
        java.util.Arrays.sort(slotOrder, (a, b) -> ys[a] != ys[b] ? Integer.compare(ys[a], ys[b]) : Integer.compare(xs[a], xs[b]));
        int[] sortedXs = new int[items.size()];
        int[] sortedYs = new int[items.size()];
        for (int i = 0; i < slotOrder.length; i++)
        {
            sortedXs[i] = xs[slotOrder[i]];
            sortedYs[i] = ys[slotOrder[i]];
        }

        items.sort((a, b) ->
        {
            double ca = getConfidence(itemManager.canonicalize(a.getItemId()));
            double cb = getConfidence(itemManager.canonicalize(b.getItemId()));
            return Double.compare(cb, ca);
        });

        for (int i = 0; i < items.size(); i++)
        {
            Widget w = items.get(i);
            w.setOriginalX(sortedXs[i]);
            w.setOriginalY(sortedYs[i]);
            w.revalidate();
        }
        log.debug("sortVisibleItemsByConfidence reordered {} visible bank items", items.size());
    }

    private double getConfidence(int canonicalId)
    {
        SignalResult r = analysisService.getCachedSignal(canonicalId);
        return r != null ? r.getConfidence() : 0.0;
    }

    private boolean matchesActiveFilter(int canonicalItemId)
    {
        ItemComposition def = itemManager.getItemComposition(canonicalItemId);
        if (def == null || !def.isTradeable())
        {
            log.trace("matchesActiveFilter canonicalItemId={} skip non-tradeable or null def", canonicalItemId);
            return false;
        }

        SignalResult result = analysisService.getCachedSignal(canonicalItemId);
        if (result == null || result.getConfidence() < config.minConfidence())
        {
            log.trace("matchesActiveFilter canonicalItemId={} skip no cached signal or below minConfidence result={} minConfidence={}",
                canonicalItemId, result, config.minConfidence());
            return false;
        }

        Signal sig = result.getSignal();
        boolean match;
        switch (activeMode)
        {
            case BUY_ONLY:
                match = sig == Signal.BUY;
                break;
            case SELL_ONLY:
                match = sig == Signal.SELL;
                break;
            case BUY_AND_SELL:
                match = sig == Signal.BUY || sig == Signal.SELL;
                break;
            default:
                match = false;
                break;
        }
        log.trace("matchesActiveFilter canonicalItemId={} signal={} confidence={} activeMode={} match={}",
            canonicalItemId, sig, result.getConfidence(), activeMode, match);
        return match;
    }

}
