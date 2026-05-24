package server.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import server.common.entity.Auction;
import server.common.entity.Auction.PlaceBidResult;
import server.common.entity.Item;
import server.common.entity.User;
import server.common.enums.ItemCategory;
import server.common.enums.ItemStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lớp kiểm thử logic đặt giá
 * ĐÂY LÀ FILE QUAN TRỌNG NHẤT - Test đặt giá hợp lệ/không hợp lệ
 */
class BidTransactionTest {

    private Auction auction;
    private User nguoiRaGia1;
    private User nguoiRaGia2;
    private User nguoiBan;
    private Item sanPham;

    @BeforeEach
    void setUp() {
        // Định nghĩa 1 lớp con nội bộ ngay TRONG hàm setUp để gán ID theo ý muốn
        class UserForTest extends User {
            private final int customId;

            public UserForTest(int id, String username, String email, String passwordHash, String fullName, String phone) {
                super(username, email, passwordHash, fullName, phone);
                this.customId = id;
            }
            @Override
            public int getId() {
                return this.customId; // Trả về ID riêng biệt để không bị trùng bằng 0
            }
        }
        // Khởi tạo các User bằng lớp nội bộ vừa tạo (ID: 1, 2, 3 hoàn toàn khác nhau)
        nguoiBan    = new UserForTest(1, "seller", "seller@test.com", "hash", "Nguoi Ban", "123");
        nguoiRaGia1 = new UserForTest(2, "bidder1", "b1@test.com", "hash", "Nguoi Ra Gia 1", "456");
        nguoiRaGia2 = new UserForTest(3, "bidder2", "b2@test.com", "hash", "Nguoi Ra Gia 2", "789");


        sanPham = new Item(nguoiBan.getId(), "iPhone 15", "Dien thoai moi",
                new BigDecimal("10000000"), ItemStatus.AVAILABLE, ItemCategory.ELECTRONIC) {
            @Override public String getCategory() { return "Dien tu"; }
            @Override public boolean validate() { return true; }
        };

        // Khởi tạo phòng đấu giá
        auction = new Auction(sanPham, nguoiBan.getId(),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(24),
                new BigDecimal("500000"), new BigDecimal("15000000"), 30, 60);
        auction.startRunning();
    }

    // ==================== ĐẶT GIÁ HỢP LỆ ====================

    /**
     * TEST 1: Đặt giá cao hơn giá hiện tại
     * Kết quả mong đợi: Thành công, cập nhật giá mới và người dẫn đầu
     */
    @Test
    @DisplayName("[HỢP LỆ] Đặt giá cao hơn giá hiện tại")
    void testDatGia_CaoHonGiaHienTai_ThanhCong() {
        BigDecimal giaMoi = new BigDecimal("12000000");

        PlaceBidResult kq = auction.placeBid(nguoiRaGia1, giaMoi, false);

        assertNotNull(kq);
        // Sử dụng compareTo để so sánh BigDecimal chuẩn xác không lỗi scale
        assertEquals(0, giaMoi.compareTo(auction.getCurrentPrice()));
        assertEquals(nguoiRaGia1, auction.getCurrentLeader());
    }

    /**
     * TEST 2: Đặt giá đúng bước giá tối thiểu
     * Kết quả mong đợi: Thành công (giá hiện tại 10tr, bước giá 500k -> đặt 10.5tr)
     */
    @Test
    @DisplayName("[HỢP LỆ] Đặt giá đúng bước giá tối thiểu")
    void testDatGia_DungBuocGiaToiThieu_ThanhCong() {
        // Giá hiện tại 10tr, bước giá 500k -> đặt 10.5tr
        BigDecimal giaMoi = new BigDecimal("10500000");

        PlaceBidResult kq = auction.placeBid(nguoiRaGia1, giaMoi, false);

        assertNotNull(kq);
        assertEquals(0, giaMoi.compareTo(auction.getCurrentPrice()));
    }

    /**
     * TEST 3: Đặt giá cao hơn bước giá tối thiểu
     * Kết quả mong đợi: Thành công
     */
    @Test
    @DisplayName("[HỢP LỆ] Đặt giá cao hơn bước giá tối thiểu")
    void testDatGia_CaoHonBuocGiaToiThieu_ThanhCong() {
        // Giá hiện tại 10tr, đặt luôn 12tr (hơn 2tr, lớn hơn bước giá 500k)
        BigDecimal giaMoi = new BigDecimal("12000000");

        PlaceBidResult kq = auction.placeBid(nguoiRaGia1, giaMoi, false);

        assertNotNull(kq);
        assertEquals(0,giaMoi.compareTo(auction.getCurrentPrice()));
    }

    /**
     * TEST 4: Đặt giá nhiều lần, mỗi lần đều hợp lệ
     * Kết quả mong đợi: Người đặt giá cao nhất cuối cùng là người dẫn đầu
     */
    @Test
    @DisplayName("[HỢP LỆ] Đặt giá nhiều lần liên tiếp")
    void testDatGiaNhieuLanLienTiep_ThanhCong() {
        auction.placeBid(nguoiRaGia1, new BigDecimal("10500000"), false);
        auction.placeBid(nguoiRaGia2, new BigDecimal("11500000"), false);
        auction.placeBid(nguoiRaGia1, new BigDecimal("12500000"), false);

        assertEquals(0, new BigDecimal("12500000").compareTo(auction.getCurrentPrice()));
        assertEquals(nguoiRaGia1, auction.getCurrentLeader());
        assertEquals(3, auction.getTotalBids());
    }

