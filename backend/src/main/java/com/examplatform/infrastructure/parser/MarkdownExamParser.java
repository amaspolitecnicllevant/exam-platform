package com.examplatform.infrastructure.parser;

import com.examplatform.domain.model.*;
import com.examplatform.domain.service.FormatsFitxer;
import com.examplatform.domain.port.ExamParser;
import com.examplatform.domain.service.ClausCorreccio;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/**
 * Interpreta un examen en Markdown (vegeu frontend/public/guia-sintaxi-examens.md).
 *
 * <p>El cos es recorre línia a línia sabent si s'és dins d'un bloc {@code :::} o d'un bloc de codi
 * {@code ```}, de manera que les línies {@code ##}/{@code ###} de dins d'un script no es confonen amb
 * preguntes o seccions. Qualsevol construcció que no s'entengui es rebutja indicant la línia i el
 * motiu: res no s'ignora en silenci.
 */
@Component
public class MarkdownExamParser implements ExamParser {

    private static final Pattern QUESTION_HEADER =
            Pattern.compile("^##\\s+(\\d+)\\.?\\s+\\[(\\w+(?:-\\w+)?)\\]\\s+\\[pts:([\\d.]+)\\]((?:\\s+\\[(?:(?:ra|dif|ordre|formats):[^\\]]+|apunts)\\])*)\\s*$");

    private static final Pattern SECTION_HEADER = Pattern.compile("^###\\s+(.+?)(\\s+\\[apunts\\])?\\s*$");
    private static final Pattern TAG_APUNTS = Pattern.compile("\\[apunts\\]");
    private static final Pattern TAG_FORMATS = Pattern.compile("\\[formats:([^\\]]+)\\]");
    private static final Pattern HEADER_LIKE    = Pattern.compile("^##\\s.*");
    private static final Pattern BLOCK_OPENER   = Pattern.compile("^:::([\\w-]+)\\s*$");
    private static final Pattern SEPARATOR      = Pattern.compile("^-{3,}\\s*$");
    /** Línia separadora de Markdown: fora d'un enunciat és decorativa i s'ignora. */
    private static final Pattern HORIZONTAL_RULE = Pattern.compile("^(?:-{3,}|\\*{3,}|_{3,})\\s*$");

    private static final Pattern TAG_RA    = Pattern.compile("\\[ra:([^\\]]+)\\]");
    private static final Pattern TAG_ORDRE = Pattern.compile("\\[ordre:([^\\]]+)\\]");
    private static final Pattern TAG_DIF   = Pattern.compile("\\[dif:([^\\]]+)\\]");
    private static final Set<String> DIFICULTATS = Set.of("baixa", "mitjana", "alta");

    private static final Pattern OPCIO = Pattern.compile("^-\\s+([a-zA-Z])\\)\\s*(.*)$");
    private static final Pattern OPCIO_SENSE_GUIO = Pattern.compile("^[a-zA-Z]\\)\\s+.*");

    private static final Set<String> BLOCS = Set.of(
            "model", "clau", "output-contains", "output-exact", "output-regex", "test");
    private static final List<String> OUTPUT_BLOCS = List.of("output-exact", "output-contains", "output-regex");

    @Override
    public Exam parse(String mdContent, User createdBy) {
        List<String> lines = normalitza(mdContent).lines().toList();

        int sep = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (SEPARATOR.matcher(lines.get(i)).matches()) { sep = i; break; }
        }
        if (sep < 0) throw new IllegalArgumentException("El fitxer MD no té el separador '---' després de la capçalera");

