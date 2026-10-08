package com.example.tutorial.ledger;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;



@Service
public class LedgerService {
    private record LockedAccount(long id, long balanceCents, boolean allowNegative) {
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public LedgerService(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    @Transactional
    public PostingResult post(String idempotencyKey, String type, String description, List<Line> lines) {
        validate(idempotencyKey, lines);

        List<Long> created = jdbc.queryForList(
                "INSERT INTO ledger_transaction (idempotency_key, type, description) " +
                "VALUES (?, ?, ?) ON CONFLICT (idempotency_key) DO NOTHING RETURNING id", Long.class, idempotencyKey, type, description
        );

        if (created.isEmpty()) {
            Long existing = jdbc.queryForObject(
                    "SELECT id FROM ledger_transaction WHERE idempotency_key = ?",
                    Long.class, idempotencyKey
            );
            return new PostingResult(existing, true);
        }

        long txTd = created.get(0);

        List<Long> accountIds = lines.stream().map(Line::accountId).distinct().sorted().toList();

        List<LockedAccount> locked = named.query(
          "SELECT id, balance_cents, allow_negative FROM accounts " +
          "WHERE id IN (:ids) ORDER BY id FOR UPDATE",
          new MapSqlParameterSource("ids", accountIds), (rs, i) -> new LockedAccount(rs.getLong("id"), rs.getLong("balance_cents"), rs.getBoolean("allow_negative"))
        );

        if (locked.size() != accountIds.size()) {
            throw new IllegalArgumentException("One or more accounts do not exist");
        }

        Map<Long, Long> deltas = new TreeMap<>();
        for (Line line : lines) {
            long signed = line.direction() == Direction.C ? line.amountCents() : -line.amountCents();
            deltas.merge(line.accountId(), signed, Long::sum);
        }

        for (LockedAccount account : locked) {
            long newBalance = account.balanceCents + deltas.get(account.id());
            if (newBalance < 0 && !account.allowNegative()) {
                throw new InsufficientFundsException(account.id());
            }
        }

        jdbc.batchUpdate(
                "INSERT INTO ledger_entries (transaction_id, account_id, direction, amount_cents) " +
                        "VALUES (?, ?, ?, ?)",
                lines, lines.size(),
                (ps, line) -> {
                    ps.setLong(1, txTd);
                    ps.setLong(2, line.accountId());
                    ps.setString(3, line.direction().name());
                    ps.setLong(4, line.amountCents());
                }
        );

        deltas.forEach((accountId, delta) -> jdbc.update(
                "UPDATE accounts SET balance_cents = balance_cents + ?, version = version + 1 WHERE id = ?",
                delta, accountId
        ));

        return new PostingResult(txTd, false);
    }

    private void validate(String idempotencyKey, List<Line> lines) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("An idempotency key is required");
        }

        if (lines == null || lines.size() < 2) {
            throw new IllegalArgumentException("A transaction needs at least two lines");
        }

        long debits = 0;
        long credits = 0;

        for (Line line : lines) {
            if (line.amountCents() <= 0) {
                throw new RuntimeException("Line amounts must be positive");
            }
            if (line.direction() == Direction.D) debits += line.amountCents();
            else credits += line.amountCents();
        }

        if (debits != credits) {
            throw new RuntimeException(
                    "Debits (" + debits + ") must be equal to credits (" + credits + ")"
            );
        }
    }
}
