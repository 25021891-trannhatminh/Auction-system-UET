# Báo Cáo Phân Tích: Hệ Thống Notification & Payment

---

## 1. Tổng Quan Kiến Trúc

Hệ thống được thiết kế theo mô hình **Observer Pattern**, tách biệt rõ hai luồng xử lý:

- **BusinessEventListener** — xử lý thông báo nghiệp vụ nặng (payment, lưu DB thông báo)
- **RealTimeObserver** — phục vụ cập nhật UI tức thời qua socket (Outbid, BidSuccess, TimeExtend)

Hai interface này là *marker interface*, chỉ cho phép implement bởi các class được chỉ định cụ thể, ngăn các thành phần khác lắng nghe sai tầng.

---

## 2. Phân Hệ Notification

### 2.1. Luồng xử lý thông báo

```
Auction / Service Layer
        │
        ▼
NotificationEventHandler  (implements BusinessEventListener + RealTimeObserver)
        │
        ├──► NotificationService.push()          ← Lưu DB + enqueue socket
        │         │
        │         ├── NotificationDAO.insert()    ← Ghi vào bảng notifications
        │         └── NotificationDispatcher.submit(NotificationEvent)
        │
        └──► NotificationService.pushRealtimeOnly()  ← Chỉ enqueue socket, KHÔNG ghi DB
                  │
                  └── NotificationDispatcher.submit(NotificationEvent)
```

### 2.2. Phân loại phương thức push

| Phương thức | Ghi DB | Gửi Socket | Dùng cho |
|---|---|---|---|
| `push()` | ✅ | ✅ | Sự kiện nghiệp vụ quan trọng (won, lost, payment) |
| `pushRealtimeOnly()` | ❌ | ✅ | Realtime tức thời (outbid, bid placed, time extended) |

**Lý do thiết kế:** Các sự kiện như `onOutbid` hay `onBidPlacedSuccess` xảy ra liên tục trong một phiên đấu giá và không cần lưu lịch sử vì BidHistory lưu thông tin này. Việc chỉ push realtime giảm tải đáng kể cho database mà không mất thông tin quan trọng.

### 2.3. NotificationDispatcher — Trung tâm phân phối

`NotificationDispatcher` là Singleton chạy trên một **worker thread riêng biệt**, nhận event từ `BlockingQueue` và phân phối đến đúng đích.

**Ba nhánh gửi tin:**

1. `userId = -1` → **Broadcast** toàn bộ client đang online
2. `userId = 0` → **Auction broadcast**: gửi tới tất cả watcher của phiên đó
3. `userId = N` → **Unicast**: gửi riêng cho user cụ thể

**Quản lý Auction Watchers:**

Dispatcher duy trì `Map<auctionId, Set<userId>>` bằng `ConcurrentHashMap` để theo dõi ai đang xem phiên nào. Khi client đặt giá hoặc xem phiên, họ được `subscribeAuction()` và tự động `unsubscribeAll()` khi disconnect.

**Đặc biệt — `pushRawToAuctionWatchers()`:** Bypass hàng đợi để push `AUCTION_BID_UPDATE` trực tiếp với độ trễ thấp nhất, chỉ dùng cho các message realtime không cần đảm bảo delivery.

### 2.4. Các loại thông báo theo sự kiện

| Sự kiện | Interface | Ghi DB | Nội dung |
|---------|-----------|--------|-----------|
| `onBidPlacedSuccess` | `RealTimeObserver` | ❌ | Xác nhận bid cho người đặt |
| `onOutbid` | `RealTimeObserver` | ❌ | Cảnh báo bị vượt giá |
| `onTimeExtended` | `RealTimeObserver` | ❌ | Phiên được gia hạn |
| `onAuctionStarted` | `BusinessEventListener` | ✅ | Phiên bắt đầu |
| `onAuctionEnded` | `BusinessEventListener` | ✅ | Phiên kết thúc |
| `onAuctionWon` | `BusinessEventListener` | ✅ | Người thắng cuộc |
| `onAuctionLost` | `BusinessEventListener` | ✅ | Người thua cuộc |
| `onPaymentDue` | `BusinessEventListener` | ✅ | Nhắc nhở thanh toán |
| `onPaymentReceived` | `BusinessEventListener` | ✅ | Xác nhận seller nhận tiền |
| `onItemApproved` | `BusinessEventListener` | ✅ | Duyệt vật phẩm |
| `onItemRejected` | `BusinessEventListener` | ✅ | Từ chối vật phẩm |
| `onSystemNotification` | `BusinessEventListener` | ✅ | Thông báo hệ thống |

