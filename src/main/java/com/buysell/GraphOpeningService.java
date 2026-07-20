package com.buysell;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Desktop;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Opens the prices.osrs.cloud item graph in the system browser.
 */
@Slf4j
@Singleton
public class GraphOpeningService
{
    static final String PRICE_GRAPH_BASE_URL = "https://prices.osrs.cloud/item/";

    private final ExecutorService browserExecutor;
    private final BooleanSupplier desktopSupported;
    private final Supplier<Desktop> desktopSupplier;

    @Inject
    public GraphOpeningService()
    {
        this(Executors.newSingleThreadExecutor(r ->
            {
                Thread t = new Thread(r, "buysell-view-graph");
                t.setDaemon(true);
                return t;
            }),
            Desktop::isDesktopSupported,
            Desktop::getDesktop);
    }

    GraphOpeningService(
        ExecutorService browserExecutor,
        BooleanSupplier desktopSupported,
        Supplier<Desktop> desktopSupplier)
    {
        this.browserExecutor = browserExecutor;
        this.desktopSupported = desktopSupported;
        this.desktopSupplier = desktopSupplier;
    }

    public void openItemGraph(int itemId)
    {
        if (itemId <= 0)
        {
            log.warn("View Graph: invalid item id {}", itemId);
            return;
        }
        String url = PRICE_GRAPH_BASE_URL + itemId;
        browserExecutor.execute(() -> openUrlInBrowser(url));
    }

    void openUrlInBrowser(String url)
    {
        try
        {
            if (!desktopSupported.getAsBoolean())
            {
                log.warn("View Graph: Desktop API not supported; cannot open {}", url);
                return;
            }
            Desktop desktop = desktopSupplier.get();
            if (desktop == null || !desktop.isSupported(Desktop.Action.BROWSE))
            {
                log.warn("View Graph: BROWSE action not supported; cannot open {}", url);
                return;
            }
            desktop.browse(new URI(url));
            log.debug("View Graph opened {}", url);
        }
        catch (Exception e)
        {
            log.warn("View Graph: failed to open {}: {}", url, e.getMessage());
        }
    }

    public void shutdown()
    {
        browserExecutor.shutdown();
    }
}
