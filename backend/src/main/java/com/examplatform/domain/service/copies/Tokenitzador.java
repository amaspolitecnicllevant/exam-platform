package com.examplatform.domain.service.copies;

import com.examplatform.domain.model.QuestionType;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converteix una resposta en una seqüència de tokens normalitzats, amb la posició de cada token
 * dins del text original (per poder ressaltar després els fragments coincidents).
 *
 * <ul>
 *   <li>Text: paraules en minúscules i sense accents.</li>
 *   <li>Codi: sense comentaris ni espais; els noms de variables o identificadors es substitueixen
 *       per un comodí, perquè canviar-los no amagui una còpia.</li>
 * </ul>
 */
public final class Tokenitzador {

    /** Token normalitzat i el seu interval [inici, fi) al text original. */
    public record Token(String valor, int inici, int fi) {}

    private Tokenitzador() {}

    public static List<Token> tokens(String text, QuestionType tipus) {
        if (text == null || text.isBlank()) return List.of();
        return switch (tipus) {
            case TEXT, SHORT, LONG -> text(text);
            case BASH_SCRIPT, BASH_CMD -> codi(text, BASH);
            case PS_SCRIPT, PS_CMD -> codi(text, POWERSHELL);
            case JAVA_PROG -> codi(text, JAVA);
            case HTML_CSS -> codi(text, HTML);
            default -> List.of();
        };
    }

    // ── Text ────────────────────────────────────────────────────────────────

    private static final Pattern PARAULA = Pattern.compile("[\\p{L}\\p{N}]+");

    private static List<Token> text(String text) {
        List<Token> out = new ArrayList<>();
        Matcher m = PARAULA.matcher(text);
        while (m.find()) out.add(new Token(normalitza(m.group()), m.start(), m.end()));
        return out;
    }

    static String normalitza(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    // ── Codi ────────────────────────────────────────────────────────────────

    private record Llenguatge(Pattern lexer, Set<String> reservades, boolean renombraIdentificadors) {}

    private static final Pattern VAR_EN_CADENA = Pattern.compile("\\$\\{?[A-Za-z_]\\w*\\}?|\\$[0-9#@?*]");

    private static final String CADENES = "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'";

    private static final Llenguatge BASH = new Llenguatge(Pattern.compile(
            "(?<com>#![^\\n]*|#[^\\n]*)"
                    + "|(?<str>" + CADENES + ")"
                    + "|(?<var>\\$\\{?[A-Za-z_]\\w*\\}?|\\$[0-9#@?*]|[A-Za-z_]\\w*(?==))"
                    + "|(?<id>[A-Za-z_][\\w-]*)"
                    + "|(?<num>\\d+(?:\\.\\d+)?)"
                    + "|(?<op>[^\\s\\w])"), Set.of(), false);

    private static final Llenguatge POWERSHELL = new Llenguatge(Pattern.compile(
            "(?<com><#[\\s\\S]*?#>|#[^\\n]*)"
                    + "|(?<str>" + CADENES + ")"
                    + "|(?<var>\\$\\{?[A-Za-z_]\\w*\\}?)"
                    + "|(?<id>[A-Za-z_][\\w-]*)"
                    + "|(?<num>\\d+(?:\\.\\d+)?)"
                    + "|(?<op>[^\\s\\w])", Pattern.CASE_INSENSITIVE), Set.of(), false);

    private static final Set<String> JAVA_RESERVADES = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "new", "package", "private",
            "protected", "public", "return", "short", "static", "super", "switch", "this", "throw", "throws",
            "try", "void", "while", "var", "true", "false", "null",
            // API habitual: forma part de l'estructura, no és un nom triat per l'alumne
            "String", "System", "out", "in", "println", "print", "printf", "Math", "Scanner", "Integer",
            "Double", "List", "ArrayList", "Map", "HashMap", "main", "args", "length", "equals",
            "nextInt", "nextLine", "charAt", "substring", "size", "add", "get", "put");

    private static final Llenguatge JAVA = new Llenguatge(Pattern.compile(
            "(?<com>//[^\\n]*|/\\*[\\s\\S]*?\\*/)"
                    + "|(?<str>" + CADENES + ")"
                    + "|(?<id>[A-Za-z_$][\\w$]*)"
                    + "|(?<num>\\d+(?:\\.\\d+)?)"
                    + "|(?<op>[^\\s\\w])"), JAVA_RESERVADES, true);

    private static final Llenguatge HTML = new Llenguatge(Pattern.compile(
            "(?<com><!--[\\s\\S]*?-->|/\\*[\\s\\S]*?\\*/)"
                    + "|(?<str>" + CADENES + ")"          // valors d'atributs: es substitueixen
                    + "|(?<id>[\\p{L}_][\\p{L}\\p{N}_-]*)"
                    + "|(?<num>\\d+(?:\\.\\d+)?)"
                    + "|(?<op>[^\\s\\p{L}\\p{N}])"), Set.of(), false);

    private static List<Token> codi(String text, Llenguatge ll) {
        List<Token> out = new ArrayList<>();
        Matcher m = ll.lexer().matcher(text);
        while (m.find()) {
            if (m.group("com") != null) continue;
            String valor;
            if (m.group("str") != null) {
                // Les cadenes compten pel seu contingut (un missatge copiat és un indici),
                // excepte a HTML, on són valors d'atributs (noms de classes, rutes…)
                String dins = m.group("str");
                String contingut = dins.substring(1, dins.length() - 1);
                if (ll == BASH || ll == POWERSHELL) {
                    // les variables dins de les cadenes també es renombren ("$dir" = "$carpeta")
                    contingut = VAR_EN_CADENA.matcher(contingut).replaceAll("VAR");
                }
                valor = ll == HTML ? "VAL" : "\"" + normalitza(contingut).strip() + "\"";
            } else if (grup(m, "var") != null) {
                valor = "VAR";
            } else if (m.group("id") != null) {
                String id = m.group("id");
                if (ll.renombraIdentificadors()) {
                    valor = ll.reservades().contains(id) ? id : "ID";
                } else {
                    valor = id.toLowerCase(Locale.ROOT);
                }
            } else if (m.group("num") != null) {
                valor = m.group("num");
            } else {
                valor = m.group("op");
            }
            out.add(new Token(valor, m.start(), m.end()));
        }
        return out;
    }

    private static String grup(Matcher m, String nom) {
        try {
            return m.group(nom);
        } catch (IllegalArgumentException e) {
            return null;   // el llenguatge no té aquest grup
        }
    }
}
