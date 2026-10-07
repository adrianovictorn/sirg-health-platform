package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.CotaUtilizacaoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.OcupacaoProfissionalViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.PeriodoIndicador;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.GrupoRelatorio;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Profissional;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Unidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.User;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.Roles;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CotaUnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.GrupoRelatorioRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.ProfissionalRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UnidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.UserRepository;

/**
 * Indicadores gerenciais de cota, com massa conhecida e o valor esperado
 * calculado a mao. As cotas ficam numa unidade propria do teste e a consulta
 * usa o filtro dessa unidade.
 */
@SpringBootTest
@Transactional
class IndicadoresCotaIT {

    @Autowired private IndicadoresCotaService indicadoresService;
    @Autowired private CotaUnidadeRepository cotaRepository;
    @Autowired private UnidadeRepository unidadeRepository;
    @Autowired private GrupoRelatorioRepository grupoRelatorioRepository;
    @Autowired private EspecialidadeRepository especialidadeRepository;
    @Autowired private ProfissionalRepository profissionalRepository;
    @Autowired private UserRepository userRepository;

    private String sufixo;
    private Unidade unidade;
    private GrupoRelatorio grupoDeUnidades;
    private User admin;
    private Especialidade espA;
    private Especialidade espB;
    private Especialidade espC;

    private YearMonth mesAtual;
    private YearMonth mesPassado;
    private String de;
    private String ate;

    @BeforeEach
    void setUp() {
        sufixo = "_IND_COTA_" + System.nanoTime();

        grupoDeUnidades = new GrupoRelatorio();
        grupoDeUnidades.setCodigo("GU" + sufixo);
        grupoDeUnidades.setNome("Grupo de Unidades" + sufixo);
        grupoDeUnidades.setAtivo(true);
        grupoDeUnidades = grupoRelatorioRepository.saveAndFlush(grupoDeUnidades);

        unidade = new Unidade();
        unidade.setNome("Unidade Cotas" + sufixo);
        unidade.setCodigo("UIC" + sufixo);
        unidade.setAtivo(true);
        unidade.setGrupoRelatorio(grupoDeUnidades);
        unidade = unidadeRepository.saveAndFlush(unidade);

        admin = usuario(Roles.ADMIN);
        espA = especialidade("A");
        espB = especialidade("B");
        espC = especialidade("C");

        mesAtual = YearMonth.now(PeriodoIndicador.FUSO);
        mesPassado = mesAtual.minusMonths(1);
        // Do primeiro dia do mes passado ate hoje: pega os dois meses.
        de = mesPassado.atDay(1).toString();
        ate = LocalDate.now(PeriodoIndicador.FUSO).toString();
    }

