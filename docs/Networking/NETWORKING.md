# BÁO CÁO PHÂN TÍCH CHI TIẾT HẠ TẦNG & LUỒNG HOẠT ĐỘNG MẠNG (NETWORKING)
Báo cáo chi tiết và phân tích về thiết kế hạ tầng mạng, giao thức truyền tải, cơ chế chịu lỗi và quy trình định vị máy chủ tự động của hệ thống đấu giá trực tuyến (Online Auction System)

---

## 1. Thiết kế Giao thức & Phân tầng Mạng (Network Protocol Selection)
Hệ thống sử dụng mô hình kết hợp giữa hai giao thức cốt lõi của tầng giao vận (Transport Layer) là **TCP** và **UDP** nhằm tối ưu hóa giữa độ tin cậy dữ liệu nghiệp vụ và tính tự động hóa của ứng dụng:

- **Giao thức TCP (Luồng nghiệp vụ chính - Port 6666)**: Được quản lý bởi lớp `NetworkManager` phía Client và `AuctionServer` phía Server. Giao thức này áp dụng cho toàn bộ các giao dịch đặt giá (`BID`), nạp tiền (`DEPOSIT_WALLET`), xác nhận hóa đơn (`CONFIRM_PAYMENT`), cũng như các tập lệnh quản trị của Admin. Cơ chế bảo chứng dòng dữ liệu (Stream) của TCP đảm bảo thông tin truyền đi không bị mất mát, đúng thứ tự, ngăn chặn hoàn toàn hiện tượng sai lệch bước giá hoặc mất gói tin đặt giá thời gian thực.
- **Giao thức UDP (Luồng dò tìm tự động - Port 9999)**: Được hiện thực qua lớp `ServerDiscovery` phía Server và `ServerFinder` phía Client. Tận dụng lợi thế không duy trì kết nối (Connectionless) và khả năng truyền phát quảng bá (Broadcast) của UDP nhằm mục đích khám phá dịch vụ tự động trong mạng nội bộ mà không đòi hỏi thao tác cấu hình thủ công phức tạp từ phía người dùng.

---

## 2. Chi tiết Quy trình & Luồng hoạt động hệ thống (System Discovery Logic)
Để đảm bảo hệ thống vận hành linh hoạt cả trong môi trường học tập trên trường lớp (Mạng LAN nhiều node trùng cổng) lẫn môi trường phát triển cục bộ hoặc qua Internet, luồng logic định vị máy chủ của hệ thống áp dụng cơ chế ưu tiên cấu hình thủ công với thứ tự ưu tiên giảm dần qua 3 giai đoạn được cấu trúc tuần tự trong code:

### 2.1. Giai đoạn 1: Kiểm tra cấu hình thủ công qua Biến môi trường (Explicit Priority)
- **Cú pháp hiện thực**: Client chủ động kiểm tra cấu hình thông qua các hàm hệ thống `System.getProperty("auction.server.host")` và `System.getenv("AUCTION_SERVER_HOST")`.
- **Vai trò**: Dành cho Quản trị viên hoặc Lập trình viên khi cần kết nối hệ thống xuyên biên giới Internet thông qua các địa chỉ IP tĩnh từ xa .
- **Đánh giá kỹ thuật**: Việc đặt kiểm tra biến môi trường lên ưu tiên hàng đầu giúp tối ưu hóa hiệu năng hệ thống khi kết nối từ xa. Client sẽ lấy ngay địa chỉ IP cấu hình bằng tay để thiết lập mảng danh sách ứng viên kết nối (Host Candidates).

### 2.2. Giai đoạn 2: Cơ chế ưu tiên kết nối máy cục bộ (Same-Machine Priority)
- **Cú pháp hiện thực**: Hệ thống tự động nạp hai giá trị tĩnh `localhost` và `127.0.0.1` vào tập hợp danh sách ứng viên ngay sau các biến môi trường thông qua mã lệnh `hosts.add(LOCALHOST); hosts.add(LOOPBACK);`.
- **Vai trò**: Tối ưu cho kịch bản lập trình viên chạy cả Server và Client trên cùng một máy tính cá nhân để phát triển tính năng cục bộ.
- **Đánh giá kỹ thuật**: Cơ chế này giúp Client ưu tiên thăm dò các tiến trình cục bộ trước. Điều này giúp loại bỏ triệt để hiện tượng "stale LAN broadcasts" (bắt nhầm các gói tin phát quảng bá cũ hoặc bị trễ của các máy tính khác chạy Server trong cùng mạng LAN).

### 2.3. Giai đoạn 3: Khám phá tự động qua mạng LAN bằng UDP Broadcast
- **Cú pháp hiện thực**: Hàm `receiveDiscoveryPacket()` khởi tạo một `DatagramSocket` lắng nghe tại cổng `9999` với thời gian chờ tối đa `TIMEOUT_MS = 1200`. Phía Server, lớp `ServerDiscovery` liên tục phát quảng bá gói tin chứa nội dung `"AUCTION_SERVER:6666"` tới địa chỉ tổng `255.255.255.255` sau mỗi 2 giây.
- **Vai trò**: Phục vụ trải nghiệm cấu hình tự động cho người dùng cuối hoặc hội đồng chấm bài khi chạy demo hệ thống trong mạng nội bộ (LAN / Wifi).
- **Đánh giá kỹ thuật**: Client nhận gói tin, bóc tách chuỗi dữ liệu và kiểm tra tiền tố hợp lệ `AUCTION_SERVER:`. Nếu khớp, hệ thống tự động trích xuất lấy địa chỉ IP của Server phát ra để bổ sung vào danh sách kết nối chung cuộc. Cơ chế giới hạn thời gian chờ `setSoTimeout(1200)` đóng vai trò quan trọng, giúp ngăn chặn luồng bị treo vô hạn nếu Server offline hoặc tường lửa chặn gói tin UDP.

