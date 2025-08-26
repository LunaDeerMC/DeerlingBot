package cn.lunadeer.mc.deerlingbot.protocols.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractPost extends Event {

    public enum PostType {
        message,
        notice,
        request,
        meta_event
    }

    private static final HandlerList HANDLER_LIST = new HandlerList();

    /**
     * Gets the static handler list for this event.
     *
     * @return the handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    /**
     * Gets the handlers for this event.
     *
     * @return the handler list
     */
    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    /**
     * Calls the event, equivalent to Paper's callEvent method.
     * This method is defined here for Spigot compatibility.
     *
     * @return true if the event was successfully executed, false if the event was cancelled
     */
    public boolean call() {
        org.bukkit.Bukkit.getPluginManager().callEvent(this);
        if (this instanceof Cancellable) {
            return !((Cancellable) this).isCancelled();
        } else {
            return true;
        }
    }

    private final long time;
    private final long self_id;

    public AbstractPost(long time, long self_id) {
        this.time = time;
        this.self_id = self_id;
    }

    public long getTime() {
        return time;
    }

    public long getSelfId() {
        return self_id;
    }
}
