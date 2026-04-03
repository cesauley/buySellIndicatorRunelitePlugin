package com.buysell;

import com.buysell.model.Signal;
import com.buysell.model.SignalResult;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.bank.BankSearch;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for bank filter widget lifecycle, script callback filtering, and mode cycling.
 */
public class BankFilterManagerTest
{
    private Client client;
    private ClientThread clientThread;
    private BankSearch bankSearch;
    private PriceAnalysisService analysisService;
    private ItemManager itemManager;
    private BuySellIndicatorConfig config;

    @Before
    public void setUp()
    {
        client = mock(Client.class);
        clientThread = mock(ClientThread.class);
        bankSearch = mock(BankSearch.class);
        analysisService = mock(PriceAnalysisService.class);
        itemManager = mock(ItemManager.class);
        config = mock(BuySellIndicatorConfig.class);
        when(config.enableBankSignalFilter()).thenReturn(true);
        when(config.minConfidence()).thenReturn(40);
        org.mockito.Mockito.lenient().when(itemManager.canonicalize(anyInt()))
            .thenAnswer(inv -> inv.getArgument(0));

        org.mockito.Mockito.doAnswer(inv ->
        {
            Runnable r = inv.getArgument(0);
            r.run();
            return null;
        }).when(clientThread).invokeLater(any(Runnable.class));

        org.mockito.Mockito.doAnswer(inv ->
        {
            Runnable r = inv.getArgument(0);
            r.run();
            return null;
        }).when(clientThread).invokeAtTickEnd(any(Runnable.class));
    }