    @Test
    @DisplayName("utilizacao: media por cota, esgotadas e ociosas, MENSAL e DATA separadas")
    void utilizacaoDeCota() {
        // MENSAL do mes passado: 4/10 (40%) e 0/5 (ociosa: periodo encerrado sem uso).
        mensal(unidade, null, espA, mesPassado, 10, 4, true);
        mensal(unidade, null, espB, mesPassado, 5, 0, true);
        // MENSAL do mes corrente sem uso: ainda pode ser usada, nao e ociosa.
        mensal(unidade, null, espA, mesAtual, 5, 0, true);
        // Inativa: fora de tudo.
        mensal(unidade, null, espC, mesPassado, 9, 9, false);
        // Pool do grupo de unidades da unidade: 1/4 (25%). Entra no filtro por unidade.
        mensal(null, grupoDeUnidades, espA, mesPassado, 4, 1, true);
        // DATA: 2/2 (esgotada) e uma sem vagas (0/0), que nao entra na media nem e esgotada.
        porData(espA, mesPassado.atDay(10), 2, 2, null, null, null);
        porData(espB, mesPassado.atDay(10), 0, 0, null, null, null);

        CotaUtilizacaoViewDTO resultado = indicadoresService.utilizacao(unidade.getId(), de, ate, admin.getCpf());

        assertThat(resultado.porTipo()).extracting(CotaUtilizacaoViewDTO.Linha::tipo).containsExactly("MENSAL", "DATA");

        CotaUtilizacaoViewDTO.Linha mensais = resultado.porTipo().get(0);
        assertThat(mensais.cotas()).isEqualTo(4);
        assertThat(mensais.esgotadas()).isZero();
        assertThat(mensais.ociosas()).isEqualTo(1);
        // (0,40 + 0 + 0 + 0,25) / 4
        assertThat(mensais.utilizacaoMedia()).isCloseTo(0.1625, within(0.0001));

        CotaUtilizacaoViewDTO.Linha porDia = resultado.porTipo().get(1);
        assertThat(porDia.cotas()).isEqualTo(2);
        assertThat(porDia.esgotadas()).isEqualTo(1);
        assertThat(porDia.ociosas()).isZero();
        assertThat(porDia.utilizacaoMedia()).isCloseTo(1.0, within(0.0001));

        // Por titular: a unidade e o pool do grupo em linhas separadas.
        CotaUtilizacaoViewDTO.Linha daUnidade = titular(resultado, "MENSAL", unidade.getNome());
        assertThat(daUnidade.unidadeId()).isEqualTo(unidade.getId());
        assertThat(daUnidade.cotas()).isEqualTo(3);
        assertThat(daUnidade.ociosas()).isEqualTo(1);
        CotaUtilizacaoViewDTO.Linha doGrupo = titular(resultado, "MENSAL", grupoDeUnidades.getNome());
        assertThat(doGrupo.unidadeId()).isNull();
        assertThat(doGrupo.cotas()).isEqualTo(1);
        assertThat(doGrupo.utilizacaoMedia()).isCloseTo(0.25, within(0.0001));
        assertThat(titular(resultado, "DATA", unidade.getNome()).esgotadas()).isEqualTo(1);
    }

    @Test
    @DisplayName("utilizacao: periodo so do mes passado nao enxerga a cota do mes corrente")
    void utilizacaoRespeitaOPeriodo() {
        mensal(unidade, null, espA, mesPassado, 10, 5, true);
        mensal(unidade, null, espA, mesAtual, 10, 10, true);

        CotaUtilizacaoViewDTO resultado = indicadoresService.utilizacao(unidade.getId(),
                mesPassado.atDay(1).toString(), mesPassado.atEndOfMonth().toString(), admin.getCpf());

        assertThat(resultado.porTipo()).hasSize(1);
        assertThat(resultado.porTipo().get(0).cotas()).isEqualTo(1);
        assertThat(resultado.porTipo().get(0).esgotadas()).isZero();
        assertThat(resultado.porTipo().get(0).utilizacaoMedia()).isCloseTo(0.5, within(0.0001));
    }

    @Test
    @DisplayName("utilizacao: unidade sem cota devolve listas vazias, nao erro")
    void semCota() {
        CotaUtilizacaoViewDTO resultado = indicadoresService.utilizacao(unidade.getId(), de, ate, admin.getCpf());

        assertThat(resultado.porTipo()).isEmpty();
        assertThat(resultado.porTitular()).isEmpty();
        assertThat(indicadoresService.ocupacaoPorProfissional(unidade.getId(), de, ate, admin.getCpf())
                .porProfissional()).isEmpty();
    }

    @Test
    @DisplayName("ocupacao: vagas ofertadas x agendadas por profissional da cota e por faixa de horario")
    void ocupacaoPorProfissionalEHorario() {
        Profissional drA = profissional("Dra. Ana");
        Profissional drB = profissional("Dr. Bruno");
        LocalTime oito = LocalTime.of(8, 0);
        LocalTime meioDia = LocalTime.of(12, 0);
        porData(espA, mesPassado.atDay(12), 8, 6, drA, oito, meioDia);
        porData(espA, mesPassado.atDay(13), 8, 2, drA, oito, meioDia);
        porData(espB, mesPassado.atDay(12), 4, 4, drB, LocalTime.of(13, 0), LocalTime.of(17, 0));
        // Sem profissional: fora da ocupacao.
        porData(espC, mesPassado.atDay(12), 50, 1, null, null, null);

        OcupacaoProfissionalViewDTO resultado =
                indicadoresService.ocupacaoPorProfissional(unidade.getId(), de, ate, admin.getCpf());

        // Quem tem mais vaga sobrando vem primeiro: Ana sobra 8, Bruno sobra 0.
        assertThat(resultado.porProfissional()).extracting(OcupacaoProfissionalViewDTO.Linha::nome)
                .containsExactly(drA.getNome(), drB.getNome());
        OcupacaoProfissionalViewDTO.Linha ana = resultado.porProfissional().get(0);
        assertThat(List.of(ana.cotas(), ana.ofertadas(), ana.agendadas())).containsExactly(2L, 16L, 8L);
        OcupacaoProfissionalViewDTO.Linha bruno = resultado.porProfissional().get(1);
        assertThat(List.of(bruno.cotas(), bruno.ofertadas(), bruno.agendadas())).containsExactly(1L, 4L, 4L);

        assertThat(resultado.porHorario()).extracting(OcupacaoProfissionalViewDTO.Linha::nome)
                .containsExactly("08:00–12:00", "13:00–17:00");
        OcupacaoProfissionalViewDTO.Linha manha = resultado.porHorario().get(0);
        assertThat(List.of(manha.ofertadas(), manha.agendadas())).containsExactly(16L, 8L);
    }

