package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.math.BigDecimal;

/** Projecoes escalares dos indicadores de custo ({@code CustoIndicadoresRepository}). */
public final class CustoIndicadorProjections {

    private CustoIndicadorProjections() {
    }

    /** Um mes da evolucao do custo. {@code mes} no formato AAAA-MM. */
    public interface EvolucaoMes {
        String getMes();
        BigDecimal getAgendado();
        BigDecimal getConcluido();
        BigDecimal getFaltas();
        Long getPacientesAtendidos();
    }

    /** Custo de faltas e cancelamentos: o total, uma especialidade ou uma unidade. */
    public interface Faltas {
        Long getId();
        String getNome();
        BigDecimal getValor();
        Long getItensComValor();
        Long getItensSemValor();
    }

    /** Especialidades ativas do catalogo e, delas, quantas nao tem preco. */
    public interface CoberturaCatalogo {
        Long getAtivas();
        Long getSemPreco();
    }

    /** Um teto financeiro ativo. */
    public interface Teto {
        Long getUnidadeId();
        String getUnidadeNome();
        String getGrupoNome();
        String getPeriodo();
        BigDecimal getValorTotal();
        BigDecimal getValorUtilizado();
    }

    /** Soma dos tetos ativos de um mes. */
    public interface TetoMes {
        String getMes();
        BigDecimal getLiberado();
        BigDecimal getUtilizado();
        Long getTetos();
    }
}