---

## 3. Thiết kế Cơ chế chịu lỗi & Duy trì kết nối (Fault Tolerance & Reliability)
Lớp điều hướng trung tâm `NetworkManager` tích hợp sâu các giải pháp xử lý lỗi đường truyền nâng cao để bảo vệ trạng thái của ứng dụng Client:

### 3.1. Cơ chế thiết lập lại kết nối thông minh (Exponential Backoff)
- **Hành vi**: Khi kết nối TCP bị ngắt, luồng `NetworkManager-Connection` được kích hoạt ngầm. Chu kỳ thử lại kết nối bắt đầu từ độ trễ cơ sở `BASE_RETRY_DELAY = 1000ms` và tự động nhân đôi thời gian chờ sau mỗi lần thất bại, giới hạn tối đa tại `MAX_RETRY_DELAY = 30000ms`.
- **Đánh giá kỹ thuật**: Thiết kế này giúp bảo vệ hạ tầng Server không bị quá tải tài nguyên hệ thống do hiện tượng "DDoS ngược" khi hàng loạt ứng dụng Client cùng lúc gửi yêu cầu kết nối lại liên tục sau sự cố sập mạng nội bộ.

### 3.2. Hệ thống hàng đợi tin nhắn bảo hiểm (Idempotent Message Queue)
- **Hành vi**: Trong trạng thái mất kết nối, hệ thống không làm sập giao diện ứng dụng mà kích hoạt bộ lọc kiểm tra tập lệnh dựa trên danh sách `NON_REPLAYABLE_COMMAND_PREFIXES`.
- **Đánh giá kỹ thuật**:
    - Đối với các lệnh an toàn có tính hoán đổi/đồng nhất (Replay-safe), dữ liệu sẽ được đưa vào hàng đợi ngầm `ConcurrentLinkedQueue`. Ngay khi mạng hồi phục, hàm `flushQueue()` tự động đẩy lại các lệnh này lên máy chủ.
    - Đối với các lệnh nguy hiểm, không được phép lặp lại (Non-replayable) như đặt giá (`BID`), nạp tiền (`DEPOSIT_WALLET`), xác nhận thanh toán (`CONFIRM_PAYMENT`), hệ thống sẽ chủ động hủy lệnh ngay lập tức và đưa ra cảnh báo để tránh rủi ro trùng lặp giao dịch tài chính cho người dùng.

### 3.3. Luồng kiểm tra nhịp tim tự động (Heartbeat Loop)
- **Hành vi**: Một luồng daemon độc lập mang tên `NetworkManager-Heartbeat` liên tục gửi gói tin `PING` định kỳ mỗi `5000ms` từ Client lên Server.
- **Đánh giá kỹ thuật**: Nếu phát hiện lỗi đường truyền từ hàm kiểm tra `out.checkError()`, Client sẽ lập tức đánh dấu ngắt kết nối (`connected = false`), chủ động giải phóng Socket cũ và kích hoạt lại quy trình dò tìm IP mới nhằm khôi phục trạng thái thời gian thực của phiên đấu giá nhanh nhất có thể.

---

## 4. Tổng kết Đánh giá Kỹ thuật về Thiết kế Mạng
1. **Kiến trúc đồng bộ hiệu năng cao**: Việc Server sử dụng công nghệ Luồng ảo (Virtual Thread) thông qua cú pháp mã lệnh `Thread.ofVirtual().start(handler)` giúp giảm tải tài nguyên hệ thống lên tới hàng nghìn lần khi xử lý hàng loạt kết nối TCP đồng thời từ nhiều Client. Điều này cực kỳ tối ưu cho bài toán xử lý đồng thời (Concurrency) và giải quyết tranh chấp nghẽn mạng lúc kết thúc phiên đấu giá.
2. **Tối ưu hóa quản lý bộ nhớ đệm log**: Toàn bộ luồng dữ liệu thô kích thước lớn (như chuỗi Base64 khi thực hiện lệnh truyền tải ảnh `UPLOAD_IMAGE`) đều được lớp mạng xử lý rút gọn định dạng hiển thị log (`<base64:... chars>`), giảm kích thước dữ liệu và cải thiện khả năng giám sát, gỡ lỗi (Debug) hệ thống một cách trực quan.
3. **Giải pháp nâng cấp cấu trúc mạng (Next Version Roadmaps)**:
    - Hiện tại, chuỗi payload phát quảng bá UDP đang được gán cứng cổng TCP là `6666` (`AUCTION_SERVER:6666`). Trong tương lai, hệ thống cần nâng cấp cấu trúc để Server tự động điền cấu hình cổng động đang chạy thật vào nội dung gói tin. Việc này giúp hệ thống co giãn tốt hơn khi triển khai nhiều phân thể Server đấu giá song song trên cùng một cụm hạ tầng.
---