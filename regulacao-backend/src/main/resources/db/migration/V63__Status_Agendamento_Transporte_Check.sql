ALTER TABLE agendamento_transporte
DROP CONSTRAINT IF EXISTS agendamento_transporte_status_check;

ALTER TABLE agendamento_transporte
ADD CONSTRAINT agendamento_transporte_status_check
CHECK (status_agendamento IN (
    'AGENDADO',
    'CANCELADO',
    'PENDENTE',
    'CONFIRMADO',
    'REALIZADO',
    'GEL'
));
