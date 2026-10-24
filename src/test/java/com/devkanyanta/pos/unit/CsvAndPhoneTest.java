package com.devkanyanta.pos.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.devkanyanta.pos.shared.csv.CsvParser;
import com.devkanyanta.pos.shared.exception.BadRequestException;
import com.devkanyanta.pos.shared.pdf.SimplePdfWriter;
import com.devkanyanta.pos.shared.util.PhoneNumbers;

class CsvAndPhoneTest {

    @Test
    void parsesQuotedFieldsBomCrlfAndBlankLines() {
        List<List<String>> rows = CsvParser.parse("﻿name,price\r\n\"Sugar, \"\"white\"\"\",45.50\r\n\r\n\"multi\nline\",1\n");

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsExactly("name", "price");
        assertThat(rows.get(1)).containsExactly("Sugar, \"white\"", "45.50");
        assertThat(rows.get(2).get(0)).isEqualTo("multi\nline");
    }

    @Test
    void detectsSemicolonDelimiterAndRejectsUnterminatedQuotes() {
        assertThat(CsvParser.parse("a;b\n1;2").get(1)).containsExactly("1", "2");
        assertThatThrownBy(() -> CsvParser.parse("a,b\n\"oops,1")).isInstanceOf(BadRequestException.class);
        assertThat(CsvParser.escape("a,b")).isEqualTo("\"a,b\"");
    }

    @Test
    void normalisesZambianPhoneNumbers() {
        assertThat(PhoneNumbers.normalize("0977 123 456", "260")).isEqualTo("260977123456");
        assertThat(PhoneNumbers.normalize("+260-97-7123456", "260")).isEqualTo("260977123456");
        assertThat(PhoneNumbers.normalize("00260977123456", "260")).isEqualTo("260977123456");
        assertThatThrownBy(() -> PhoneNumbers.normalize("12", "260")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void writesAValidMultiPagePdf() {
        List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < 150; i++) {
            lines.add("Line " + i + " (with parentheses) \\ backslash");
        }
        String pdf = new String(SimplePdfWriter.renderA4("Test", lines), StandardCharsets.ISO_8859_1);

        assertThat(pdf).startsWith("%PDF-1.4").endsWith("%%EOF\n").contains("/Count 3").contains("\\(with parentheses\\)");
        int xref = Integer.parseInt(pdf.substring(pdf.lastIndexOf("startxref") + 10, pdf.lastIndexOf("%%EOF")).trim());
        assertThat(pdf.substring(xref)).startsWith("xref");
    }
}
