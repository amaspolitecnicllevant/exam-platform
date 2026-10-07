package com.examplatform.domain.service.exportacio;

import com.examplatform.domain.model.Answer;
import com.examplatform.domain.model.Question;
import com.examplatform.domain.service.Puntuacio;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.*;

/**
 * Notes d'un examen en CSV pensat per a Excel en català: separador «;», coma decimal i BOM UTF-8.
 * Les notes són les de {@link Puntuacio} (les respostes sense nota compten 0 i es compten a «pendents»).
 */
public final class ExportacioNotes {

    /** Nom de la columna per a les preguntes sense resultat d'aprenentatge. */
    public static final String SENSE_RA = "(sense RA)";

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder().setDelimiter(';').build();

    private ExportacioNotes() {}

    /** Capçalera d'una pregunta: «P3 (2 pts)». */
    public static String capcaleraPregunta(Question q) {
        return "P" + q.getOrdre() + " (" + CsvSegur.curt(q.getPunts()) + " pts)";
    }

    /** Respostes d'una sessió que el professor encara no ha qualificat. */
    static int pendents(DadesExamen d, DadesExamen.Alumne a) {
        int n = 0;
        for (Question q : d.preguntes()) {
            Answer r = a.respostes().get(q.getId());
            if (r != null && Puntuacio.punts(q, r) == null) n++;
        }
        return n;
    }

    /** Punts d'una pregunta per a un alumne; null si hi ha resposta però encara no té nota. */
    static BigDecimal punts(DadesExamen.Alumne a, Question q) {
        return Puntuacio.punts(q, a.respostes().get(q.getId()));
    }

    /** Una fila per alumne: estat, nota sobre 10, punts i els punts de cada pregunta. */
    public static String notesCsv(DadesExamen d) {
        StringWriter sw = new StringWriter();
        sw.write(CsvSegur.BOM);
        try (CSVPrinter csv = new CSVPrinter(sw, FORMAT)) {
            List<String> cap = new ArrayList<>(List.of("alumne", "email", "estat", "nota_sobre_10", "punts",
                    "punts_maxims", "pendents_de_revisar"));
            d.preguntes().forEach(q -> cap.add(capcaleraPregunta(q)));
            csv.printRecord(cap);
            BigDecimal maxim = Puntuacio.maxim(d.preguntes());
            for (DadesExamen.Alumne a : d.alumnes()) {
                boolean comencat = a.sessio().getStartedAt() != null || a.entregat();
                List<String> fila = new ArrayList<>();
                fila.add(CsvSegur.cel(a.nom()));
                fila.add(CsvSegur.cel(a.email()));
                fila.add(a.estat());
                fila.add(comencat ? CsvSegur.num(Puntuacio.notaSobreDeu(d.preguntes(), a.respostes())) : "");
                fila.add(comencat ? CsvSegur.num(Puntuacio.total(d.preguntes(), a.respostes())) : "");
                fila.add(CsvSegur.num(maxim));
                fila.add(comencat ? String.valueOf(pendents(d, a)) : "");
                for (Question q : d.preguntes()) {
                    BigDecimal p = punts(a, q);
                    fila.add(!comencat ? "" : a.respostes().get(q.getId()) != null && p == null ? "pendent" : CsvSegur.num(p));
                }
                csv.printRecord(fila);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return sw.toString();
    }

    /** Resultats d'aprenentatge presents a l'examen, en ordre natural (RA1, RA2, RA10…), i «(sense RA)» al final. */
    public static List<String> resultatsAprenentatge(DadesExamen d) {
        TreeSet<String> ras = new TreeSet<>(NATURAL);
        boolean senseRa = false;
        for (Question q : d.preguntes()) {
            if (q.getRa() == null || q.getRa().isBlank()) senseRa = true;
            else ras.add(q.getRa().strip());
        }
        List<String> res = new ArrayList<>(ras);
        if (senseRa) res.add(SENSE_RA);
        return res;
    }

    /** Nota sobre 10 d'un alumne en un RA (null si el RA no té punts). Les respostes sense nota compten 0. */
    public static BigDecimal notaRa(DadesExamen d, DadesExamen.Alumne a, String ra) {
        List<Question> delRa = d.preguntes().stream()
                .filter(q -> ra.equals(SENSE_RA) ? (q.getRa() == null || q.getRa().isBlank()) : ra.equals(q.getRa() == null ? null : q.getRa().strip()))
                .toList();
        return Puntuacio.notaSobreDeu(delRa, a.respostes());
    }

    /** Una fila per alumne entregat: nota sobre 10 de cada resultat d'aprenentatge i la global. */
    public static String raCsv(DadesExamen d) {
        List<String> ras = resultatsAprenentatge(d);
        StringWriter sw = new StringWriter();
        sw.write(CsvSegur.BOM);
        try (CSVPrinter csv = new CSVPrinter(sw, FORMAT)) {
            List<String> cap = new ArrayList<>(List.of("alumne", "email"));
            cap.addAll(ras);
            cap.add("nota_global");
            csv.printRecord(cap);
            for (DadesExamen.Alumne a : d.entregats()) {
                List<String> fila = new ArrayList<>(List.of(CsvSegur.cel(a.nom()), CsvSegur.cel(a.email())));
                for (String ra : ras) fila.add(CsvSegur.num(notaRa(d, a, ra)));
                fila.add(CsvSegur.num(Puntuacio.notaSobreDeu(d.preguntes(), a.respostes())));
                csv.printRecord(fila);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return sw.toString();
    }

    /** Ordre natural: RA2 abans que RA10. */
    private static final Comparator<String> NATURAL = (x, y) -> {
        int i = 0, j = 0;
        while (i < x.length() && j < y.length()) {
            char a = x.charAt(i), b = y.charAt(j);
            if (Character.isDigit(a) && Character.isDigit(b)) {
                int i2 = i, j2 = j;
                while (i2 < x.length() && Character.isDigit(x.charAt(i2))) i2++;
                while (j2 < y.length() && Character.isDigit(y.charAt(j2))) j2++;
                int c = new java.math.BigInteger(x.substring(i, i2)).compareTo(new java.math.BigInteger(y.substring(j, j2)));
                if (c != 0) return c;
                i = i2; j = j2;
            } else {
                int c = Character.compare(Character.toLowerCase(a), Character.toLowerCase(b));
                if (c != 0) return c;
                i++; j++;
            }
        }
        return Integer.compare(x.length() - i, y.length() - j);
    };
}
