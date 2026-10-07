CREATE TABLE stokvels(
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    contribution_cents BIGINT NOT NULL CHECK (contribution_cents > 0),
    created_at TIMESTAMPZ NOT NULL DEFAULT now()
);

CREATE TABLE members (
    id BIGSERIAL PRIMARY KEY,
    stokvel_id BIGINT NOT NULL REFERENCES stokvels(id),
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPZ NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    stokvel_id BIGINT NOT NULL REFERENCES stokvels(id),
    member_id BIGINT REFERENCES members(id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('POOL', 'CLEARING', 'WALLET'))
    allow_negative BOOLEAN NOT NULL DEFAULT FALSE,
    balance_cents BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (allow_negative OR balance_cents >= 0)
);

--One POOL and one CLEARING per stokvel, one WALLET per member
CREATE UNIQUE INDEX uq_account_stokvel_type ON accounts (stokvel_id, type) WHERE member_id IS NULL;
CREATE UNIQUE INDEX uq_account_member ON accounts (member_id) WHERE member_id IS NOT NULL;


CREATE TABLE ledger_transaction (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    type VARCHAR(30) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPZ NOT NULL DEFAULT now()
);


CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES ledger_transaction(id),
    account_id BIGINT NOT NULL REFERENCES accounts(id),
    direction CHAR(1) NOT NULL CHECK (direction IN ('D', 'C')),
    amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
    created_at TIMESTAMPZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_entries_account ON ledger_entries (account_id, id);
CREATE INDEX idx_entries_tx ON ledger_entries (transaction_id)


-- Make the ledger append-only at the database level
CREATE FUNCTION forbid_entry_changes() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_entries_immutable
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION forbid_entry_changes();



