-- V83: Novo perfil ADMIN_UNIDADE — usuários dos postos/unidades de saúde.
-- Tem permissões equivalentes às de ADMIN, porém restritas à unidade de lotação
-- do próprio usuário (usuarios.unidade_id).

ALTER TABLE usuarios
DROP CONSTRAINT IF EXISTS usuarios_cargo_check;

ALTER TABLE usuarios
ADD CONSTRAINT usuarios_cargo_check
CHECK (cargo IN (
    'ADMIN',
    'ADMIN_UNIDADE',
    'USER',
    'PACIENTE',
    'ENFERMEIRO',
    'MEDICO',
    'RECEPCAO',
    'COORD_TRANSPORTE'
));
