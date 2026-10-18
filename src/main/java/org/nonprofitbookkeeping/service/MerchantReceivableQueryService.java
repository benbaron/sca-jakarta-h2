package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.MerchantReceivableRole;
import org.nonprofitbookkeeping.model.SupplementalItemEffect;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Read-only breakdown of existing receivable dated effects and canonical counterpart lines. */
public final class MerchantReceivableQueryService
{
    private static final BigDecimal ZERO = new BigDecimal("0.0000");

    private MerchantReceivableQueryService()
    {
    }

    public record Observation(LocalDate date, long transactionId, BigDecimal amount, String evidence)
    {
    }

    public record Movement(UUID itemId, LocalDate date, long transactionId, long sourceTransactionId,
                           String merchant, String account, String fund, String event, String role,
                           BigDecimal gross, BigDecimal payout, BigDecimal fee, BigDecimal nmr,
                           BigDecimal unexplained, BigDecimal change, BigDecimal observation,
                           String evidence, boolean correction)
    {
    }

    public record Summary(SupplementalOpenItemQueryService.Row item, String merchant, String account,
                          String fund, String event, BigDecimal opening, BigDecimal gross,
                          BigDecimal payout, BigDecimal fee, BigDecimal nmr, BigDecimal unexplained,
                          BigDecimal unknownGross, BigDecimal corrections, BigDecimal closing,
                          BigDecimal difference, Observation latestObservation)
    {
    }

    public record Result(LocalDate start, LocalDate end, SupplementalOpenItemQueryService.Result opening,
                         SupplementalOpenItemQueryService.Result closing, List<Summary> summaries, List<Movement> movements)
    {
        public Result
        {
            summaries = List.copyOf(summaries);
            movements = List.copyOf(movements);
        }

        public boolean ready()
        {
            return opening.ready() && closing.ready() && summaries.stream().allMatch(s -> !s.merchant().isBlank()
                    && s.unknownGross().signum() == 0 && s.difference().signum() == 0);
        }
    }

    static Result query(EntityManager em, String company, LocalDate start, LocalDate end)
    {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start) || start.equals(LocalDate.MIN))
        {
            throw new IllegalArgumentException("Merchant receivable period requires an ordered range with a preceding date.");
        }
        var kind = SupplementalOpenItemQueryService.Kind.RECEIVABLE;
        var opening = SupplementalOpenItemQueryService.query(em, company, kind, start.minusDays(1));
        var closing = SupplementalOpenItemQueryService.query(em, company, kind, end);
        Map<UUID, SupplementalOpenItemQueryService.Row> items = new LinkedHashMap<>();
        Map<UUID, BigDecimal> initial = new HashMap<>();
        Map<UUID, BigDecimal> finalAmounts = new HashMap<>();
        opening.authoritativeRows().forEach(r -> { items.put(r.itemId(), r); initial.put(r.itemId(), r.openBalance()); });
        closing.authoritativeRows().forEach(r -> { items.put(r.itemId(), r); finalAmounts.put(r.itemId(), r.openBalance()); });
        var effects = SupplementalOpenItemQueryService.periodEffects(em, company, kind, LocalDate.MIN, end);
        Map<Long, Integer> active = new HashMap<>();
        effects.forEach(e -> active.merge(e.allocation().getId(), e.direction(), Integer::sum));
        Map<UUID, Observation> observations = new HashMap<>();
        Map<UUID, Movement> dimensions = new HashMap<>();
        List<Movement> movements = new ArrayList<>();
        for (var effect : effects)
        {
            var line = effect.allocation();
            var split = line.getTxnSplit();
            BigDecimal signed = line.getAmount().multiply(BigDecimal.valueOf(effect.direction()));
            BigDecimal change = line.getItemEffect() == SupplementalItemEffect.INCREASE ? signed : signed.negate();
            BigDecimal gross = ZERO, payout = ZERO, fee = ZERO, nmr = ZERO;
            if (line.getMerchantRole() == MerchantReceivableRole.GROSS_RECEIPT)
            {
                gross = signed;
            }
            else if (line.getMerchantRole() == MerchantReceivableRole.SETTLEMENT)
            {
                for (var link : line.getMerchantComponents().entrySet())
                {
                    BigDecimal amount = link.getKey().getAmountSigned().abs().multiply(BigDecimal.valueOf(effect.direction()));
                    switch (link.getValue())
                    {
                        case PAYOUT -> payout = payout.add(amount);
                        case FEE -> fee = fee.add(amount);
                        case NMR_DISCHARGE -> nmr = nmr.add(amount);
                    }
                }
            }
            BigDecimal unexplained = change.subtract(gross.subtract(payout).subtract(fee).subtract(nmr));
            var movement = new Movement(line.getItemId(), effect.date(), effect.transactionId(), line.getTxn().getId(),
                    split.getMerchant() == null ? "" : split.getMerchant().getName(), split.getAccount().getCode(), split.getFund().getCode(),
                    split.getActivity() == null ? "" : split.getActivity().getCode() + " — " + split.getActivity().getName(),
                    line.getMerchantRole() == null ? "UNCLASSIFIED" : line.getMerchantRole().name(),
                    gross, payout, fee, nmr, unexplained, change, line.getMerchantWithheldUnconfirmed(), line.getMerchantEvidenceReference(),
                    effect.transactionId() != line.getTxn().getId());
            dimensions.put(line.getItemId(), movement);
            if (!effect.date().isBefore(start))
            {
                movements.add(movement);
            }
            if (line.getMerchantWithheldUnconfirmed() != null && active.get(line.getId()) > 0)
            {
                Observation observation = new Observation(line.getTxn().getTxnDate(), line.getTxn().getId(),
                        line.getMerchantWithheldUnconfirmed(), line.getMerchantEvidenceReference());
                observations.merge(line.getItemId(), observation, (a, b) ->
                        Comparator.comparing(Observation::date).thenComparingLong(Observation::transactionId).compare(a, b) >= 0 ? a : b);
            }
        }
        List<Summary> summaries = new ArrayList<>();
        for (var item : items.values())
        {
            BigDecimal gross = ZERO, payout = ZERO, fee = ZERO, nmr = ZERO, unknown = ZERO, unknownGross = ZERO, corrections = ZERO;
            for (var movement : movements.stream().filter(m -> m.itemId().equals(item.itemId())).toList())
            {
                gross = gross.add(movement.gross());
                payout = payout.add(movement.payout());
                fee = fee.add(movement.fee());
                nmr = nmr.add(movement.nmr());
                unknown = unknown.add(movement.unexplained());
                unknownGross = unknownGross.add(movement.unexplained().abs());
                if (movement.correction())
                {
                    corrections = corrections.add(movement.change());
                }
            }
            BigDecimal before = initial.getOrDefault(item.itemId(), ZERO), after = finalAmounts.getOrDefault(item.itemId(), ZERO);
            var dimension = dimensions.get(item.itemId());
            summaries.add(new Summary(item, dimension == null ? "" : dimension.merchant(), dimension == null ? "" : dimension.account(),
                    dimension == null ? "" : dimension.fund(), dimension == null ? "" : dimension.event(),
                    before, gross, payout, fee, nmr, unknown, unknownGross, corrections, after,
                    after.subtract(before.add(gross).subtract(payout).subtract(fee).subtract(nmr).add(unknown)), observations.get(item.itemId())));
        }
        summaries.sort(Comparator.comparing((Summary s) -> s.item().openingDate()).thenComparing(s -> s.item().itemId()));
        return new Result(start, end, opening, closing, summaries, movements);
    }
}
