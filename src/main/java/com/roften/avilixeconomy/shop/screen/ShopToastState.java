package com.roften.avilixeconomy.shop.screen;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Lightweight client-side "toast" messages intended to be rendered ABOVE the shop GUI.
 *
 * Does not reference any client-only classes, so it is safe to keep in common sources.
 */
public final class ShopToastState {

    private ShopToastState() {}

    public record Toast(Component message, boolean success, long expiresAtMillis) {}

    private static final Deque<Toast> QUEUE = new ArrayDeque<>();
    private static Toast current;

    /** Push a new toast. Default duration: 2.5s. */
    public static synchronized void push(Component message, boolean success) {
        push(message, success, 2500);
    }

    public static synchronized void push(Component message, boolean success, int durationMs) {
        long until = System.currentTimeMillis() + Math.max(250, durationMs);
        Toast t = new Toast(message, success, until);
        if (current == null) {
            current = t;
        } else {
            QUEUE.addLast(t);
        }
    }

    /** Call once per tick (e.g. from Shop screens). */
    public static synchronized void tick() {
        if (current == null) return;
        if (System.currentTimeMillis() <= current.expiresAtMillis()) return;
        current = QUEUE.pollFirst();
    }

    public static synchronized @Nullable Toast getCurrent() {
        return current;
    }

    public static synchronized void clear() {
        QUEUE.clear();
        current = null;
    }
}