    private BankFilterManager newManager()
    {
        return new BankFilterManager(client, clientThread, bankSearch, analysisService, itemManager, config);
    }

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field f = BankFilterManager.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception
    {
        Field f = BankFilterManager.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    /** @return the UNIVERSE widget mock for verify(createChild...) */
    private Widget stubUniverseForInject()
    {
        Widget universe = mock(Widget.class);
        Widget graphicChild = mock(Widget.class);
        Widget textChild = mock(Widget.class);
        when(client.getWidget(InterfaceID.Bankmain.UNIVERSE)).thenReturn(universe);
        when(universe.isHidden()).thenReturn(false);
        AtomicInteger createChildSeq = new AtomicInteger();
        when(universe.createChild(anyInt(), anyInt())).thenAnswer(invocation ->
            createChildSeq.getAndIncrement() % 2 == 0 ? graphicChild : textChild);
        when(client.getWidget(InterfaceID.Bankmain.POPUP_BUTTON_OUT)).thenReturn(null);
        when(universe.getOriginalWidth()).thenReturn(500);
        return universe;
    }

    private void invokeCycleMode(BankFilterManager m) throws Exception
    {
        Method method = BankFilterManager.class.getDeclaredMethod("cycleMode");
        method.setAccessible(true);
        method.invoke(m);
    }

    // --- Group G: reset ---

    @Test
    public void reset_hidesWidgetsAndNullsRefs_andSetsModeOff() throws Exception
    {
        Widget bg = mock(Widget.class);
        Widget label = mock(Widget.class);
        BankFilterManager m = newManager();
        setField(m, "filterBackground", bg);
        setField(m, "filterLabel", label);
        setField(m, "activeMode", BuySellIndicatorConfig.FilterMode.BUY_ONLY);

        when(client.getWidget(InterfaceID.Bankmain.UNIVERSE)).thenReturn(null);

        m.reset();

        verify(bg).setHidden(true);
        verify(label).setHidden(true);
        assertNull(getField(m, "filterBackground"));
        assertNull(getField(m, "filterLabel"));
        assertEquals(BuySellIndicatorConfig.FilterMode.OFF, getField(m, "activeMode"));
    }

    @Test
    public void reset_withNullWidgets_doesNotThrow() throws Exception
    {
        BankFilterManager m = newManager();
        when(client.getWidget(InterfaceID.Bankmain.UNIVERSE)).thenReturn(null);
        m.reset();
        assertNull(getField(m, "filterBackground"));
    }

    @Test
    public void reset_invokesBankSearchWhenUniverseOpen() throws Exception
    {
        BankFilterManager m = newManager();
        Widget universe = mock(Widget.class);
        when(client.getWidget(InterfaceID.Bankmain.UNIVERSE)).thenReturn(universe);
        m.reset();
        verify(bankSearch).reset(eq(true));
    }

    @Test
    public void reset_skipsBankSearchWhenUniverseClosed() throws Exception
    {
        BankFilterManager m = newManager();
        when(client.getWidget(InterfaceID.Bankmain.UNIVERSE)).thenReturn(null);
        m.reset();
        verify(bankSearch, never()).reset(true);
    }

    // --- Group H: onWidgetClosed ---

    @Test
    public void onWidgetClosed_bankMain_nullsWidgetRefs() throws Exception
    {
        BankFilterManager m = newManager();
        setField(m, "filterBackground", mock(Widget.class));
        setField(m, "filterLabel", mock(Widget.class));
        WidgetClosed ev = mock(WidgetClosed.class);
        when(ev.getGroupId()).thenReturn(InterfaceID.BANKMAIN);
        m.onWidgetClosed(ev);
        assertNull(getField(m, "filterBackground"));
        assertNull(getField(m, "filterLabel"));
    }

    @Test
    public void onWidgetClosed_otherGroup_preservesRefs() throws Exception
    {
        BankFilterManager m = newManager();
        Widget bg = mock(Widget.class);
        setField(m, "filterBackground", bg);
        setField(m, "filterLabel", mock(Widget.class));
        WidgetClosed ev = mock(WidgetClosed.class);
        when(ev.getGroupId()).thenReturn(InterfaceID.BANKMAIN + 9999);
        m.onWidgetClosed(ev);
        assertSame(bg, getField(m, "filterBackground"));
    }

    // --- Group I: WidgetLoaded / GameTick ---

    @Test
    public void onWidgetLoaded_bankMain_schedulesInject() throws Exception
    {
        BankFilterManager m = newManager();
        Widget universe = stubUniverseForInject();
        WidgetLoaded ev = mock(WidgetLoaded.class);
        when(ev.getGroupId()).thenReturn(InterfaceID.BANKMAIN);
        m.onWidgetLoaded(ev);
        ArgumentCaptor<Runnable> cap = ArgumentCaptor.forClass(Runnable.class);
        verify(clientThread).invokeLater(cap.capture());
        cap.getValue().run();
        verify(universe, atLeast(1)).createChild(anyInt(), anyInt());
    }

    @Test
    public void onWidgetLoaded_nonBank_doesNotScheduleInject() throws Exception
    {
        BankFilterManager m = newManager();
        WidgetLoaded ev = mock(WidgetLoaded.class);
        when(ev.getGroupId()).thenReturn(InterfaceID.BANKMAIN + 9999);
        m.onWidgetLoaded(ev);
        verify(clientThread, times(0)).invokeLater(any(Runnable.class));
    }

    @Test
    public void onGameTick_filterDisabled_noInject() throws Exception
    {
        Widget universe = stubUniverseForInject();
        when(config.enableBankSignalFilter()).thenReturn(false);
        BankFilterManager m = newManager();
        m.onGameTick(mock(GameTick.class));
        verify(universe, never()).createChild(anyInt(), anyInt());
    }

    @Test
    public void onGameTick_universeNull_noCrash() throws Exception
    {
        BankFilterManager m = newManager();
        when(client.getWidget(InterfaceID.Bankmain.UNIVERSE)).thenReturn(null);
        m.onGameTick(mock(GameTick.class));
    }

    @Test
    public void onGameTick_staleFilterParent_triggersReinject() throws Exception
    {
        BankFilterManager m = newManager();
        Widget universe = stubUniverseForInject();
        Widget staleBg = mock(Widget.class);
        Widget wrongParent = mock(Widget.class);
        when(staleBg.getParent()).thenReturn(wrongParent);
        setField(m, "filterBackground", staleBg);
        setField(m, "filterLabel", mock(Widget.class));

        m.onGameTick(mock(GameTick.class));

        verify(universe, atLeast(2)).createChild(anyInt(), anyInt());
    }

    // --- Group J: ScriptCallbackEvent bankSearchFilter ---

    @Test
    public void scriptCallback_modeOff_doesNotTouchIncludeFlag() throws Exception
    {
        BankFilterManager m = newManager();
        setField(m, "activeMode", BuySellIndicatorConfig.FilterMode.OFF);
        int[] stack = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 42, 4151};
        when(client.getIntStack()).thenReturn(stack);
        when(client.getIntStackSize()).thenReturn(10);
        ScriptCallbackEvent ev = mock(ScriptCallbackEvent.class);
        when(ev.getEventName()).thenReturn("bankSearchFilter");
        m.onScriptCallbackEvent(ev);
        assertEquals(42, stack[8]);
    }

