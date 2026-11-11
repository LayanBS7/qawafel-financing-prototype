package com.qawafel.financing.ingest;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an invoice export (.xlsx or .xls) into {@link RawInvoiceRow}s.
 * <p>
 * This class only deals with the file format: finding the sheet, mapping headers to columns and
 * converting cells. Business rules (duplicates, VAT, period...) live in {@link InvoiceValidator}.
 */
@Component
public class ExcelInvoiceParser {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy"));

    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    /** Header must be within the first rows; some exports put a title above the table. */
    private static final int HEADER_SEARCH_ROWS = 10;

    private final DataFormatter formatter = new DataFormatter();

    public ParsedWorkbook parse(InputStream in) {
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = findInvoiceSheet(wb);
            HeaderMatch header = findHeader(sheet);
            List<RawInvoiceRow> rows = readRows(sheet, header);
            if (rows.isEmpty()) {
                throw new InvalidFileException("The invoice sheet has a header row but no invoices.");
            }
            return readCompanyInfo(wb, rows);
        } catch (IOException | org.apache.poi.EmptyFileException e) {
            throw new InvalidFileException("This doesn't look like an Excel file (.xlsx or .xls).", e);
        }
    }

    // ---------------------------------------------------------------- sheet & header

    private Sheet findInvoiceSheet(Workbook wb) {
        for (Sheet s : wb) {
            if (s.getSheetName().toLowerCase().contains("invoice")) {
                return s;
            }
        }
        if (wb.getNumberOfSheets() == 0) {
            throw new InvalidFileException("The workbook has no sheets.");
        }
        return wb.getSheetAt(0);
    }

    private record HeaderMatch(int rowIndex, Map<Column, Integer> columns) {
    }

    private HeaderMatch findHeader(Sheet sheet) {
        HeaderMatch best = null;
        int last = Math.min(sheet.getLastRowNum(), HEADER_SEARCH_ROWS);
        for (int r = sheet.getFirstRowNum(); r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<Column, Integer> found = matchColumns(row);
            if (best == null || found.size() > best.columns().size()) {
                best = new HeaderMatch(r, found);
            }
        }
        if (best == null || best.columns().isEmpty()) {
            throw new InvalidFileException("Couldn't find a header row in sheet '" + sheet.getSheetName() + "'.");
        }
        Map<Column, Integer> found = best.columns();
        List<String> missing = Arrays.stream(Column.values())
                .filter(Column::required)
                .filter(c -> !found.containsKey(c))
                .map(Enum::name)
                .toList();
        if (!missing.isEmpty()) {
            throw new InvalidFileException("Required columns are missing: " + String.join(", ", missing)
                    + ". Expected headers like 'Invoice Number', 'Invoice Date', 'Customer Name', "
                    + "'Subtotal (SAR)' and 'Status'.");
        }
        return best;
    }

    /** Exact alias match first, so "Customer" doesn't steal the "Customer Name" column. */
    private Map<Column, Integer> matchColumns(Row row) {
        Map<String, Integer> headers = new LinkedHashMap<>();
        for (Cell cell : row) {
            String h = Column.normalise(formatter.formatCellValue(cell));
            if (!h.isEmpty()) headers.putIfAbsent(h, cell.getColumnIndex());
        }
        Map<Column, Integer> result = new EnumMap<>(Column.class);
        Set<Integer> taken = new HashSet<>();
        for (Column col : Column.values()) {
            for (String alias : col.aliases()) {
                Integer idx = headers.get(alias);
                if (idx != null && !taken.contains(idx)) {
                    result.put(col, idx);
                    taken.add(idx);
                    break;
                }
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- rows

    private List<RawInvoiceRow> readRows(Sheet sheet, HeaderMatch header) {
        List<RawInvoiceRow> rows = new ArrayList<>();
        for (int r = header.rowIndex() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || isBlank(row)) continue;
            rows.add(readRow(row, header.columns()));
        }
        return rows;
    }

    private RawInvoiceRow readRow(Row row, Map<Column, Integer> cols) {
        Map<Column, String> bad = new EnumMap<>(Column.class);
        CellReader rd = new CellReader(row, cols, bad);
        return new RawInvoiceRow(
                row.getRowNum() + 1, // 1-based, as shown in Excel
                rd.text(Column.INVOICE_NUMBER),
                rd.date(Column.INVOICE_DATE),
                rd.date(Column.DUE_DATE),
                rd.text(Column.CUSTOMER_ID),
                rd.text(Column.CUSTOMER_NAME),
                rd.text(Column.CUSTOMER_VAT),
                rd.text(Column.SEGMENT),
                rd.text(Column.CITY),
                rd.text(Column.CATEGORY),
                rd.integer(Column.PAYMENT_TERMS),
                rd.money(Column.SUBTOTAL),
                rd.money(Column.VAT),
                rd.money(Column.TOTAL),
                rd.text(Column.STATUS),
                rd.date(Column.PAID_DATE),
                rd.money(Column.AMOUNT_PAID),
                rd.text(Column.NOTES),
                bad);
    }

    private boolean isBlank(Row row) {
        for (Cell c : row) {
            if (!formatter.formatCellValue(c).isBlank()) return false;
        }
        return true;
    }

    /** Converts cells to Java types; records anything it can't convert instead of throwing. */
    private final class CellReader {
        private final Row row;
        private final Map<Column, Integer> cols;
        private final Map<Column, String> bad;

        CellReader(Row row, Map<Column, Integer> cols, Map<Column, String> bad) {
            this.row = row;
            this.cols = cols;
            this.bad = bad;
        }

        private Cell cell(Column c) {
            Integer idx = cols.get(c);
            if (idx == null) return null;
            Cell cell = row.getCell(idx);
            if (cell == null || cell.getCellType() == CellType.BLANK) return null;
            return cell;
        }

        String text(Column c) {
            Cell cell = cell(c);
            if (cell == null) return null;
            String v = formatter.formatCellValue(cell).trim();
            return v.isEmpty() ? null : v;
        }

        LocalDate date(Column c) {
            Cell cell = cell(c);
            if (cell == null) return null;
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            if (cell.getCellType() == CellType.NUMERIC) {
                // A date stored as a plain Excel serial number.
                return DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate();
            }
            String s = text(c);
            if (s == null) return null;
            LocalDate d = parseDate(s);
            if (d == null) bad.put(c, s);
            return d;
        }

        BigDecimal money(Column c) {
            Cell cell = cell(c);
            if (cell == null) return null;
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
            }
            String s = text(c);
            if (s == null) return null;
            try {
                return new BigDecimal(s.replaceAll("[,\\sSARsar]", "")).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                bad.put(c, s);
                return null;
            }
        }

        Integer integer(Column c) {
            BigDecimal v = money(c);
            return v == null ? null : v.intValue();
        }
    }

    private static LocalDate parseDate(String s) {
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- company info

    private ParsedWorkbook readCompanyInfo(Workbook wb, List<RawInvoiceRow> rows) {
        Sheet info = null;
        for (Sheet s : wb) {
            String n = s.getSheetName().toLowerCase();
            if (n.contains("company") || n.contains("info")) {
                info = s;
                break;
            }
        }
        String company = null;
        LocalDate start = null, end = null, exported = null;
        if (info != null) {
            for (Row row : info) {
                String key = Column.normalise(formatter.formatCellValue(row.getCell(0)));
                Cell valueCell = row.getCell(1);
                String value = formatter.formatCellValue(valueCell).trim();
                switch (key) {
                    case "seller", "company", "companyname" -> company = value.isEmpty() ? null : value;
                    case "period" -> {
                        Matcher m = ISO_DATE.matcher(value);
                        if (m.find()) start = parseDate(m.group(1));
                        if (m.find()) end = parseDate(m.group(1));
                    }
                    case "exportedon", "exportdate", "asof" -> exported = readDate(valueCell, value);
                    default -> {
                        // other rows are informational
                    }
                }
            }
        }
        return new ParsedWorkbook(rows, company, start, end, exported);
    }

    private static LocalDate readDate(Cell cell, String text) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        Matcher m = ISO_DATE.matcher(text);
        return m.find() ? parseDate(m.group(1)) : parseDate(text);
    }
}
