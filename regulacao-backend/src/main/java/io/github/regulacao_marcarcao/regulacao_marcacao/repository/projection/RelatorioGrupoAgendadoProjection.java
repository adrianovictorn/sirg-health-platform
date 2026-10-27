package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.time.LocalDate;

public interface RelatorioGrupoAgendadoProjection {
     Long getSolicitacaoId();
    String getNomePaciente();
    String getCpfPaciente();
    String getCns();
    LocalDate getDataNascimento();
    String getUsfOrigem();      
    LocalDate getDataAgendada();
    String getTurno();         
    String getEspecialidades();
    /** Nomes dos profissionais executantes dos itens da linha; "Não informado" para item sem executante. */
    String getProfissionalExecutante();
}
