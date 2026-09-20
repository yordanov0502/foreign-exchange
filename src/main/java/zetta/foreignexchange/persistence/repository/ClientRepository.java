package zetta.foreignexchange.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zetta.foreignexchange.persistence.entity.ClientEntity;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<ClientEntity, Long> {

    boolean existsByClientId(String clientId);

    Optional<ClientEntity> findByClientId(String clientId);
}
