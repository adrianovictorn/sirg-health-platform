package io.github.regulacao_marcarcao.regulacao_marcacao.config;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.EspecialidadesEnum;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.EspecialidadeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;

@Component
@RequiredArgsConstructor
public class EspecialidadeInitializer implements ApplicationRunner {

    private final EspecialidadeRepository especialidadeRepository;
    private final EntityManager em;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 1) Seed tabela de especialidades a partir do enum existente (garantir que já existem)
       for (EspecialidadesEnum e : EspecialidadesEnum.values()) {
            final String codigo = e.name();
            final String nome = e.getDescricao();

            if (especialidadeRepository.findByCodigo(codigo).isEmpty()
                    && especialidadeRepository.findByNome(nome).isEmpty()) {

                Especialidade esp = new Especialidade();
                esp.setCodigo(codigo);
                esp.setNome(nome);
                esp.setCategoria(e.getCategoria());
                esp.setAtivo(true);

                especialidadeRepository.save(esp);
            }
        }

        // 2) Migrar dados legados de solicitacao_especialidade: preencher especialidade_id onde estiver nulo
        try {
            em.createNativeQuery(
                    "UPDATE solicitacao_especialidade se " +
                    "SET especialidade_id = e.id " +
                    "FROM especialidade e " +
                    "WHERE se.especialidade_id IS NULL " +
                    "AND se.especialidade_solicitada = e.codigo")
              .executeUpdate();
        } catch (Exception ignore) {}
    }
}

