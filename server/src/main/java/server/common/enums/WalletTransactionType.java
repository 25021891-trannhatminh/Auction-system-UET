package server.common.enums;

public enum WalletTransactionType {
    DEPOSIT,    // Nạp tiền vào ví
    WITHDRAW,   // Rút tiền ra
    HOLD,       // Giữ tiền khi đặt bid
    RELEASE,    // Hoàn tiền khi thua bid
    PAYMENT,    // Trừ tiền khi thắng auction
    REFUND      // Hoàn tiền khi auction bị hủy
}