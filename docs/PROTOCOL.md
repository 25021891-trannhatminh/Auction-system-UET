# PROTOCOL LAYER

Hệ thống sử dụng giao thức văn bản tùy biến (Custom Text-based Protocol) truyền qua Socket TCP/UDP. Dữ liệu được làm phẳng thành chuỗi ký tự tối giản, phân tách bằng ký tự đặc biệt giúp tiết kiệm tối đa băng thông và đảm bảo tốc độ phản hồi thời gian thực.

---

## 1. Cơ chế hoạt động của Giao thức

Hệ thống tổ chức luồng truyền thông dựa trên mô hình kiến trúc Client - Server siêu nhẹ (Thin Client):

* **Tập hằng số lệnh tập trung (`ProtocolConstants.java`):** Định nghĩa nhất quán toàn bộ từ khóa lệnh hệ thống (Token tĩnh), loại bỏ hoàn toàn mã chuỗi tự do (Magic Strings).
* **Biên dịch dữ liệu phẳng (Server-side):** Lớp `ResponseBuilder` chịu trách nhiệm đóng gói dữ liệu nghiệp vụ thành chuỗi ký tự dạng phẳng theo quy ước cố định: `MÃ_LỆNH|ThamSố1|ThamSố2|...`
* **Phân tích gói tin dựa trên Token (Client-side):** Client bóc tách chuỗi thô dựa trên ký tự phân tách (`|`) và chỉ cần đọc mã lệnh ở đầu để cập nhật trực tiếp lên giao diện UI mà không cần xử lý logic.

---

## 2. Chi tiết Đặc tả Tập lệnh Hệ thống (`ProtocolConstants`)

* **Xác thực & Tài khoản:** `LOGIN`, `LOGIN_SUCCESS`, `LOGIN_FAIL`, `REGISTER`, `REGISTER_SUCCESS`, `REGISTER_FAIL`.
* **N nghiệp vụ Đấu giá:** `JOIN_AUCTION`, `JOIN_AUCTION_SUCCESS`, `JOIN_AUCTION_FAIL`, `LEAVE_AUCTION`, `BID`, `BID_SUCCESS`, `BID_FAIL`.
    * *Mã lỗi đặt giá thất bại:* `BID_REASON_BELOW_CURRENT_PRICE` (Thấp hơn giá hiện tại), `BID_REASON_BELOW_MIN_INCREMENT` (Không đủ bước giá), `BID_REASON_OWN_AUCTION` (Chủ phòng tự nâng giá), `BID_REASON_AUCTION_CLOSED` (Phiên đã đóng).
* **Bộ máy Tự động nâng giá (Auto-Bid):** `AUTOBID_REGISTER`, `AUTOBID_CANCEL`, `AUTOBID_SUCCESS`, `AUTOBID_FAIL`.
* **Giám sát & Đồng bộ:** `PING` / `PONG` (Giữ kết nối Keep-Alive), `NEW_BID`, `AUCTION_CLOSED`, `AUCTION_BID_UPDATE`, `AUCTION_CLOSED_UPDATE`.
* **Trực quan hóa đồ thị:** `GET_AUCTION_VISUALISATION`, `HISTORY_START`, `HISTORY_ITEM`, `HISTORY_END` (Luồng dữ liệu tọa độ vẽ biểu đồ tuyến tính).

---

## 3. Định dạng cấu trúc Gói tin Thực tế (Payload Examples)

| Ngữ cảnh hệ thống | Chuỗi ký tự truyền trên Socket (Raw Text Payload)             |
|---|---------------------------------------------------------------|
| **Đặt giá thành công** | `BID_SUCCESS 1001 12000000`                             |
| **Cập nhật đóng phiên** | `AUCTION_CLOSED_UPDATE\|1001\|FINISHED\|15000000\|789\|UserA` |
| **Truyền tọa độ biểu đồ** | `HISTORY_ITEM\|11000000\|2026-05-31T01:55:21`           |
| **Đặt giá thất bại** | `BID_FAIL\|1001\|BELOW_MIN_INCREMENT`                         |

---

## 4. Đánh giá Chung (General Evaluation)

* **Tối ưu hóa băng thông:** Việc loại bỏ định dạng nặng nề (như JSON/XML) giúp kích thước gói tin đạt mức tối giản, tăng tốc độ truyền tải trên đường truyền Socket.
* **Xử lý độ trễ thấp:** Thao tác cắt chuỗi văn bản thuần túy theo ký tự phân tách diễn ra ngay lập tức trên CPU với độ trễ xấp xỉ $0\text{ms}$, đáp ứng tốt kịch bản tranh chấp giá tại giây cuối cùng.
* **Đồng bộ nhất quán:** Cấu trúc token tĩnh đóng vai trò như một bản giao ước chung giúp Client luôn cập nhật giao diện chính xác theo đúng trạng thái thực tế từ Server.