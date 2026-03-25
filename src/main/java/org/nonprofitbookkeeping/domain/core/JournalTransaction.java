
package org.nonprofitbookkeeping.domain.core;

import org.nonprofitbookkeeping.domain.timing.TransactionTiming;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable accounting transaction as the source of truth for all derived schedules.
 */
public record JournalTransaction(
	UUID transactionId,
	String groupCode,
	LocalDate postedOn,
	String memo,
	TransactionTiming timing,
	List<PostingLine> lines,
	UUID reversedTransactionId)
{
	public JournalTransaction
	{
		transactionId = Objects.requireNonNull(transactionId, "transactionId");
		reversedTransactionId = reversedTransactionId;
		groupCode = requireText(groupCode, "groupCode");
		postedOn = Objects.requireNonNull(postedOn, "postedOn");
		memo = requireText(memo, "memo");
		timing = Objects.requireNonNull(timing, "timing");
		lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
		
		if (lines.size() < 2)
		{
			throw new IllegalArgumentException(
				"journal transaction must contain at least two lines");
		}
		
		if (!isBalanced(lines))
		{
			throw new IllegalArgumentException(
				"journal transaction lines must balance debits and credits");
		}
		
	}
	
	public static JournalTransaction create(String groupCode,
		LocalDate postedOn, String memo,
		TransactionTiming timing, List<PostingLine> lines)
	{
		return new JournalTransaction(UUID.randomUUID(), groupCode, postedOn,
			memo, timing, lines, null);
		
	}
	
	public JournalTransaction reverseOn(LocalDate reversalDate,
		String reversalMemo)
	{
		List<PostingLine> reversedLines = lines.stream()
			.map(line -> new PostingLine(
				line.accountCode(),
				line.fundCode(),
				line.side() == EntrySide.DEBIT ? EntrySide.CREDIT :
					EntrySide.DEBIT,
				line.amount()))
			.toList();
		
		return new JournalTransaction(
			UUID.randomUUID(),
			groupCode,
			Objects.requireNonNull(reversalDate, "reversalDate"),
			requireText(reversalMemo, "reversalMemo"),
			timing,
			reversedLines,
			transactionId);
		
	}
	
	private static boolean isBalanced(List<PostingLine> lines)
	{
		BigDecimal debitTotal = lines.stream()
			.filter(line -> line.side() == EntrySide.DEBIT)
			.map(PostingLine::amount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		
		BigDecimal creditTotal = lines.stream()
			.filter(line -> line.side() == EntrySide.CREDIT)
			.map(PostingLine::amount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		
		return debitTotal.compareTo(creditTotal) == 0;
		
	}
	
	private static String requireText(String value, String fieldName)
	{
		String normalized = Objects.requireNonNull(value, fieldName).trim();
		
		if (normalized.isEmpty())
		{
			throw new IllegalArgumentException(fieldName + " cannot be blank");
		}
		
		return normalized;
		
	}
	
}
