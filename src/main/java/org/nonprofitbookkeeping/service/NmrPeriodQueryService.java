package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.NmrMovementKind;
import org.nonprofitbookkeeping.model.SupplementalItemEffect;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Period attribution over the existing allocation effects and as-of balances. */
public final class NmrPeriodQueryService
{
    private static final BigDecimal ZERO = new BigDecimal("0.0000");

    private NmrPeriodQueryService()
    {
    }

    public record Movement(UUID itemId, LocalDate date, long transactionId, long sourceTransactionId,
                           NmrMovementKind kind, BigDecimal change, BigDecimal attributedAmount,
                           String evidence, Long merchantId, String merchantName, boolean correction)
    {
    }

    public record Summary(SupplementalOpenItemQueryService.Row item, BigDecimal opening,
                          Map<NmrMovementKind, BigDecimal> amounts, BigDecimal unclassified,
                          BigDecimal unclassifiedGross, BigDecimal corrections, BigDecimal closing,
                          BigDecimal difference)
    {
        public Summary
        {
            amounts = Map.copyOf(amounts);
        }

        public BigDecimal amount(NmrMovementKind kind)
        {
            return amounts.getOrDefault(kind, ZERO);
        }
    }

    public record Result(LocalDate start, LocalDate end, NmrLiabilityQueryService.Result opening,
                         NmrLiabilityQueryService.Result closing, List<Summary> summaries, List<Movement> movements)
    {
        public Result
        {
            summaries = List.copyOf(summaries);
            movements = List.copyOf(movements);
        }

        public boolean ready()
        {
            return opening.ready() && closing.ready() && summaries.stream()
                    .allMatch(s -> s.difference().signum() == 0 && s.unclassifiedGross().signum() == 0);
        }
    }

    static Result query(EntityManager em, String companyCode, LocalDate start, LocalDate end)
    {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start) || start.equals(LocalDate.MIN))
        {
            throw new IllegalArgumentException("NMR period requires an ordered range with a representable preceding date.");
        }
        var opening = NmrLiabilityQueryService.query(em, companyCode, start.minusDays(1));
        var closing = NmrLiabilityQueryService.query(em, companyCode, end);
        Map<UUID, SupplementalOpenItemQueryService.Row> items = new LinkedHashMap<>();
        Map<UUID, BigDecimal> initial = new LinkedHashMap<>();
        Map<UUID, BigDecimal> finalAmounts = new LinkedHashMap<>();
        for (var row : opening.items())
        {
            items.put(row.itemId(), row); initial.put(row.itemId(), row.openBalance());
        }
        for (var row : closing.items())
        {
            items.put(row.itemId(), row); finalAmounts.put(row.itemId(), row.openBalance());
        }
        List<Movement> movements = new ArrayList<>();
        for (var effect : SupplementalOpenItemQueryService.nmrEffects(em, companyCode, start, end))
        {
            var line = effect.allocation();
            BigDecimal attributed = line.getAmount().multiply(BigDecimal.valueOf(effect.direction()));
            BigDecimal change = line.getItemEffect() == SupplementalItemEffect.INCREASE ? attributed : attributed.negate();
            var merchant = line.getTxnSplit().getMerchant();
            movements.add(new Movement(line.getItemId(), effect.date(), effect.transactionId(), line.getTxn().getId(),
                    line.getNmrMovement(), change,
                    line.getNmrMovement() == NmrMovementKind.ADJUSTMENT ? change : attributed,
                    line.getNmrEvidenceReference(), merchant == null ? null : merchant.getId(), merchant == null ? null : merchant.getName(),
                    effect.transactionId() != line.getTxn().getId()));
        }
        List<Summary> summaries = new ArrayList<>();
        for (var entry : items.entrySet())
        {
            var itemMovements = movements.stream().filter(m -> m.itemId().equals(entry.getKey())).toList();
            Map<NmrMovementKind, BigDecimal> classified = new EnumMap<>(NmrMovementKind.class);
            BigDecimal unknown = ZERO, gross = ZERO, corrections = ZERO, delta = ZERO;
            for (var movement : itemMovements)
            {
                delta = delta.add(movement.change());
                if (movement.kind() == null)
                {
                    unknown = unknown.add(movement.change()); gross = gross.add(movement.change().abs());
                }
                else
                {
                    classified.merge(movement.kind(), movement.attributedAmount(), BigDecimal::add);
                }
                if (movement.correction())
                {
                    corrections = corrections.add(movement.change());
                }
            }
            BigDecimal before = initial.getOrDefault(entry.getKey(), ZERO);
            BigDecimal after = finalAmounts.getOrDefault(entry.getKey(), ZERO);
            summaries.add(new Summary(entry.getValue(), before, classified, unknown, gross, corrections, after,
                    after.subtract(before.add(delta))));
        }
        summaries.sort(java.util.Comparator.comparing((Summary s) -> s.item().openingDate()).thenComparing(s -> s.item().itemId()));
        return new Result(start, end, opening, closing, summaries, movements);
    }
}
