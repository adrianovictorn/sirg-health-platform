-- Tabela solicitacao
CREATE TABLE solicitacao (
    id BIGSERIAL PRIMARY KEY,
    usf_origem VARCHAR(20) NOT NULL,
    nome_paciente VARCHAR(150) NOT NULL,
    cpf_paciente VARCHAR(15) NOT NULL,
    cns VARCHAR(15) NOT NULL,
    data_nascimento DATE,
    observacoes VARCHAR(500),
    data_malote DATE
);

-- Tabela agendamento_solicitacao
CREATE TABLE agendamento_solicitacao (
    id BIGSERIAL PRIMARY KEY,
    local_agendado VARCHAR(50) NOT NULL,
    data_agendada DATE NOT NULL,
    observacoes VARCHAR(500),
    solicitacao_id BIGINT NOT NULL,
    CONSTRAINT fk_agendamento_solicitacao_solicitacao 
        FOREIGN KEY (solicitacao_id) REFERENCES solicitacao(id)
);

-- Tabela solicitacao_especialidade
CREATE TABLE solicitacao_especialidade (
    id BIGSERIAL PRIMARY KEY,
    solicitacao_id BIGINT NOT NULL,
    agendamento_id BIGINT,
    especialidade_solicitada VARCHAR(100) NOT NULL,
    status VARCHAR(50),
    prioridade VARCHAR(20),
    CONSTRAINT fk_solicitacao_especialidade_solicitacao
        FOREIGN KEY (solicitacao_id) REFERENCES solicitacao(id),
    CONSTRAINT fk_solicitacao_especialidade_agendamento
        FOREIGN KEY (agendamento_id) REFERENCES agendamento_solicitacao(id)
);
