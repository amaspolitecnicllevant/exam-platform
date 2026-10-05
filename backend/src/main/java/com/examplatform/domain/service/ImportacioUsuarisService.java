package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.ImportacioDto;
import com.examplatform.infrastructure.persistence.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Importa usuaris des d'un CSV. Columnes (la capçalera pot ser en català o en anglès, en qualsevol ordre):
 * <pre>
 *   nom | name              obligatòria
 *   email | correu          obligatòria
 *   contrasenya | password  en blanc = se'n genera una (es retorna una sola vegada)
 *   rol | role              en blanc = STUDENT (un professor només pot crear alumnes)
 *   modul                   codi del mòdul on matricular l'alumne (opcional)
 *   curs                    curs de la matrícula (opcional; per defecte, el curs actiu)
 *   grup                    nom del grup on afegir l'alumne; si no existeix, es crea (opcional)
 * </pre>
 * Sense capçalera s'entén el format antic: nom, email, contrasenya, rol. Accepta el separador ";"
 * (el que fa servir Excel en català) i fitxers en UTF-8 o Windows-1252.
 * Si el correu ja té compte, no es modifica: només es matricula o s'afegeix al grup.
 */
@Service
@RequiredArgsConstructor
public class ImportacioUsuarisService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** Sense caràcters que es confonen (l/1, o/0): les contrasenyes es copien a mà. */
    private static final String LLETRES = "abcdefghjkmnpqrstuvwxyz";
    private static final String XIFRES = "23456789";
    private static final int LLARGADA_CONTRASENYA = 10;
    private static final SecureRandom ATZAR = new SecureRandom();

    /** Noms de columna acceptats → nom intern. */
    private static final Map<String, String> COLUMNES = Map.ofEntries(
            Map.entry("nom", "nom"), Map.entry("name", "nom"),
            Map.entry("email", "email"), Map.entry("correu", "email"), Map.entry("e-mail", "email"),
            Map.entry("contrasenya", "contrasenya"), Map.entry("password", "contrasenya"),
            Map.entry("rol", "rol"), Map.entry("role", "rol"),
            Map.entry("modul", "modul"), Map.entry("module", "modul"),
            Map.entry("curs", "curs"),
            Map.entry("grup", "grup"), Map.entry("group", "grup"));
    private static final List<String> FORMAT_ANTIC = List.of("nom", "email", "contrasenya", "rol");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModulRepository modulRepository;
    private final MatriculaRepository matriculaRepository;
    private final GrupRepository grupRepository;
    private final ImparticioRepository imparticioRepository;
    private final ConfiguracioService configuracioService;

    @Transactional
    public ImportacioDto importa(byte[] contingut, Role rolForcat, User caller) throws IOException {
        if (rolForcat != null && caller.getRole() != Role.ADMIN && rolForcat != Role.STUDENT) {
            throw new AccessDeniedException("Només un administrador pot crear usuaris amb rol " + rolForcat);
        }
        String text = decodifica(contingut);
        List<CSVRecord> files;
        try (CSVParser csv = CSVFormat.DEFAULT.builder().setDelimiter(separador(text)).setTrim(true)
                .setIgnoreEmptyLines(true).build().parse(new StringReader(text))) {
            files = csv.getRecords();
        }
        if (files.isEmpty()) throw new IllegalArgumentException("El fitxer és buit");

        List<String> columnes = capcalera(files.get(0));
        boolean ambCapcalera = columnes != null;
        if (!ambCapcalera) columnes = FORMAT_ANTIC;
        if (!columnes.contains("nom") || !columnes.contains("email")) {
            throw new IllegalArgumentException("Falten columnes obligatòries: cal «nom» i «email»");
        }

        Estat e = new Estat(caller);
        for (int i = ambCapcalera ? 1 : 0; i < files.size(); i++) {
            CSVRecord fila = files.get(i);
            long num = fila.getRecordNumber();
            try {
                String error = importaFila(valors(columnes, fila), e);
                if (error != null) e.errors.add("Fila " + num + ": " + error);
            } catch (RuntimeException ex) {
                e.errors.add("Fila " + num + ": " + ex.getMessage());
            }
        }
        e.grupsModificats.forEach(grupRepository::save);
        return new ImportacioDto(e.creats, e.existents, e.matriculats, e.afegitsAGrup,
                e.grupsCreats, e.errors, e.contrasenyes);
    }

    /** Estat d'una importació: recomptes i memòria cau de mòduls i grups. */
    private final class Estat {
        final User caller;
        final Map<String, Modul> moduls = new HashMap<>();
        final Map<String, Grup> grups = new HashMap<>();
        final Set<Grup> grupsModificats = new LinkedHashSet<>();
        final List<String> grupsCreats = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final List<ImportacioDto.Credencial> contrasenyes = new ArrayList<>();
        int creats, existents, matriculats, afegitsAGrup;
        final String cursActiu;

        Estat(User caller) {
            this.caller = caller;
            this.cursActiu = configuracioService.get().getCursActiu();
            // Els grups on es pot afegir alumnes són els propis (com a la resta de l'aplicació)
            for (Grup g : grupRepository.findByCreatedByIdWithStudents(caller.getId())) {
                grups.putIfAbsent(clau(g.getName()), g);
            }
        }
    }

    /** @return el missatge d'error de la fila, o null si s'ha importat */
    private String importaFila(Map<String, String> v, Estat e) {
        String nom = v.getOrDefault("nom", "");
        String email = v.getOrDefault("email", "").toLowerCase(Locale.ROOT);
        String contrasenya = v.getOrDefault("contrasenya", "");
        String codiModul = v.getOrDefault("modul", "");
        String curs = v.getOrDefault("curs", "");
        String nomGrup = v.getOrDefault("grup", "");
        if (curs.isEmpty()) curs = e.cursActiu;

        if (nom.isEmpty() || nom.length() > 255) return "falta el nom o és massa llarg";
        if (email.length() > 255 || !EMAIL.matcher(email).matches()) return "el correu «" + email + "» no és vàlid";
        Role rol;
        try {
            String r = v.getOrDefault("rol", "");
            rol = r.isEmpty() ? Role.STUDENT : Role.valueOf(r.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return "rol desconegut «" + v.get("rol") + "» (STUDENT, PROFESSOR o ADMIN)";
        }
        if (e.caller.getRole() != Role.ADMIN && rol != Role.STUDENT) {
            return "només un administrador pot crear usuaris amb rol " + rol;
        }
        boolean ambMatricula = !codiModul.isEmpty() || !nomGrup.isEmpty();
        if (ambMatricula && rol != Role.STUDENT) return "només es poden matricular o afegir a grups alumnes";

        // Mòdul: es valida abans de crear res, perquè una fila errònia no deixi un compte a mitges
        Modul modul = null;
        if (!codiModul.isEmpty()) {
            modul = e.moduls.computeIfAbsent(codiModul, c -> modulRepository.findByCodi(c).orElse(null));
            if (modul == null) return "no existeix cap mòdul amb el codi «" + codiModul + "»";
            if (e.caller.getRole() != Role.ADMIN
                    && !imparticioRepository.professorImparteixModul(e.caller.getId(), modul.getId())) {
                return "no imparteixes el mòdul " + modul.getCodi();
            }
            if (curs.length() > 20) return "el curs «" + curs + "» no és vàlid";
        }
        if (nomGrup.length() > 255) return "el nom del grup és massa llarg";

        User usuari = userRepository.findByEmail(email).orElse(null);
        if (usuari != null) {
            if (ambMatricula && usuari.getRole() != Role.STUDENT) return email + " ja té un compte que no és d'alumne";
            e.existents++;
        } else {
            boolean generada = contrasenya.isEmpty();
            if (generada) contrasenya = generaContrasenya();
            else if (contrasenya.length() < 8 || contrasenya.length() > 72) {
                return "la contrasenya ha de tenir entre 8 i 72 caràcters (o deixa-la en blanc perquè se'n generi una)";
            }
            usuari = userRepository.save(User.builder().name(nom).email(email)
                    .passwordHash(passwordEncoder.encode(contrasenya)).role(rol).build());
            e.creats++;
            if (generada) e.contrasenyes.add(new ImportacioDto.Credencial(nom, email, contrasenya));
        }

        if (modul != null && !matriculaRepository.existsByAlumneIdAndModulIdAndCurs(usuari.getId(), modul.getId(), curs)) {
            matriculaRepository.save(Matricula.builder().alumne(usuari).modul(modul).curs(curs).build());
            e.matriculats++;
        }
        if (!nomGrup.isEmpty()) {
            Grup grup = e.grups.get(clau(nomGrup));
            if (grup == null) {
                grup = grupRepository.save(Grup.builder().name(nomGrup).createdBy(e.caller).modul(modul).build());
                e.grups.put(clau(nomGrup), grup);
                e.grupsCreats.add(nomGrup);
            }
            if (grup.getStudents().add(usuari)) {
                e.afegitsAGrup++;
                e.grupsModificats.add(grup);
            }
        }
        return null;
    }

    static String generaContrasenya() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < LLARGADA_CONTRASENYA; i++) {
            // Dues xifres en posicions fixes perquè sempre n'hi hagi
            String font = (i == 4 || i == 8) ? XIFRES : LLETRES;
            sb.append(font.charAt(ATZAR.nextInt(font.length())));
        }
        return sb.toString();
    }

    /**
     * Llegeix la capçalera: és capçalera si té algun nom de columna conegut i cap adreça de correu.
     * Retorna null si la primera fila ja són dades (format antic sense capçalera).
     */
    private static List<String> capcalera(CSVRecord fila) {
        List<String> noms = new ArrayList<>();
        boolean ambCorreu = false;
        for (String cel : fila) {
            noms.add(COLUMNES.getOrDefault(normalitza(cel), "?"));
            ambCorreu |= cel.contains("@");
        }
        return !ambCorreu && noms.stream().anyMatch(n -> !"?".equals(n)) ? noms : null;
    }

    private static Map<String, String> valors(List<String> columnes, CSVRecord fila) {
        Map<String, String> v = new HashMap<>();
        for (int i = 0; i < columnes.size() && i < fila.size(); i++) {
            if (!"?".equals(columnes.get(i))) v.put(columnes.get(i), fila.get(i).strip());
        }
        return v;
    }

    /** Minúscules, sense accents ni BOM: «Mòdul» → «modul». */
    private static String normalitza(String s) {
        String t = s.replace("﻿", "").strip().toLowerCase(Locale.ROOT);
        return Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static String clau(String nomGrup) {
        return nomGrup.strip().toLowerCase(Locale.ROOT);
    }

    /** Excel en català desa els CSV amb ";": si la primera línia en té i no té comes, és el separador. */
    private static char separador(String text) {
        String primera = text.lines().findFirst().orElse("");
        return primera.contains(";") && !primera.contains(",") ? ';' : ',';
    }

    /** UTF-8 (amb BOM o sense) o, si no ho és, Windows-1252 (el que fa servir Excel a Windows). */
    static String decodifica(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString().replace("﻿", "");
        } catch (CharacterCodingException noEsUtf8) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }
}
