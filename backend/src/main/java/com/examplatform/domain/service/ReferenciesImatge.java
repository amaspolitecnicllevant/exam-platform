package com.examplatform.domain.service;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Les imatges d'un enunciat s'hi referencien amb {@code ![text](fitxer:<id>)}, on id és el d'un QuestionFile. */
public final class ReferenciesImatge {

    private static final Pattern REFERENCIA = Pattern.compile(
            "fitxer:([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");

    private ReferenciesImatge() {}

    /** Substitueix els ids coneguts pels nous (p. ex. en duplicar un examen); la resta es deixa igual. */
    public static String remapeja(String text, Map<UUID, UUID> ids) {
        if (text == null || ids.isEmpty()) return text;
        Matcher m = REFERENCIA.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            UUID nou = ids.get(UUID.fromString(m.group(1)));
            m.appendReplacement(sb, Matcher.quoteReplacement("fitxer:" + (nou != null ? nou : m.group(1))));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
