package org.nonprofitbookkeeping.repository;

import org.nonprofitbookkeeping.domain.core.EntrySide;
import org.nonprofitbookkeeping.domain.core.JournalTransaction;
import org.nonprofitbookkeeping.domain.core.PostingLine;
import org.nonprofitbookkeeping.domain.timing.TimingPosition;
import org.nonprofitbookkeeping.domain.timing.TransactionTiming;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation for append-only journal persistence.
 */
public class JdbcJournalTransactionRepository implements JournalTransactionRepository
{
    private final DataSource dataSource;

    public JdbcJournalTransactionRepository(DataSource dataSource)
    {
        this.dataSource = dataSource;
    }

    @Override
    public void append(JournalTransaction transaction)
    {
        try (Connection connection = dataSource.getConnection())
        {
            connection.setAutoCommit(false);
            try
            {
                insertHeader(connection, transaction);
                insertLines(connection, transaction);
                connection.commit();
            }
            catch (SQLException ex)
            {
                connection.rollback();
                throw ex;
            }
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not append journal transaction", ex);
        }
    }

    @Override
    public Optional<JournalTransaction> findById(UUID transactionId)
    {
        String sql = """
                SELECT t.id, t.group_code, t.posted_on, t.memo, t.bank_timing, t.budget_timing, t.reversed_transaction_id,
                       l.line_order, l.account_code, l.fund_code, l.entry_side, l.amount
                  FROM journal_transaction t
                  JOIN journal_posting_line l ON l.transaction_id = t.id
                 WHERE t.id = ?
                 ORDER BY l.line_order
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setObject(1, transactionId);
            try (ResultSet rs = ps.executeQuery())
            {
                return buildTransactions(rs).stream().findFirst();
            }
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not query journal transaction", ex);
        }
    }

    @Override
    public List<JournalTransaction> findByGroupAndDateRange(String groupCode, java.time.LocalDate fromDate, java.time.LocalDate toDate)
    {
        String sql = """
                SELECT t.id, t.group_code, t.posted_on, t.memo, t.bank_timing, t.budget_timing, t.reversed_transaction_id,
                       l.line_order, l.account_code, l.fund_code, l.entry_side, l.amount
                  FROM journal_transaction t
                  JOIN journal_posting_line l ON l.transaction_id = t.id
                 WHERE t.group_code = ?
                   AND t.posted_on BETWEEN ? AND ?
                 ORDER BY t.posted_on, t.id, l.line_order
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setString(1, groupCode);
            ps.setDate(2, Date.valueOf(fromDate));
            ps.setDate(3, Date.valueOf(toDate));

            try (ResultSet rs = ps.executeQuery())
            {
                return buildTransactions(rs);
            }
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not query journal transactions", ex);
        }
    }

    private void insertHeader(Connection connection, JournalTransaction transaction) throws SQLException
    {
        String sql = """
                INSERT INTO journal_transaction
                (id, group_code, posted_on, memo, bank_timing, budget_timing, reversed_transaction_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setObject(1, transaction.transactionId());
            ps.setString(2, transaction.groupCode());
            ps.setDate(3, Date.valueOf(transaction.postedOn()));
            ps.setString(4, transaction.memo());
            ps.setString(5, transaction.timing().bankTiming().name());
            ps.setString(6, transaction.timing().budgetTiming().name());
            ps.setObject(7, transaction.reversedTransactionId());
            ps.executeUpdate();
        }
    }

    private void insertLines(Connection connection, JournalTransaction transaction) throws SQLException
    {
        String sql = """
                INSERT INTO journal_posting_line
                (transaction_id, line_order, account_code, fund_code, entry_side, amount)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql))
        {
            for (int i = 0; i < transaction.lines().size(); i++)
            {
                PostingLine line = transaction.lines().get(i);
                ps.setObject(1, transaction.transactionId());
                ps.setInt(2, i);
                ps.setString(3, line.accountCode());
                ps.setString(4, line.fundCode());
                ps.setString(5, line.side().name());
                ps.setBigDecimal(6, line.amount());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private List<JournalTransaction> buildTransactions(ResultSet rs) throws SQLException
    {
        Map<UUID, TxnAccumulator> byId = new LinkedHashMap<>();
        while (rs.next())
        {
            UUID id = rs.getObject("id", UUID.class);
            TxnAccumulator accumulator = byId.get(id);
            if (accumulator == null)
            {
                accumulator = new TxnAccumulator(
                        id,
                        rsGetString(rs, "group_code"),
                        rs.getDate("posted_on").toLocalDate(),
                        rsGetString(rs, "memo"),
                        TransactionTiming.of(
                                TimingPosition.valueOf(rsGetString(rs, "bank_timing")),
                                TimingPosition.valueOf(rsGetString(rs, "budget_timing"))),
                        rs.getObject("reversed_transaction_id", UUID.class)
                );
                byId.put(id, accumulator);
            }

            accumulator.lines.add(new LineAccumulator(
                    rs.getInt("line_order"),
                    new PostingLine(
                            rsGetString(rs, "account_code"),
                            rsGetString(rs, "fund_code"),
                            EntrySide.valueOf(rsGetString(rs, "entry_side")),
                            rs.getBigDecimal("amount"))));
        }

        List<JournalTransaction> transactions = new ArrayList<>();
        for (TxnAccumulator value : byId.values())
        {
            value.lines.sort(Comparator.comparingInt(line -> line.order));
            List<PostingLine> lines = value.lines.stream().map(line -> line.line).toList();
            transactions.add(new JournalTransaction(value.id, value.groupCode, value.postedOn, value.memo, value.timing, lines, value.reversedTransactionId));
        }
        return transactions;
    }

    private String rsGetString(ResultSet rs, String column) throws SQLException
    {
        String value = rs.getString(column);
        if (value == null)
        {
            throw new IllegalStateException("Unexpected null column: " + column);
        }
        return value;
    }

    private static final class TxnAccumulator
    {
        private final UUID id;
        private final String groupCode;
        private final java.time.LocalDate postedOn;
        private final String memo;
        private final TransactionTiming timing;
        private final UUID reversedTransactionId;
        private final List<LineAccumulator> lines = new ArrayList<>();

        private TxnAccumulator(UUID id, String groupCode, java.time.LocalDate postedOn, String memo,
                               TransactionTiming timing, UUID reversedTransactionId)
        {
            this.id = id;
            this.groupCode = groupCode;
            this.postedOn = postedOn;
            this.memo = memo;
            this.timing = timing;
            this.reversedTransactionId = reversedTransactionId;
        }
    }

    private record LineAccumulator(int order, PostingLine line)
    {
    }
}
