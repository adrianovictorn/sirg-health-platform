-- V89: Agenda — a entidade que descreve a oferta (executante, profissional,
-- procedimento, vigencia, horario) e distribui vagas entre unidades solicitantes.
--
-- Contexto: o oficio da Central de Regulacao de Sao Felipe (25/09/2026) pediu uma
-- ferramenta para abrir agenda sem repetir cadastro de cota unidade por unidade.
-- A agenda NAO substitui o motor de saldo (cota_unidade) — ela o alimenta,
-- materializando uma cota por ocorrencia x unidade solicitante (V90).
--
-- Ver docs/especificacoes/Agenda e Oferta.md, secoes 3, 4.4-4.7 e 8.
--
-- Operacoes ADITIVAS: nenhuma tabela existente e alterada por esta migracao.

-- ---------------------------------------------------------------------------
-- 1. Agenda
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS agenda (
    id                              BIGSERIAL PRIMARY KEY,

    -- Prestador que executa o atendimento. Unidade com tipo EXECUTANTE ou AMBOS
    -- (V86) — a validacao de qual tipo e aceito fica no service, nao aqui.
    estabelecimento_executante_id  BIGINT       NOT NULL,

    profissional_id                 BIGINT       NOT NULL,

    -- Ocupacao nesta oferta. Opcional: sugerido a partir do vinculo, mas o
    -- vinculo pode nao ter CBO (vinculos herdados da V88).
    cbo_id                           BIGINT,

    local_agendamento_id            BIGINT,

    -- Sobrescreve o endereco do executante quando o atendimento acontece em
    -- outro lugar (ex.: coleta de laboratorio num ponto diferente da sede).
    local_descricao                 VARCHAR(255),

    tipo_oferta                     VARCHAR(20)  NOT NULL,

    -- Preenchido apenas quando tipo_oferta = GRUPO.
    grupo_especialidades_id         BIGINT,

    vigencia_inicio                 DATE         NOT NULL,
    vigencia_fim                    DATE         NOT NULL,

    -- Dias da semana marcados, ex.: 'TER,QUI'. Data unica repete o mesmo dia.
    dias_semana                     VARCHAR(20)  NOT NULL,

    hora_inicial                    TIME         NOT NULL,
    hora_final                      TIME         NOT NULL,

    -- Reservado para a fase de slots individuais (decisao 3.3) — sempre nulo
    -- nesta entrega.
    minutos_por_atendimento         INT,

    observacao                      VARCHAR(500),

    ativo                            BOOLEAN      NOT NULL DEFAULT TRUE,

    criado_por_id                   BIGINT       NOT NULL,

    criado_em                       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Optimistic locking: materializacao e cancelamento de ocorrencia podem
    -- concorrer com uma edicao da agenda.
    version                         BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_agenda_executante
        FOREIGN KEY (estabelecimento_executante_id) REFERENCES unidade(id) ON DELETE RESTRICT,

    CONSTRAINT fk_agenda_profissional
        FOREIGN KEY (profissional_id) REFERENCES profissional(id) ON DELETE RESTRICT,

    CONSTRAINT fk_agenda_cbo
        FOREIGN KEY (cbo_id) REFERENCES cbo(id) ON DELETE RESTRICT,

    CONSTRAINT fk_agenda_local_agendamento
        FOREIGN KEY (local_agendamento_id) REFERENCES local_agendamento(id) ON DELETE RESTRICT,

    CONSTRAINT fk_agenda_grupo_especialidades
        FOREIGN KEY (grupo_especialidades_id) REFERENCES grupo_relatorio(id) ON DELETE RESTRICT,

    CONSTRAINT fk_agenda_criado_por
        FOREIGN KEY (criado_por_id) REFERENCES usuarios(id) ON DELETE RESTRICT,

    CONSTRAINT ck_agenda_tipo_oferta
        CHECK (tipo_oferta IN ('INDIVIDUAL', 'GRUPO')),

    -- Espelha ck_cota_titular_exclusivo (V84): agenda de grupo tem o grupo
    -- preenchido, agenda individual nao.
    CONSTRAINT ck_agenda_grupo_coerente
        CHECK (
            (tipo_oferta = 'GRUPO' AND grupo_especialidades_id IS NOT NULL)
         OR (tipo_oferta = 'INDIVIDUAL' AND grupo_especialidades_id IS NULL)
        ),

    CONSTRAINT ck_agenda_vigencia
        CHECK (vigencia_fim >= vigencia_inicio),

    CONSTRAINT ck_agenda_horario
        CHECK (hora_final > hora_inicial)
);

