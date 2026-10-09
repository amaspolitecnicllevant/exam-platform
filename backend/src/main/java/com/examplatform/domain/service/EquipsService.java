package com.examplatform.domain.service;

import com.examplatform.domain.model.Aula;
import com.examplatform.domain.model.EquipAula;
import com.examplatform.domain.model.EquipsReferencia;
import com.examplatform.domain.model.User;
import com.examplatform.dto.EquipsDto;
import com.examplatform.dto.EquipsDto.Estat;
import com.examplatform.infrastructure.persistence.AulaRepository;
import com.examplatform.infrastructure.persistence.EquipAulaRepository;
import com.examplatform.infrastructure.persistence.EquipsReferenciaRepository;
import com.examplatform.util.CidrUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Estat dels ordinadors de les aules. Cada ordinador envia periòdicament un informe (nom, integritat de la
 * configuració d'examen, si arriba a la plataforma i a Isard, estat del sistema); l'aplicació el
 * relliga amb l'aula per la IP d'origen i el compara amb la referència. L'aplicació no es connecta a cap
 * ordinador ni en guarda credencials.
 *
 * <p>Límit: qui administra un ordinador pot falsejar-ne l'informe (només el d'aquell ordinador: la IP
 * d'origen impedeix fer-ho en nom d'un altre) o deixar d'enviar-lo, cosa que surt com a «sense notícies».
 */
@Service
public class EquipsService {

    static final Pattern NOM = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$");
    public static final int MAX_INTEGRITAT = 100_000;
    static final int MAX_DIFERENCIES = 20;
    static final int DISC_MINIM_MB = 2000;

    private final AulaRepository aulaRepository;
    private final EquipAulaRepository equipRepository;
    private final EquipsReferenciaRepository referenciaRepository;
    private final AuditLogService auditLog;
    private final String token;
    private final Duration senseNoticies;
    private final Duration faTemps;
    private final int maxPerAula;

    public EquipsService(AulaRepository aulaRepository, EquipAulaRepository equipRepository,
                         EquipsReferenciaRepository referenciaRepository, AuditLogService auditLog,
                         @Value("${equips.token:}") String token,
                         @Value("${equips.sense-noticies-minuts:45}") int senseNoticiesMinuts,
                         @Value("${equips.avis-dies:7}") int avisDies,
                         @Value("${equips.max-per-aula:500}") int maxPerAula) {
        this.aulaRepository = aulaRepository;
        this.equipRepository = equipRepository;
        this.referenciaRepository = referenciaRepository;
        this.auditLog = auditLog;
        this.token = token == null ? "" : token;
        this.senseNoticies = Duration.ofMinutes(senseNoticiesMinuts);
        this.faTemps = Duration.ofDays(avisDies);
        this.maxPerAula = maxPerAula;
    }

    /** Un informe tal com el rep l'API. Els camps opcionals poden ser null. */
    public record Informe(String nom, String integritat, Boolean arribaPlataforma, Boolean arribaIsard,
                          String navegador, Integer discLliureMb, Long uptimeSegons, Integer usuarisDins) {}

    // ── Recepció d'informes ───────────────────────────────────────────────────

    @Transactional
    public void registraInforme(String tokenRebut, String ip, Informe inf, LocalDateTime ara) {
        if (token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La recepció d'informes d'ordinadors no està activada");
        }
        if (tokenRebut == null || !MessageDigest.isEqual(
                tokenRebut.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Testimoni no vàlid");
        }
        if (inf.nom() == null || !NOM.matcher(inf.nom()).matches()) {
            throw new IllegalArgumentException("Nom d'ordinador no vàlid");
        }
        String integritat = normalitza(inf.integritat());
        if (integritat.length() > MAX_INTEGRITAT) {
            throw new IllegalArgumentException("La llista d'integritat és massa llarga");
        }
        Aula aula = aulaDe(ip).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                "L'adreça " + ip + " no pertany a cap aula"));

