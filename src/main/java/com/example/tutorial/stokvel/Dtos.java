package com.example.tutorial.stokvel;

import java.time.Instant;
import java.util.List;

public final class Dtos {

    private Dtos() {}

    public record CreateStokvelRequest(String name, long contributionCents) {}

    public record AddMemberRequest(String name) {}

    public record MoneyRequest(long memberId, long amountCents){}

    public record StokvelSummary(long id, String name, long contributionCents, long poolBalanceCents, long clearingBalanceCents) {}

    public record MemberView(long id, String name, long walletAccountId, long contributionCents) {}

    public record StokvelDetail(StokvelSummary stokvel, List<MemberView> members) {}

    public record EntryView(long entryId, long transactionId, String type, String description, String direction, long amountCents, Instant createdAt) {}

    public record IntegrityReport(long totalDebitsCents, long totalCreditsCents, boolean balanced, long driftingAccounts) {}




}
