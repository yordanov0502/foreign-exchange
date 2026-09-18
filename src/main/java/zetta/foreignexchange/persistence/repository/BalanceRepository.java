package zetta.foreignexchange.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zetta.foreignexchange.persistence.entity.BalanceEntity;

import java.util.List;

public interface BalanceRepository extends JpaRepository<BalanceEntity, Long> {

    List<BalanceEntity> findByClientClientIdOrderByCurrencyAsc(String clientId);
}
