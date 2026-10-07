package com.examplatform.domain.service.exportacio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Escriptor mínim de fitxers .xlsx (Office Open XML), sense dependències. Cada full té una capçalera en
 * negreta amb la fila congelada. Els textos s'escriuen com a cadenes en línia: Excel mai els interpreta
 * com a fórmules, així que el que escrigui un alumne («=HYPERLINK(…)») es queda com a text.
 */
public final class XlsxWriter {

    /** Màxim de caràcters d'una cel·la a Excel és 32.767. */
    static final int MAX_CEL = 32_000;

    private static final int ESTIL_NORMAL = 0, ESTIL_CAPCALERA = 1, ESTIL_ENVOLTA = 2, ESTIL_NOMBRE = 3;

    /**
     * @param amples caràcters d'ample de cada columna (opcional)
     * @param envolta índexs (des de 0) de les columnes amb text que salta de línia
     * Els valors de les files poden ser String, Number o null (cel·la buida).
     */
    public record Full(String nom, List<String> capcalera, List<List<Object>> files, List<Integer> amples,
                       Set<Integer> envolta) {
        public Full(String nom, List<String> capcalera, List<List<Object>> files) {
            this(nom, capcalera, files, List.of(), Set.of());
        }
    }

    private XlsxWriter() {}

    public static byte[] escriu(List<Full> fulls) throws IOException {
        if (fulls.isEmpty()) throw new IllegalArgumentException("Cal almenys un full");
        List<String> noms = nomsUnics(fulls);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            part(zip, "[Content_Types].xml", contentTypes(fulls.size()));
            part(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""");
            part(zip, "xl/workbook.xml", workbook(noms));
            part(zip, "xl/_rels/workbook.xml.rels", relsLlibre(fulls.size()));
            part(zip, "xl/styles.xml", ESTILS);
            for (int i = 0; i < fulls.size(); i++) part(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", full(fulls.get(i)));
        }
        return out.toByteArray();
    }

    // ── Parts del paquet ──────────────────────────────────────────────────────

    private static void part(ZipOutputStream zip, String nom, String contingut) throws IOException {
        zip.putNextEntry(new ZipEntry(nom));
        zip.write(contingut.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes(int fulls) {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""");
        for (int i = 1; i <= fulls; i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return sb.append("</Types>").toString();
    }

    private static String workbook(List<String> noms) {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""");
        for (int i = 0; i < noms.size(); i++) {
            sb.append("<sheet name=\"").append(escapa(noms.get(i))).append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return sb.append("</sheets></workbook>").toString();
    }

    private static String relsLlibre(int fulls) {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""");
        for (int i = 1; i <= fulls; i++) {
            sb.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i).append(".xml\"/>");
        }
        sb.append("<Relationship Id=\"rId").append(fulls + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return sb.append("</Relationships>").toString();
    }

    private static String full(Full f) {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""");
        if (!f.amples().isEmpty()) {
            sb.append("<cols>");
            for (int i = 0; i < f.amples().size(); i++) {
                sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"")
                        .append(f.amples().get(i)).append("\" customWidth=\"1\"/>");
            }
            sb.append("</cols>");
        }
        sb.append("<sheetData>");
        fila(sb, 1, new ArrayList<Object>(f.capcalera()), true, f.envolta());
        for (int r = 0; r < f.files().size(); r++) fila(sb, r + 2, f.files().get(r), false, f.envolta());
        return sb.append("</sheetData></worksheet>").toString();
    }

    private static void fila(StringBuilder sb, int numero, List<Object> cel_les, boolean capcalera, Set<Integer> envolta) {
        sb.append("<row r=\"").append(numero).append("\">");
        for (int c = 0; c < cel_les.size(); c++) {
            Object v = cel_les.get(c);
            if (v == null) continue;
            String ref = columna(c) + numero;
            if (v instanceof Number n) {
                BigDecimal b = v instanceof BigDecimal bd ? bd : new BigDecimal(n.toString());
                sb.append("<c r=\"").append(ref).append("\" s=\"").append(capcalera ? ESTIL_CAPCALERA : ESTIL_NOMBRE).append("\"><v>")
                        .append(b.setScale(Math.max(0, Math.min(b.scale(), 4)), RoundingMode.HALF_UP).toPlainString()).append("</v></c>");
            } else {
                String text = netejaXml(String.valueOf(v));
                if (text.length() > MAX_CEL) text = text.substring(0, MAX_CEL) + "…";
                int estil = capcalera ? ESTIL_CAPCALERA : envolta.contains(c) ? ESTIL_ENVOLTA : ESTIL_NORMAL;
                sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\" s=\"").append(estil)
                        .append("\"><is><t xml:space=\"preserve\">").append(escapa(text)).append("</t></is></c>");
            }
        }
        sb.append("</row>");
    }

    // ── Ajudes ────────────────────────────────────────────────────────────────

    /** Lletra de la columna: 0 → A, 25 → Z, 26 → AA. */
    static String columna(int index) {
        StringBuilder sb = new StringBuilder();
        for (int i = index; i >= 0; i = i / 26 - 1) sb.insert(0, (char) ('A' + i % 26));
        return sb.toString();
    }

    /** Treu els caràcters que XML 1.0 no admet (control, etc.): un d'sol faria el fitxer il·legible. */
    static String netejaXml(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            boolean valid = cp == 0x9 || cp == 0xA || cp == 0xD || (cp >= 0x20 && cp <= 0xD7FF)
                    || (cp >= 0xE000 && cp <= 0xFFFD) || (cp >= 0x10000 && cp <= 0x10FFFF);
            if (valid) sb.appendCodePoint(cp);
        });
        return sb.toString();
    }

    static String escapa(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Noms de full vàlids a Excel (≤ 31 caràcters, sense [ ] : * ? / \) i únics. */
    static List<String> nomsUnics(List<Full> fulls) {
        List<String> res = new ArrayList<>();
        Set<String> usats = new HashSet<>();
        for (Full f : fulls) {
            String base = f.nom().replaceAll("[\\[\\]:*?/\\\\]", "_").strip();
            if (base.isEmpty()) base = "Full";
            if (base.length() > 31) base = base.substring(0, 31);
            String nom = base;
            for (int n = 2; !usats.add(nom.toLowerCase(Locale.ROOT)); n++) {
                String suf = " " + n;
                nom = base.substring(0, Math.min(base.length(), 31 - suf.length())) + suf;
            }
            res.add(nom);
        }
        return res;
    }

    private static final String ESTILS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFEFEFEF"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf><xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""";
}
