package com.example.tutorial.stokvel;


import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import org.springframework.jdbc.core.RowMapper;
import com.example.tutorial.stokvel.Dtos.StokvelSummary;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StokvelService {

    private static final String SUMMARY_SQL = """
            SELECT s.id, s.name, s.contribution_cents, p.balance_cents AS pool, c.balance_cents AS clearing
            FROM stokvel s JOIN account p ON p.stokvel_id = s.id AND p.type = 'POOL'
            JOIN account c ON c.stokvel_id = s.id AND c.type = 'CLEARING'
            """;

    private static final RowMapper<StokvelSummary> SUMMARY_MAPPER = (rs, i) -> new StokvelSummary(
            rs.getLong("id"), rs.getString("name"), rs.getLong("contribution_cents"),
            rs.getLong("pool"), rs.getLong("clearing")
    );

    private final JdbcTemplate jdbc;

    public StokvelService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public StokvelSummary create(String name, long contributionCents) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is not entered");
        if (contributionCents <= 0) throw new IllegalArgumentException("Contribution should be greater than zero");

      Long id = jdbc.queryForObject("INSERT INTO stokvels (name, contribution_cents) VALUES (?, ?)", Long.class, name.trim(), contributionCents);

      jdbc.update("INSERT INTO accounts (stokvel_id, type, allow_negetive) VALUES (?, 'POOL', 'FALSE')", id);
      jdbc.update("INSERT INTO accounts (stokvel_id, type, allow_negetive) VALUES (?, 'CLEARING', 'FALSE')", id);

      return get(id).stokvel();
    }


    @Transactional
    public long addMember(long stokvelId, String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is not entered");
        get(stokvelId);

        Long memberId = jdbc.queryForObject("INSERT INTO members (stokvel_id, name) VALUES (?, ?) RETURNING id", Long.class, stokvelId, name.trim());

        jdbc.update("INSERT INTO accounts (stokvel_id, member_id, type, allow_negative) VALUES (?, ?, 'WALLET', 'FALSE')", stokvelId, memberId);
        return memberId;
    }

    public List<StokvelSummary> list() {
        return jdbc.query(SUMMARY_SQL + " ORDER BY s.id", SUMMARY_MAPPER);
    }

    public Dtos.StokvelDetail get(long id) {
        List<StokvelSummary> found = jdbc.query(SUMMARY_SQL + " WHERE s.id = ?", SUMMARY_MAPPER, id);
        if (found.isEmpty()) throw new NotFoundException("Stokvel " + id + " not found");


        List<Dtos.MemberView> members = jdbc.query("""
                SELECT m.id, m.name, w.id AS wallet_id,
                    COALESCE((
                        SELECT SUM(e.amount_cents)
                        FROM ledger_entries e
                        JOIN ledger_transactions t ON t.id = e.transition_id
                        WHERE e.account_id = w.id AND e.direction = 'C' AND t.type = 'CONTRIBUTION
                    ), 0) AS contributed
                FROM members m
                JOIN accounts w ON w.member_id = m.id
                WHERE m.stokvel_id = ?
                ORDER BY m.id
                """, (rs, i) -> new Dtos.MemberView(rs.getLong("id"), rs.getString("name"), rs.getLong("wallet_id"), rs.getLong("contributed")), id);
        return new Dtos.StokvelDetail(found.get(0), members);
    }
}
