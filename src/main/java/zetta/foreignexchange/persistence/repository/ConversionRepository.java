package zetta.foreignexchange.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import zetta.foreignexchange.persistence.entity.ConversionEntity;

import java.util.Optional;

public interface ConversionRepository extends JpaRepository<ConversionEntity, Long> {

    Optional<ConversionEntity> findByClientClientIdAndIdempotencyKey(String clientId, String idempotencyKey);

    long countByClientClientId(String clientId);

    @Transactional
    void deleteByClientClientId(String clientId);
}
