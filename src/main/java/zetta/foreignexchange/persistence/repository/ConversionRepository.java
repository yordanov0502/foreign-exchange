package zetta.foreignexchange.persistence.repository;

import lombok.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;
import zetta.foreignexchange.persistence.constant.ConversionAttributeConstant;
import zetta.foreignexchange.persistence.entity.ConversionEntity;

import java.util.Optional;

public interface ConversionRepository
        extends JpaRepository<ConversionEntity, Long>, JpaSpecificationExecutor<ConversionEntity> {

    Optional<ConversionEntity> findByClientClientIdAndIdempotencyKey(String clientId, String idempotencyKey);

    long countByClientClientId(String clientId);

    @Transactional
    void deleteByClientClientId(String clientId);

    @Override
    @EntityGraph(attributePaths = ConversionAttributeConstant.CLIENT)
    @NonNull
    Page<ConversionEntity> findAll(@NonNull Specification<ConversionEntity> specification, @NonNull Pageable pageable);
}
