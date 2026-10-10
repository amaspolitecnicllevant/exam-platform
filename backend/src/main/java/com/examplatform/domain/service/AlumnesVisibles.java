package com.examplatform.domain.service;

import com.examplatform.domain.model.Role;
import com.examplatform.domain.model.User;
import com.examplatform.infrastructure.persistence.GrupRepository;
import com.examplatform.infrastructure.persistence.ImparticioRepository;
import com.examplatform.infrastructure.persistence.MatriculaRepository;
import com.examplatform.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Quins alumnes pot veure i triar un usuari. L'administració, tots. Un professor, els alumnes de les assignatures (mòduls) que
 * imparteix, els dels grups que ha creat (si no, en desar un grup se'n perdrien membres) i els comptes que ha creat ell mateix (si no,
 * un alumne acabat de donar d'alta «desapareixeria» de la llista). La resta d'alumnes no els veu ni els pot afegir a un grup o a un examen.
 */
@Service
@RequiredArgsConstructor
public class AlumnesVisibles {

    private final ImparticioRepository imparticioRepository;
    private final MatriculaRepository matriculaRepository;
    private final GrupRepository grupRepository;
    private final UserRepository userRepository;

    /** Identificadors dels alumnes visibles per a l'usuari; {@code null} vol dir tots (només l'administració). */
    @Transactional(readOnly = true)
    public Set<UUID> idsVisibles(User usuari) {
        if (usuari.getRole() == Role.ADMIN) return null;
        Set<UUID> ids = new HashSet<>();
        if (usuari.getRole() == Role.PROFESSOR) {
            List<UUID> moduls = imparticioRepository.findModulIdsByProfessorId(usuari.getId());
            if (!moduls.isEmpty()) {
                matriculaRepository.findByModulIdsAmbDetall(moduls).forEach(m -> ids.add(m.getAlumne().getId()));
            }
            grupRepository.findByCreatedByIdWithStudents(usuari.getId())
                    .forEach(g -> g.getStudents().forEach(s -> ids.add(s.getId())));
            userRepository.findByCreatPer(usuari.getId())
                    .stream().filter(u -> u.getRole() == Role.STUDENT).forEach(u -> ids.add(u.getId()));
        }
        return ids;
    }

    /** Falla si algun dels alumnes no és visible per a l'usuari (l'administració no té cap límit). */
    @Transactional(readOnly = true)
    public void assertVisibles(User usuari, Collection<UUID> alumneIds) {
        Set<UUID> visibles = idsVisibles(usuari);
        if (visibles == null || alumneIds == null) return;
        if (alumneIds.stream().anyMatch(id -> !visibles.contains(id))) {
            throw new AccessDeniedException("Hi ha alumnes que no són de les teves assignatures");
        }
    }
}
