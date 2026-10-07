package com.example.tutorial.stokvel;


import com.example.tutorial.ledger.LedgerService;
import com.example.tutorial.ledger.PostingResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private record Account(long pool, long clearing, long wallet){}

    private final LedgerService ledger;
    private final JdbcTemplate jdbc;

    public PaymentService(LedgerService ledger, JdbcTemplate jdbc) {
        this.ledger = ledger;
        this.jdbc = jdbc;
    }

    public PostingResult payout(long stokvelID, long memberId, long amountCents, String idempotencyKey)


}
