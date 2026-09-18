package zetta.foreignexchange.core.service.implementation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zetta.foreignexchange.core.exception.ClientNotFoundException;
import zetta.foreignexchange.core.mapper.BalanceMapper;
import zetta.foreignexchange.core.model.Balance;
import zetta.foreignexchange.core.service.BalanceService;
import zetta.foreignexchange.persistence.entity.BalanceEntity;
import zetta.foreignexchange.persistence.repository.BalanceRepository;
import zetta.foreignexchange.persistence.repository.ClientRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BalanceServiceImpl implements BalanceService {

    private final ClientRepository clientRepository;
    private final BalanceRepository balanceRepository;
    private final BalanceMapper balanceMapper;

    @Override
    @Transactional(readOnly = true)
    public List<Balance> getClientBalances(String clientId) {
        validateClientExists(clientId);
        List<BalanceEntity> balanceEntities = balanceRepository.findByClientClientIdOrderByCurrencyAsc(clientId);
        return balanceMapper.mapToBalances(balanceEntities);
    }

    private void validateClientExists(String clientId) {
        if (!clientRepository.existsByClientId(clientId)) {
            throw new ClientNotFoundException(clientId);
        }
    }
}