        Exam exam = parseHeader(lines.subList(0, sep), createdBy, mdContent);
        parseQuestions(lines.subList(sep + 1, lines.size()), sep + 2, exam);
        validateTotalPoints(exam);
        return exam;
    }

    /** Treu el BOM i converteix els salts de línia de Windows (CRLF) i Mac antic (CR) a LF. */
    private static String normalitza(String md) {
        String s = md.startsWith("﻿") ? md.substring(1) : md;
        return s.replace("\r\n", "\n").replace('\r', '\n');
    }

    private Exam parseHeader(List<String> header, User createdBy, String rawMd) {
        String title = "";
        int durada = 90;
        String instruccions = null;

        for (String raw : header) {
            String line = raw.trim();
            if (line.startsWith("# ")) {
                title = line.substring(2).trim();
            } else if (line.startsWith("durada:")) {
                try {
                    durada = Integer.parseInt(line.substring(7).trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("La durada ha de ser un nombre enter de minuts: «" + line + "»");
                }
                if (durada <= 0) {
                    throw new IllegalArgumentException("La durada ha de ser positiva: «" + line + "»");
                }
            } else if (line.startsWith("instruccions:")) {
                instruccions = line.substring(13).trim();
            }
        }

        if (title.isEmpty()) throw new IllegalArgumentException("El MD no té títol (# Títol)");

        return Exam.builder()
                .title(title)
                .durada(durada)
                .instruccions(instruccions)
                .createdBy(createdBy)
                .rawMd(rawMd)
                .questions(new ArrayList<>())
                .build();
    }

    /** Pregunta en construcció mentre es recorre el fitxer. */
    private static final class Draft {
        final String nom;      // "Pregunta N" amb la numeració del fitxer
        final int line;
        final String tipus;
        final String pts;
        final String tags;
        final List<String> enunciat = new ArrayList<>();
        final Map<String, String> blocks = new LinkedHashMap<>();

        final boolean ambApunts;

        Draft(Matcher header, int line, boolean apuntsSeccio) {
            this.nom = "Pregunta " + header.group(1);
            this.line = line;
            this.tipus = header.group(2);
            this.pts = header.group(3);
            this.tags = header.group(4) == null ? "" : header.group(4);
            this.ambApunts = apuntsSeccio || TAG_APUNTS.matcher(this.tags).find();
        }
    }

    private void parseQuestions(List<String> lines, int firstLine, Exam exam) {
        Draft cur = null;
        String seccio = null;          // secció oberta que encara no té cap pregunta
        boolean apuntsSeccio = false;  // la secció actual permet apunts ([apunts] al títol)
        String blockName = null;
        int blockLine = 0;
        StringBuilder blockContent = null;
        boolean inFence = false;
        int fenceLine = 0;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String t = line.strip();
            int ln = firstLine + i;

            // Dins d'un bloc ::: només s'espera el contingut i el tancament
            if (blockName != null) {
                if (t.equals(":::")) {
                    cur.blocks.put(blockName, blockContent.toString().strip());
                    blockName = null;
                } else if (BLOCK_OPENER.matcher(t).matches()) {
                    throw error(ln, cur.nom + ": el bloc :::" + blockName + " (línia " + blockLine
                            + ") no està tancat. Afegeix una línia ':::' abans d'obrir " + t);
                } else if (t.contains(":::")) {
                    throw error(ln, cur.nom + ": el text del bloc :::" + blockName + " no pot contenir ':::'");
                } else {
                    blockContent.append(line).append('\n');
                }
                continue;
            }

            // Bloc de codi a l'enunciat: el contingut és text i no s'interpreta
            boolean fenceToggle = t.startsWith("```");
            if (inFence || fenceToggle) {
                if (fenceToggle) {
                    inFence = !inFence;
                    fenceLine = ln;
                }
                afegeixText(cur, seccio, line, ln);
                continue;
            }

            Matcher opener = BLOCK_OPENER.matcher(t);
            if (opener.matches()) {
                if (cur == null) throw error(ln, "bloc " + t + " fora de cap pregunta");
                String name = opener.group(1);
                if (!BLOCS.contains(name)) {
                    throw error(ln, cur.nom + ": bloc desconegut :::" + name + ". " + pistaBloc(name));
                }
                if (cur.blocks.containsKey(name)) {
                    throw error(ln, cur.nom + ": el bloc :::" + name + " està repetit");
                }
                blockName = name;
                blockLine = ln;
                blockContent = new StringBuilder();
                continue;
            }
            if (t.startsWith(":::")) {
                throw error(ln, (cur != null ? cur.nom + ": " : "") + "línia «" + t + "» no vàlida. "
                        + "Un bloc s'obre amb ':::nom' i es tanca amb ':::', cadascun en una línia pròpia");
            }

            Matcher sm = SECTION_HEADER.matcher(line);
            if (sm.matches()) {
                tancaPregunta(cur, exam);
                cur = null;
                seccio = sm.group(1).trim();
                apuntsSeccio = sm.group(2) != null;
                exam.getQuestions().add(Question.builder()
                        .exam(exam)
                        .ordre(exam.getQuestions().size() + 1)
                        .tipus(QuestionType.SECTION)
                        .enunciat(seccio)
                        .punts(BigDecimal.ZERO)
                        .build());
                continue;
            }

            if (HEADER_LIKE.matcher(line).matches()) {
                Matcher qm = QUESTION_HEADER.matcher(line);
                if (!qm.matches()) {
                    throw error(ln, "capçalera de pregunta no vàlida «" + t + "». " + pistaCapcalera(t));
                }
                tancaPregunta(cur, exam);
                cur = new Draft(qm, ln, apuntsSeccio);
                seccio = null;
                continue;
            }

            afegeixText(cur, seccio, line, ln);
        }

        if (blockName != null) {
            throw error(blockLine, cur.nom + ": el bloc :::" + blockName + " no està tancat (falta una línia ':::')");
        }
        if (inFence) {
            throw error(fenceLine, "bloc de codi (```) sense tancar");
        }
        tancaPregunta(cur, exam);
    }

    private static void afegeixText(Draft cur, String seccio, String line, int ln) {
        if (line.isBlank()) {
            if (cur != null) cur.enunciat.add(line);
            return;
        }
        boolean separador = HORIZONTAL_RULE.matcher(line.strip()).matches();
        if (separador && (cur == null || !cur.blocks.isEmpty())) {
            return;
        }
        if (cur == null) {
            if (seccio != null) {
                throw error(ln, "hi ha text sota la secció «" + seccio + "» abans de cap pregunta. "
                        + "Si és part d'un enunciat, no usis '###' (usa negreta: **text**)");
            }
            throw error(ln, "hi ha text abans de la primera pregunta (## 1. [tipus] [pts:X])");
        }
        if (!cur.blocks.isEmpty()) {
            throw error(ln, cur.nom + ": hi ha text després dels blocs ':::'. L'enunciat va abans dels blocs");
        }
        cur.enunciat.add(line);
    }

    private void tancaPregunta(Draft d, Exam exam) {
        if (d == null) return;
        exam.getQuestions().add(buildQuestion(d, exam.getQuestions().size() + 1, exam));
    }

    private static IllegalArgumentException error(int line, String msg) {
        return new IllegalArgumentException("Línia " + line + ": " + msg);
    }

    private static String pistaBloc(String name) {
        if (name.equals("correct-choice") || name.equals("correcta") || name.equals("resposta")) {
            return "La lletra correcta d'una pregunta de test va a :::model";
        }
        return "Blocs vàlids: :::model, :::clau, :::output-contains, :::output-exact, :::output-regex i :::test";
    }

    private static String pistaCapcalera(String t) {
        if (!t.matches("^##\\s+\\d+.*")) return "Falta el número de pregunta: ## 1. [tipus] [pts:X]";
        if (!t.contains("[pts:")) return "Falta [pts:X]";
        if (t.matches(".*\\[pts:[\\d.]*,.*")) return "Els punts s'escriuen amb punt decimal: [pts:0.5]";
        Matcher tags = Pattern.compile("\\[(\\w+):").matcher(t);
        while (tags.find()) {
            String tag = tags.group(1);
            if (!Set.of("pts", "ra", "dif", "ordre", "formats").contains(tag)) {
                return "Etiqueta desconeguda [" + tag + ":…]. Només s'admeten [ra:…], [dif:…], [ordre:fix], [formats:…] i [apunts], després de [pts:X]";
            }
        }
        return "Format: ## N. [tipus] [pts:X] i, opcionalment, [ra:RA1] [dif:mitjana] [ordre:fix] [formats:docx,xlsx] [apunts]";
    }

    private Question buildQuestion(Draft d, int ordre, Exam exam) {
        String nom = d.nom + " (línia " + d.line + ")";
        String typeStr = d.tipus.trim().toUpperCase().replace("-", "_");
        if (typeStr.equals("FITXER")) typeStr = QuestionType.FILE_UPLOAD.name();   // alies en català
        QuestionType type;
        try {
            type = QuestionType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(nom + ": tipus de pregunta desconegut: " + typeStr);
        }
        if (type == QuestionType.SECTION) {
            throw new IllegalArgumentException(nom + ": per crear una secció usa una línia '### Títol'");
        }
        BigDecimal pts;
        try {
            pts = new BigDecimal(d.pts);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(nom + ": punts no vàlids [pts:" + d.pts + "]");
        }

        Map<String, String> blocks = d.blocks;
        String rawEnunciat = String.join("\n", d.enunciat).strip();

        String ra = null;
        Matcher raM = TAG_RA.matcher(d.tags);
        if (raM.find()) ra = raM.group(1).trim();

        String dificultat = null;
        Matcher difM = TAG_DIF.matcher(d.tags);
        if (difM.find()) {
            dificultat = difM.group(1).strip().toLowerCase();
            if (!DIFICULTATS.contains(dificultat)) {
                throw new IllegalArgumentException(nom + ": dificultat «" + difM.group(1)
                        + "» no vàlida. Usa baixa, mitjana o alta");
            }
        }

        boolean barrejarOpcions = true;
        Matcher ordreM = TAG_ORDRE.matcher(d.tags);
        if (ordreM.find()) {
            if (!ordreM.group(1).strip().equalsIgnoreCase("fix")) {
                throw new IllegalArgumentException(nom + ": [ordre:...] només admet el valor 'fix'");
            }
            if (type != QuestionType.CHOICE) {
                throw new IllegalArgumentException(nom + ": [ordre:fix] només es pot usar en preguntes CHOICE");
            }
            barrejarOpcions = false;
        }

        String formatsPermesos = validateFormats(nom, type, d.tags);
        validateBlocsPerTipus(nom, type, blocks);

        if (type == QuestionType.CHOICE) {
            Question q = buildChoiceQuestion(nom, ordre, pts, rawEnunciat, blocks, exam);
            q.setAmbApunts(d.ambApunts);
            q.setBarrejarOpcions(barrejarOpcions);
            q.setRa(ra);
            q.setDificultat(dificultat);
            return q;
        }

        validateOutputCriteria(nom, blocks);
        String claus = validateClaus(nom, pts, blocks.get("clau"));

        return Question.builder()
                .exam(exam)
                .ordre(ordre)
                .tipus(type)
                .enunciat(rawEnunciat)
                .punts(pts)
                .modelResposta(blocks.get("model"))
                .outputContains(blocks.get("output-contains"))
                .outputExact(blocks.get("output-exact"))
                .outputRegex(blocks.get("output-regex"))
                .testScript(blocks.get("test"))
                .claus(claus)
                .ambApunts(d.ambApunts)
                .ra(ra)
                .dificultat(dificultat)
                .formatsPermesos(formatsPermesos)
                .build();
    }

    /**
     * Formats d'una pregunta de lliurament de fitxer ({@code [formats:docx,xlsx]}), normalitzats.
     * Sense l'etiqueta, s'admeten tots els formats permesos (es desa null). L'etiqueta només té sentit
     * en preguntes {@code [fitxer]}.
     */
    private String validateFormats(String nom, QuestionType type, String tags) {
        Matcher m = TAG_FORMATS.matcher(tags);
        boolean present = m.find();
        if (type != QuestionType.FILE_UPLOAD) {
            if (present) {
                throw new IllegalArgumentException(nom + ": [formats:…] només es pot usar en preguntes [fitxer]");
            }
            return null;
        }
        if (!present) return null;
        try {
            return String.join(",", FormatsFitxer.normalitzaLlista(m.group(1)));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(nom + ": [formats:…] — " + e.getMessage());
        }
    }

    /** Rebutja els blocs que la correcció ignoraria per al tipus de pregunta. */
    private void validateBlocsPerTipus(String nom, QuestionType type, Map<String, String> blocks) {
        if (type == QuestionType.FILE_UPLOAD) {
            String estrany = blocks.keySet().stream().filter(b -> !b.equals("model")).findFirst().orElse(null);
            if (estrany != null) {
                throw new IllegalArgumentException(nom + ": :::" + estrany + " no es pot usar en preguntes [fitxer]; "
                        + "es corregeixen a mà (només es permet :::model, com a nota per al professor)");
            }
            return;
        }
        boolean text = type == QuestionType.TEXT || type == QuestionType.SHORT || type == QuestionType.LONG;
        if (blocks.containsKey("clau") && !text) {
            throw new IllegalArgumentException(nom + ": :::clau només es pot usar en preguntes TEXT, SHORT o LONG");
        }
        List<String> outputs = OUTPUT_BLOCS.stream().filter(blocks::containsKey).toList();
        if (!outputs.isEmpty() && !type.isExecutable()) {
            throw new IllegalArgumentException(nom + ": :::" + outputs.get(0)
                    + " només es pot usar en preguntes de codi (bash, PowerShell o Java)"
                    + (type == QuestionType.HTML_CSS ? ". Les preguntes HTML_CSS no s'executen: es corregeixen a mà" : ""));
        }
        if (outputs.size() > 1) {
            throw new IllegalArgumentException(nom + ": usa només un criteri de sortida; hi ha :::"
                    + String.join(", :::", outputs));
        }
        if (blocks.containsKey("test")) {
            if (!type.isScript()) {
                throw new IllegalArgumentException(nom + ": :::test només es pot usar en preguntes BASH_SCRIPT o PS_SCRIPT"
                        + (type.isExecutable() ? "; en aquest tipus usa un criteri :::output-*" : ""));
            }
            if (!outputs.isEmpty()) {
                throw new IllegalArgumentException(nom + ": no combinis :::test amb :::" + outputs.get(0)
                        + " (amb :::test, el criteri de sortida no s'aplicaria)");
            }
        }
    }

    private String validateClaus(String nom, BigDecimal pts, String claus) {
        if (claus == null) return null;
        try {
            ClausCorreccio.parse(claus, pts);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(nom + ": :::clau — " + e.getMessage());
        }
        return claus;
    }

    private void validateOutputCriteria(String nom, Map<String, String> blocks) {
        String contains = blocks.get("output-contains");
        if (contains != null && contains.isBlank()) {
            throw new IllegalArgumentException(nom + ": el bloc :::output-contains és buit");
        }
        String regex = blocks.get("output-regex");
        if (regex != null) {
            try {
                Pattern.compile(regex, Pattern.MULTILINE);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException(nom + ": :::output-regex no és una expressió regular vàlida ("
                        + e.getDescription() + ")");
            }
        }
    }

    private Question buildChoiceQuestion(String nom, int ordre, BigDecimal pts,
                                         String rawEnunciat, Map<String, String> blocks,
                                         Exam exam) {
        List<String> choiceLines = new ArrayList<>();
        List<String> textLines   = new ArrayList<>();
        Set<String> lletres = new LinkedHashSet<>();

        for (String line : rawEnunciat.split("\n")) {
            String t = line.trim();
            Matcher m = OPCIO.matcher(t);
            if (m.matches()) {
                String lletra = m.group(1).toLowerCase();
                if (lletra.charAt(0) > 'd') {
                    throw new IllegalArgumentException(nom + ": l'opció «" + t
                            + "» no és vàlida. Només s'admeten les opcions a) a d)");
                }
                if (!lletres.add(lletra)) {
                    throw new IllegalArgumentException(nom + ": l'opció " + lletra + ") està repetida");
                }
                if (m.group(2).isBlank()) {
                    throw new IllegalArgumentException(nom + ": l'opció " + lletra + ") no té text");
                }
                choiceLines.add(t.replaceFirst("^-\\s+", ""));
            } else {
                if (!choiceLines.isEmpty() && OPCIO_SENSE_GUIO.matcher(t).matches()) {
                    throw new IllegalArgumentException(nom + ": l'opció «" + t + "» ha de començar amb '- '");
                }
                textLines.add(line);
            }
        }

        if (choiceLines.isEmpty()) {
            throw new IllegalArgumentException(
                    nom + ": pregunta CHOICE sense opcions (usa '- a) ...', '- b) ...', etc.)");
        }

        String correctChoice = blocks.get("model");
        if (correctChoice == null || correctChoice.isBlank()) {
            throw new IllegalArgumentException(
                    nom + ": pregunta CHOICE sense resposta correcta (:::model ha de contenir la lletra)");
        }
        correctChoice = correctChoice.strip().toLowerCase().substring(0, 1);
        if (!lletres.contains(correctChoice)) {
            throw new IllegalArgumentException(nom + ": la resposta correcta «" + correctChoice
                    + "» no és cap de les opcions (" + String.join(", ", lletres) + ")");
        }

        return Question.builder()
                .exam(exam)
                .ordre(ordre)
                .tipus(QuestionType.CHOICE)
                .enunciat(String.join("\n", textLines).strip())
                .punts(pts)
                .choices(String.join("\n", choiceLines))
                .correctChoice(correctChoice)
                .build();
    }

    private void validateTotalPoints(Exam exam) {
        BigDecimal total = exam.getQuestions().stream()
                .filter(q -> q.getTipus() != QuestionType.SECTION)
                .map(Question::getPunts)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(new BigDecimal("10")) != 0) {
            throw new IllegalArgumentException(
                    "La suma de punts és " + total + " (ha de ser exactament 10)");
        }
    }
}
