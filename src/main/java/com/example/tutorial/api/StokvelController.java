package com.example.tutorial.api;


import com.example.tutorial.ledger.InsufficientFundsException;
import com.example.tutorial.ledger.PostingResult;
import com.example.tutorial.stokvel.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class StokvelController {
    private final StokvelService stokvels;
    private final PaymentService payments;
    private final ReportService reports;

    public StokvelController(StokvelService stokvels, PaymentService payments, ReportService reports) {
        this.stokvels = stokvels;
        this.payments = payments;
        this.reports = reports;
    }

    @PostMapping("/stokvels")
    public Dtos.StokvelSummary create(@RequestBody Dtos.CreateStokvelRequest req) {
        return stokvels.create(req.name(), req.contributionCents());
    }

    @GetMapping("/stokvels")
    public List<Dtos.StokvelSummary> list() {
        return stokvels.list();
    }

    @GetMapping("/stokvels/{id}")
    public Dtos.StokvelDetail get(@PathVariable("id") long id) {
        return stokvels.get(id);
    }

    @PostMapping("/stokvels/{id}/members")
    public Map<String, Long> addMember(@PathVariable("id") long id, @RequestBody Dtos.AddMemberRequest req) {
        return Map.of("memberId", stokvels.addMember(id, req.name()));
    }

    @PostMapping("/stokvels/{id}/contributions")
    public PostingResult contribute(@PathVariable("id") long id,
                                    @RequestHeader("Idempotency-Key") String key,
                                    @RequestBody Dtos.MoneyRequest req) {
        return payments.contribute(id, req.memberId(), req.amountCents(), key);
    }

    @PostMapping("/stokvels/{id}/payouts")
    public PostingResult payout(@PathVariable("id") long id,
                                @RequestHeader("Idempotency-Key") String key,
                                @RequestBody Dtos.MoneyRequest req) {
        return payments.payout(id, req.memberId(), req.amountCents(), key);
    }

    @GetMapping("/members/{memberId}/statement")
    public List<Dtos.EntryView> statement(@PathVariable("memberId") long memberId) {
        return reports.memberStatement(memberId);
    }

    @GetMapping("/ledger/integrity")
    public Dtos.IntegrityReport integrity() {
        return reports.integrity();
    }
}
