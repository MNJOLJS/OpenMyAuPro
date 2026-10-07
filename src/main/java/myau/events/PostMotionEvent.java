package myau.events;

import myau.event.events.Event;

public class PostMotionEvent implements Event {
	private final boolean flyingPacketSent;

	public PostMotionEvent(boolean flyingPacketSent) {
		this.flyingPacketSent = flyingPacketSent;
	}

	public boolean hasFlyingPacket() {
		return this.flyingPacketSent;
	}
}