-- V85: perfil GESTOR + multiplos perfis por usuario (com alternancia).
--
-- Duas mudancas, ambas ADITIVAS — nenhuma coluna e removida e nenhum dado
-- existente e alterado:
--
-- 1. GESTOR: perfil de acompanhamento (indicadores, relatorios, dashboards).
--    Nao monta cota nem configura agenda — so leitura.
--
-- 2. usuario_perfis: o CONJUNTO de perfis concedidos a cada usuario.
--    `usuarios.cargo` CONTINUA existindo e passa a significar o perfil
--    PRINCIPAL (o que vale no login e o fallback de tokens antigos). O perfil
--    em uso no momento viaja no JWT e e sempre validado contra esta tabela,
--    entao revogar um perfil tem efeito imediato, sem esperar o token expirar.

ALTER TABLE usuarios
DROP CONSTRAINT IF EXISTS usuarios_cargo_check;

ALTER TABLE usuarios
ADD CONSTRAINT usuarios_cargo_check
CHECK (cargo IN (
    'ADMIN',
    'ADMIN_UNIDADE',
    'GESTOR',
    'USER',
    'PACIENTE',
    'ENFERMEIRO',
    'MEDICO',
    'RECEPCAO',
    'COORD_TRANSPORTE'
));

CREATE TABLE IF NOT EXISTS usuario_perfis (
    usuario_id BIGINT NOT NULL,
    perfil VARCHAR(30) NOT NULL,
    CONSTRAINT pk_usuario_perfis PRIMARY KEY (usuario_id, perfil),
    CONSTRAINT fk_usuario_perfis_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT usuario_perfis_perfil_check CHECK (perfil IN (
        'ADMIN',
        'ADMIN_UNIDADE',
        'GESTOR',
        'USER',
        'PACIENTE',
        'ENFERMEIRO',
        'MEDICO',
        'RECEPCAO',
        'COORD_TRANSPORTE'
    ))
);

-- Backfill: todo usuario existente passa a ter exatamente o perfil que ja tinha.
-- Sem isto, um usuario ficaria sem nenhum perfil concedido e perderia o acesso.
INSERT INTO usuario_perfis (usuario_id, perfil)
SELECT id, cargo FROM usuarios
ON CONFLICT (usuario_id, perfil) DO NOTHING;
