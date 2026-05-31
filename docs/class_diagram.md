# Báo cáo Sơ đồ Entity — Hệ thống Đấu Giá Trực Tuyến


## 1. Cây Kế Thừa Chính

```mermaid
classDiagram
    class Entity {
        <<abstract>>
        +id: int
        +createdAt: LocalDateTime
        +printInfo() void
    }

    class Account {
        <<abstract>>
        +username: String
        +email: String
        +passwordHash: String
        +fullName: String
        +phone: String
        +role: AccountRole
        +status: UserStatus
        +lastLogin: LocalDateTime
        +verifyPassword(plain) boolean
        +recordLogin() void
    }

    class Item {
        <<abstract>>
        +sellerId: int
        +name: String
        +description: String
        +startingPrice: BigDecimal
        +status: ItemStatus
        +category: ItemCategory
        +imageUrls: List~String~
        +getCategory() String
        +validate() boolean
        +addImageUrl() void
    }

    class User {
        +balance: BigDecimal
        +rating: double
        +autoBidMap: Map~int, AutoBidConfig~
        +itemIDs: List~String~
        +deposit(amount) void
        +debit(amount) void
        +setAutoBid(config) void
        +cancelAutoBid(auctionId) void
        +hasAutoBid(auctionId) boolean
    }

    class Admin {
        +permissions: Set~AdminPermission~
        +hasPermission(p) boolean
        +grantPermission(p) void
        +revokePermission(p) void
        +getPermissions() Set
    }

    class Electronics {
        +brand: String
        +warrantyMonths: int
        +getCategory() String
        +validate() boolean
    }

    class Art {
        +artist: String
        +yearCreated: int
        +medium: String
        +getCategory() String
        +validate() boolean
    }

    class Vehicle {
        +brand: String
        +model: String
        +yearManufactured: int
        +mileageKm: int
        +getCategory() String
        +validate() boolean
    }

    class Auction {
        +sellerId: int
        +startTime: LocalDateTime
        +endTime: LocalDateTime
        +startingPrice: BigDecimal
        +minBidIncrement: BigDecimal
        +reservePrice: BigDecimal
        +status: AuctionStatus
        +currentPrice: BigDecimal
        +currentLeader: User
        +lastBidTime: LocalDateTime
        +snipeWindowSeconds: int
        +snipeExtensionSeconds: int
        +lock: ReentrantLock
        +placeBid(bidder, amount, isAuto) PlaceBidResult
        +closeSession() void
        +forceCancel(reason) void
        +startRunning() void
        +rollbackLastBid(...) void
        +getSecondsRemaining() long
    }

    class BidTransaction {
        +id: int
        +auctionId: int
        +bidderId: int
        +bidderName: String
        +amount: BigDecimal
        +bidTime: LocalDateTime
        +isAutoBid: boolean
        +status: BidStatus
        +markOutbid() void
        +markWon() void
        +markLost() void
        +restoreWinning() void
    }

    class AutoBidConfig {
        +auctionId: int
        +bidderId: int
        +maxBid: BigDecimal
        +increment: BigDecimal
        +status: AutoBidStatus
        +registeredAt: LocalDateTime
        +canBid(currentPrice) boolean
        +nextBidAmount(currentPrice) BigDecimal
        +cancel() void
        +complete() void
        +compareTo(other) int
    }

    class AutoBidEngine {
        -AutoBidManager: Map~int, PriorityQueue~
        -AutoBidBidderManager: Map~int, Map~
        +register(config, bidder) void
        +unregister(auctionId, bidderId) void
        +trigger(auction, triggerBidder) PlaceBidResult
        +cleanupAutoBids(auctionId) void
        +peekWinner(auctionId) AutoBidConfig
        +calculateWinnerAmount(...) BigDecimal
    }

    class AuctionManager {
        <<Singleton>>
        -auctionMap: Map~int, Auction~
        -userMap: Map~int, User~
        -autoBidEngine: AutoBidEngine
        -scheduler: ScheduledExecutorService
        +getInstance() AuctionManager
        +createAuction(...) Auction
        +loadAuction(auction) void
        +placeBid(...) void
        +registerAutoBid(config, bidder) void
        +cancelAutoBid(auctionId, bidder) void
        +forceCloseAuction(id, reason) void
        +registerUser(user) void
        +findUserById(id) Optional~User~
        +getAuction(id) Optional~Auction~
        +shutdown() void
    }

    class RealTimeObserver {
        <<interface>>
        +onBidPlacedSuccess(userId, auctionId, itemName, amount) void
        +onOutbid(userId, auctionId, itemName, amount) void
        +onTimeExtended(auctionId, itemName, seconds) void
        +onAuctionEnded(userId, auctionId, itemName, price) void
    }



    %% Inheritance — Entity root
    Entity <|-- Account
    Entity <|-- Item
    Entity <|-- Auction

    %% Inheritance — Account subtypes
    Account <|-- User
    Account <|-- Admin

    %% Inheritance — Item subtypes
    Item <|-- Electronics
    Item <|-- Art
    Item <|-- Vehicle

    %% Auction internal compositions
    Auction *-- "1" Item : contains
    Auction *-- "0..*" BidTransaction : bidHistory
    Auction --> "0..1" User : currentLeader
    Auction o-- "0..*" RealTimeObserver : observers

    %% User owns AutoBidConfig
    User *-- "0..*" AutoBidConfig : autoBidMap

    %% AutoBidEngine manages configs and users
    AutoBidEngine o-- "0..*" AutoBidConfig : PriorityQueue per auction
    AutoBidEngine --> "0..*" User : BidderManager lookup
    AutoBidEngine --> Auction : trigger placeBid

    %% AuctionManager — central registry
    AuctionManager *-- "1" AutoBidEngine : owns
    AuctionManager o-- "0..*" Auction : auctionMap
    AuctionManager o-- "0..*" User : userMap
    AuctionManager o-- "0..*" RealTimeObserver : globalObservers
```

