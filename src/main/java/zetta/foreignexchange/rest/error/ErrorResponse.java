package zetta.foreignexchange.rest.error;

import lombok.Builder;

@Builder(toBuilder = true)
public record ErrorResponse(String code, String message, int status, String path) {
}
