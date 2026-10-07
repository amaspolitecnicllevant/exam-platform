package com.examplatform.domain.service.exportacio;

import com.examplatform.domain.model.Answer;
import com.examplatform.domain.model.Question;
import com.examplatform.domain.model.QuestionType;
import com.examplatform.domain.service.Puntuacio;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/** Excel (.xlsx) d'un examen: notes, notes per resultat d'aprenentatge i respostes. */
public final class ExportacioExcel {

    private ExportacioExcel() {}

    public static byte[] excel(DadesExamen d) throws IOException {
        return XlsxWriter.escriu(List.of(notes(d), notesRa(d), respostes(d)));
    }

    private static BigDecimal dosDecimals(BigDecimal n) {
        return n == null ? null : n.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    static XlsxWriter.Full notes(DadesExamen d) {
        List<String> cap = new ArrayList<>(List.of("Alumne", "Correu", "Estat", "Nota sobre 10", "Punts", "Punts màxims",
                "Pendents de revisar"));
        d.preguntes().forEach(q -> cap.add(ExportacioNotes.capcaleraPregunta(q)));
        List<List<Object>> files = new ArrayList<>();
        BigDecimal maxim = Puntuacio.maxim(d.preguntes());
        for (DadesExamen.Alumne a : d.alumnes()) {
            boolean comencat = a.sessio().getStartedAt() != null || a.entregat();
            List<Object> fila = new ArrayList<>(List.of(a.nom(), a.email(), a.estat()));
            fila.add(comencat ? dosDecimals(Puntuacio.notaSobreDeu(d.preguntes(), a.respostes())) : null);
            fila.add(comencat ? dosDecimals(Puntuacio.total(d.preguntes(), a.respostes())) : null);
            fila.add(dosDecimals(maxim));
            fila.add(comencat ? (Object) ExportacioNotes.pendents(d, a) : null);
            for (Question q : d.preguntes()) {
                BigDecimal p = ExportacioNotes.punts(a, q);
                fila.add(!comencat ? null : a.respostes().get(q.getId()) != null && p == null ? "pendent" : dosDecimals(p));
            }
            files.add(fila);
        }
        List<Integer> amples = new ArrayList<>(List.of(32, 32, 14, 14, 9, 13, 18));
        d.preguntes().forEach(q -> amples.add(14));
        return new XlsxWriter.Full("Notes", cap, files, amples, Set.of());
    }

    static XlsxWriter.Full notesRa(DadesExamen d) {
        List<String> ras = ExportacioNotes.resultatsAprenentatge(d);
        List<String> cap = new ArrayList<>(List.of("Alumne", "Correu"));
        cap.addAll(ras);
        cap.add("Nota global");
        List<List<Object>> files = new ArrayList<>();
        for (DadesExamen.Alumne a : d.entregats()) {
            List<Object> fila = new ArrayList<>(List.of(a.nom(), a.email()));
            for (String ra : ras) fila.add(dosDecimals(ExportacioNotes.notaRa(d, a, ra)));
            fila.add(dosDecimals(Puntuacio.notaSobreDeu(d.preguntes(), a.respostes())));
            files.add(fila);
        }
        List<Integer> amples = new ArrayList<>(List.of(32, 32));
        ras.forEach(r -> amples.add(14));
        amples.add(14);
        return new XlsxWriter.Full("Notes per RA", cap, files, amples, Set.of());
    }

    static XlsxWriter.Full respostes(DadesExamen d) {
        List<String> cap = List.of("Alumne", "Correu", "Pregunta", "Tipus", "Punts màxims", "Resposta", "Nota", "Comentari");
        List<List<Object>> files = new ArrayList<>();
        for (DadesExamen.Alumne a : d.entregats()) {
            for (Question q : d.preguntes()) {
                Answer r = a.respostes().get(q.getId());
                BigDecimal p = Puntuacio.punts(q, r);
                files.add(new ArrayList<>(Arrays.asList(a.nom(), a.email(), "P" + q.getOrdre(),
                        ExportacioMarkdown.etiquetaTipus(q.getTipus()), dosDecimals(q.getPunts()), textResposta(q, r),
                        r != null && p == null ? "pendent" : dosDecimals(p), r == null ? null : r.getComentari())));
            }
        }
        return new XlsxWriter.Full("Respostes", cap, files, List.of(32, 32, 10, 20, 12, 70, 10, 40), Set.of(5, 7));
    }

    private static String textResposta(Question q, Answer r) {
        if (r == null) return null;
        if (q.getTipus() == QuestionType.FILE_UPLOAD) return r.teFitxer() ? "Fitxer: " + r.getFitxerNom() : null;
        return r.getContingut();
    }
}
