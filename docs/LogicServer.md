# Logic Hoạt Động Server — Hệ Thống Đấu Giá Trực Tuyến

---

## 1. Tổng Quan Luồng Xử Lý

Mọi request từ client đều đi qua một luồng cố định:

```
Client (JSON qua Socket)
  → ClientHandler        (parse & route request)
  → AuctionService       (business logic, orchestration)
  → BidTransactionService (DB transaction + RAM sync)
  → AuctionManager / Auction (domain core)
  → DAO Layer            (persist xuống MySQL)
  → notify RealTimeObserver (push kết quả về client)
```

`AuctionManager` là điểm trung tâm duy nhất — mọi thao tác với auction đều phải qua đây, không tầng nào tham chiếu trực tiếp tới `Auction` object.

---

## 2. Luồng Đặt Giá (placeBid)

Khi server nhận request đặt giá `{auctionId, bidderId, amount}`:

**Bước 1 — Validate sơ bộ (Service layer)**
`AuctionService` kiểm tra auction và bidder tồn tại, bidder đang `ACTIVE`. Nếu không hợp lệ, từ chối ngay trước khi chạm DB.

**Bước 2 — DB lock**
`BidTransactionService` mở transaction và thực hiện `SELECT ... FOR UPDATE` trên row auction. Lấy `status` và `endTime` thực tế từ DB để guard — phòng trường hợp scheduler chưa kịp đổi trạng thái trên RAM.

**Bước 3 — RAM lock + validate giá**
Gọi `Auction.placeBid()` trong `ReentrantLock(fair=true)`. Bên trong lock, kiểm tra lại status, endTime, rồi validate amount theo 3 điều kiện: `amount > 0`, `amount > currentPrice`, `amount - currentPrice >= minBidIncrement`. Nếu pass, cập nhật `currentPrice`, `currentLeader`, thêm `BidTransaction` mới vào `bidHistory`, đánh dấu bid cũ thành `OUTBID`.

**Bước 4 — Đồng bộ DB**
`UPDATE auctions` (price, winner, end_time) và `INSERT bid_transactions` trong cùng một transaction. Sau đó `commit`.

**Bước 5 — Notify**
Sau khi commit thành công, push realtime qua socket và gọi `auction.notifyBidCommitted()` để các `RealTimeObserver` cập nhật UI cho tất cả client trong phòng.

---

## 3. Anti-Sniping

Mỗi lần bid thành công, trong vùng lock, server tính `secondsLeft = endTime - now()`. Nếu `secondsLeft <= snipeWindowSeconds`, `endTime` được cộng thêm `snipeExtensionSeconds` ngay trong RAM. Sau khi commit DB, `end_time` mới được ghi xuống và `AuctionManager` reschedule lại task đóng phiên.

Scheduler tự kiểm tra: khi task đóng phiên chạy, nếu `remaining > 1s` thì tự lên lịch lại thay vì đóng ngay — xử lý trường hợp anti-snipe kéo dài nhiều lần liên tiếp.

---

## 4. Auto-Bid Engine

Sau mỗi bid thủ công commit thành công, `AuctionService` gọi `AutoBidEngine.trigger()`. Engine hoạt động theo các bước:

**Xác định winner** — `peek()` config có `maxBid` cao nhất trong `PriorityQueue`. Nếu người vừa bid chính là winner thì dừng (không tự outbid mình).

**Tính giá auto-bid** theo 2 trường hợp:
- Chỉ có 1 config: `amount = currentPrice + increment` (không vượt `maxBid`).
- Có 2+ config cạnh tranh: `amount = secondWinner.maxBid + winner.increment` (không vượt `winner.maxBid`).

**Persist auto-bid** — Engine trả về `PlaceBidResult`, `AuctionService` gọi lại toàn bộ flow `placeBid()` với `isAutoBid=true` để persist qua đúng DB transaction, không duplicate code.

Trigger được gọi trong 3 tình huống: auction bắt đầu (OPEN→RUNNING), sau mỗi bid thủ công thành công, và khi có config auto-bid mới đăng ký vào phiên đang chạy.

---

## 5. Đồng Bộ RAM và Database

Hệ thống giữ bất biến: **RAM state và DB state luôn nhất quán** — hoặc cả hai cùng có bid mới, hoặc cả hai rollback về trạng thái trước.

**Khi DB commit thất bại**, `BidTransactionService` gọi `auction.rollbackLastBid()` để khôi phục RAM về snapshot trước khi `placeBid()` chạy — xóa `BidTransaction` vừa thêm, khôi phục `currentPrice`, `currentLeader`, `endTime`, và restore `OUTBID → WINNING` cho bid cũ.

**Khi auto-bid persist thất bại**, chỉ rollback phần RAM của auto-bid. Manual bid đã committed DB trước đó **không bị rollback**.

**Khi đóng phiên thất bại**, `onAuctionClosed()` dùng Reflection để set lại `status = RUNNING` trên RAM, cho phép Scheduler quét lại ở chu kỳ sau — tránh phiên bị kẹt ở trạng thái sai mà không có cách phục hồi.

---

## 6. Vòng Đời Auction

```
OPEN  →  RUNNING  →  FINISHED  →  PAID
                  ↘  CANCELED
```

`AuctionManager` dùng `ScheduledExecutorService` (thread pool, daemon) để tự động chuyển trạng thái:
- `scheduleOpen()` — chạy khi đến `startTime`, gọi `auction.startRunning()` rồi trigger auto-bid.
- `scheduleClose()` — chạy khi đến `endTime`, kiểm tra remaining time trước khi đóng (re-schedule nếu anti-snipe đã gia hạn).

Khi server khởi động lại, `AuctionService.loadAllFromDatabase()` load lại toàn bộ auction `OPEN/RUNNING`, khôi phục bid history và auto-bid config, rồi đăng ký lại vào scheduler — đảm bảo không mất phiên sau khi restart.

---

## 7. Thread Safety

| Cơ chế | Nơi áp dụng | Mục đích |
|---|---|---|
| `SELECT ... FOR UPDATE` | DB layer | Ngăn 2 request cùng sửa row auction |
| `ReentrantLock(fair=true)` | `Auction.placeBid()` | Bảo vệ critical section trên RAM, FIFO ordering |
| `ConcurrentHashMap` | `AuctionManager`, `AutoBidEngine` | Map auction/user thread-safe |
| `CopyOnWriteArrayList` | `Auction.observers` | Đọc-ghi observer list đồng thời an toàn |

Double-check pattern được áp dụng trong `placeBid()`: kiểm tra status trước lock (fast path) và kiểm tra lại sau khi acquire lock — phòng trường hợp phiên vừa đóng trong khoảng giữa hai lần check.
