ALTER TABLE solicitacao_especialidade
DROP CONSTRAINT IF EXISTS solicitacao_especialidade_status_check;

ALTER TABLE solicitacao_especialidade
ADD CONSTRAINT solicitacao_especialidade_status_check
CHECK (status IN (
    'AGUARDANDO',
    'AGENDADO',
    'FALTOU',
    'CANCELADO',
    'REALIZADO',
    'RETORNO',
    'RETORNO_POLICLINICA',
    'GEL'
));
