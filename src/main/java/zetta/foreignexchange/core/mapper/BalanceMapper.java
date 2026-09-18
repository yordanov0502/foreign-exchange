package zetta.foreignexchange.core.mapper;

import org.mapstruct.Mapper;
import zetta.foreignexchange.core.model.Balance;
import zetta.foreignexchange.persistence.entity.BalanceEntity;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BalanceMapper {

    Balance mapToBalance(BalanceEntity balanceEntity);

    List<Balance> mapToBalances(List<BalanceEntity> balanceEntities);
}
