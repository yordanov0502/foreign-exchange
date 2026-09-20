package zetta.foreignexchange.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import zetta.foreignexchange.persistence.entity.BalanceEntity;

import java.util.List;
import java.util.Optional;

public interface BalanceRepository extends JpaRepository<BalanceEntity, Long> {

    List<BalanceEntity> findByClientClientIdOrderByCurrencyAsc(String clientId);

    Optional<BalanceEntity> findByClientClientIdAndCurrency(String clientId, String currency);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BalanceEntity> findAndLockByClientClientIdAndCurrency(String clientId, String currency);
}
