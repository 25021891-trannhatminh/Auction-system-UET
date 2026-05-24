package server.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import server.common.entity.Auction;
import server.common.entity.Item;
import server.common.entity.User;
import server.common.enums.AuctionStatus;
import server.common.enums.ItemCategory;
import server.common.enums.ItemStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lớp kiểm thử logic phiên đấu giá
 * Test trạng thái phiên, thời gian bắt đầu/kết thúc, anti-sniping
 */
class AuctionTest {

    private Auction auction;
    private User nguoiRaGia;
    private User nguoiBan;
    private Item sanPham;

    @BeforeEach
    void setUp() {
        // Định nghĩa lớp nội bộ để gán ID riêng biệt
        class UserForTest extends User {
            private final int customId;
            public UserForTest(int id, String username, String email, String passwordHash, String fullName, String phone) {
                super(username, email, passwordHash, fullName, phone);
                this.customId = id;
            }
            @Override
            public int getId() { return this.customId; }
        }

        nguoiBan = new UserForTest(1, "seller", "seller@test.com", "hash", "Nguoi Ban", "123");
        nguoiRaGia = new UserForTest(2, "bidder", "bidder@test.com", "hash", "Nguoi Ra Gia", "456");

        sanPham = new Item(nguoiBan.getId(), "iPhone 15", "Dien thoai moi",
                new BigDecimal("10000000"), ItemStatus.AVAILABLE, ItemCategory.ELECTRONIC) {
            @Override public String getCategory() { return "Dien tu"; }
            @Override public boolean validate() { return true; }
        };

        auction = new Auction(sanPham, nguoiBan.getId(),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(24),
                new BigDecimal("500000"), new BigDecimal("15000000"), 30, 60);
    }

    // ==================== TRẠNG THÁI PHIÊN ====================

    /**
     * TEST 1: Phiên khởi tạo có trạng thái OPEN
     */
    @Test
    @DisplayName("Phiên mới tạo có trạng thái OPEN")
    void testPhienMoi_TrangThaiOpen() {
        assertEquals(AuctionStatus.OPEN, auction.getStatus());
    }

    /**
     * TEST 2: startRunning chuyển trạng thái từ OPEN sang RUNNING
     */
    @Test
    @DisplayName("startRunning chuyển trạng thái OPEN -> RUNNING")
    void testStartRunning_ChuyenTrangThaiThanhRunning() {
        auction.startRunning();
        assertEquals(AuctionStatus.RUNNING, auction.getStatus());
    }

    /**
     * TEST 3: Không thể startRunning khi phiên đã RUNNING
     */
    @Test
    @DisplayName("Không thể startRunning khi phiên đã RUNNING")
    void testStartRunning_KhiDaRunning_ThatBai() {
        auction.startRunning();
        assertThrows(IllegalStateException.class, () -> auction.startRunning());
    }

    // ==================== KẾT THÚC PHIÊN ====================

    /**
     * TEST 4: Kết thúc phiên không có giá đặt -> CANCELED
     */
    @Test
    @DisplayName("Kết thúc phiên không có giá đặt -> CANCELED")
    void testKetThucPhien_KhongCoGiaDat_TrangThaiCanceled() {
        auction.startRunning();
        auction.closeSession();
        assertEquals(AuctionStatus.CANCELED, auction.getStatus());
    }

    /**
     * TEST 5: Kết thúc phiên có giá đặt dưới giá sàn -> CANCELED
     */
    @Test
    @DisplayName("Kết thúc phiên có giá dưới giá sàn -> CANCELED")
    void testKetThucPhien_GiaDuoiSan_TrangThaiCanceled() {
        auction.startRunning();
        auction.placeBid(nguoiRaGia, new BigDecimal("12000000"), false);
        auction.closeSession();
        assertEquals(AuctionStatus.CANCELED, auction.getStatus());
    }

    /**
     * TEST 6: Kết thúc phiên có giá đặt đạt giá sàn -> FINISHED
     */
    @Test
    @DisplayName("Kết thúc phiên có giá đạt giá sàn -> FINISHED")
    void testKetThucPhien_GiaDatSan_TrangThaiFinished() {
        auction.startRunning();
        auction.placeBid(nguoiRaGia, new BigDecimal("15000000"), false);
        auction.closeSession();
        assertEquals(AuctionStatus.FINISHED, auction.getStatus());
    }

    // ==================== ANTI-SNIPING ====================

    /**
     * TEST 7: Đặt giá trong phút cuối -> thời gian được gia hạn
     */
    @Test
    @DisplayName("Anti-sniping: Đặt giá trong phút cuối -> gia hạn thời gian")
    void testAntiSniping_DatGiaTrongPhutCuoi_GiaHanThoiGian() {
        // Tạo phiên sắp kết thúc (còn 20 giây)
        LocalDateTime ganKetThuc = LocalDateTime.now().plusSeconds(20);
        Auction phienGanKetThuc = new Auction(sanPham, nguoiBan.getId(),
                LocalDateTime.now().minusHours(1), ganKetThuc,
                new BigDecimal("500000"), new BigDecimal("15000000"), 30, 60);
        phienGanKetThuc.startRunning();

        LocalDateTime thoiGianCu = phienGanKetThuc.getEndTime();

        // Đặt giá trong 20 giây cuối
        phienGanKetThuc.placeBid(nguoiRaGia, new BigDecimal("11000000"), false);

        // Thời gian kết thúc phải được đẩy lên sau
        assertTrue(phienGanKetThuc.getEndTime().isAfter(thoiGianCu));
    }

    /**
     * TEST 8: Đặt giá ngoài phút cuối -> không gia hạn
     */
    @Test
    @DisplayName("Anti-sniping: Đặt giá ngoài phút cuối -> không gia hạn")
    void testAntiSniping_DatGiaNgoaiPhutCuoi_KhongGiaHan() {
        LocalDateTime thoiGianCu = auction.getEndTime();
        auction.startRunning();
        auction.placeBid(nguoiRaGia, new BigDecimal("11000000"), false);

        assertEquals(thoiGianCu, auction.getEndTime());
    }
}