## 2. Quan Hệ Chính Giữa Các Lớp

| Từ lớp | Tới lớp | Loại | Ghi chú |
|---|---|---|---|
| `Auction` | `Item` | Composition 1:1 | Phiên gắn chặt với 1 sản phẩm |
| `Auction` | `BidTransaction` | Composition 1:N | Lịch sử bid (`bidHistory`) |
| `Auction` | `User` | Association 0..1 | `currentLeader` — người dẫn đầu |
| `Auction` | `RealTimeObserver` | Aggregation 1:N | Danh sách observer realtime |
| `User` | `AutoBidConfig` | Composition 1:N | `autoBidMap<auctionId, config>` |
| `AuctionManager` | `Auction` | Aggregation 1:N | Registry toàn bộ phiên trong RAM |
| `AuctionManager` | `User` | Aggregation 1:N | Registry toàn bộ user trong RAM |
| `AuctionManager` | `AutoBidEngine` | Composition 1:1 | Engine nội tại của Manager |
| `AutoBidEngine` | `AutoBidConfig` | Aggregation 1:N | `PriorityQueue` per auction |

---

## 3. Vai Trò Các Lớp

### Lớp nền tảng

`Entity` là lớp gốc trừu tượng — cung cấp `id` và `createdAt` cho toàn bộ hệ thống, định nghĩa `equals/hashCode` theo `id`.

`Account` (abstract) kế thừa `Entity`, chứa logic xác thực mật khẩu BCrypt. Hai subclass `User` (bidder/seller, có ví tiền và auto-bid map) và `Admin` (có tập quyền động) kế thừa từ đây.

`Item` (abstract) đại diện sản phẩm đấu giá. Ba subclass `Electronics`, `Art`, `Vehicle` override `getCategory()` và `validate()` với rule riêng. `ItemFactory` tạo đúng subclass từ category string (Factory Pattern).

---

### Lớp trung tâm

**`Auction`** là lớp lõi của hệ thống — quản lý toàn bộ state một phiên đấu giá trong RAM. Chứa `Item`, danh sách `BidTransaction`, con trỏ `currentLeader`, và `ReentrantLock(fair=true)` bảo vệ critical section. Ba chức năng cốt lõi:
- `placeBid()` — validate + cập nhật state + anti-snipe, thread-safe qua lock.
- `closeSession()` — xác định winner/canceled dựa trên reserve price và bid history.
- `notifyBidCommitted()` — phát sự kiện tới tất cả `RealTimeObserver` sau khi DB commit.

**`AuctionManager`** (Singleton) là điểm truy cập duy nhất vào tầng domain. Duy trì `auctionMap` và `userMap` trong RAM, sở hữu `AutoBidEngine` và `ScheduledExecutorService` để tự động mở/đóng phiên theo thời gian. Không tầng nào khác được tham chiếu trực tiếp tới `Auction` object mà không qua Manager.

**`AutoBidEngine`** xử lý toàn bộ logic đặt giá tự động. Mỗi auction có một `PriorityQueue<AutoBidConfig>` riêng — config `maxBid` cao hơn được ưu tiên, bằng nhau thì ai đăng ký sớm hơn thắng (FIFO tie-breaking). Trigger được gọi sau mỗi bid thủ công, khi auction mở, hoặc khi có config mới đăng ký / hủy,.

**`RealTimeObserver`** (interface) tách biệt domain logic khỏi network layer. `Auction` chỉ biết gọi `onBidPlacedSuccess()`, `onOutbid()`, `onTimeExtended()` — không quan tâm phía sau là socket hay bất kỳ cơ chế gì khác (Observer Pattern).

---

### Lớp dữ liệu

`BidTransaction` ghi lại một lần đặt giá — hầu hết các trường là `final`, chỉ `status` thay đổi theo vòng đời `WINNING → OUTBID → WON/LOST`.

`AutoBidConfig` lưu cấu hình auto-bid của một bidder trong một phiên, implements `Comparable` để sắp xếp trong PriorityQueue của Engine.

---

## 4. Design Patterns Áp Dụng

| Pattern | Nơi áp dụng |
|---|---|
| **Singleton** | `AuctionManager` — một registry duy nhất trong toàn bộ server |
| **Factory** | `ItemFactory` — tạo đúng subclass `Item` từ category string |
| **Observer** | `Auction → RealTimeObserver` — notify realtime sau khi DB commit |

---
