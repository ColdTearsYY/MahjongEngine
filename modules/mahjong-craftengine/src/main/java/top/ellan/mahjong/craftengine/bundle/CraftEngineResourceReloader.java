package top.ellan.mahjong.craftengine.bundle;

import java.util.Objects;
import java.util.logging.Level;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import org.bukkit.plugin.Plugin;

/** Serializes resource-only reloads through CE's own manager lifecycle and reload event. */
public final class CraftEngineResourceReloader implements AutoCloseable {
    private final Plugin owner;
    private final BukkitCraftEngine engine;
    private final Object lock = new Object();
    private boolean inFlight;
    private boolean pending;
    private boolean waitingForReady;
    private boolean closed;

    public CraftEngineResourceReloader(Plugin owner, BukkitCraftEngine engine) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public void requestWhenReady() {
        synchronized (lock) {
            if (closed || waitingForReady) {
                return;
            }
            waitingForReady = true;
        }
        // CE sets isFullyLoaded after its initial reload event, on the platform scheduler.
        // A single delayed callback can run before that event, especially on Folia.
        engine.scheduler().platform().runDelayed(() -> checkReady(0));
    }

    private void checkReady(int attempts) {
        synchronized (lock) {
            if (closed || !owner.isEnabled()) {
                waitingForReady = false;
                return;
            }
            if (engine.isFullyLoaded()) {
                waitingForReady = false;
            } else if (attempts >= 60) {
                waitingForReady = false;
                owner.getLogger().severe(
                        "CraftEngine did not finish its initial resource load within 60 seconds");
                return;
            } else {
                engine.scheduler().platform().runLater(() -> checkReady(attempts + 1), 20L);
                return;
            }
        }
        request();
    }

    public void request() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            if (inFlight) {
                pending = true;
                return;
            }
            inFlight = true;
        }
        reload();
    }

    @Override
    public void close() {
        synchronized (lock) {
            closed = true;
            waitingForReady = false;
            pending = false;
        }
    }

    private void reload() {
        engine.reloadPlugin(
                        engine.scheduler().async(),
                        task -> engine.scheduler().platform().run(task),
                        false,
                        true)
                .whenComplete((result, failure) -> {
                    if (failure != null || result == null || !result.success()) {
                        owner.getLogger()
                                .log(
                                        Level.SEVERE,
                                        "CraftEngine resource reload failed; scenes remain closed",
                                        failure);
                        finishReload();
                        return;
                    }
                    engine.scheduler().executeAsync(() -> {
                        try {
                            engine.packManager().triggerWorkflows("reload_pack");
                        } catch (Exception generationFailure) {
                            owner.getLogger()
                                    .log(
                                            Level.SEVERE,
                                            "CraftEngine resource-pack generation failed",
                                            generationFailure);
                        } finally {
                            finishReload();
                        }
                    });
                });
    }

    private void finishReload() {
        boolean repeat;
        synchronized (lock) {
            repeat = pending && !closed && owner.isEnabled();
            pending = false;
            inFlight = repeat;
        }
        if (repeat) {
            reload();
        }
    }
}
