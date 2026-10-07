package myau.util;

import java.util.concurrent.atomic.AtomicLong;

public final class WalkingPacketTracker {
    private static final AtomicLong SENT_FLYING_PACKETS = new AtomicLong();
    private static final ThreadLocal<Long> START_PACKET_COUNT = new ThreadLocal<>();

    private WalkingPacketTracker() {
    }

    public static void begin() {
        START_PACKET_COUNT.set(SENT_FLYING_PACKETS.get());
    }

    public static void markFlyingPacketSent() {
        SENT_FLYING_PACKETS.incrementAndGet();
    }

    public static boolean finish() {
        Long startCount = START_PACKET_COUNT.get();
        START_PACKET_COUNT.remove();
        return startCount != null && SENT_FLYING_PACKETS.get() > startCount;
    }
}