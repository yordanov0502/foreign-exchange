package zetta.foreignexchange.common.integrations.frankfurter.exception;

public class FrankfurterPairNotQuotableException extends RuntimeException {

    public FrankfurterPairNotQuotableException(String message) {
        super(message);
    }
}
