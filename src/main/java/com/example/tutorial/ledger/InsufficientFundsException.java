package com.example.tutorial.ledger;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(long accountId) {
        super("Account " + accountId + " has insufficient funds for this transaction");
    }
}
