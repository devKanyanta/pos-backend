package com.devkanyanta.pos.shared.csv;

import java.util.ArrayList;
import java.util.List;

import com.devkanyanta.pos.shared.exception.BadRequestException;

/**
 * Small RFC 4180 CSV parser: comma (or semicolon) separated, double-quoted fields with
 * {@code ""} escapes, quoted line breaks, CRLF/LF line endings and an optional UTF-8 BOM.
 */
public final class CsvParser {

    private CsvParser() {
    }

    public static List<List<String>> parse(String content) {

        if (content == null) {
            return List.of();
        }

        if (!content.isEmpty() && content.charAt(0) == '﻿') {
            content = content.substring(1);
        }

        char delimiter = detectDelimiter(content);

        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }

            if (c == '"' && !fieldStarted) {
                inQuotes = true;
                fieldStarted = true;
            } else if (c == delimiter) {
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                addRow(rows, row);
                row = new ArrayList<>();
            } else {
                field.append(c);
                fieldStarted = true;
            }
        }

        if (inQuotes) {
            throw new BadRequestException("CSV has an unterminated quoted field");
        }

        if (fieldStarted || field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            addRow(rows, row);
        }

        return rows;
    }

    /** Quotes a value for CSV output when needed. */
    public static String escape(Object value) {

        if (value == null) {
            return "";
        }

        String text = value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static void addRow(List<List<String>> rows, List<String> row) {
        boolean blank = row.stream().allMatch(value -> value.isBlank());
        if (!blank) {
            rows.add(row);
        }
    }

    private static char detectDelimiter(String content) {
        int end = content.indexOf('\n');
        String header = end >= 0 ? content.substring(0, end) : content;
        long commas = header.chars().filter(ch -> ch == ',').count();
        long semicolons = header.chars().filter(ch -> ch == ';').count();
        return semicolons > commas ? ';' : ',';
    }
}
