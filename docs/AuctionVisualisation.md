[//]: # (# Báo Cáo: Auction Visualisation Flow)

[//]: # ()
[//]: # (## Flow Tổng Thể)

[//]: # ()
[//]: # (```)

[//]: # ([User mở auction detail])

[//]: # (        │)

[//]: # (        ▼)

[//]: # (buildAuctionVisualisationSection&#40;&#41;)

[//]: # (        │  set activeAuctionVisualisationSection + activeAuctionVisualisationAuctionId)

[//]: # (        │  UI hiển thị "Loading bid chart from database...")

[//]: # (        ▼)

[//]: # (requestAuctionVisualisation&#40;auctionId&#41;)

[//]: # (        │  pendingVisualisationAuctionId = auctionId)

[//]: # (        │  auctionVisualisationLoaded = false)

[//]: # (        ▼)

[//]: # (Gửi: GET_AUCTION_VISUALISATION <auctionId>)

[//]: # (        │)

[//]: # (        ▼)

[//]: # (Server: AuctionVisualisationHandler)

[//]: # (        │  validate command / userId / format / parse int)

[//]: # (        ▼)

[//]: # (AuctionVisualisationService.getVisualisation&#40;auctionId&#41;)

[//]: # (        │  AuctionDAO → ItemDAO → BidTransactionDAO)

[//]: # (        ▼)

[//]: # (ResponseBuilder → AUCTION_VISUALISATION_SUCCESS payload)

[//]: # (        │)

[//]: # (        ▼)

[//]: # (Client: handleAuctionVisualisationMessage&#40;&#41;)

[//]: # (        │  parse → cache vào auctionVisualisations map)

[//]: # (        │  auctionVisualisationLoaded = true)

[//]: # (        ▼)

[//]: # (refreshActiveAuctionVisualisationSection&#40;&#41; → render LineChart)

[//]: # (```)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (**Trigger thứ hai — realtime update:**)

[//]: # ()
[//]: # (```)

[//]: # ([Bid thành công])

[//]: # (        │)

[//]: # (        ├──► AUCTION_BID_UPDATE &#40;broadcast ngay, không cần DB&#41;)

[//]: # (        │         └── cập nhật giá, countdown, tên leader trên UI)

[//]: # (        │)

[//]: # (        └──► isActiveAuction&#40;auctionId&#41; == true)

[//]: # (                  └── requestAuctionVisualisation&#40;auctionId&#41;)

[//]: # (                            └── refetch toàn bộ từ DB → re-render chart)

[//]: # (```)

[//]: # ()
[//]: # (Mỗi bid thành công = chart được **refetch lại hoàn toàn từ DB**, không append điểm mới trực tiếp. `AUCTION_BID_UPDATE` không chứa `bidTime` nên không thể tự vẽ thêm điểm mà không qua DB.)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (## Điểm Quan Trọng)

[//]: # ()
[//]: # (**Hai luồng độc lập sau mỗi bid:** `AUCTION_BID_UPDATE` cập nhật metadata tức thì &#40;không cần DB&#41;, còn chart thì bắt buộc round-trip xuống DB mới cập nhật được.)

[//]: # ()
[//]: # (**Cache tại client:** `auctionVisualisations` &#40;Map&#41; giữ lại data — nếu user rời đi rồi quay lại, chart hiển thị ngay từ cache trong khi request mới đang chờ DB.)

[//]: # ()
[//]: # (**Trạng thái loading 3 cấp:**)

[//]: # (- `loaded = false`, `data = null` → hiển thị "Loading bid chart from database...")

[//]: # (- `loaded = true`, `data = null` → hiển thị "No visualisation data available")

[//]: # (- `data != null`, `points` rỗng → hiển thị metrics + "No bids placed yet")

[//]: # (- `data != null`, `points` có dữ liệu → hiển thị metrics + `LineChart`)

[//]: # ()
[//]: # (**Fallback khi FAIL:** `auctionVisualisationLoaded` vẫn được set `true` dù nhận `FAIL` — tránh UI bị kẹt ở trạng thái loading vô hạn.)

[//]: # ()
[//]: # (**UI guard chống race condition:** `refreshActiveAuctionVisualisationSection&#40;&#41;` kiểm tra `activeAuctionVisualisationAuctionId.equals&#40;auctionId&#41;` trước khi render — response trễ từ auction cũ không ghi đè chart của auction đang xem.)

[//]: # ()
[//]: # (**Fallback khi item bị xóa:** Service không throw exception khi `ItemDAO.getById&#40;&#41;` trả `null` — tên fallback thành `"Auction #<id>"`, `startingPrice` fallback thành `currentPrice`.)

[//]: # ()
[//]: # (---)

[//]: # ()
[//]: # (## Data Flow của Chart)

[//]: # ()
[//]: # (```)

[//]: # (DB &#40;bid_transactions&#41;)

[//]: # (    │  getBidPointHistory&#40;&#41; — sorted ASC by bidTime)

[//]: # (    ▼)

[//]: # (BidPointDTO list)

[//]: # (    ▼)

[//]: # (AuctionVisualisationDTO &#40;immutable, sort lại trong constructor&#41;)

[//]: # (    ▼)

[//]: # (ResponseBuilder → "time1,amount1;time2,amount2;...")

[//]: # (    │  &#40;| phân tách field, ; phân tách point, , phân tách time/amount&#41;)

[//]: # (    ▼)

[//]: # (Client parse → List<VisualisationPoint>)

[//]: # (    ▼)

[//]: # (populateAuctionVisualisationSection&#40;&#41;)

[//]: # (    │)

[//]: # (    ├── Metrics bar &#40;luôn hiển thị khi có data&#41;:)

[//]: # (    │       Starting price / Current price / Bid points &#40;count&#41; / Latest &#40;timestamp bid cuối&#41;)

[//]: # (    │)

[//]: # (    └── buildAuctionVisualisationChart&#40;&#41;)

[//]: # (            │  Trục X &#40;CategoryAxis&#41;:)

[//]: # (            │      ≤ 6 điểm → hiển thị timestamp thực &#40;"HH:mm:ss"&#41;)

[//]: # (            │      > 6 điểm → hiển thị số thứ tự &#40;1, 2, 3...&#41; — tránh label chồng nhau)

[//]: # (            │  Trục Y &#40;NumberAxis&#41;:)

[//]: # (            │      forceZeroInRange=false — scale theo vùng giá thực, không bắt đầu từ 0)

[//]: # (            │      → giúp thấy rõ biến động giá dù khoảng cách nhỏ)

[//]: # (            │  Symbols &#40;chấm tròn trên điểm&#41;:)

[//]: # (            │      ≤ 18 điểm → hiển thị — dễ nhìn từng bid)

[//]: # (            │      > 18 điểm → ẩn — tránh chart rối khi nhiều bid)

[//]: # (            │  setAnimated&#40;false&#41; — tắt animation, re-render ngay khi có bid mới)

[//]: # (            ▼)

[//]: # (    LineChart<String, Number> render trên JavaFX Application Thread)

[//]: # (        &#40;toàn bộ UI update chạy qua Platform.runLater&#41;)
# Báo Cáo: Auction Visualisation Flow

## Flow Tổng Thể

```
[User mở auction detail]
        │
        ▼
buildAuctionVisualisationSection()
        │  set activeAuctionVisualisationSection + activeAuctionVisualisationAuctionId
        │  UI hiển thị "Loading bid chart from database..."
        ▼
requestAuctionVisualisation(auctionId)
        │  pendingVisualisationAuctionId = auctionId
        │  auctionVisualisationLoaded = false
        ▼
Gửi: GET_AUCTION_VISUALISATION <auctionId>
        │
        ▼
Server: AuctionVisualisationHandler
        │  validate command / userId / format / parse int
        ▼
AuctionVisualisationService.getVisualisation(auctionId)
        │  AuctionDAO → ItemDAO → BidTransactionDAO
        ▼
ResponseBuilder → AUCTION_VISUALISATION_SUCCESS payload
        │
        ▼
Client: handleAuctionVisualisationMessage()
        │  parse → cache vào auctionVisualisations map
        │  auctionVisualisationLoaded = true
        ▼
refreshActiveAuctionVisualisationSection() → render LineChart
```

---

**Trigger thứ hai — realtime update:**

```
[Bid thành công]
        │
        ├──► AUCTION_BID_UPDATE (broadcast ngay, không cần DB)
        │         └── cập nhật giá, countdown, tên leader trên UI
        │
        └──► isActiveAuction(auctionId) == true
                  └── requestAuctionVisualisation(auctionId)
                            └── refetch toàn bộ từ DB → re-render chart
```

Mỗi bid thành công = chart được **refetch lại hoàn toàn từ DB**, không append điểm mới trực tiếp. `AUCTION_BID_UPDATE` không chứa `bidTime` nên không thể tự vẽ thêm điểm mà không qua DB.

---

## Cơ Chế Cancel Auto-Bid và Tác Động Lên Chart

Cancel auto-bid có **hai nhánh xử lý** tùy theo bidder có đang là winner hay không:

```
cancelAutoBid(auctionId, bidder)
        │
        ├── DB: autoBidConfigDAO.cancelByAuctionAndBidder()  — đánh dấu CANCELED trong DB
        ├── RAM: auctionManager.cancelAutoBid()              — xóa khỏi PriorityQueue
        │
        ├── [Nhánh 1] bidder KHÔNG phải winner
        │       └── triggerAutoBids() — các auto-bid còn lại phản ứng bình thường
        │               → nếu có bid mới → executePlaceBidFlow → onBidPlacedSuccess
        │               → broadcast AUCTION_BID_UPDATE → client requestAuctionVisualisation()
        │               → chart refetch DB, thêm điểm mới
        │
        └── [Nhánh 2] bidder LÀ winner (phức tạp hơn)
                │
                ├── rollBackLastAuctionStatus():
                │       - Tìm bid OUTBID gần nhất của người khác (người thứ 2)
                │       - Có người thứ 2 → rollback RAM về giá người đó, restore WINNING
                │       - Không có người thứ 2 → reset về startingPrice, leader = null
                │
                ├── executePlaceBidFlow(secondWinner) — persist DB + broadcast
                │       → onBidPlacedSuccess → AUCTION_BID_UPDATE
                │       → client requestAuctionVisualisation()
                │       → chart refetch DB
                │
                └── Chart phản ánh trạng thái SAU rollback:
                        - currentPrice trên metrics bar giảm xuống (giá người thứ 2 hoặc startingPrice)
                        - Số điểm trên chart KHÔNG giảm — bid_transactions không bị xóa khỏi DB
                        - Đường giá trên chart "lùi lại" ở lần refetch tiếp theo
```

**Điểm then chốt:** cancel auto-bid của winner **không xóa bid_transactions** khỏi DB. Chart giữ nguyên toàn bộ lịch sử — chỉ `currentPrice` và `currentLeader` trên metrics bar thay đổi để phản ánh trạng thái sau rollback.

---

## Điểm Quan Trọng

**Hai luồng độc lập sau mỗi bid:** `AUCTION_BID_UPDATE` cập nhật metadata tức thì (không cần DB), còn chart thì bắt buộc round-trip xuống DB mới cập nhật được.

**Cache tại client:** `auctionVisualisations` (Map) giữ lại data — nếu user rời đi rồi quay lại, chart hiển thị ngay từ cache trong khi request mới đang chờ DB.

**Trạng thái loading 3 cấp:**
- `loaded = false`, `data = null` → hiển thị "Loading bid chart from database..."
- `loaded = true`, `data = null` → hiển thị "No visualisation data available"
- `data != null`, `points` rỗng → hiển thị metrics + "No bids placed yet"
- `data != null`, `points` có dữ liệu → hiển thị metrics + `LineChart`

**Fallback khi FAIL:** `auctionVisualisationLoaded` vẫn được set `true` dù nhận `FAIL` — tránh UI bị kẹt ở trạng thái loading vô hạn.

**UI guard chống race condition:** `refreshActiveAuctionVisualisationSection()` kiểm tra `activeAuctionVisualisationAuctionId.equals(auctionId)` trước khi render — response trễ từ auction cũ không ghi đè chart của auction đang xem.

**Fallback khi item bị xóa:** Service không throw exception khi `ItemDAO.getById()` trả `null` — tên fallback thành `"Auction #<id>"`, `startingPrice` fallback thành `currentPrice`.

**Immutable DTO + double sort:** `AuctionVisualisationDTO` dùng `Collections.unmodifiableList`, constructor tự sort `points` theo `bidTime` (nulls last). Tuy nhiên `BidTransactionDAO.getBidPointHistory()` đã có `ORDER BY bid_time ASC` ở tầng SQL rồi — nghĩa là dữ liệu được sort **hai lần**: một lần ở DB, một lần ở constructor. Sort ở DTO là lớp phòng thủ phòng trường hợp DAO khác truyền vào không đảm bảo thứ tự, nhưng trong flow hiện tại thì thừa.

**Encode protocol-safe:** `encodeField()` escape `|`, `\`, newline trong `itemName` — tránh phá vỡ cấu trúc pipe-delimited khi tên item có ký tự đặc biệt.

---

## Data Flow của Chart

```
DB (bid_transactions)
    │  getBidPointHistory() — sorted ASC by bidTime
    ▼
BidPointDTO list
    ▼
AuctionVisualisationDTO (immutable, sort lại trong constructor)
    ▼
ResponseBuilder → "time1,amount1;time2,amount2;..."
    │  (| phân tách field, ; phân tách point, , phân tách time/amount)
    ▼
Client parse → List<VisualisationPoint>
    ▼
populateAuctionVisualisationSection()
    │
    ├── Metrics bar (luôn hiển thị khi có data):
    │       Starting price / Current price / Bid points (count) / Latest (timestamp bid cuối)
    │
    └── buildAuctionVisualisationChart()
            │  Trục X (CategoryAxis):
            │      ≤ 6 điểm → hiển thị timestamp thực ("HH:mm:ss")
            │      > 6 điểm → hiển thị số thứ tự (1, 2, 3...) — tránh label chồng nhau
            │  Trục Y (NumberAxis):
            │      forceZeroInRange=false — scale theo vùng giá thực, không bắt đầu từ 0
            │      → giúp thấy rõ biến động giá dù khoảng cách nhỏ
            │  Symbols (chấm tròn trên điểm):
            │      ≤ 18 điểm → hiển thị — dễ nhìn từng bid
            │      > 18 điểm → ẩn — tránh chart rối khi nhiều bid
            │  setAnimated(false) — tắt animation, re-render ngay khi có bid mới
            ▼
    LineChart<String, Number> render trên JavaFX Application Thread
        (toàn bộ UI update chạy qua Platform.runLater)
```

**Lưu ý về metrics bar:** 4 chỉ số được render **trước** chart và tồn tại cả khi `points` rỗng. Riêng `Latest` lấy từ `data.points.get(last).time` — nếu chưa có bid thì hiển thị `"No bids yet"`.