package org.nonprofitbookkeeping.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * XLSX adapter using Apache POI directly.
 */
public class PoiXlsxFinancialReportAdapter implements FinancialReportExportAdapter
{
    @Override
    public FinancialReportExportFormat format()
    {
        return FinancialReportExportFormat.XLSX;
    }

    @Override
    public byte[] render(String reportName, String textPreview, String csvBody)
    {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            Sheet sheet = workbook.createSheet("Report");
            List<List<String>> rows = parseCsv(csvBody == null ? "" : csvBody);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            int r = 0;
            for (List<String> values : rows)
            {
                Row row = sheet.createRow(r);
                for (int c = 0; c < values.size(); c++)
                {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(values.get(c));
                    if (r == 0)
                    {
                        cell.setCellStyle(headerStyle);
                    }
                }
                r++;
            }

            int maxCols = rows.stream().mapToInt(List::size).max().orElse(0);
            for (int c = 0; c < maxCols; c++)
            {
                sheet.autoSizeColumn(c);
            }

            workbook.write(out);
            return out.toByteArray();
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not build XLSX export via Apache POI.", ex);
        }
    }

    static List<List<String>> parseCsv(String csv)
    {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < csv.length(); i++)
        {
            char c = csv.charAt(i);
            if (quoted)
            {
                if (c == '"')
                {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"')
                    {
                        cell.append('"');
                        i++;
                    }
                    else
                    {
                        quoted = false;
                    }
                }
                else
                {
                    cell.append(c);
                }
            }
            else
            {
                if (c == '"')
                {
                    quoted = true;
                }
                else if (c == ',')
                {
                    row.add(cell.toString());
                    cell.setLength(0);
                }
                else if (c == '\n')
                {
                    row.add(cell.toString());
                    rows.add(row);
                    row = new ArrayList<>();
                    cell.setLength(0);
                }
                else if (c != '\r')
                {
                    cell.append(c);
                }
            }
        }

        if (cell.length() > 0 || !row.isEmpty())
        {
            row.add(cell.toString());
            rows.add(row);
        }

        return rows;
    }
}
