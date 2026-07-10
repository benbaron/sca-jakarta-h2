package org.nonprofitbookkeeping.repository;

import org.nonprofitbookkeeping.model.CompanyUiPreferences;
import org.nonprofitbookkeeping.model.DateDisplayFormat;
import org.nonprofitbookkeeping.model.MoneyPrintFormat;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** JDBC/H2 implementation for company-specific display preferences and UI state. */
public final class JdbcCompanyUiPreferenceRepository implements CompanyUiPreferenceRepository
{
    private final DataSource dataSource;

    public JdbcCompanyUiPreferenceRepository(DataSource dataSource)
    {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<CompanyUiPreferences> findPreferences(String companyCode)
    {
        String sql = """
                SELECT currency_symbol, money_print_format, date_display_format
                  FROM company_ui_preference
                 WHERE company_code = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setString(1, companyCode);
            try (ResultSet result = statement.executeQuery())
            {
                if (!result.next())
                {
                    return Optional.empty();
                }
                return Optional.of(new CompanyUiPreferences(
                        result.getString("currency_symbol"),
                        enumValue(MoneyPrintFormat.class, result.getString("money_print_format"), MoneyPrintFormat.SYMBOL_PREFIX),
                        enumValue(DateDisplayFormat.class, result.getString("date_display_format"), DateDisplayFormat.MONTH_DAY_YEAR)));
            }
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not load company UI preferences for " + companyCode, ex);
        }
    }

    @Override
    public void savePreferences(String companyCode, CompanyUiPreferences preferences)
    {
        String sql = """
                MERGE INTO company_ui_preference
                (company_code, currency_symbol, money_print_format, date_display_format, updated_at)
                KEY(company_code)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setString(1, companyCode);
            statement.setString(2, preferences.currencySymbol());
            statement.setString(3, preferences.moneyPrintFormat().name());
            statement.setString(4, preferences.dateDisplayFormat().name());
            statement.executeUpdate();
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not save company UI preferences for " + companyCode, ex);
        }
    }

    @Override
    public Map<String, String> findStateByPrefix(String companyCode, String keyPrefix)
    {
        String sql = """
                SELECT state_key, state_value
                  FROM company_ui_state
                 WHERE company_code = ?
                   AND state_key LIKE ?
                 ORDER BY state_key
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setString(1, companyCode);
            statement.setString(2, keyPrefix + "%");
            try (ResultSet result = statement.executeQuery())
            {
                Map<String, String> values = new LinkedHashMap<>();
                while (result.next())
                {
                    values.put(result.getString("state_key"), result.getString("state_value"));
                }
                return Map.copyOf(values);
            }
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not load company UI state for " + companyCode, ex);
        }
    }

    @Override
    public void saveState(String companyCode, Map<String, String> values)
    {
        if (values == null || values.isEmpty())
        {
            return;
        }
        String sql = """
                MERGE INTO company_ui_state
                (company_code, state_key, state_value, updated_at)
                KEY(company_code, state_key)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql))
        {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try
            {
                for (Map.Entry<String, String> entry : values.entrySet())
                {
                    statement.setString(1, companyCode);
                    statement.setString(2, entry.getKey());
                    statement.setString(3, entry.getValue() == null ? "" : entry.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            }
            catch (SQLException ex)
            {
                connection.rollback();
                throw ex;
            }
            finally
            {
                connection.setAutoCommit(oldAutoCommit);
            }
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not save company UI state for " + companyCode, ex);
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback)
    {
        if (value == null || value.isBlank())
        {
            return fallback;
        }
        try
        {
            return Enum.valueOf(type, value);
        }
        catch (IllegalArgumentException ex)
        {
            return fallback;
        }
    }
}
