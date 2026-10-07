package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CoberturaPrecoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoEvolucaoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoFaltasViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.CustoPainelLinhaViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo.TetoExecucaoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.dto.indicadores.PeriodoIndicador;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.CustoIndicadoresRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.CustoIndicadorProjections;
import lombok.RequiredArgsConstructor;

/**
 * Indicadores de custo: execucao do teto, custo de faltas e cancelamentos,
 * cobertura de preco e evolucao mensal. Somente leitura e somente agregados —
 * nao debita, nao estorna, nao altera preco.
 *
 * <p>Quem pode chamar e decidido no controller (ADMIN e GESTOR); o servico
 * confere de novo, como segunda barreira. Valor em reais nao pode sair por
 * outro caminho.
 */
@Service
@RequiredArgsConstructor
public class CustoIndicadoresService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final CustoIndicadoresRepository repository;
    private final CustoPainelService custoPainelService;
    private final UnidadeAcessoService unidadeAcessoService;

    @Transactional(readOnly = true)
    public TetoExecucaoViewDTO execucaoDoTeto(Long unidadeId, String de, String ate, String callerCpf) {
        exigirGestao(callerCpf);
        PeriodoIndicador periodo = PeriodoIndicador.de(de, ate);
        PeriodoIndicador serie = PeriodoIndicador.ultimosDozeMeses();

        List<TetoExecucaoViewDTO.Teto> tetos = repository
                .tetos(unidadeId, periodo.mesInicial(), periodo.mesFinal()).stream()
                .map(TetoExecucaoViewDTO.Teto::from)
                .toList();

        Map<String, CustoIndicadorProjections.TetoMes> porMes = new HashMap<>();
        for (CustoIndicadorProjections.TetoMes p : repository
                .tetosPorMes(unidadeId, serie.mesInicial(), serie.mesFinal())) {
            porMes.put(p.getMes(), p);
        }
        List<TetoExecucaoViewDTO.Mes> meses = new ArrayList<>();
        for (YearMonth mes : mesesDe(serie)) {
            CustoIndicadorProjections.TetoMes p = porMes.get(mes.toString());
            meses.add(new TetoExecucaoViewDTO.Mes(
                    mes.toString(),
                    p != null ? zeroSeNulo(p.getLiberado()) : ZERO,
                    p != null ? zeroSeNulo(p.getUtilizado()) : ZERO,
                    p != null && p.getTetos() != null ? p.getTetos() : 0L));
        }
        return new TetoExecucaoViewDTO(periodo.de(), periodo.ate(), tetos, meses);
    }

    @Transactional(readOnly = true)
    public CustoFaltasViewDTO faltas(Long unidadeId, String de, String ate, String callerCpf) {
        exigirGestao(callerCpf);
        PeriodoIndicador periodo = PeriodoIndicador.de(de, ate);

        // Onde se perde mais dinheiro aparece primeiro.
        Comparator<CustoFaltasViewDTO.Linha> maiorValorPrimeiro = Comparator
                .comparing(CustoFaltasViewDTO.Linha::valor).reversed()
                .thenComparing(CustoFaltasViewDTO.Linha::nome, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

        return new CustoFaltasViewDTO(
                periodo.de(),
                periodo.ate(),
                CustoFaltasViewDTO.Linha.from(repository.faltasTotal(unidadeId, periodo.de(), periodo.ate())),
                repository.faltasPorEspecialidade(unidadeId, periodo.de(), periodo.ate()).stream()
                        .map(CustoFaltasViewDTO.Linha::from).sorted(maiorValorPrimeiro).toList(),
                repository.faltasPorUnidade(unidadeId, periodo.de(), periodo.ate()).stream()
                        .map(CustoFaltasViewDTO.Linha::from).sorted(maiorValorPrimeiro).toList());
    }

    @Transactional(readOnly = true)
    public CoberturaPrecoViewDTO coberturaDePreco(Long unidadeId, String de, String ate, String callerCpf) {
        exigirGestao(callerCpf);
        PeriodoIndicador periodo = PeriodoIndicador.de(de, ate);

        CustoIndicadorProjections.CoberturaCatalogo catalogo = repository.coberturaDoCatalogo();
        // Fila e agendados: os numeros do proprio painel de custos, sem refazer a conta.
        CustoPainelLinhaViewDTO painel = custoPainelService
                .painel(unidadeId, null, null, periodo.de(), periodo.ate(), callerCpf).total();

        return new CoberturaPrecoViewDTO(
                periodo.de(),
                periodo.ate(),
                catalogo.getAtivas() != null ? catalogo.getAtivas() : 0L,
                catalogo.getSemPreco() != null ? catalogo.getSemPreco() : 0L,
                painel.estimadoItens(),
                painel.estimadoSemPreco(),
                painel.agendadoItens(),
                painel.agendadoSemValor());
    }

    /** Sempre os ultimos 12 meses, fechando no mes corrente do municipio. */
    @Transactional(readOnly = true)
    public CustoEvolucaoViewDTO evolucao(Long unidadeId, String callerCpf) {
        exigirGestao(callerCpf);
        PeriodoIndicador serie = PeriodoIndicador.ultimosDozeMeses();

        Map<String, CustoIndicadorProjections.EvolucaoMes> porMes = new HashMap<>();
        for (CustoIndicadorProjections.EvolucaoMes p : repository
                .evolucaoPorMes(unidadeId, serie.de(), serie.ate())) {
            porMes.put(p.getMes(), p);
        }

        List<CustoEvolucaoViewDTO.Mes> meses = new ArrayList<>();
        for (YearMonth mes : mesesDe(serie)) {
            CustoIndicadorProjections.EvolucaoMes p = porMes.get(mes.toString());
            BigDecimal concluido = p != null ? zeroSeNulo(p.getConcluido()) : ZERO;
            long atendidos = p != null && p.getPacientesAtendidos() != null ? p.getPacientesAtendidos() : 0L;
            meses.add(new CustoEvolucaoViewDTO.Mes(
                    mes.toString(),
                    p != null ? zeroSeNulo(p.getAgendado()) : ZERO,
                    concluido,
                    p != null ? zeroSeNulo(p.getFaltas()) : ZERO,
                    atendidos,
                    atendidos > 0 ? concluido.divide(BigDecimal.valueOf(atendidos), 2, RoundingMode.HALF_UP) : null));
        }
        return new CustoEvolucaoViewDTO(meses);
    }

    /** Mes sem movimento nao vem do banco: a serie e montada aqui, sem buraco. */
    private static List<YearMonth> mesesDe(PeriodoIndicador serie) {
        List<YearMonth> meses = new ArrayList<>();
        YearMonth ultimo = YearMonth.from(serie.ate());
        for (YearMonth mes = YearMonth.from(serie.de()); !mes.isAfter(ultimo); mes = mes.plusMonths(1)) {
            meses.add(mes);
        }
        return meses;
    }

    private static BigDecimal zeroSeNulo(BigDecimal valor) {
        return valor != null ? valor : ZERO;
    }

    private void exigirGestao(String callerCpf) {
        if (!unidadeAcessoService.isAdminOuGestor(callerCpf)) {
            throw new AccessDeniedException("Acesso restrito ao administrador e ao gestor.");
        }
    }
}