    @Test
    @DisplayName("segunda barreira e periodo invalido")
    void acessoEPeriodo() {
        Long id = unidade.getId();
        indicadoresService.utilizacao(id, de, ate, usuario(Roles.GESTOR).getCpf());

        String recepcao = usuario(Roles.RECEPCAO).getCpf();
        assertThatThrownBy(() -> indicadoresService.utilizacao(id, de, ate, recepcao))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> indicadoresService.ocupacaoPorProfissional(id, de, ate, recepcao))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> indicadoresService.utilizacao(id, "ontem", ate, admin.getCpf()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------

    private static CotaUtilizacaoViewDTO.Linha titular(CotaUtilizacaoViewDTO resultado, String tipo, String nome) {
        return resultado.porTitular().stream()
                .filter(l -> tipo.equals(l.tipo()) && nome.equals(l.titular()))
                .findFirst().orElseThrow();
    }

    private User usuario(Roles role) {
        User u = new User();
        u.setCpf(String.format("%011d", System.nanoTime() % 100_000_000_000L));
        u.setNome(role + sufixo);
        u.setPassword("x");
        u.setRole(role);
        u.setAtivo(true);
        if (role == Roles.RECEPCAO) {
            u.setUnidade(unidade);
        }
        return userRepository.saveAndFlush(u);
    }

    private Especialidade especialidade(String prefixo) {
        Especialidade e = new Especialidade();
        e.setCodigo(prefixo + sufixo);
        e.setNome(prefixo + sufixo);
        e.setCategoria(ItemCategoria.EXAME_OU_PROCEDIMENTO);
        e.setAtivo(true);
        e.setVagas(0);
        return especialidadeRepository.saveAndFlush(e);
    }

    private Profissional profissional(String nome) {
        Profissional p = new Profissional();
        p.setNome(nome + sufixo);
        p.setAtivo(true);
        return profissionalRepository.saveAndFlush(p);
    }

    private void mensal(Unidade daUnidade, GrupoRelatorio doGrupo, Especialidade especialidade, YearMonth mes,
            int total, int utilizada, boolean ativa) {
        CotaUnidade c = new CotaUnidade();
        c.setUnidade(daUnidade);
        c.setGrupoUnidades(doGrupo);
        c.setEspecialidade(especialidade);
        c.setTipoPeriodo(TipoPeriodoCota.MENSAL);
        c.setPeriodo(mes.toString());
        c.setQuantidadeTotal(total);
        c.setQuantidadeUtilizada(utilizada);
        c.setAtivo(ativa);
        cotaRepository.saveAndFlush(c);
    }

    private void porData(Especialidade especialidade, LocalDate data, int total, int utilizada,
            Profissional profissional, LocalTime inicio, LocalTime fim) {
        CotaUnidade c = new CotaUnidade();
        c.setUnidade(unidade);
        c.setEspecialidade(especialidade);
        c.setTipoPeriodo(TipoPeriodoCota.DATA);
        c.setDataEspecifica(data);
        c.setQuantidadeTotal(total);
        c.setQuantidadeUtilizada(utilizada);
        c.setAtivo(true);
        c.setProfissionalExecutante(profissional);
        c.setHoraInicial(inicio);
        c.setHoraFinal(fim);
        cotaRepository.saveAndFlush(c);
    }
}
