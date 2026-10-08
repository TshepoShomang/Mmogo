package com.example.tutorial.stokvel;


import com.example.tutorial.ledger.LedgerService;
import com.example.tutorial.ledger.Line;
import com.example.tutorial.ledger.PostingResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentService {

    private record Accounts(long pool, long clearing, long wallet){}

    private final LedgerService ledger;
    private final JdbcTemplate jdbc;

    public PaymentService(LedgerService ledger, JdbcTemplate jdbc) {
        this.ledger = ledger;
        this.jdbc = jdbc;
    }

    public PostingResult contribute(long stokvelId, long memberId, long amountCents, String idempotencyKey) {
        Accounts a = resolve(stokvelId, memberId);
        return ledger.post(idempotencyKey, "CONTRIBUTION",
                "Contribution by member " + memberId,
                List.of(
                        Line.debit(a.clearing(), amountCents),   // funds received from outside
                        Line.credit(a.wallet(), amountCents),
                        Line.debit(a.wallet(), amountCents),     // funds applied to the pool
                        Line.credit(a.pool(), amountCents)));
    }

    /** Money leaves the pool, passes through the member's wallet, and exits the system. */
    public PostingResult payout(long stokvelId, long memberId, long amountCents, String idempotencyKey) {
        Accounts a = resolve(stokvelId, memberId);
        return ledger.post(idempotencyKey, "PAYOUT",
                "Payout to member " + memberId,
                List.of(
                        Line.debit(a.pool(), amountCents),       // allocated from the pool
                        Line.credit(a.wallet(), amountCents),
                        Line.debit(a.wallet(), amountCents),     // sent out to the member's bank
                        Line.credit(a.clearing(), amountCents)));
    }

    private Accounts resolve(long stokvelId, long memberId) {
        List<Accounts> rows = jdbc.query("""
                SELECT
                  (SELECT id FROM accounts WHERE stokvel_id = ? AND type = 'POOL')     AS pool,
                  (SELECT id FROM accounts WHERE stokvel_id = ? AND type = 'CLEARING') AS clearing,
                  (SELECT w.id FROM accounts w JOIN members m ON m.id = w.member_id
                    WHERE m.id = ? AND m.stokvel_id = ?)                               AS wallet
                """,
                (rs, i) -> new Accounts(rs.getLong("pool"), rs.getLong("clearing"), rs.getLong("wallet")),
                stokvelId, stokvelId, memberId, stokvelId);

        Accounts a = rows.get(0);
        if (a.pool() == 0 || a.wallet() == 0) {
            throw new NotFoundException("Stokvel or member not found");
        }
        return a;
    }


}
