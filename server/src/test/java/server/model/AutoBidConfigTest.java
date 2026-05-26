package server.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import server.common.entity.AutoBidConfig;
import server.common.entity.exception.AutoBidConfigException;
import server.common.enums.AutoBidStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit test cho AutoBidConfig.
 * Kiểm tra logic canBid, nextBidAmount, priority, và status transitions.
 */
class AutoBidConfigTest {

    private AutoBidConfig config;

    /**
     * Khởi tạo AutoBidConfig mẫu trước mỗi test.
     */
    @BeforeEach
    void setUp() {
        // Khởi tạo đúng kiểu dữ liệu int cho ID: auctionId = 1001, bidderId = 501
        config = new AutoBidConfig(1001, 501, BigDecimal.valueOf(1000.0), BigDecimal.valueOf(100.0));
    }

    // ==================== KHỞI TẠO ====================

    /**
     * maxBid âm phải ném IllegalArgumentException.
     */
    @Test
    @DisplayName("maxBid <= 0 → IllegalArgumentException")
    void constructor_negativeMaxBid_shouldThrow() {
        assertThrows(AutoBidConfigException.class, () ->
                new AutoBidConfig(1001, 501,
                        new BigDecimal("-10"), new BigDecimal("100"))
        );
    }

    /**
     * increment âm phải ném AutoBidConfigException.
     */
    @Test
    @DisplayName("increment <= 0 → AutoBidConfigException")
    void constructor_negativeIncrement_shouldThrow() {
        assertThrows(AutoBidConfigException.class, () ->
                new AutoBidConfig(1001, 501, new BigDecimal("1000"), new BigDecimal("-50")));
    }

    /**
     * maxBid < increment phải ném AutoBidConfigException.
     */
    @Test
    @DisplayName("maxBid < increment → AutoBidConfigException")
    void constructor_maxBidLessThanIncrement_shouldThrow() {
        assertThrows(AutoBidConfigException.class, () ->
                new AutoBidConfig(1001, 501, new BigDecimal("1000"), new BigDecimal("-50")));
    }

    /**
     * Khởi tạo hợp lệ phải có status ACTIVE.
     */
    @Test
    @DisplayName("Khởi tạo hợp lệ → status ACTIVE")
    void constructor_valid_shouldHaveActiveStatus() {
        assertEquals(AutoBidStatus.ACTIVE, config.getStatus());
    }

    // ==================== CAN BID ====================

    /**
     * canBid() khi currentPrice + increment <= maxBid phải trả về true.
     */
    @Test
    @DisplayName("canBid() currentPrice=800 → true (800+100=900 <= 1000)")
    void canBid_priceWithinRange_shouldReturnTrue() {
        // currentPrice=800, nextBid=900 <= maxBid=1000
        assertTrue(config.canBid(new BigDecimal("800")));
    }

    /**
     * canBid() khi nextBid vừa đúng maxBid phải trả về true.
     */
    @Test
    @DisplayName("canBid() currentPrice=900 → true (900+100=1000 = maxBid)")
    void canBid_nextBidEqualsMaxBid_shouldReturnTrue() {
        // currentPrice=900, nextBid=1000 = maxBid=1000
        assertTrue(config.canBid(new BigDecimal("900")));
    }

    /**
     * canBid() khi nextBid vượt maxBid phải trả về false.
     */
    @Test
    @DisplayName("canBid() currentPrice=950 → false (950+100=1050 > maxBid=1000)")
    void canBid_nextBidExceedsMaxBid_shouldReturnFalse() {
        // currentPrice=950, nextBid=1050 > maxBid=1000
        assertFalse(config.canBid(new BigDecimal("950")));
    }

    /**
     * canBid() khi status CANCELED phải trả về false.
     */
    @Test
    @DisplayName("canBid() khi CANCELED → false")
    void canBid_whenCanceled_shouldReturnFalse() {
        config.cancel();
        assertFalse(config.canBid(new BigDecimal("800")));
    }

    /**
     * canBid() khi status COMPLETED phải trả về false.
     */
    @Test
    @DisplayName("canBid() khi COMPLETED → false")
    void canBid_whenCompleted_shouldReturnFalse() {
        config.complete();
        assertFalse(config.canBid(new BigDecimal("800")));
    }

    // ==================== NEXT BID AMOUNT ====================

    /**
     * nextBidAmount() phải trả về currentPrice + increment.
     */
    @Test
    @DisplayName("nextBidAmount(800) → 900 (800 + 100)")
    void nextBidAmount_shouldReturnCurrentPluIncrement() {
        BigDecimal result = config.nextBidAmount(new BigDecimal("800"));
        assertEquals(BigDecimal.valueOf(900.0), result);
    }

    // ==================== STATUS TRANSITIONS ====================

    /**
     * cancel() phải chuyển status sang CANCELED.
     */
    @Test
    @DisplayName("cancel() → status CANCELED")
    void cancel_shouldChangeStatusToCanceled() {
        config.cancel();
        assertEquals(AutoBidStatus.CANCELED, config.getStatus());
    }

    /**
     * complete() phải chuyển status sang COMPLETED.
     */
    @Test
    @DisplayName("complete() → status COMPLETED")
    void complete_shouldChangeStatusToCompleted() {
        config.complete();
        assertEquals(AutoBidStatus.COMPLETED, config.getStatus());
    }

    // ==================== COMPARABLE / PRIORITY ====================

    /**
     * Config có maxBid cao hơn phải có priority cao hơn (compareTo âm).
     */
    @Test
    @DisplayName("maxBid cao hơn → priority cao hơn trong PriorityQueue")
    void compareTo_higherMaxBid_shouldHaveHigherPriority() {
        AutoBidConfig higher = new AutoBidConfig(1001, 502, new BigDecimal("2000"), new BigDecimal("100"));

        // higher.maxBid=2000 > config.maxBid=1000
        // higher phải đứng trước → kết quả so sánh mong đợi phù hợp định nghĩa cấu trúc Heap của nhóm
        assertTrue(config.compareTo(higher) > 0);
        assertTrue(higher.compareTo(config) < 0);
    }

    /**
     * Config đăng ký sớm hơn có maxBid bằng nhau phải có priority cao hơn.
     */
    @Test
    @DisplayName("maxBid bằng nhau → đăng ký sớm hơn có priority cao hơn")
    void compareTo_sameMaxBid_earlierRegistration_shouldHaveHigherPriority()
            throws InterruptedException {
        // config được tạo trước trong setUp() có maxBid = 1000
        // Đợi 10ms để registeredAt khác nhau
        Thread.sleep(10);

        AutoBidConfig later = new AutoBidConfig(1001,502,new BigDecimal("1000"), new BigDecimal("100"));

        // config đăng ký trước → priority cao hơn
        assertTrue(config.compareTo(later) < 0);
        assertTrue(later.compareTo(config) > 0);
    }
}