### 2.5. NotificationUIHandler — Phía Client (JavaFX)

Client nhận message dạng `PUSH_NOTIF|TYPE|TITLE|MESSAGE`, parse và hiển thị **Toast notification** tương ứng.

Mapping loại Toast:

| Loại Toast | Notification Types |
|---|---|
| SUCCESS | AUCTION_WON, BID_PLACED, ITEM_APPROVED, PAYMENT_RECEIVED, AUCTION_STARTED |
| WARNING | OUTBID, PAYMENT_DUE |
| ERROR | AUCTION_LOST, ITEM_REJECTED |
| INFO | Các loại còn lại |

Toast tự động ẩn sau 4 giây với hiệu ứng fade in/out. Mọi thao tác UI đều chạy qua `Platform.runLater()` để đảm bảo thread-safe với JavaFX.

---

## 3. Phân Hệ Payment

### 3.1. Luồng tổng thể

```
Auction kết thúc (FINISHED)
        │
        ▼
PaymentTriggerObserver.onAuctionSessionClosed()
        │  (async — paymentExecutor thread)
        ▼
PaymentService.createPendingPayment()
        │  → Tạo bản ghi payments (status = PENDING)
        │
        ▼
[Buyer xác nhận thanh toán]
        │
        ▼
PaymentService.processPayment()
        │  → DB Transaction: withdraw buyer + deposit seller
        │  → Cập nhật payments (PENDING → COMPLETED)
        │  → Cập nhật auction (FINISHED → PAID)
        │  → Thông báo + cập nhật wallet UI realtime
```

### 3.2. PaymentService — Xử lý giao dịch tài chính

#### Tạo Pending Payment (`createPendingPayment`)
- Kiểm tra auction tồn tại trong RAM (`AuctionManager`)
- Xác minh có `currentLeader` (người thắng)
- Idempotency check lần hai trước khi INSERT
- Ghi bản ghi `payments` với status `PENDING`

#### Xử lý Thanh toán (`processPayment`)
Toàn bộ thực hiện trong **một DB Transaction duy nhất**:

1. **Lock payment row** (SELECT FOR UPDATE) → ngăn xử lý trùng
2. **Đảm bảo wallet tồn tại** cho cả buyer và seller
3. **Lock ví theo thứ tự userId tăng dần** → tránh deadlock
4. Kiểm tra số dư buyer đủ để thanh toán
5. `withdraw` từ ví buyer
6. `deposit` vào ví seller
7. Ghi log vào `wallet_transactions` (PAYMENT) cho cả hai
8. Cập nhật `payments` → `COMPLETED`
9. Cập nhật `auction` → `PAID`
10. `COMMIT`

**Post-commit (ngoài transaction):**
- Gửi thông báo cho buyer và seller qua `NotificationService`
- Push `WALLET_UPDATE` realtime để client cập nhật số dư ngay lập tức

#### Hoàn tiền (`refundPayment`)
Quy trình ngược: lock payment `COMPLETED`, chuyển tiền seller → buyer, ghi log `REFUND`, cập nhật status → `REFUNDED`.

---

## 4. Đánh Giá Kỹ Thuật
- Phân tách rõ **realtime (socket)** và **persistent (DB)** notification tránh ghi DB thừa
- Observer Pattern cho phép mở rộng listener mới mà không sửa core auction logic
- Payment transaction đảm bảo tính **ACID** đầy đủ; không có trạng thái trung gian không nhất quán
- Graceful shutdown cho `paymentExecutor` đảm bảo giao dịch dở dang được hoàn thành trước khi tắt server
