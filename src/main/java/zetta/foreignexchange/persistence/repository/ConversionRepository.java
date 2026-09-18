package zetta.foreignexchange.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zetta.foreignexchange.persistence.entity.ConversionEntity;

public interface ConversionRepository extends JpaRepository<ConversionEntity, Long> {
}
