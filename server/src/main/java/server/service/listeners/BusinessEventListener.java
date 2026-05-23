package server.service.listeners;

/**
 * MARKER INTERFACE cho Business Logic.
 * Chỉ NotificationEventHandler và PaymentTriggerObserver được phép implement.
 * KHÔNG được dùng cho realtime UI.
 */
public interface BusinessEventListener extends AuctionEventListener {
}