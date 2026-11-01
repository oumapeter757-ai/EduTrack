package com.peterscode.edutrack.fees.service;

import com.peterscode.edutrack.fees.exception.FeesRuleException;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Utility for generating CSV content with formula injection protection.
 * <p>
 * CSV formula injection occurs when a cell starts with =, +, -, or @ —
 * these can be interpreted as formulas by Excel/Google Sheets.
 * This utility escapes such cells by prefixing them with a single quote (').
 * </p>
 */
public final class CsvExporter {

    private CsvExporter() {}

    /**
     * Maximum number of rows allowed in a CSV export to prevent DoS.
     */
    public static final int MAX_ROWS = 10_000;

    /**
     * Escape a CSV cell value to prevent formula injection.
     * If the value starts with =, +, -, or @, prefix it with a single quote.
     */
    public static String escapeCell(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("=") || trimmed.startsWith("+")
                || trimmed.startsWith("-") || trimmed.startsWith("@")) {
            return "'" + value;
        }
        return value;
    }

    /**
     * Escape a CSV cell value from a number to prevent formula injection.
     */
    public static String escapeCell(Number value) {
        if (value == null) {
            return "";
        }
        String str = value.toString();
        return escapeCell(str);
    }

    /**
     * Generate CSV content from headers and rows.
     *
     * @param headers column headers
     * @param rows    data rows, each row is a list of string values
     * @return CSV string
     */
    public static String toCsv(List<String> headers, List<List<String>> rows) {
        if (rows.size() > MAX_ROWS) {
            throw new FeesRuleException(
                    "INVALID_AMOUNT", BAD_REQUEST,
                    "Export exceeds maximum rows: " + MAX_ROWS);
        }

        StringWriter writer = new StringWriter();

        // Headers
        writer.write(escapeAndJoin(headers));
        writer.write("\n");

        // Rows
        for (List<String> row : rows) {
            writer.write(escapeAndJoin(row));
            writer.write("\n");
        }

        return writer.toString();
    }

    /**
     * Generate CSV content with a BOM for Excel compatibility.
     */
    public static String toCsvWithBom(List<String> headers, List<List<String>> rows) {
        return "\uFEFF" + toCsv(headers, rows);
    }

    private static String escapeAndJoin(List<String> values) {
        return values.stream()
                .map(CsvExporter::escapeAndQuote)
                .collect(Collectors.joining(","));
    }

    private static String escapeAndQuote(String value) {
        if (value == null) {
            return "\"\"";
        }
        String escaped = escapeCell(value);
        // Quote if contains comma, quote, or newline
        if (escaped.contains(",") || escaped.contains("\"")
                || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped.replace("\"", "\"\"") + "\"";
        }
        return escaped;
    }
}
