package com.example.tutorial.ledger;

public class UnbalancedTransactionException extends IllegalAccessException {
    public UnbalancedTransactionException(String message) {
        super(message);
    }
}
