package com.examplatform.domain.service.exportacio;

import com.examplatform.domain.model.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Un examen amb les seves preguntes i, per cada sessió, les respostes de l'alumne, preparat per
 * exportar. No toca la BD: qui el construeix ja ha carregat tot (així els exportadors són funcions
 * pures i es proven sense base de dades).
 */
public record DadesExamen(Exam exam, List<Question> preguntes, List<Alumne> alumnes) {

    /** Una sessió d'alumne amb les seves respostes per pregunta. */
    public record Alumne(ExamSession sessio, Map<UUID, Answer> respostes) {
        public String nom() { return sessio.getStudent().getName() == null ? "" : sessio.getStudent().getName(); }
        public String email() { return sessio.getStudent().getEmail() == null ? "" : sessio.getStudent().getEmail(); }
        public boolean entregat() { return sessio.getStatus() == SessionStatus.SUBMITTED; }

        public String estat() {
            if (entregat()) return "Entregat";
            return sessio.getStartedAt() != null ? "En curs" : "No presentat";
        }
    }

    /** Construeix les dades: preguntes (sense seccions) per ordre, alumnes per nom i respostes per sessió. */
    public static DadesExamen de(Exam exam, List<ExamSession> sessions, Collection<Answer> respostes) {
        List<Question> preguntes = exam.getQuestions().stream()
                .filter(q -> q.getTipus() != QuestionType.SECTION)
                .sorted(Comparator.comparingInt(Question::getOrdre))
                .toList();
        Map<UUID, Map<UUID, Answer>> perSessio = new HashMap<>();
        for (Answer a : respostes) {
            perSessio.computeIfAbsent(a.getSession().getId(), k -> new HashMap<>()).put(a.getQuestion().getId(), a);
        }
        // Ordre alfabètic català: sense distingir majúscules ni accents («Àlex» abans que «Berta»)
        java.text.Collator collator = java.text.Collator.getInstance(Locale.forLanguageTag("ca"));
        collator.setStrength(java.text.Collator.PRIMARY);
        List<Alumne> alumnes = sessions.stream()
                .map(s -> new Alumne(s, perSessio.getOrDefault(s.getId(), Map.of())))
                .sorted(Comparator.comparing(Alumne::nom, collator)
                        .thenComparing(Alumne::email, collator)
                        .thenComparing(a -> a.sessio().getId().toString()))
                .toList();
        return new DadesExamen(exam, preguntes, alumnes);
    }

    public List<Alumne> entregats() {
        return alumnes.stream().filter(Alumne::entregat).toList();
    }

    /**
     * Codi anònim estable d'un alumne («Alumne 7F3A2C»): surt de l'identificador de la sessió, no de
     * la posició a la llista, així que és el mateix a cada descàrrega encara que n'entrin més alumnes.
     * No revela el nom ni l'ordre alfabètic. Si dos codis coincidissin, s'allarguen fins a ser únics.
     */
    public Map<UUID, String> codisAnonims() {
        List<UUID> ids = alumnes.stream().map(a -> a.sessio().getId()).toList();
        for (int llargada = 6; llargada <= 32; llargada += 2) {
            Map<UUID, String> codis = new LinkedHashMap<>();
            Set<String> vists = new HashSet<>();
            boolean unics = true;
            for (UUID id : ids) {
                String codi = id.toString().replace("-", "").substring(0, llargada).toUpperCase(Locale.ROOT);
                if (!vists.add(codi)) { unics = false; break; }
                codis.put(id, "Alumne " + codi);
            }
            if (unics) return codis;
        }
        throw new IllegalStateException("No s'han pogut generar codis anònims únics");
    }

    /** Nom net per a fitxers i directoris: lletres, xifres, espais i punts, sense barres ni «..». */
    public static String nomNet(String text, int maxim) {
        String net = text == null ? "" : java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
                .replaceAll("[^\\p{L}\\p{N} ._()-]", "_").replaceAll("\\.{2,}", "_").replaceAll("\\s+", " ").strip();
        net = net.replaceAll("^[._ ]+", "");
        if (net.length() > maxim) net = net.substring(0, maxim).strip();
        return net.isEmpty() ? "sense-nom" : net;
    }

    /** Nom de fitxer basat en el títol de l'examen: «Parcial UT1.csv» → «Parcial_UT1». */
    public String titolPerFitxer() {
        String net = nomNet(exam.getTitle(), 60).replace(' ', '_');
        return net.isEmpty() ? "examen" : net;
    }

    static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
