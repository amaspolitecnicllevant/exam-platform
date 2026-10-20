package com.examplatform.domain.service;

import com.examplatform.domain.model.*;
import com.examplatform.dto.ExamDto;
import com.examplatform.dto.ProgramacioDto;
import com.examplatform.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgramacioServiceTest {

    @Mock ModulRepository modulRepository;
    @Mock CompetenciaRepository competenciaRepository;
    @Mock UnitatTreballRepository unitatRepository;
    @Mock QuestionRepository questionRepository;
    @Mock ExamRepository examRepository;
    @Mock ImparticioRepository imparticioRepository;
    @Mock ExamService examService;
    @Mock ExamSessionRepository sessionRepository;

    ProgramacioService service;
    User professor;
    User admin;
    Modul modul;

    @BeforeEach
    void setUp() {
        service = new ProgramacioService(modulRepository, competenciaRepository, unitatRepository,
                questionRepository, examRepository, imparticioRepository, examService, sessionRepository);
        professor = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();
        admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
        Cicle cicle = Cicle.builder().id(UUID.randomUUID()).codi("ASIX").nom("ASIX").build();
        modul = Modul.builder().id(UUID.randomUUID()).codi("0485").nom("Mòdul").cicle(cicle).build();
        lenient().when(modulRepository.findById(modul.getId())).thenReturn(Optional.of(modul));
        lenient().when(imparticioRepository.professorImparteixModul(professor.getId(), modul.getId())).thenReturn(true);
        lenient().when(competenciaRepository.findByModulIdOrderByOrdreAsc(modul.getId())).thenReturn(List.of());
        lenient().when(unitatRepository.findByModulIdOrderByOrdreAsc(modul.getId())).thenReturn(List.of());
    }

    private static ProgramacioDto.Peticio peticio(List<ProgramacioDto.CompetenciaPeticio> cos, List<ProgramacioDto.UnitatPeticio> uts) {
        return new ProgramacioDto.Peticio(cos, uts);
    }

    private static ProgramacioDto.CompetenciaPeticio co(String codi) {
        return new ProgramacioDto.CompetenciaPeticio(codi, "Descripció " + codi);
    }

    private static ProgramacioDto.UnitatPeticio ut(String codi, String... cos) {
        return new ProgramacioDto.UnitatPeticio(codi, "Nom " + codi, List.of(cos));
    }

    // ── Accés ──────────────────────────────────────────────────────────────

    @Test
    void un_professor_que_no_imparteix_el_modul_no_hi_pot_accedir() {
        User altre = User.builder().id(UUID.randomUUID()).role(Role.PROFESSOR).build();
        when(imparticioRepository.professorImparteixModul(altre.getId(), modul.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.obte(modul.getId(), altre)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.substitueix(modul.getId(), peticio(List.of(), List.of()), altre))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void un_alumne_no_hi_pot_accedir_encara_que_ho_provi() {
        User alumne = User.builder().id(UUID.randomUUID()).role(Role.STUDENT).build();

        assertThatThrownBy(() -> service.obte(modul.getId(), alumne)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void l_admin_hi_pot_accedir_sense_imparticio() {
        assertThat(service.obte(modul.getId(), admin).competencies()).isEmpty();
        verifyNoInteractions(imparticioRepository);
    }

    // ── Substitució ────────────────────────────────────────────────────────

    @Test
    void desa_competencies_i_unitats_amb_ordre_i_en_majuscules() {
        service.substitueix(modul.getId(), peticio(
                List.of(co("co1"), co("CO2")), List.of(ut("ut01", "co1", "CO2"), ut("UT02", "CO2"))), professor);

        ArgumentCaptor<Collection<Competencia>> cos = ArgumentCaptor.forClass(Collection.class);
        verify(competenciaRepository).saveAll(cos.capture());
        assertThat(cos.getValue()).extracting(Competencia::getCodi).containsExactly("CO1", "CO2");
        assertThat(cos.getValue()).extracting(Competencia::getOrdre).containsExactly(0, 1);
        ArgumentCaptor<List<UnitatTreball>> uts = ArgumentCaptor.forClass(List.class);
        verify(unitatRepository).saveAll(uts.capture());
        assertThat(uts.getValue()).extracting(UnitatTreball::getCodi).containsExactly("UT01", "UT02");
        assertThat(uts.getValue().get(0).getCompetencies()).extracting(Competencia::getCodi).containsExactly("CO1", "CO2");
        assertThat(uts.getValue().get(1).getCompetencies()).extracting(Competencia::getCodi).containsExactly("CO2");
    }

    @Test
    void conserva_els_ids_de_les_competencies_i_unitats_que_ja_hi_eren() {
        Competencia antiga = Competencia.builder().id(UUID.randomUUID()).modul(modul).codi("CO1").descripcio("vella").build();
        UnitatTreball utAntiga = UnitatTreball.builder().id(UUID.randomUUID()).modul(modul).codi("UT01").build();
        when(competenciaRepository.findByModulIdOrderByOrdreAsc(modul.getId())).thenReturn(List.of(antiga));
        when(unitatRepository.findByModulIdOrderByOrdreAsc(modul.getId())).thenReturn(List.of(utAntiga));

        service.substitueix(modul.getId(), peticio(List.of(co("CO1")), List.of(ut("UT01", "CO1"))), admin);

        assertThat(antiga.getDescripcio()).isEqualTo("Descripció CO1");
        assertThat(utAntiga.getCompetencies()).containsExactly(antiga);
        verify(competenciaRepository, never()).deleteAll(argThatNoneEmpty());
    }

    private static Collection<Competencia> argThatNoneEmpty() {
        return org.mockito.ArgumentMatchers.argThat(c -> c != null && !c.isEmpty());
    }

    @Test
    void rebutja_codis_repetits_o_invalids() {
        assertThatThrownBy(() -> service.substitueix(modul.getId(), peticio(List.of(co("CO1"), co("co1")), List.of()), admin))
                .hasMessageContaining("repetida");
        assertThatThrownBy(() -> service.substitueix(modul.getId(), peticio(List.of(co("")), List.of()), admin))
                .hasMessageContaining("no és vàlid");
        assertThatThrownBy(() -> service.substitueix(modul.getId(), peticio(List.of(co("CO 1")), List.of()), admin))
                .hasMessageContaining("no és vàlid");
        assertThatThrownBy(() -> service.substitueix(modul.getId(), peticio(List.of(co("C".repeat(21))), List.of()), admin))
                .hasMessageContaining("no és vàlid");
        assertThatThrownBy(() -> service.substitueix(modul.getId(),
                peticio(List.of(co("CO1")), List.of(ut("UT01", "CO1"), ut("ut01", "CO1"))), admin))
                .hasMessageContaining("UT01").hasMessageContaining("repetida");
        verify(competenciaRepository, never()).saveAll(anyIterable());
    }

    @Test
    void rebutja_una_unitat_que_fa_servir_una_competencia_inexistent() {
        assertThatThrownBy(() -> service.substitueix(modul.getId(),
                peticio(List.of(co("CO1")), List.of(ut("UT01", "CO7"))), admin))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CO7").hasMessageContaining("no existeix");
        verify(unitatRepository, never()).saveAll(anyIterable());
    }

    @Test
    void no_deixa_treure_una_competencia_que_te_preguntes() {
        Competencia co2 = Competencia.builder().id(UUID.randomUUID()).modul(modul).codi("CO2").build();
        when(competenciaRepository.findByModulIdOrderByOrdreAsc(modul.getId())).thenReturn(List.of(co2));
        when(questionRepository.comptaCompetencia(modul.getId(), "CO2")).thenReturn(3L);

        assertThatThrownBy(() -> service.substitueix(modul.getId(), peticio(List.of(co("CO1")), List.of()), admin))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CO2").hasMessageContaining("preguntes");
        verify(competenciaRepository, never()).saveAll(anyIterable());
    }

    @Test
    void deixa_treure_una_competencia_sense_preguntes() {
        Competencia co2 = Competencia.builder().id(UUID.randomUUID()).modul(modul).codi("CO2").build();
        when(competenciaRepository.findByModulIdOrderByOrdreAsc(modul.getId())).thenReturn(List.of(co2));
        when(questionRepository.comptaCompetencia(modul.getId(), "CO2")).thenReturn(0L);

        service.substitueix(modul.getId(), peticio(List.of(co("CO1")), List.of()), admin);

        verify(competenciaRepository).deleteAll(org.mockito.ArgumentMatchers.<Iterable<Competencia>>argThat(c -> {
            List<Competencia> llista = new ArrayList<>();
            c.forEach(llista::add);
            return llista.equals(List.of(co2));
        }));
    }

    @Test
    void no_deixa_treure_una_unitat_que_avalua_un_examen() {
        UnitatTreball u = UnitatTreball.builder().id(UUID.randomUUID()).modul(modul).codi("UT03").build();
        when(unitatRepository.findByModulIdOrderByOrdreAsc(modul.getId())).thenReturn(List.of(u));
        when(examRepository.comptaPerUnitat(u.getId())).thenReturn(1L);

        assertThatThrownBy(() -> service.substitueix(modul.getId(), peticio(List.of(), List.of()), admin))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("UT03").hasMessageContaining("exàmens");
        verify(unitatRepository, never()).deleteAll(anyIterable());
    }

    @Test
    void importa_un_csv_com_una_substitucio_completa() {
        service.importaCsv(modul.getId(), "UT01;CO1;Instal·lar\nUT02;CO2;Xarxes", professor);

        ArgumentCaptor<List<UnitatTreball>> uts = ArgumentCaptor.forClass(List.class);
        verify(unitatRepository).saveAll(uts.capture());
        assertThat(uts.getValue()).extracting(UnitatTreball::getCodi).containsExactly("UT01", "UT02");
    }

    // ── UT de l'examen ─────────────────────────────────────────────────────

    private Exam examen() {
        Exam e = Exam.builder().id(UUID.randomUUID()).title("E").createdBy(professor).modul(modul).build();
        when(examService.getEntity(e.getId())).thenReturn(e);
        return e;
    }

    private UnitatTreball unitat(Modul m, String codi, String... cos) {
        UnitatTreball u = UnitatTreball.builder().id(UUID.randomUUID()).modul(m).codi(codi).build();
        for (String c : cos) u.getCompetencies().add(Competencia.builder().id(UUID.randomUUID()).modul(m).codi(c).build());
        lenient().when(unitatRepository.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void assigna_les_unitats_a_l_examen() {
        Exam e = examen();
        UnitatTreball u1 = unitat(modul, "UT01", "CO1");
        when(examRepository.save(e)).thenReturn(e);

        ExamDto dto = service.assignaUnitats(e.getId(), List.of(u1.getId()), professor);

        verify(examService).assertOwnership(e, professor);
        assertThat(dto.unitats()).extracting(ProgramacioDto.Unitat::codi).containsExactly("UT01");
    }

    @Test
    void l_examen_ha_de_tenir_modul_per_assignar_unitats() {
        Exam e = examen();
        e.setModul(null);

        assertThatThrownBy(() -> service.assignaUnitats(e.getId(), List.of(UUID.randomUUID()), professor))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mòdul");
    }

    @Test
    void rebutja_una_unitat_d_un_altre_modul() {
        Exam e = examen();
        Modul altre = Modul.builder().id(UUID.randomUUID()).codi("0001").build();
        UnitatTreball estranya = unitat(altre, "UT09", "CO1");

        assertThatThrownBy(() -> service.assignaUnitats(e.getId(), List.of(estranya.getId()), professor))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("UT09").hasMessageContaining("no és del mòdul");
        assertThat(e.getUnitats()).isEmpty();
    }

    @Test
    void unitats_que_deixen_preguntes_amb_competencia_fora_de_les_ut_nomes_donen_un_avis() {
        Exam e = examen();
        e.getQuestions().add(Question.builder().id(UUID.randomUUID()).exam(e).ordre(1)
                .tipus(QuestionType.SHORT).competencia("CO3").build());
        UnitatTreball antiga = unitat(modul, "UT01", "CO3");
        e.getUnitats().add(antiga);
        UnitatTreball nova = unitat(modul, "UT02", "CO1");
        modul.setCompetencies(new java.util.ArrayList<>(List.of(
                Competencia.builder().id(UUID.randomUUID()).modul(modul).codi("CO1").build(),
                Competencia.builder().id(UUID.randomUUID()).modul(modul).codi("CO3").build())));
        when(examRepository.save(e)).thenReturn(e);

        ExamDto dto = service.assignaUnitats(e.getId(), List.of(nova.getId()), professor);

        assertThat(e.getUnitats()).containsExactly(nova);
        assertThat(dto.avisos()).anyMatch(a -> a.contains("P1: CO3"));
    }

    @Test
    void fixa_les_competencies_de_l_examen_en_majuscules_sense_repetits_i_amb_codis_lliures() {
        Exam e = examen();
        when(examRepository.save(e)).thenReturn(e);

        ExamDto dto = service.fixaCompetencies(e.getId(), java.util.Arrays.asList("co2", " CO1 ", "CO2", "", null, "CO99"), professor);

        assertThat(dto.competenciesExamen()).containsExactly("CO1", "CO2", "CO99");
        assertThat(dto.avisos()).anyMatch(a -> a.contains("CO1, CO2, CO99"));
    }

    @Test
    void fixa_competencies_rebutja_un_codi_amb_format_invalid() {
        Exam e = examen();

        assertThatThrownBy(() -> service.fixaCompetencies(e.getId(), List.of("CO 1!"), professor))
                .isInstanceOf(IllegalArgumentException.class);
        verify(examRepository, never()).save(any());
    }

    @Test
    void una_llista_buida_treu_la_restriccio() {
        Exam e = examen();
        e.getUnitats().add(unitat(modul, "UT01", "CO1"));
        when(examRepository.save(e)).thenReturn(e);

        ExamDto dto = service.assignaUnitats(e.getId(), List.of(), professor);

        assertThat(dto.unitats()).isEmpty();
    }
}