    @Test
    public void scriptCallback_buyOnly_withBuySignal_setsInclude() throws Exception
    {
        BankFilterManager m = newManager();
        setField(m, "activeMode", BuySellIndicatorConfig.FilterMode.BUY_ONLY);
        ItemComposition def = mock(ItemComposition.class);
        when(def.isTradeable()).thenReturn(true);
        when(itemManager.getItemComposition(4151)).thenReturn(def);
        when(analysisService.getCachedSignal(4151)).thenReturn(new SignalResult(Signal.BUY, 90.0, 0L));

        int[] stack = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 4151};
        when(client.getIntStack()).thenReturn(stack);
        when(client.getIntStackSize()).thenReturn(10);
        ScriptCallbackEvent ev = mock(ScriptCallbackEvent.class);
        when(ev.getEventName()).thenReturn("bankSearchFilter");
        m.onScriptCallbackEvent(ev);
        assertEquals(1, stack[8]);
    }

    @Test
    public void scriptCallback_buyOnly_withSellSignal_excludes() throws Exception
    {
        BankFilterManager m = newManager();
        setField(m, "activeMode", BuySellIndicatorConfig.FilterMode.BUY_ONLY);
        ItemComposition def = mock(ItemComposition.class);
        when(def.isTradeable()).thenReturn(true);
        when(itemManager.getItemComposition(4151)).thenReturn(def);
        when(analysisService.getCachedSignal(4151)).thenReturn(new SignalResult(Signal.SELL, 90.0, 0L));

        int[] stack = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 1, 4151};
        when(client.getIntStack()).thenReturn(stack);
        when(client.getIntStackSize()).thenReturn(10);
        ScriptCallbackEvent ev = mock(ScriptCallbackEvent.class);
        when(ev.getEventName()).thenReturn("bankSearchFilter");
        m.onScriptCallbackEvent(ev);
        assertEquals(0, stack[8]);
    }

    @Test
    public void scriptCallback_sellOnly_withSell_includes() throws Exception
    {
        BankFilterManager m = newManager();
        setField(m, "activeMode", BuySellIndicatorConfig.FilterMode.SELL_ONLY);
        ItemComposition def = mock(ItemComposition.class);
        when(def.isTradeable()).thenReturn(true);
        when(itemManager.getItemComposition(1)).thenReturn(def);
        when(analysisService.getCachedSignal(1)).thenReturn(new SignalResult(Signal.SELL, 80.0, 0L));

        int[] stack = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
        when(client.getIntStack()).thenReturn(stack);
        when(client.getIntStackSize()).thenReturn(10);
        ScriptCallbackEvent ev = mock(ScriptCallbackEvent.class);
        when(ev.getEventName()).thenReturn("bankSearchFilter");
        m.onScriptCallbackEvent(ev);
        assertEquals(1, stack[8]);
    }

    @Test
    public void scriptCallback_buyAndSell_hold_excludes() throws Exception
    {
        BankFilterManager m = newManager();
        setField(m, "activeMode", BuySellIndicatorConfig.FilterMode.BUY_AND_SELL);
        ItemComposition def = mock(ItemComposition.class);
        when(def.isTradeable()).thenReturn(true);
        when(itemManager.getItemComposition(1)).thenReturn(def);
        when(analysisService.getCachedSignal(1)).thenReturn(new SignalResult(Signal.HOLD, 100.0, 0L));

        int[] stack = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 1, 1};
        when(client.getIntStack()).thenReturn(stack);
        when(client.getIntStackSize()).thenReturn(10);
        ScriptCallbackEvent ev = mock(ScriptCallbackEvent.class);
        when(ev.getEventName()).thenReturn("bankSearchFilter");
        m.onScriptCallbackEvent(ev);
        assertEquals(0, stack[8]);
    }

    @Test
    public void scriptCallback_negativeRawId_noWrite() throws Exception
    {
        BankFilterManager m = newManager();
        setField(m, "activeMode", BuySellIndicatorConfig.FilterMode.BUY_ONLY);
        int[] stack = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 7, -5};
        when(client.getIntStack()).thenReturn(stack);
        when(client.getIntStackSize()).thenReturn(10);
        ScriptCallbackEvent ev = mock(ScriptCallbackEvent.class);
        when(ev.getEventName()).thenReturn("bankSearchFilter");
        m.onScriptCallbackEvent(ev);
        assertEquals(7, stack[8]);
    }

    @Test
    public void scriptCallback_rawIdZero_nonTradeableOrNull_excludes() throws Exception
    {
        BankFilterManager m = newManager();
        setField(m, "activeMode", BuySellIndicatorConfig.FilterMode.BUY_ONLY);
        ItemComposition def = mock(ItemComposition.class);
        when(def.isTradeable()).thenReturn(false);
        when(itemManager.getItemComposition(0)).thenReturn(def);

        int[] stack = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 1, 0};
        when(client.getIntStack()).thenReturn(stack);
        when(client.getIntStackSize()).thenReturn(10);
        ScriptCallbackEvent ev = mock(ScriptCallbackEvent.class);
        when(ev.getEventName()).thenReturn("bankSearchFilter");
        m.onScriptCallbackEvent(ev);
        assertEquals(0, stack[8]);
    }

    @Test
    public void scriptCallback_nullItemComposition_noNpe() throws Exception
    {
        BankFilterManager m = newManager();
        setField(m, "activeMode", BuySellIndicatorConfig.FilterMode.BUY_ONLY);
        when(itemManager.getItemComposition(123)).thenReturn(null);

        int[] stack = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 1, 123};
        when(client.getIntStack()).thenReturn(stack);
        when(client.getIntStackSize()).thenReturn(10);
        ScriptCallbackEvent ev = mock(ScriptCallbackEvent.class);
        when(ev.getEventName()).thenReturn("bankSearchFilter");
        m.onScriptCallbackEvent(ev);
        assertEquals(0, stack[8]);
    }

    @Test
    public void scriptCallback_lowConfidence_excludes() throws Exception
    {
        BankFilterManager m = newManager();
        setField(m, "activeMode", BuySellIndicatorConfig.FilterMode.BUY_ONLY);
        ItemComposition def = mock(ItemComposition.class);
        when(def.isTradeable()).thenReturn(true);
        when(itemManager.getItemComposition(1)).thenReturn(def);
        when(analysisService.getCachedSignal(1)).thenReturn(new SignalResult(Signal.BUY, 5.0, 0L));
        when(config.minConfidence()).thenReturn(40);

        int[] stack = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 1, 1};
        when(client.getIntStack()).thenReturn(stack);
        when(client.getIntStackSize()).thenReturn(10);
        ScriptCallbackEvent ev = mock(ScriptCallbackEvent.class);
        when(ev.getEventName()).thenReturn("bankSearchFilter");
        m.onScriptCallbackEvent(ev);
        assertEquals(0, stack[8]);
    }

    @Test
    public void scriptCallback_nonTradeable_excludes() throws Exception
    {
        BankFilterManager m = newManager();
        setField(m, "activeMode", BuySellIndicatorConfig.FilterMode.BUY_ONLY);
        ItemComposition def = mock(ItemComposition.class);
        when(def.isTradeable()).thenReturn(false);
        when(itemManager.getItemComposition(1)).thenReturn(def);
        when(analysisService.getCachedSignal(1)).thenReturn(new SignalResult(Signal.BUY, 99.0, 0L));

        int[] stack = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 1, 1};
        when(client.getIntStack()).thenReturn(stack);
        when(client.getIntStackSize()).thenReturn(10);
        ScriptCallbackEvent ev = mock(ScriptCallbackEvent.class);
        when(ev.getEventName()).thenReturn("bankSearchFilter");
        m.onScriptCallbackEvent(ev);
        assertEquals(0, stack[8]);
    }

    // --- Group L: cycleMode ---

    @Test
    public void cycleMode_filterDisabled_doesNothing() throws Exception
    {
        when(config.enableBankSignalFilter()).thenReturn(false);
        BankFilterManager m = newManager();
        setField(m, "activeMode", BuySellIndicatorConfig.FilterMode.BUY_ONLY);
        invokeCycleMode(m);
        assertEquals(BuySellIndicatorConfig.FilterMode.BUY_ONLY, getField(m, "activeMode"));
        verify(bankSearch, never()).layoutBank();
    }

    @Test
    public void cycleMode_offToBuy_invokesLayoutBank() throws Exception
    {
        BankFilterManager m = newManager();
        stubUniverseForInject();
        setField(m, "activeMode", BuySellIndicatorConfig.FilterMode.OFF);
        Widget bg = mock(Widget.class);
        Widget label = mock(Widget.class);
        setField(m, "filterBackground", bg);
        setField(m, "filterLabel", label);
        when(client.getVarbitValue(anyInt())).thenReturn(0);
        Widget searchBg = mock(Widget.class);
        when(client.getWidget(InterfaceID.Bankmain.SEARCH)).thenReturn(searchBg);

        invokeCycleMode(m);

        assertEquals(BuySellIndicatorConfig.FilterMode.BUY_ONLY, getField(m, "activeMode"));
        verify(bankSearch).layoutBank();
    }

    @Test
    public void cycleMode_fromBuyAndSellToOff_resetsBankSearch() throws Exception
    {
        BankFilterManager m = newManager();
        stubUniverseForInject();
        setField(m, "activeMode", BuySellIndicatorConfig.FilterMode.BUY_AND_SELL);
        Widget bg = mock(Widget.class);
        Widget label = mock(Widget.class);
        setField(m, "filterBackground", bg);
        setField(m, "filterLabel", label);
        when(client.getVarbitValue(anyInt())).thenReturn(0);
        when(client.getWidget(InterfaceID.Bankmain.SEARCH)).thenReturn(mock(Widget.class));

        invokeCycleMode(m);
        assertEquals(BuySellIndicatorConfig.FilterMode.OFF, getField(m, "activeMode"));
        verify(bankSearch).reset(eq(true));
    }
}
