package cn.openlims.platform.fund.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "fund_accounts")
public class FundAccountEntity {
    @Id @Column(length = 16) private String id = "LAB";
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal openingBalance = BigDecimal.ZERO.setScale(2);
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal balance = BigDecimal.ZERO.setScale(2);
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal income = BigDecimal.ZERO.setScale(2);
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal expense = BigDecimal.ZERO.setScale(2);
    private LocalDate openedOn;
    private Instant initializedAt;
    private Instant updatedAt;
    public FundAccountEntity() {}
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public BigDecimal getBalance() { return balance; }
    public BigDecimal getIncome() { return income; }
    public BigDecimal getExpense() { return expense; }
    public LocalDate getOpenedOn() { return openedOn; }
    public Instant getInitializedAt() { return initializedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void initialize(BigDecimal amount, LocalDate date) {
        openingBalance = amount.setScale(2); balance = openingBalance; openedOn = date;
        initializedAt = Instant.now(); updatedAt = initializedAt;
    }
    public void apply(FundEntryType type, BigDecimal amount, boolean reverse) {
        BigDecimal delta = (reverse ? amount.negate() : amount).setScale(2);
        if (type == FundEntryType.INCOME) { income = income.add(delta); balance = balance.add(delta); }
        else { expense = expense.add(delta); balance = balance.subtract(delta); }
        updatedAt = Instant.now();
    }
}
