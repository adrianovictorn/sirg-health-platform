package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.time.LocalDate;

public interface PainelEspecialidadeProjection {
    Long getSolicitacaoId();
    String getNomePaciente();
    String getCpfPaciente();
    String getCns();
    LocalDate getDataNascimento();
    String getUsfOrigem();
    String getSolicitacaoEspecialidadeId();
    String getEspecialidades();

    /**
     * Data(s) de coleta do material, agregadas como texto ja formatado.
     *
     * A consulta agrupa por paciente, entao um mesmo registro pode reunir varios
     * exames — e cada um tem a sua propria data de coleta. Por isso o valor vem
     * agregado (DISTINCT, separado por virgula), no mesmo formato ja usado para
     * `especialidades` nesta consulta. Nulo quando nenhum exame tem coleta
     * informada, que e o caso de todo registro anterior a V81.
     */
    String getDataColeta();

}