CREATE INDEX IF NOT EXISTS ix_agenda_profissional_ativo
    ON agenda (profissional_id, ativo);

CREATE INDEX IF NOT EXISTS ix_agenda_executante
    ON agenda (estabelecimento_executante_id);

-- ---------------------------------------------------------------------------
-- 2. Especialidades ofertadas
-- ---------------------------------------------------------------------------
-- Agenda INDIVIDUAL tem exatamente uma linha; agenda GRUPO tem N — o
-- subconjunto de exames do grupo que a coordenacao marcou (R8).

CREATE TABLE IF NOT EXISTS agenda_especialidade (
    id               BIGSERIAL PRIMARY KEY,
    agenda_id        BIGINT NOT NULL,
    especialidade_id BIGINT NOT NULL,

    CONSTRAINT fk_agenda_especialidade_agenda
        FOREIGN KEY (agenda_id) REFERENCES agenda(id) ON DELETE CASCADE,

    CONSTRAINT fk_agenda_especialidade_especialidade
        FOREIGN KEY (especialidade_id) REFERENCES especialidade(id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agenda_especialidade
    ON agenda_especialidade (agenda_id, especialidade_id);

-- ---------------------------------------------------------------------------
-- 3. Distribuicao de vagas por unidade solicitante
-- ---------------------------------------------------------------------------
-- O coracao da "multipla escolha de unidades" do oficio: N unidades por
-- agenda, cada uma com sua quantidade de vagas por ocorrencia.

CREATE TABLE IF NOT EXISTS agenda_distribuicao (
    id                       BIGSERIAL PRIMARY KEY,
    agenda_id                BIGINT  NOT NULL,
    unidade_solicitante_id   BIGINT  NOT NULL,
    vagas_por_ocorrencia     INT     NOT NULL,

    CONSTRAINT fk_agenda_distribuicao_agenda
        FOREIGN KEY (agenda_id) REFERENCES agenda(id) ON DELETE CASCADE,

    CONSTRAINT fk_agenda_distribuicao_unidade
        FOREIGN KEY (unidade_solicitante_id) REFERENCES unidade(id) ON DELETE RESTRICT,

    CONSTRAINT ck_agenda_distribuicao_vagas
        CHECK (vagas_por_ocorrencia > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agenda_distribuicao
    ON agenda_distribuicao (agenda_id, unidade_solicitante_id);

-- ---------------------------------------------------------------------------
-- 4. Ocorrencias — a data concreta materializada (R2)
-- ---------------------------------------------------------------------------
-- Cada uma vira uma cota_unidade por unidade solicitante (V90). Cancelar uma
-- ocorrencia (R10) desativa as cotas geradas por ela e estorna o saldo nao
-- consumido; nao apaga a linha, para manter o historico.

CREATE TABLE IF NOT EXISTS agenda_ocorrencia (
    id            BIGSERIAL PRIMARY KEY,
    agenda_id     BIGINT      NOT NULL,
    data          DATE        NOT NULL,
    hora_inicial  TIME        NOT NULL,
    hora_final    TIME        NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ABERTA',
    version       BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT fk_agenda_ocorrencia_agenda
        FOREIGN KEY (agenda_id) REFERENCES agenda(id) ON DELETE CASCADE,

    CONSTRAINT ck_agenda_ocorrencia_status
        CHECK (status IN ('ABERTA', 'CANCELADA'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agenda_ocorrencia_data
    ON agenda_ocorrencia (agenda_id, data);

-- Consulta de R9 (sobreposicao de horario do mesmo profissional na mesma data)
-- e de listagem por data.
CREATE INDEX IF NOT EXISTS ix_agenda_ocorrencia_data
    ON agenda_ocorrencia (data, status);