        EquipAula equip = equipRepository.findByAulaIdAndNom(aula.getId(), inf.nom()).orElse(null);
        if (equip == null) {
            if (equipRepository.countByAulaId(aula.getId()) >= maxPerAula) {
                throw new IllegalArgumentException("L'aula ja té el màxim d'ordinadors");
            }
            equip = EquipAula.builder().aula(aula).nom(inf.nom()).build();
        }
        equip.setIp(ip);
        equip.setDarrerInforme(ara);
        equip.setIntegritat(integritat);
        equip.setIntegritatResum(resum(integritat));
        equip.setArribaPlataforma(inf.arribaPlataforma());
        equip.setArribaIsard(inf.arribaIsard());
        equip.setNavegador(retalla(inf.navegador(), 200));
        equip.setDiscLliureMb(inf.discLliureMb());
        equip.setArrencada(inf.uptimeSegons() == null || inf.uptimeSegons() < 0 ? null : ara.minusSeconds(inf.uptimeSegons()));
        equip.setUsuarisDins(inf.usuarisDins());
        equipRepository.save(equip);
    }

    private Optional<Aula> aulaDe(String ip) {
        return aulaRepository.findAll().stream()
                .filter(a -> CidrUtil.isInCidr(ip, a.getXarxaCidr()))
                .max(Comparator.comparingInt(a -> prefix(a.getXarxaCidr())));   // la més específica
    }

    private static int prefix(String cidr) {
        try { return Integer.parseInt(cidr.split("/")[1].trim()); } catch (RuntimeException e) { return 0; }
    }

    // ── Consulta ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EquipsDto equipsDeLAula(UUID aulaId, LocalDateTime ara) {
        if (!aulaRepository.existsById(aulaId)) throw new NoSuchElementException("Aula no trobada: " + aulaId);
        EquipsReferencia ref = referenciaRepository.findById(EquipsReferencia.ID).orElse(null);
        List<EquipsDto.Equip> equips = equipRepository.findByAulaIdOrderByNomAsc(aulaId).stream()
                .map(e -> dto(e, ref, ara)).toList();
        int preparats = 0, alterats = 0, silenci = 0, altres = 0, fa = 0;
        for (EquipsDto.Equip e : equips) {
            if (e.faTemps()) fa++;
            switch (e.estat()) {
                case PREPARAT -> preparats++;
                case ALTERAT -> alterats++;
                case SENSE_NOTICIES -> silenci++;
                default -> altres++;
            }
        }
        return new EquipsDto(ref == null ? null : new EquipsDto.Referencia(ref.getFixadaEl(), ref.getOrigenNom()),
                preparats, alterats, silenci, altres, fa, equips);
    }

    private EquipsDto.Equip dto(EquipAula e, EquipsReferencia ref, LocalDateTime ara) {
        Estat estat = estat(e, ref, ara);
        Duration sense = Duration.between(e.getDarrerInforme(), ara);
        return new EquipsDto.Equip(e.getId(), e.getNom(), e.getIp(), e.getDarrerInforme(), estat,
                sense.compareTo(faTemps) >= 0, Math.max(0, sense.toDays()),
                e.getArribaPlataforma(), e.getArribaIsard(), e.getNavegador(), e.getDiscLliureMb(), e.getArrencada(),
                e.getUsuarisDins(), avisos(e), estat == Estat.ALTERAT ? diferencies(ref.getIntegritat(), e.getIntegritat()) : List.of());
    }

    Estat estat(EquipAula e, EquipsReferencia ref, LocalDateTime ara) {
        if (e.getDarrerInforme().plus(senseNoticies).isBefore(ara)) return Estat.SENSE_NOTICIES;
        if (ref == null) return Estat.SENSE_REFERENCIA;
        if (!Objects.equals(e.getIntegritatResum(), ref.getIntegritatResum())) return Estat.ALTERAT;
        if (!Boolean.TRUE.equals(e.getArribaPlataforma())) return Estat.SENSE_PLATAFORMA;
        return Estat.PREPARAT;
    }

    static List<String> avisos(EquipAula e) {
        List<String> avisos = new ArrayList<>();
        if (Boolean.FALSE.equals(e.getArribaIsard())) avisos.add("No arriba a Isard");
        if (e.getDiscLliureMb() != null && e.getDiscLliureMb() < DISC_MINIM_MB) {
            avisos.add("Poc espai al disc (" + e.getDiscLliureMb() + " MB lliures)");
        }
        if (e.getUsuarisDins() != null && e.getUsuarisDins() > 0) {
            avisos.add("Hi ha " + e.getUsuarisDins() + (e.getUsuarisDins() == 1 ? " sessió oberta" : " sessions obertes"));
        }
        if (e.getNavegador() == null || e.getNavegador().isBlank()) avisos.add("No s'ha detectat cap navegador");
        return avisos;
    }

    /** Línies que difereixen: «+» les d'aquest ordinador que no són a la referència, «−» les de la referència que hi falten. */
    static List<String> diferencies(String referencia, String equip) {
        Set<String> r = new LinkedHashSet<>(linies(referencia)), q = new LinkedHashSet<>(linies(equip));
        List<String> dif = new ArrayList<>();
        for (String l : q) if (!r.contains(l)) dif.add("+ " + l);
        for (String l : r) if (!q.contains(l)) dif.add("− " + l);
        if (dif.size() > MAX_DIFERENCIES) {
            int mes = dif.size() - MAX_DIFERENCIES;
            dif = new ArrayList<>(dif.subList(0, MAX_DIFERENCIES));
            dif.add("… i " + mes + " diferències més");
        }
        return dif;
    }

    private static List<String> linies(String t) {
        return t == null || t.isEmpty() ? List.of() : Arrays.asList(t.split("\n"));
    }

    // ── Referència i neteja ───────────────────────────────────────────────────

    @Transactional
    public void fixaReferencia(UUID equipId, User admin, LocalDateTime ara) {
        EquipAula e = equipRepository.findById(equipId)
                .orElseThrow(() -> new NoSuchElementException("Ordinador no trobat: " + equipId));
        if (e.getIntegritat() == null || e.getIntegritat().isBlank()) {
            throw new IllegalStateException("Aquest ordinador no ha enviat cap llista d'integritat");
        }
        EquipsReferencia ref = referenciaRepository.findById(EquipsReferencia.ID)
                .orElseGet(() -> EquipsReferencia.builder().id(EquipsReferencia.ID).build());
        ref.setIntegritat(e.getIntegritat());
        ref.setIntegritatResum(e.getIntegritatResum());
        ref.setOrigenNom(e.getNom());
        ref.setFixadaPer(admin.getId());
        ref.setFixadaEl(ara);
        referenciaRepository.save(ref);
        auditLog.log(admin.getId(), "EQUIPS_REFERENCIA_FIXADA", "ordinador " + e.getNom() + " (" + e.getAula().getNom() + ")");
    }

    @Transactional
    public void esborra(UUID equipId, User admin) {
        EquipAula e = equipRepository.findById(equipId)
                .orElseThrow(() -> new NoSuchElementException("Ordinador no trobat: " + equipId));
        equipRepository.delete(e);
        auditLog.log(admin.getId(), "EQUIP_ESBORRAT", "ordinador " + e.getNom() + " (" + e.getAula().getNom() + ")");
    }

    // ── Ajudes ────────────────────────────────────────────────────────────────

    /** Salts de línia unificats, sense espais al final de cada línia ni línies buides al final. */
    static String normalitza(String t) {
        if (t == null) return "";
        String[] ls = t.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String l : ls) sb.append(l.stripTrailing()).append('\n');
        return sb.toString().stripTrailing();
    }

    static String resum(String t) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(t.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String retalla(String t, int max) {
        if (t == null) return null;
        String s = t.replaceAll("[\\r\\n]+", " ").strip();
        return s.length() <= max ? s : s.substring(0, max);
    }
}
