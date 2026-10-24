package com.devkanyanta.pos.shared.pdf;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal PDF 1.4 generator for monospaced text documents (receipts, Z-reports). Uses the
 * built-in Courier font, so no font embedding or external library is required. Long documents
 * are split over multiple pages automatically.
 */
public final class SimplePdfWriter {

    private static final int FONT_SIZE = 9;

    private static final int LEADING = 12;

    private static final int MARGIN = 36;

    private SimplePdfWriter() {
    }

    /**
     * @param pageWidthPt  page width in points (e.g. 595 for A4, 227 for 80mm thermal paper)
     * @param pageHeightPt page height in points (e.g. 842 for A4)
     */
    public static byte[] render(String title, List<String> lines, int pageWidthPt, int pageHeightPt) {

        int linesPerPage = Math.max(1, (pageHeightPt - 2 * MARGIN) / LEADING);
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < Math.max(lines.size(), 1); i += linesPerPage) {
            pages.add(lines.isEmpty() ? List.of() : lines.subList(i, Math.min(lines.size(), i + linesPerPage)));
        }

        List<String> objects = new ArrayList<>();
        // 1: catalog, 2: pages, 3: font, 4: info, then page/content pairs.
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");

        StringBuilder kids = new StringBuilder();
        for (int p = 0; p < pages.size(); p++) {
            kids.append(5 + p * 2).append(" 0 R ");
        }
        objects.add("<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + pages.size() + " >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>");
        objects.add("<< /Title (" + escape(title) + ") /Producer (POS Backend) >>");

        for (int p = 0; p < pages.size(); p++) {
            int contentObj = 6 + p * 2;
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + pageWidthPt + " " + pageHeightPt + "] "
                    + "/Resources << /Font << /F1 3 0 R >> >> /Contents " + contentObj + " 0 R >>");

            StringBuilder stream = new StringBuilder();
            stream.append("BT /F1 ").append(FONT_SIZE).append(" Tf ").append(LEADING).append(" TL ")
                    .append(MARGIN).append(' ').append(pageHeightPt - MARGIN).append(" Td\n");
            for (String line : pages.get(p)) {
                stream.append('(').append(escape(line)).append(") Tj T*\n");
            }
            stream.append("ET");

            byte[] content = stream.toString().getBytes(StandardCharsets.ISO_8859_1);
            objects.add("<< /Length " + content.length + " >>\nstream\n" + stream + "\nendstream");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n%âãÏÓ\n");

        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }

        int xref = out.size();
        StringBuilder trailer = new StringBuilder();
        trailer.append("xref\n0 ").append(objects.size() + 1).append('\n');
        trailer.append("0000000000 65535 f \n");
        for (int offset : offsets) {
            trailer.append(String.format("%010d 00000 n \n", offset));
        }
        trailer.append("trailer\n<< /Size ").append(objects.size() + 1)
                .append(" /Root 1 0 R /Info 4 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        write(out, trailer.toString());

        return out.toByteArray();
    }

    public static byte[] renderA4(String title, List<String> lines) {
        return render(title, lines, 595, 842);
    }

    private static void write(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
        out.write(bytes, 0, bytes.length);
    }

    private static String escape(String text) {

        if (text == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '(' -> sb.append("\\(");
                case ')' -> sb.append("\\)");
                case '\r', '\n', '\t' -> sb.append(' ');
                default -> sb.append(c <= 0xFF ? c : '?');
            }
        }
        return sb.toString();
    }
}
