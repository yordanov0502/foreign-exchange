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
import org.hibernate.annotations.Immutable;
import zetta.foreignexchange.persistence.constant.EntityConstant;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversions")
@Immutable
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ConversionEntity {

    private static final int IDEMPOTENCY_KEY_LENGTH = 128;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientEntity client;

    @Column(name = "base_currency", nullable = false, length = EntityConstant.CURRENCY_LENGTH)
    private String baseCurrency;

    @Column(name = "base_amount",
            nullable = false,
            precision = EntityConstant.MONEY_PRECISION,
            scale = EntityConstant.MONEY_SCALE)
    private BigDecimal baseAmount;

    @Column(name = "quote_currency", nullable = false, length = EntityConstant.CURRENCY_LENGTH)
    private String quoteCurrency;

    @Column(name = "quote_amount",
            nullable = false,
            precision = EntityConstant.MONEY_PRECISION,
            scale = EntityConstant.MONEY_SCALE)
    private BigDecimal quoteAmount;

    @Column(nullable = false, precision = EntityConstant.MONEY_PRECISION, scale = EntityConstant.RATE_SCALE)
    private BigDecimal rate;

    @Column(name = "idempotency_key", length = IDEMPOTENCY_KEY_LENGTH)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
