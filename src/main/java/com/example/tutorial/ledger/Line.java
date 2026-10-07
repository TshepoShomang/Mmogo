package com.example.tutorial.ledger;

public record Line(long accountId, Direction direction, long amountCents) {

    public static Line debit(long accountId, long amountCents){
        return new Line(accountId, Direction.D, amountCents);
    }

    public static Line credit(long accountId, long amountCents) {
        return new Line(accountId, Direction.C, amountCents);
    }

}
