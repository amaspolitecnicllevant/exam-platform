package com.examplatform.domain.service.emmagatzematge;

import com.examplatform.dto.EmmagatzematgeDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Auditoria de l'espai: suma, per examen, els fitxers de les preguntes i els lliuraments dels alumnes
 * (segons la BD) i els agrupa per professor, departament, cicle o mòdul. A més mesura el disc real, per
 * veure si hi ha fitxers que la BD no coneix o s'ha acabat l'espai.
 */
@Service
public class EmmagatzematgeService {

    @PersistenceContext
    private EntityManager em;

    private final Path arrel;

    @Autowired
    public EmmagatzematgeService(@Value("${execution.files-host-path}") String filesHostPath) {
        this.arrel = Path.of(filesHostPath).toAbsolutePath().normalize();
    }

    /** Constructor per a proves amb un EntityManager concret. */
    public EmmagatzematgeService(EntityManager em, String filesHostPath) {
        this(filesHostPath);
        this.em = em;
    }

    @Transactional(readOnly = true)
    public EmmagatzematgeDto auditoria(Agrupacio agrupacio) {
        return new EmmagatzematgeDto(AgregadorEspai.agrupa(usPerExamen(), agrupacio), disc());
    }

    /** Una fila per examen amb l'espai que ocupa, segons les metadades de la BD. */
    List<UsExamen> usPerExamen() {
        Map<UUID, long[]> preguntes = sumes("""
                select f.question.exam.id, count(f), coalesce(sum(f.fileSize), 0)
                from QuestionFile f group by f.question.exam.id""");
        Map<UUID, long[]> lliuraments = sumes("""
                select a.session.exam.id, count(a), coalesce(sum(a.fitxerMida), 0)
                from Answer a where a.fitxerRuta is not null group by a.session.exam.id""");

        @SuppressWarnings("unchecked")
        List<Object[]> examens = em.createQuery("""
                select e.id, e.title, u.id, u.name, m.id, m.codi, m.nom, c.id, c.nom, d.id, d.nom
                from Exam e join e.createdBy u
                left join e.modul m left join m.cicle c left join c.departament d""").getResultList();

        List<UsExamen> res = new ArrayList<>();
        for (Object[] r : examens) {
            UUID id = (UUID) r[0];
            long[] p = preguntes.getOrDefault(id, new long[2]);
            long[] l = lliuraments.getOrDefault(id, new long[2]);
            String modul = r[5] == null ? null : r[5] + " — " + r[6];
            res.add(new UsExamen(id, (String) r[1], (UUID) r[2], (String) r[3], (UUID) r[4], modul,
                    (UUID) r[7], (String) r[8], (UUID) r[9], (String) r[10],
                    (int) p[0], p[1], (int) l[0], l[1]));
        }
        return res;
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, long[]> sumes(String jpql) {
        Map<UUID, long[]> res = new HashMap<>();
        for (Object[] r : (List<Object[]>) em.createQuery(jpql).getResultList()) {
            res.put((UUID) r[0], new long[]{((Number) r[1]).longValue(), ((Number) r[2]).longValue()});
        }
        return res;
    }

    EmmagatzematgeDto.Disc disc() {
        Long lliure = null, total = null;
        try {
            Path existent = arrel;
            while (existent != null && !Files.exists(existent)) existent = existent.getParent();
            if (existent != null) {
                var store = Files.getFileStore(existent);
                lliure = store.getUsableSpace();
                total = store.getTotalSpace();
            }
        } catch (IOException ignorat) {
            // sense dades del disc: el camp queda a null
        }
        return new EmmagatzematgeDto.Disc(midaDirectori(arrel.resolve("questions")), midaDirectori(arrel.resolve("answers")),
                lliure, total);
    }

    /** Bytes dels fitxers d'un directori (recursiu); 0 si no existeix. */
    static long midaDirectori(Path dir) {
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).mapToLong(p -> {
                try { return Files.size(p); } catch (IOException e) { return 0; }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }
}
