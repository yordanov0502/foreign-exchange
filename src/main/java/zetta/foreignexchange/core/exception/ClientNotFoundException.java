package zetta.foreignexchange.core.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ClientNotFoundException extends RuntimeException {

    private final String clientId;
}
