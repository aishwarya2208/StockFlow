package com.stockflow.common.exception;

public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String message) {
        super(message);
    }

    public InvalidOrderStateException(String orderNumber, String currentState, String targetAction) {
        super(String.format("Cannot perform '%s' on order '%s' in current state '%s'",
                targetAction, orderNumber, currentState));
    }
}
