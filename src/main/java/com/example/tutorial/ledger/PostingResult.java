package com.example.tutorial.ledger;

public record PostingResult(long transactionId, boolean replayed) {
}
