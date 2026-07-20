package com.buysell;

import org.junit.Test;

import java.awt.Desktop;
import java.net.URI;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GraphOpeningServiceTest
{
    private static class ImmediateExecutor extends AbstractExecutorService
    {
        private boolean shutdown;

        @Override
        public void shutdown()
        {
            shutdown = true;
        }

        @Override
        public java.util.List<Runnable> shutdownNow()
        {
            shutdown = true;
            return java.util.Collections.emptyList();
        }

        @Override
        public boolean isShutdown()
        {
            return shutdown;
        }

        @Override
        public boolean isTerminated()
        {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit)
        {
            return true;
        }

        @Override
        public void execute(Runnable command)
        {
            command.run();
        }
    }

    @Test
    public void openItemGraph_invalidId_doesNothing()
    {
        ImmediateExecutor exec = new ImmediateExecutor();
        Desktop desktop = mock(Desktop.class);
        GraphOpeningService svc = new GraphOpeningService(exec, () -> true, () -> desktop);
        svc.openItemGraph(0);
        svc.openItemGraph(-3);
        verify(desktop, never()).isSupported(any());
    }

    @Test
    public void openItemGraph_happyPath_browsesUrl() throws Exception
    {
        ImmediateExecutor exec = new ImmediateExecutor();
        Desktop desktop = mock(Desktop.class);
        when(desktop.isSupported(Desktop.Action.BROWSE)).thenReturn(true);
        AtomicReference<URI> seen = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(inv ->
        {
            seen.set(inv.getArgument(0));
            return null;
        }).when(desktop).browse(any(URI.class));

        GraphOpeningService svc = new GraphOpeningService(exec, () -> true, () -> desktop);
        svc.openItemGraph(4151);
        assertEquals(URI.create(GraphOpeningService.PRICE_GRAPH_BASE_URL + "4151"), seen.get());
    }

    @Test
    public void openUrlInBrowser_desktopUnsupported()
    {
        Desktop desktop = mock(Desktop.class);
        GraphOpeningService svc = new GraphOpeningService(new ImmediateExecutor(), () -> false, () -> desktop);
        svc.openUrlInBrowser(GraphOpeningService.PRICE_GRAPH_BASE_URL + "1");
        try
        {
            verify(desktop, never()).browse(any());
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    public void openUrlInBrowser_browseUnsupported_doesNotThrow()
    {
        Desktop desktop = mock(Desktop.class);
        when(desktop.isSupported(Desktop.Action.BROWSE)).thenReturn(false);
        GraphOpeningService svc = new GraphOpeningService(new ImmediateExecutor(), () -> true, () -> desktop);
        svc.openUrlInBrowser(GraphOpeningService.PRICE_GRAPH_BASE_URL + "1");
        try
        {
            verify(desktop, never()).browse(any());
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    public void openUrlInBrowser_nullDesktop_doesNotThrow()
    {
        GraphOpeningService svc = new GraphOpeningService(new ImmediateExecutor(), () -> true, () -> null);
        svc.openUrlInBrowser(GraphOpeningService.PRICE_GRAPH_BASE_URL + "1");
    }

    @Test
    public void openUrlInBrowser_browseThrows_swallowed() throws Exception
    {
        Desktop desktop = mock(Desktop.class);
        when(desktop.isSupported(Desktop.Action.BROWSE)).thenReturn(true);
        doThrow(new RuntimeException("boom")).when(desktop).browse(any(URI.class));
        GraphOpeningService svc = new GraphOpeningService(new ImmediateExecutor(), () -> true, () -> desktop);
        svc.openUrlInBrowser(GraphOpeningService.PRICE_GRAPH_BASE_URL + "1");
    }

    @Test
    public void shutdown_shutsExecutor()
    {
        ImmediateExecutor exec = new ImmediateExecutor();
        GraphOpeningService svc = new GraphOpeningService(exec, () -> true, Desktop::getDesktop);
        svc.shutdown();
        assertTrue(exec.isShutdown());
    }

    @Test
    public void defaultConstructor_canShutdown()
    {
        GraphOpeningService svc = new GraphOpeningService();
        svc.shutdown();
    }
}
