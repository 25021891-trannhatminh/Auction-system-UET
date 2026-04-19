package server.common.model;

import java.io.Serializable;
import java.math.BigDecimal;

public class WinnerDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;
    private BigDecimal amount;

    public WinnerDTO() {}

    public WinnerDTO(String username, BigDecimal amount) {
        this.username = username;
        this.amount = amount;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}