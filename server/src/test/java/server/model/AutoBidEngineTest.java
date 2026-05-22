package server.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import server.common.entity.*;
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

        nguoiBan = new User("seller", "seller@test.com", "hash", "Nguoi Ban", "123");
        nguoiRaGiaA = new User("bidderA", "a@test.com", "hash", "Nguoi A", "456");
        nguoiRaGiaB = new User("bidderB", "b@test.com", "hash", "Nguoi B", "789");

        sanPham = new Item(nguoiBan.getId(), "Test Item", "Desc", new BigDecimal("10000000"),
                ItemStatus.APPROVED, ItemCategory.OTHER) {
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
        // Đăng ký auto-bid cho người B với max 15tr
        AutoBidConfig configB = new AutoBidConfig(auction.getId(), nguoiRaGiaB.getId(),
                new BigDecimal("15000000"), new BigDecimal("500000"));
        autoBidEngine.register(configB, nguoiRaGiaB);

        // Người A đặt giá thủ công 11tr
        auction.placeBid(nguoiRaGiaA, new BigDecimal("11000000"), false);

        // Kích hoạt auto-bid
        BidTransaction autoTx = autoBidEngine.trigger(auction, nguoiRaGiaA);

        assertNotNull(autoTx);
        assertTrue(autoTx.isAutoBid());
        assertEquals(nguoiRaGiaB.getId(), autoTx.getBidderId());
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
        BidTransaction tx = autoBidEngine.trigger(auction, nguoiRaGiaA);

        assertNull(tx);
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