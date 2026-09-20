package zetta.foreignexchange.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import zetta.foreignexchange.persistence.constant.EntityConstant;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "balances")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BalanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false, updatable = false)
    private ClientEntity client;

    @Column(nullable = false, length = EntityConstant.CURRENCY_LENGTH, updatable = false)
    private String currency;

    @Column(nullable = false, precision = EntityConstant.MONEY_PRECISION, scale = EntityConstant.MONEY_SCALE)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public void debit(BigDecimal baseAmount) {
        if (this.amount.compareTo(baseAmount) < 0) {
            throw new IllegalStateException(
                    "Balance " + currency + " cannot be debited below zero.");
        }
        this.amount = this.amount.subtract(baseAmount);
    }

    public void credit(BigDecimal quoteAmount) {
        this.amount = this.amount.add(quoteAmount);
    }
}
