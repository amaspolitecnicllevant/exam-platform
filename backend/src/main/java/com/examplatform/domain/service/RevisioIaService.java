package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.domain.service.exportacio.DadesExamen;
import com.examplatform.domain.service.exportacio.ExportacioService;
import com.examplatform.domain.service.exportacio.RevisioIaParser;
import com.examplatform.dto.RevisioIaDto;
import com.examplatform.dto.RevisioIaDto.*;
import com.examplatform.infrastructure.persistence.AnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Importa la revisió que una IA ha fet de les respostes d'un examen exportat. Dos passos separats:
 * <ol>
 *   <li>{@link #previsualitza}: llegeix el text i diu què canviaria, sense desar res;</li>
 *   <li>{@link #aplica}: torna a llegir el text i desa només les files que el professor ha acceptat,
 *       com a nota revisada, amb la nota anterior i la justificació (només visibles per al professor).</li>
 * </ol>
 * Només es toquen respostes que corregeix el professor: les de test (les corregeix el sistema), els
 * lliuraments de fitxer (no s'envien a la IA) i les preguntes anul·lades queden fora.
 */
@Service
@RequiredArgsConstructor
public class RevisioIaService {

    static final int MAX_JUSTIFICACIO = 2000;
    private static final Pattern EMAIL = Pattern.compile("[^\\s<>,;]+@[^\\s<>,;]+");
    private static final Pattern PRIMER_NOMBRE = Pattern.compile("-?\\d+(?:[.,]\\d+)?");
    private static final Pattern CODI = Pattern.compile("^[0-9A-F]{6,32}$");

    private final ExportacioService exportacio;
    private final AnswerRepository answerRepository;
    private final AuditLogService auditLog;

    @Transactional(readOnly = true)
    public RevisioIaDto previsualitza(UUID examId, String text, User usuari) {
        return analitza(exportacio.dades(examId, usuari), text).dto();
    }

    @Transactional
    public Aplicacio aplica(UUID examId, String text, List<Acceptada> acceptades, User usuari) {
        DadesExamen d = exportacio.dades(examId, usuari);
        Analisi analisi = analitza(d, text);
        Map<UUID, Fila> perResposta = new HashMap<>();
        for (Fila f : analisi.dto().files()) if (f.answerId() != null) perResposta.put(f.answerId(), f);

        int aplicades = 0;
        List<String> motius = new ArrayList<>();
        Set<UUID> fets = new HashSet<>();
        LocalDateTime ara = LocalDateTime.now();
        for (Acceptada acc : acceptades == null ? List.<Acceptada>of() : acceptades) {
            if (acc == null || acc.answerId() == null || !fets.add(acc.answerId())) continue;
            Fila f = perResposta.get(acc.answerId());
            if (f == null || f.estat() != Estat.CANVI) {
                motius.add("Una resposta ja no és un canvi vàlid del fitxer (línia " + (f == null ? "?" : f.linia()) + ")");
                continue;
            }
            if (!mateixaNota(f.notaActual(), acc.notaActual())) {
                motius.add("Línia " + f.linia() + ": la nota ha canviat des de la previsualització; torna-la a revisar");
                continue;
            }
            Answer a = analisi.respostes().get(acc.answerId());
            a.setRevisioIaNotaAbans(f.notaActual());
            a.setManualScore(f.notaNova());
            a.setCorrectedAt(ara);
            a.setRevisioIaEl(ara);
            a.setRevisioIaJustificacio(retalla(f.justificacio()));
            answerRepository.save(a);
            aplicades++;
        }
        int saltades = fets.size() - aplicades;
        auditLog.log(usuari.getId(), "EXAM_REVISIO_IA",
                aplicades + " notes aplicades (" + saltades + " saltades) · examen " + examId);
        return new Aplicacio(aplicades, saltades, motius);
    }

    // ── Anàlisi (sense efectes) ───────────────────────────────────────────────

    private record Analisi(RevisioIaDto dto, Map<UUID, Answer> respostes) {}

    private Analisi analitza(DadesExamen d, String text) {
        List<RevisioIaParser.Fila> brutes = RevisioIaParser.analitza(text);
        List<String> avisos = new ArrayList<>();
        if (brutes.isEmpty()) {
            avisos.add("No s'ha trobat cap fila amb el format «alumne; pregunta; nota; justificacio».");
        }

        Map<UUID, String> codis = d.codisAnonims();
        Map<String, DadesExamen.Alumne> perCodi = new HashMap<>();
        Map<String, DadesExamen.Alumne> perEmail = new HashMap<>();
        Map<String, List<DadesExamen.Alumne>> perNom = new HashMap<>();
        for (DadesExamen.Alumne a : d.alumnes()) {
            perCodi.put(codis.get(a.sessio().getId()).substring("Alumne ".length()), a);
            perEmail.put(a.email().toLowerCase(Locale.ROOT), a);
            perNom.computeIfAbsent(normalitza(a.nom()), k -> new ArrayList<>()).add(a);
        }
        Map<Integer, Question> perOrdre = new HashMap<>();
        for (Question q : d.preguntes()) perOrdre.put(q.getOrdre(), q);

        // Resolució prèvia, per detectar files repetides (mateix alumne i pregunta)
        record Resolta(RevisioIaParser.Fila fila, DadesExamen.Alumne alumne, Question pregunta, String error) {}
        List<Resolta> resoltes = new ArrayList<>();
        Map<String, Integer> repeticions = new HashMap<>();
        for (RevisioIaParser.Fila f : brutes) {
            DadesExamen.Alumne alumne = resolAlumne(f.alumne(), perCodi, perEmail, perNom);
            Integer ordre = ordreDe(f.pregunta());
            Question q = ordre == null ? null : perOrdre.get(ordre);
            String error = null;
            if (alumne == null) error = "Alumne no reconegut: «" + curt(f.alumne()) + "»";
            else if (q == null) error = "La pregunta «" + curt(f.pregunta()) + "» no existeix en aquest examen";
            resoltes.add(new Resolta(f, alumne, q, error));
            if (error == null) repeticions.merge(alumne.sessio().getId() + "|" + q.getId(), 1, Integer::sum);
        }

        List<Fila> files = new ArrayList<>();
        Map<UUID, Answer> respostes = new HashMap<>();
        Map<UUID, Map<UUID, BigDecimal>> novesPerSessio = new HashMap<>();
        int canvis = 0, iguals = 0, ignorades = 0, errors = 0;

        for (Resolta r : resoltes) {
            RevisioIaParser.Fila f = r.fila();
            String codi = r.alumne() == null ? null : codis.get(r.alumne().sessio().getId());
            String nom = r.alumne() == null ? curt(f.alumne()) : r.alumne().nom();
            Integer ordre = r.pregunta() == null ? null : r.pregunta().getOrdre();
            String just = f.justificacio() == null ? "" : f.justificacio().strip();

            if (r.error() != null) {
                files.add(fila(f.linia(), null, nom, codi, ordre, r.pregunta(), null, null, null, just, Estat.ERROR, r.error(), false));
                errors++;
                continue;
            }
            Question q = r.pregunta();
            DadesExamen.Alumne a = r.alumne();
            Answer resposta = a.respostes().get(q.getId());
            String ignorada = motiuIgnorada(q, a, resposta);
            if (ignorada != null) {
                files.add(fila(f.linia(), null, nom, codi, ordre, q, null, null, null, just, Estat.IGNORADA, ignorada, false));
                ignorades++;
                continue;
            }
            if (repeticions.get(a.sessio().getId() + "|" + q.getId()) > 1) {
                files.add(fila(f.linia(), resposta.getId(), nom, codi, ordre, q, null, null, null, just, Estat.ERROR,
                        "Aquesta alumne i pregunta surten més d'una vegada al fitxer", false));
                errors++;
                continue;
            }
            BigDecimal nova = nota(f.nota());
            if (nova == null) {
                files.add(fila(f.linia(), resposta.getId(), nom, codi, ordre, q, null, null, null, just, Estat.ERROR,
                        "Nota no vàlida: «" + curt(f.nota()) + "»", false));
                errors++;
                continue;
            }
            if (nova.signum() < 0 || nova.compareTo(q.getPunts()) > 0) {
                files.add(fila(f.linia(), resposta.getId(), nom, codi, ordre, q, null, null, nova, just, Estat.ERROR,
                        "Nota fora de rang: ha d'estar entre 0 i " + q.getPunts().stripTrailingZeros().toPlainString(), false));
                errors++;
                continue;
            }

            BigDecimal actual = resposta.getManualScore() != null ? resposta.getManualScore() : resposta.getAutoScore();
            Origen origen = resposta.getManualScore() != null ? Origen.REVISADA
                    : resposta.getAutoScore() != null ? Origen.PROPOSTA : Origen.CAP;
            respostes.put(resposta.getId(), resposta);
            boolean mateixa = actual != null && actual.compareTo(nova) == 0;
            if (mateixa && origen == Origen.REVISADA) {
                files.add(fila(f.linia(), resposta.getId(), nom, codi, ordre, q, actual, origen, nova, just, Estat.IGUAL,
                        "Mateixa nota que ja tens", false, q));
                iguals++;
                continue;
            }
            String motiu = mateixa ? "Coincideix amb la proposta automàtica: passaria a nota revisada" : null;
            files.add(fila(f.linia(), resposta.getId(), nom, codi, ordre, q, actual, origen, nova, just, Estat.CANVI, motiu,
                    origen == Origen.REVISADA, q));
            novesPerSessio.computeIfAbsent(a.sessio().getId(), k -> new HashMap<>()).put(q.getId(), nova);
            canvis++;
        }

        List<Alumne> alumnes = new ArrayList<>();
        for (DadesExamen.Alumne a : d.alumnes()) {
            Map<UUID, BigDecimal> noves = novesPerSessio.get(a.sessio().getId());
            if (noves == null) continue;
            BigDecimal abans = notaSobreDeu(d.preguntes(), a.respostes(), Map.of());
            BigDecimal despres = notaSobreDeu(d.preguntes(), a.respostes(), noves);
            alumnes.add(new Alumne(a.nom(), codis.get(a.sessio().getId()), abans, despres));
        }

        RevisioIaDto dto = new RevisioIaDto(d.exam().isNotesVisibles(), canvis, iguals, ignorades, errors, files, alumnes, avisos);
        return new Analisi(dto, respostes);
    }

    private static Fila fila(int linia, UUID answerId, String alumne, String codi, Integer pregunta, Question q,
                             BigDecimal actual, Origen origen, BigDecimal nova, String just, Estat estat, String motiu,
                             boolean sobreescriu) {
        return fila(linia, answerId, alumne, codi, pregunta, q, actual, origen, nova, just, estat, motiu, sobreescriu, q);
    }

    private static Fila fila(int linia, UUID answerId, String alumne, String codi, Integer pregunta, Question q,
                             BigDecimal actual, Origen origen, BigDecimal nova, String just, Estat estat, String motiu,
                             boolean sobreescriu, Question per) {
        String enunciat = per == null ? null : abreuja(per.getEnunciat(), 90);
        BigDecimal max = per == null ? null : per.getPunts();
        return new Fila(linia, answerId, alumne, codi, pregunta, enunciat, max, actual, origen, nova,
                retalla(just), estat, motiu, sobreescriu);
    }

    private static String motiuIgnorada(Question q, DadesExamen.Alumne a, Answer resposta) {
        if (q.getTipus() == QuestionType.CHOICE) return "Pregunta de test: la corregeix el sistema";
        if (q.getTipus() == QuestionType.FILE_UPLOAD) return "Lliurament de fitxer: no s'envia a la IA";
        if (q.isAnulada()) return "Pregunta anul·lada (bonus per a tothom)";
        if (!a.entregat()) return "L'alumne no ha entregat l'examen";
        if (resposta == null || resposta.getContingut() == null || resposta.getContingut().isBlank()) {
            return "Sense resposta: no hi ha res a qualificar";
        }
        return null;
    }

    // ── Interpretació de cel·les ──────────────────────────────────────────────

    private static DadesExamen.Alumne resolAlumne(String brut, Map<String, DadesExamen.Alumne> perCodi,
                                                   Map<String, DadesExamen.Alumne> perEmail,
                                                   Map<String, List<DadesExamen.Alumne>> perNom) {
        if (brut == null || brut.isBlank()) return null;
        Matcher m = EMAIL.matcher(brut);
        if (m.find()) {
            DadesExamen.Alumne a = perEmail.get(m.group().toLowerCase(Locale.ROOT));
            if (a != null) return a;
        }
        String codi = brut.toUpperCase(Locale.ROOT).replace("ALUMNE", "").replaceAll("[\\s_#:*`-]", "");
        if (CODI.matcher(codi).matches()) {
            DadesExamen.Alumne a = perCodi.get(codi);
            if (a != null) return a;
        }
        List<DadesExamen.Alumne> perNomTrobats = perNom.get(normalitza(brut.replaceAll("<[^>]*>", "")));
        return perNomTrobats != null && perNomTrobats.size() == 1 ? perNomTrobats.get(0) : null;
    }

    private static Integer ordreDe(String brut) {
        if (brut == null) return null;
        Matcher m = Pattern.compile("\\d+").matcher(brut);
        if (!m.find()) return null;
        try { return Integer.parseInt(m.group()); } catch (NumberFormatException e) { return null; }
    }

    /** «7,5», «7.5», «7,5/10», «**7,5**»: el primer nombre, a 2 decimals; null si no n'hi ha cap. */
    static BigDecimal nota(String brut) {
        if (brut == null) return null;
        Matcher m = PRIMER_NOMBRE.matcher(brut);
        if (!m.find()) return null;
        try {
            return new BigDecimal(m.group().replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean mateixaNota(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    /** Nota sobre 10 d'un alumne, amb {@code noves} (pregunta → nota) substituint la que hi ha ara. */
    private static BigDecimal notaSobreDeu(Collection<Question> preguntes, Map<UUID, Answer> respostes, Map<UUID, BigDecimal> noves) {
        BigDecimal total = BigDecimal.ZERO;
        for (Question q : preguntes) {
            BigDecimal p = noves.containsKey(q.getId()) ? noves.get(q.getId()) : Puntuacio.punts(q, respostes.get(q.getId()));
            if (p != null) total = total.add(p);
        }
        return Puntuacio.sobreDeu(total, Puntuacio.maxim(preguntes));
    }

    private static String normalitza(String t) {
        return Normalizer.normalize(t == null ? "" : t, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    private static String curt(String t) {
        return abreuja(t, 60);
    }

    private static String abreuja(String t, int max) {
        String l = t == null ? "" : t.replaceAll("[\\r\\n]+", " ").strip();
        return l.length() <= max ? l : l.substring(0, max - 1).stripTrailing() + "…";
    }

    private static String retalla(String t) {
        if (t == null || t.isBlank()) return null;
        String s = t.strip();
        return s.length() <= MAX_JUSTIFICACIO ? s : s.substring(0, MAX_JUSTIFICACIO - 1) + "…";
    }
}