    // ==================== ĐẶT GIÁ KHÔNG HỢP LỆ ====================

    /**
     * TEST 5: Đặt giá thấp hơn giá hiện tại
     * Kết quả mong đợi: Ném ngoại lệ, không cập nhật giá
     */
    @Test
    @DisplayName("[KHÔNG HỢP LỆ] Đặt giá thấp hơn giá hiện tại")
    void testDatGia_ThapHonGiaHienTai_ThatBai() {
        auction.placeBid(nguoiRaGia1, new BigDecimal("11000000"), false);

        // Bắt chính xác InvalidBidException từ hệ thống của bạn
        assertThrows(RuntimeException.class, () ->
                auction.placeBid(nguoiRaGia2, new BigDecimal("10500000"), false));

        // Giá vẫn giữ nguyên
        assertEquals(new BigDecimal("11000000"), auction.getCurrentPrice());
        assertEquals(nguoiRaGia1, auction.getCurrentLeader());
    }

    /**
     * TEST 6: Đặt giá bằng giá hiện tại
     * Kết quả mong đợi: Ném ngoại lệ
     */
    @Test
    @DisplayName("[KHÔNG HỢP LỆ] Đặt giá bằng giá hiện tại")
    void testDatGia_BangGiaHienTai_ThatBai() {
        auction.placeBid(nguoiRaGia1, new BigDecimal("11000000"), false);

        assertThrows(RuntimeException.class, () ->
                auction.placeBid(nguoiRaGia2, new BigDecimal("11000000"), false));
    }

    /**
     * TEST 7: Đặt giá thiếu bước giá tối thiểu
     * Kết quả mong đợi: Ném ngoại lệ (giá hiện tại 10tr, bước giá 500k, chỉ đặt 10.2tr)
     */
    @Test
    @DisplayName("[KHÔNG HỢP LỆ] Đặt giá thiếu bước giá tối thiểu")
    void testDatGia_ThieuBuocGiaToiThieu_ThatBai() {
        assertThrows(RuntimeException.class, () ->
                auction.placeBid(nguoiRaGia1, new BigDecimal("10200000"), false));
    }

    /**
     * TEST 8: Đặt giá âm
     * Kết quả mong đợi: Ném ngoại lệ
     */
    @Test
    @DisplayName("[KHÔNG HỢP LỆ] Đặt giá âm")
    void testDatGia_Am_ThatBai() {
        assertThrows(RuntimeException.class, () ->
                auction.placeBid(nguoiRaGia1, new BigDecimal("-1000000"), false));
    }

    /**
     * TEST 9: Đặt giá bằng 0
     * Kết quả mong đợi: Ném ngoại lệ
     */
    @Test
    @DisplayName("[KHÔNG HỢP LỆ] Đặt giá bằng 0")
    void testDatGia_BangKhong_ThatBai() {
        assertThrows(RuntimeException.class, () ->
                auction.placeBid(nguoiRaGia1, BigDecimal.ZERO, false));
    }

    /**
     * TEST 10: Người bán tự đặt giá phiên của mình
     * Kết quả mong đợi: Ném ngoại lệ
     */
    @Test
    @DisplayName("[KHÔNG HỢP LỆ] Người bán đặt giá phiên của mình")
    void testNguoiBanTuDatGia_ThatBai() {
        assertThrows(RuntimeException.class, () ->
                auction.placeBid(nguoiBan, new BigDecimal("20000000"), false));
    }

    /**
     * TEST 11: Đặt giá sau khi phiên đã kết thúc
     * Kết quả mong đợi: Ném ngoại lệ
     */
    @Test
    @DisplayName("[KHÔNG HỢP LỆ] Đặt giá sau khi phiên kết thúc")
    void testDatGia_SauKhiKetThucPhien_ThatBai() {
        auction.placeBid(nguoiRaGia1, new BigDecimal("15000000"), false);
        auction.closeSession();

        assertThrows(RuntimeException.class, () ->
                auction.placeBid(nguoiRaGia2, new BigDecimal("16000000"), false));
    }

    /**
     * TEST 12: Đặt giá khi phiên chưa bắt đầu
     * Kết quả mong đợi: Ném ngoại lệ
     */
    @Test
    @DisplayName("[KHÔNG HỢP LỆ] Đặt giá khi phiên chưa bắt đầu")
    void testDatGia_KhiPhienChuaBatDau_ThatBai() {
        // Tạo phiên mới chưa startRunning
        Auction phienChuaBatDau = new Auction(sanPham, nguoiBan.getId(),
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(25),
                new BigDecimal("500000"), new BigDecimal("15000000"), 30, 60);
        // Không gọi startRunning()

        assertThrows(RuntimeException.class, () ->
                phienChuaBatDau.placeBid(nguoiRaGia1, new BigDecimal("11000000"), false));
    }
}