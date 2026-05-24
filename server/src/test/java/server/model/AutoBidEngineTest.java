package server.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import server.common.entity.*;
import server.common.entity.Auction.PlaceBidResult;
import server.common.enums.ItemCategory;
import server.common.enums.ItemStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lớp kiểm thử AutoBidEngine
 * Test logic đặt giá tự động
 */
class AutoBidEngineTest {

    private AutoBidEngine autoBidEngine;
    private Auction auction;
    private User nguoiRaGiaA;
    private User nguoiRaGiaB;
    private User nguoiBan;
    private Item sanPham;

    @BeforeEach
    void setUp() {
        autoBidEngine = new AutoBidEngine();

        // Định nghĩa lớp nội bộ gán ID tránh xung đột heap queue
        class UserForTest extends User {
            private final int customId;
            public UserForTest(int id, String username, String email, String passwordHash, String fullName, String phone) {
                super(username, email, passwordHash, fullName, phone);
                this.customId = id;
            }
            @Override
            public int getId() { return this.customId; }
        }

        nguoiBan    = new UserForTest(1, "seller", "seller@test.com", "hash", "Nguoi Ban", "123");
        nguoiRaGiaA = new UserForTest(2, "bidderA", "a@test.com", "hash", "Nguoi A", "456");
        nguoiRaGiaB = new UserForTest(3, "bidderB", "b@test.com", "hash", "Nguoi B", "789");

        sanPham = new Item(nguoiBan.getId(), "Test Item", "Desc", new BigDecimal("10000000"),
                ItemStatus.AVAILABLE, ItemCategory.ELECTRONIC) {
            @Override
            public String getCategory() {
                return "Test";
            }

            @Override
            public boolean validate() {
                return true;
            }
        };

        auction = new Auction(sanPham, nguoiBan.getId(),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(24),
                new BigDecimal("500000"), new BigDecimal("15000000"), 30, 60);
        auction.startRunning();
    }

    // ==================== ĐĂNG KÝ / HỦY AUTO-BID ====================

    /**
     * Test đăng ký auto-bid
     * Kết quả mong đợi: Đăng ký thành công, số lượng tăng lên 1
     */
    @Test
    @DisplayName("Đăng ký auto-bid - thành công")
    void testDangKyAutoBid_ThanhCong() {
        AutoBidConfig config = new AutoBidConfig(auction.getId(), nguoiRaGiaA.getId(),
                new BigDecimal("20000000"), new BigDecimal("1000000"));
        autoBidEngine.register(config, nguoiRaGiaA);

        assertEquals(1, autoBidEngine.getRegisteredCount(auction.getId()));
    }

    /**
     * Test hủy đăng ký auto-bid
     * Kết quả mong đợi: Hủy thành công, số lượng về 0
     */
    @Test
    @DisplayName("Hủy đăng ký auto-bid - thành công")
    void testHuyDangKyAutoBid_ThanhCong() {
        AutoBidConfig config = new AutoBidConfig(auction.getId(), nguoiRaGiaA.getId(),
                new BigDecimal("20000000"), new BigDecimal("1000000"));
        autoBidEngine.register(config, nguoiRaGiaA);
        autoBidEngine.unregister(auction.getId(), nguoiRaGiaA.getId());

        assertEquals(0, autoBidEngine.getRegisteredCount(auction.getId()));
    }

    /**
     * Test xem người thắng hiện tại trong hàng đợi auto-bid
     * Kết quả mong đợi: Trả về đúng config của người có maxBid cao nhất
     */
    @Test
    @DisplayName("Xem người thắng auto-bid - chính xác")
    void testXemNguoiThangAutoBid_ChinhXac() {
        AutoBidConfig config = new AutoBidConfig(auction.getId(), nguoiRaGiaA.getId(),
                new BigDecimal("20000000"), new BigDecimal("1000000"));
        autoBidEngine.register(config, nguoiRaGiaA);

        AutoBidConfig nguoiThang = autoBidEngine.peekWinner(auction.getId());
        assertNotNull(nguoiThang);
        assertEquals(nguoiRaGiaA.getId(), nguoiThang.getBidderId());
    }

