


# Online Auction System - Hệ thống đấu giá trực tuyến
Advanced Programming Project - Computer Science - UET
- **Link Repository:** [https://github.com/25021891-trannhatminh/Auction-system-UET](https://github.com/25021891-trannhatminh/Auction-system-UET)
## 1. Mô tả hệ thống
Dự án xây dựng một hệ thống đấu giá trực tuyến
- Hệ thống hỗ trợ các vai trò chính gồm **Bidder**, **Seller** và **Admin**, đồng thời cho phép quản lý sản phẩm, tham gia đấu giá, cập nhật realtime và xử lý kết thúc phiên đấu giá theo thời gian quy định.
- Cho phép nhiều người dùng tham gia mua/bán sản phẩm theo cơ chế đặt giá thời gian thực.

Phạm vi hệ thống tập trung vào các chức năng cốt lõi của một nền tảng đấu giá online
- Quản lý người dùng, quản lý sản phẩm đấu giá
- Tham gia đấu giá, cập nhật realtime và xử lý lỗi, ngoại lệ phát sinh và kết thúc phiên đấu giá theo thời gian quy định. 
- Hỗ trợ các tính năng nâng cao như Auto-bidding, Concurrent Bidding, Anti-sniping và Bid History Visualization.


## 2. Công nghệ sử dụng
- **Ngôn ngữ lập trình:** `Java`
- **GUI:** `JavaFx, SceneBuilder`
- **Kiến trúc:** `Client–Server`
- **Networking:** `Socket`
- **Build tool:** `Maven multi-module`
- **Database:** `MySQL, TiDB, HikariCP`
- **Testing:** `JUnit 5`
- **CI/CD:** `GitHub Actions + JUnit5`

### Môi trường chạy
- **Hệ điều hành:** `Windows, Linux, MacOS`
- **JDK:** `25`
- **Phiên bản JavaFX:** `23`

### Yêu cầu cài đặt
1. Cài đặt `Java JDK 25, Maven 3.8+`.
2. Thiết lập database `MySQL WorkBench`.
3. Chạy server trước, sau đó mới chạy client.

## 3. Cấu trúc thư mục
```text
project-root/
├── server/
│   ├── src/main/java/server
│   ├── resources/
│   └── ...
├── client/
│   ├── src/main/java/client
│   ├── resources/
│   └── ...
├── docs/
│   ├── report.pdf
│   └── demo.mp4
└── README.md
```

### Các module chính
- `Controller`: xử lý giao diện người dùng.
- `Service`: xử lý nghiệp vụ đấu giá.
- `NetworkManager, AuctionServer`: quản lý kết nối.
- `Entity`: lưu trữ entity / model / DTO.
- `Repository`: xử lý các thao tác với database.

## 4. Hướng dẫn chạy hệ thống
### Bước 1: Khởi động Server
```bash
java -jar server/target/auction-server.jar
```

### Bước 2: Khởi động Client
```bash
java -jar client/target/auction-client.jar
```

### Bước 3: Đăng nhập và sử dụng hệ thống
- Mở ứng dụng client.
- Đăng nhập bằng tài khoản tương ứng.
- Thực hiện các chức năng theo quyền hạn của tài khoản.

> Lưu ý: Server phải được khởi động trước client để đảm bảo kết nối hoạt động bình thường.

## 5. Danh sách chức năng đã hoàn thành
### Chức năng bắt buộc
- [x] Đăng ký / đăng nhập tài khoản.
- [x] Quản lý người dùng theo vai trò User / Admin.
- [x] Thêm / sửa / duyệt sản phẩm đấu giá.
- [x] Hiển thị giá khởi điểm, giá hiện tại, thời gian bắt đầu / kết thúc.
- [x] Tham gia đấu giá đồng thời, cập nhật kết quả Realtime.
- [x] Tự động kết thúc phiên đấu giá theo thời gian.
- [x] Xử lý lỗi và ngoại lệ.
- [x] Giao diện người dùng GUI.
- [x] Thiết kế OOP.
- [x] Design Patterns
- [x] Client-Server + MVC
- [x] Unit Test, CI/CD
### Chức năng nâng cao
- [x] Auto-Bidding.
- [x] Anti-sniping.
- [x] Bid History Visualization.

## 6. Tài liệu và demo
- **Báo cáo PDF:** [AuctionSystem_Report](docs/AuctionSystem_Report.pdf)
- **Video demo:** [link video](________________)

## 7. Thành viên nhóm
- `Trần Nhật Minh` - `Xây dựng domain, logic đặt giá, Auto-Bidding, Anti-snipping`
- `Vũ Bảo Ngọc` - `Xây dựng Database, xử lý truy vân Database cho Server`
- `Đồng Thị Trà My` - `Networking, giao tiếp Client-Server`
- `Nguyễn Trang Linh` - `Design UI, xử lý logic Controller UX/UI, Bid History Visualization`

## 8. Ghi chú
- Đây là bài tập lớn môn **Lập trình nâng cao**.
- Hệ thống được thiết kế theo mô hình **Client-Server** và **MVC**.
- Một số tính năng đang được phát triển thêm.
