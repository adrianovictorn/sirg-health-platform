package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.CotaUtilizacaoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.OcupacaoProfissionalViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.PeriodoIndicador;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.IndicadoresCotaRepository;
import lombok.RequiredArgsConstructor;

/**
 * Indicadores gerenciais de cota: utilizacao e ocupacao por profissional e
 * horario. Somente leitura — nao passa por {@link CotaUnidadeService} nem altera
 * saldo.
 *
 * <p>Quem pode chamar e decidido no controller (ADMIN e GESTOR); o servico
 * confere de novo, como segunda barreira.
 */
@Service
@RequiredArgsConstructor
public class IndicadoresCotaService {

    private final IndicadoresCotaRepository repository;
    private final UnidadeAcessoService unidadeAcessoService;

    @Transactional(readOnly = true)
    public CotaUtilizacaoViewDTO utilizacao(Long unidadeId, String de, String ate, String callerCpf) {
        exigirGestao(callerCpf);
        PeriodoIndicador periodo = PeriodoIndicador.de(de, ate);
        // "Vencida" e relativo ao dia e ao mes do municipio, nao aos da JVM.
        LocalDate hoje = LocalDate.now(PeriodoIndicador.FUSO);
        String mesAtual = YearMonth.from(hoje).toString();

        List<CotaUtilizacaoViewDTO.Linha> porTipo = repository
                .utilizacaoPorTipo(unidadeId, periodo.mesInicial(), periodo.mesFinal(),
                        periodo.de(), periodo.ate(), mesAtual, hoje)
                .stream()
                .map(CotaUtilizacaoViewDTO.Linha::from)
                // MENSAL antes de DATA: do limite mais amplo para o mais especifico.
                .sorted(Comparator.comparing(CotaUtilizacaoViewDTO.Linha::tipo).reversed())
                .toList();

        List<CotaUtilizacaoViewDTO.Linha> porTitular = repository
                .utilizacaoPorTitular(unidadeId, periodo.mesInicial(), periodo.mesFinal(),
                        periodo.de(), periodo.ate(), mesAtual, hoje)
                .stream()
                .map(CotaUtilizacaoViewDTO.Linha::from)
                .sorted(Comparator.comparing(CotaUtilizacaoViewDTO.Linha::tipo).reversed()
                        .thenComparing(CotaUtilizacaoViewDTO.Linha::titular,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        return new CotaUtilizacaoViewDTO(periodo.de(), periodo.ate(), porTipo, porTitular);
    }

    @Transactional(readOnly = true)
    public OcupacaoProfissionalViewDTO ocupacaoPorProfissional(Long unidadeId, String de, String ate,
            String callerCpf) {
        exigirGestao(callerCpf);
        PeriodoIndicador periodo = PeriodoIndicador.de(de, ate);

        List<OcupacaoProfissionalViewDTO.Linha> porProfissional = repository
                .ocupacaoPorProfissional(unidadeId, periodo.mesInicial(), periodo.mesFinal(),
                        periodo.de(), periodo.ate())
                .stream()
                .map(OcupacaoProfissionalViewDTO.Linha::from)
                // Quem tem mais vaga sobrando primeiro: e onde ha o que redistribuir.
                .sorted(Comparator
                        .comparingLong((OcupacaoProfissionalViewDTO.Linha l) -> l.ofertadas() - l.agendadas())
                        .reversed()
                        .thenComparing(OcupacaoProfissionalViewDTO.Linha::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<OcupacaoProfissionalViewDTO.Linha> porHorario = repository
                .ocupacaoPorHorario(unidadeId, periodo.mesInicial(), periodo.mesFinal(), periodo.de(), periodo.ate())
                .stream()
                .map(OcupacaoProfissionalViewDTO.Linha::from)
                .toList();

        return new OcupacaoProfissionalViewDTO(periodo.de(), periodo.ate(), porProfissional, porHorario);
    }

    private void exigirGestao(String callerCpf) {
        if (!unidadeAcessoService.isAdminOuGestor(callerCpf)) {
            throw new AccessDeniedException("Acesso restrito ao administrador e ao gestor.");
        }
    }
}
