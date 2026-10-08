package com.example.tutorial.stokvel;


import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@Service
public class ReportService {
    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Dtos.EntryView> memberStatement(long memberId) {
        return jdbc.query("""
                SELECT e.id, e.transaction_id, t.type, t.description,
                       e.direction, e.amount_cents, e.created_at
                FROM ledger_entries e
                JOIN ledger_transactions t ON t.id = e.transaction_id
                JOIN accounts a ON a.id = e.account_id
                WHERE a.member_id = ?
                ORDER BY e.id DESC
                LIMIT 50
                """,
                (rs, i) -> new Dtos.EntryView(rs.getLong("id"), rs.getLong("transaction_id"),
                        rs.getString("type"), rs.getString("description"),
                        rs.getString("direction"), rs.getLong("amount_cents"),
                        rs.getTimestamp("created_at").toInstant()),
                memberId);
    }

    public Dtos.IntegrityReport integrity() {
        long debits = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM ledger_entries WHERE direction = 'D'", Long.class);
        long credits = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM ledger_entries WHERE direction = 'C'", Long.class);

        long drift = jdbc.queryForObject("""
                SELECT COUNT(*) FROM accounts a
                WHERE a.balance_cents <> COALESCE((
                    SELECT SUM(CASE WHEN e.direction = 'C' THEN e.amount_cents ELSE -e.amount_cents END)
                    FROM ledger_entries e WHERE e.account_id = a.id), 0)
                """, Long.class);

        return new Dtos.IntegrityReport(debits, credits, debits == credits, drift);
    }


}
