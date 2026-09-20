package zetta.foreignexchange.persistence.specification;

import static zetta.foreignexchange.persistence.constant.ConversionAttributeConstant.CLIENT;
import static zetta.foreignexchange.persistence.constant.ConversionAttributeConstant.CLIENT_ID;
import static zetta.foreignexchange.persistence.constant.ConversionAttributeConstant.CREATED_AT;
import static zetta.foreignexchange.persistence.constant.ConversionAttributeConstant.TRANSACTION_ID;

import org.springframework.data.jpa.domain.Specification;
import zetta.foreignexchange.persistence.entity.ConversionEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One single-argument {@link Specification} factory per {@code GET /conversions} filter. Each factory
 * returns {@link Specification#unrestricted()} for a {@code null} value, which {@link Specification#allOf}
 * drops, so only the supplied filters reach the generated SQL. Never fetch the {@code client} association
 * here: the same specification also drives the count query, where a fetch join fails outright.
 */
public final class ConversionSpecification {

    private ConversionSpecification() {}

    public static Specification<ConversionEntity> hasTransactionId(UUID transactionId) {
        if (transactionId == null) {
            return Specification.unrestricted();
        }
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(root.get(TRANSACTION_ID), transactionId);
    }

    public static Specification<ConversionEntity> hasClientId(String clientId) {
        if (clientId == null) {
            return Specification.unrestricted();
        }
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.equal(root.get(CLIENT).get(CLIENT_ID), clientId);
    }

    public static Specification<ConversionEntity> isCreatedOnOrAfter(OffsetDateTime startOfDay) {
        if (startOfDay == null) {
            return Specification.unrestricted();
        }
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(root.get(CREATED_AT), startOfDay);
    }

    public static Specification<ConversionEntity> isCreatedBefore(OffsetDateTime startOfNextDay) {
        if (startOfNextDay == null) {
            return Specification.unrestricted();
        }
        return (root, criteriaQuery, criteriaBuilder) ->
                criteriaBuilder.lessThan(root.get(CREATED_AT), startOfNextDay);
    }
}