    // ==================== KÍCH HOẠT AUTO-BID ====================

    /**
     * Test kích hoạt auto-bid sau khi có giá đặt thủ công
     * Kết quả mong đợi: Auto-bid được kích hoạt và đặt giá tự động
     */
    @Test
    @DisplayName("Kích hoạt auto-bid sau giá thủ công - thành công")
    void testKichHoatAutoBid_SauGiaThuCong_ThanhCong() {
        // 1. Đăng ký cấu hình Auto-bid cho Người B: Tối đa 20tr, mỗi lần tăng 1tr
        AutoBidConfig configB = new AutoBidConfig(auction.getId(), nguoiRaGiaB.getId(),
                new BigDecimal("20000000"), new BigDecimal("1000000"));
        autoBidEngine.register(configB, nguoiRaGiaB);

        // 2. Người A nhảy vào đặt giá thủ công 11,000,000đ (Hợp lệ vì giá gốc là 10tr + bước giá 500k)
        auction.placeBid(nguoiRaGiaA, new BigDecimal("11000000"), false);

        // 3. Kích hoạt hệ thống đấu giá tự động sau khi người A vừa đặt giá thủ công
        PlaceBidResult result = autoBidEngine.trigger(auction, nguoiRaGiaA);

        // 4. Kiểm tra kết quả: Hệ thống phải tự đặt lệnh thay cho Người B thành công
        assertNotNull(result, "Hệ thống đấu giá tự động phải sinh ra một giao dịch!");

        // Sau khi Người B kích hoạt Auto-bid thành công, người dẫn đầu hiện tại trong phòng phải chuyển sang Người B
        assertNotNull(auction.getCurrentLeader(), "Phải có người dẫn đầu hiện tại!");
        assertEquals(nguoiRaGiaB.getId(), auction.getCurrentLeader().getId(), "Người dẫn đầu sau Auto-bid phải là Người B!");
        assertEquals(0, new BigDecimal("12000000").compareTo(auction.getCurrentPrice()), "Giá phòng đấu giá phải tăng lên đúng 12 triệu!");
        // Giá hiện tại phòng đấu giá sẽ được nâng lên 12tr (11tr của người A + 1tr bước giá của người B)
        assertEquals(0, new BigDecimal("12000000").compareTo(auction.getCurrentPrice()), "Giá phòng đấu giá phải tăng lên 12 triệu!");
    }

    /**
     * Test không kích hoạt auto-bid khi người thắng chính là người vừa đặt giá
     * Kết quả mong đợi: Không đặt auto-bid, trả về null
     */
    @Test
    @DisplayName("Không kích hoạt auto-bid khi người thắng là người vừa đặt giá")
    void testKhongKichHoat_KhiNguoiThangLaNguoiVuaDat() {
        AutoBidConfig configA = new AutoBidConfig(auction.getId(), nguoiRaGiaA.getId(),
                new BigDecimal("15000000"), new BigDecimal("500000"));
        autoBidEngine.register(configA, nguoiRaGiaA);

        // Trigger với chính người A -> không được tự outbid mình
        PlaceBidResult result = autoBidEngine.trigger(auction, nguoiRaGiaA);

        assertNull(result);
    }

    // ==================== DỌN DẸP ====================

    /**
     * Test dọn dẹp auto-bid khi phiên đấu giá kết thúc
     * Kết quả mong đợi: Xóa sạch dữ liệu auto-bid của phiên
     */
    @Test
    @DisplayName("Dọn dẹp auto-bid khi kết thúc phiên - thành công")
    void testDonDepAutoBid_KhiKetThucPhien_ThanhCong() {
        AutoBidConfig config = new AutoBidConfig(auction.getId(), nguoiRaGiaA.getId(),
                new BigDecimal("20000000"), new BigDecimal("1000000"));
        autoBidEngine.register(config, nguoiRaGiaA);
        autoBidEngine.cleanupAutoBids(auction.getId());

        assertEquals(0, autoBidEngine.getRegisteredCount(auction.getId()));
        assertNull(autoBidEngine.peekWinner(auction.getId()));
    }
}