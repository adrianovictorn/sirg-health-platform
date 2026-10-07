package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.AntecedenciaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.BalancoFilaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.FaixasEsperaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.FilaEnvelhecimentoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.PeriodoIndicador;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.IndicadoresFilaRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.BalancoFilaMesProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.FaixasEsperaProjection;
import lombok.RequiredArgsConstructor;

/**
 * Indicadores gerenciais de fila e operacao: envelhecimento da fila,
 * antecedencia do agendamento e balanco mensal. Somente leitura e somente
 * agregados.
 *
 * <p>Quem pode chamar e decidido no controller (ADMIN e GESTOR); o servico
 * confere de novo, como segunda barreira.
 */
@Service
@RequiredArgsConstructor
public class IndicadoresFilaService {

    // Mesmo valor "sem filtro" de FilaEsperaService: lista de IN nunca vai vazia.
    private static final List<String> SEM_FILTRO_DE_PRIORIDADE = List.of("");

    private final IndicadoresFilaRepository repository;
    private final UnidadeAcessoService unidadeAcessoService;

    /** A fila de HOJE por faixa de espera. Nao tem periodo: e o que esta aguardando agora. */
    @Transactional(readOnly = true)
    public FilaEnvelhecimentoViewDTO envelhecimento(Long unidadeId, String callerCpf) {
        exigirGestao(callerCpf);

        // Mesmo relogio de FilaEsperaService (data_cadastro e gravada pela JVM).
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime corte31 = agora.minusDays(31);
        LocalDateTime corte61 = agora.minusDays(61);
        LocalDateTime corte91 = agora.minusDays(91);

        List<FaixasEsperaViewDTO> porUnidade = repository.pacientesPorFaixaEUnidade(
                        FilaEsperaService.STATUS_DA_FILA, null, null, false, SEM_FILTRO_DE_PRIORIDADE,
                        unidadeId, null, null, null, null, null, corte31, corte61, corte91)
                .stream()
                .map(FaixasEsperaViewDTO::from)
                // "Sem unidade" (nome nulo) por ultimo.
                .sorted(Comparator.comparing(FaixasEsperaViewDTO::nome,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        List<FaixasEsperaViewDTO> porEspecialidade = maisAntigosPrimeiro(repository.pedidosPorFaixaEEspecialidade(
                FilaEsperaService.STATUS_DA_FILA, null, null, false, SEM_FILTRO_DE_PRIORIDADE,
                unidadeId, null, null, null, null, null, corte31, corte61, corte91));

        List<FaixasEsperaViewDTO> porPrioridade = maisAntigosPrimeiro(repository.pedidosPorFaixaEPrioridade(
                FilaEsperaService.STATUS_DA_FILA, null, null, false, SEM_FILTRO_DE_PRIORIDADE,
                unidadeId, null, null, null, null, null, corte31, corte61, corte91));

        // Cada paciente esta em uma unica unidade: o total e a soma das linhas.
        return new FilaEnvelhecimentoViewDTO(
                FaixasEsperaViewDTO.somar(porUnidade), porUnidade, porEspecialidade, porPrioridade);
    }

    @Transactional(readOnly = true)
    public AntecedenciaViewDTO antecedencia(Long unidadeId, String de, String ate, String callerCpf) {
        exigirGestao(callerCpf);
        PeriodoIndicador periodo = PeriodoIndicador.de(de, ate);
        return AntecedenciaViewDTO.from(repository.antecedencia(unidadeId, periodo.de(), periodo.ate()), periodo);
    }

    /** Sempre os ultimos 12 meses, fechando no mes corrente do municipio. */
    @Transactional(readOnly = true)
    public BalancoFilaViewDTO balanco(Long unidadeId, String callerCpf) {
        exigirGestao(callerCpf);
        PeriodoIndicador serie = PeriodoIndicador.ultimosDozeMeses();

        Map<String, Long> novos = new HashMap<>();
        for (BalancoFilaMesProjection p : repository.pedidosNovosPorMes(unidadeId, serie.de(), serie.ate())) {
            novos.put(p.getMes(), p.getNovos());
        }
        Map<String, BalancoFilaMesProjection> agendados = new HashMap<>();
        for (BalancoFilaMesProjection p : repository.pedidosAgendadosPorMes(unidadeId, serie.de(), serie.ate())) {
            agendados.put(p.getMes(), p);
        }

        // Mes sem movimento nao vem do banco: entra com zero, para a serie nao ter buraco.
        List<BalancoFilaViewDTO.Mes> meses = new ArrayList<>();
        YearMonth ultimo = YearMonth.from(serie.ate());
        for (YearMonth mes = YearMonth.from(serie.de()); !mes.isAfter(ultimo); mes = mes.plusMonths(1)) {
            String chave = mes.toString();
            BalancoFilaMesProjection ag = agendados.get(chave);
            meses.add(new BalancoFilaViewDTO.Mes(
                    chave,
                    novos.getOrDefault(chave, 0L),
                    ag != null && ag.getAgendados() != null ? ag.getAgendados() : 0L,
                    ag != null && ag.getConcluidos() != null ? ag.getConcluidos() : 0L));
        }
        return new BalancoFilaViewDTO(meses);
    }

    /** Quem tem mais gente esperando ha mais de 90 dias aparece primeiro. */
    private static List<FaixasEsperaViewDTO> maisAntigosPrimeiro(List<FaixasEsperaProjection> linhas) {
        return linhas.stream()
                .map(FaixasEsperaViewDTO::from)
                .sorted(Comparator.comparingLong(FaixasEsperaViewDTO::mais90).reversed()
                        .thenComparing(Comparator.comparingLong(FaixasEsperaViewDTO::total).reversed())
                        .thenComparing(FaixasEsperaViewDTO::nome,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private void exigirGestao(String callerCpf) {
        if (!unidadeAcessoService.isAdminOuGestor(callerCpf)) {
            throw new AccessDeniedException("Acesso restrito ao administrador e ao gestor.");
        }
    }
}
