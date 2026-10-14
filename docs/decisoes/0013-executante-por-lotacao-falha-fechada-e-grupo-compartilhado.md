---
titulo: Executante por lotação, falha fechada e grupo compartilhado
tags:
  - sirg/adr
status: aceita
data: 2026-10-14
substitui: 0011-unidade-executante-parametrizada
---

# 0013 — Executante por lotação, falha fechada e grupo compartilhado

## Contexto

O ADR 0011 recortava `/atendimentos` por unidade executante, mas com três regras que não
batiam com o uso real:

- O vínculo do USER vinha **só** da lista de operadores da parametrização. Quem estava lotado
  na executante (`usuarios.unidade_id`) e não estava na lista via o município inteiro.
- USER sem vínculo, ou só com executante incompleta, caía na **visão municipal**: uma USF que
  não recebe agendamento nenhum via os pacientes de todos os locais, com CPF e CNS.
- Grupo de Relatório e especialidade avulsa eram **exclusivos** de uma executante, e dois
  locais que fazem os mesmos exames (dois laboratórios) não podiam ser parametrizados.

## Decisão

1. **As executantes do USER são a união** da unidade de lotação (se for executante
   configurada) com as executantes em que ele está na lista de operadores. As duas passam pelo
   mesmo filtro de "configurada" (`UsuarioUnidadeExecutanteRepository.findExecutantesConfiguradasDoUsuario`).
2. **Falha fechada:** USER sem nenhuma executante configurada **não vê nada** — lista, resumo,
   quantitativos, Relatório de Atendimento e menu "Agendas" vazios. A tela diz isso
   explicitamente (`semExecutante` no resumo), para não ser lida como "dia sem paciente".
   `EscopoAtendimento` distingue `MUNICIPAL` de `NENHUMA`; só o ADMIN é municipal.
3. **Grupo e especialidade avulsa podem estar em mais de uma executante** (V111: o índice
   único passa a ser no par `(grupo/especialidade, unidade)`; a checagem de exclusividade do
   service foi removida).
4. **Quem desempata é o local do agendamento.** Com local, o item é do dono do local (como
   antes). Sem local, o item só é de X se X for a **única** executante ativa
   (`EXECUTANTE`/`AMBOS`) com aquela especialidade — por grupo ou avulsa. Especialidade
   compartilhada sem local só aparece na visão municipal, **inclusive para o operador
   vinculado às duas** (regra uniforme por executante). Executante concorrente inativa ou só
   solicitante não conta, porque o administrador não consegue limpar o vínculo dela.

Continuam do ADR 0011: executante é `Unidade`; pertencimento calculado na consulta, sem
backfill; o recorte é só de leitura (desfecho por id não confere a executante); vincular local
de outra executante move o local; só conta executante configurada.

## Alternativas consideradas

- **Só a lotação** (sem lista de operadores): perde quem atende em duas executantes.
- **Manter a visão municipal para USER sem executante**: é o que permitia a USF02 ver os
  pacientes do Hospital A e do Hospital B; descartado pelo usuário.
- **Interruptor de configuração** para ligar a falha fechada depois do deploy: elimina a
  janela de tela vazia, mas é código a mais que alguém precisa lembrar de ligar e remover.
  Escolhida a nota de deploy com janela.
- **Editar a V110** em vez de criar a V111: ela já rodou em ambiente de teste; `flyway repair`
  só reescreve o checksum e o índice antigo ficaria lá, dando 500 no primeiro grupo
  compartilhado.
- **Item sem local de especialidade compartilhada visível para todas as executantes**:
  expõe o paciente a quem não vai atendê-lo.
- **Visível para o operador que está em todas as executantes que compartilham**: regra mais
  complexa, sem caso real pedido.

## Consequências

- **Deploy:** todo USER passa a ver `/atendimentos` vazio até o ADMIN parametrizar as
  executantes (local + grupo/especialidade) e a lotação ou a lista de operadores. Fazer o
  deploy fora do horário de atendimento e parametrizar em seguida (ver `INFRA.md`).
- **Falha fechada nos dois sentidos:** desativar a unidade, trocar o tipo para "Só
  solicitante", desvincular o último local ou excluir a última especialidade deixa os
  operadores dela sem tela. As confirmações das telas de Unidades e Locais avisam (contam só
  a lista de operadores, não os lotados). Excluir especialidade não avisa.
- **Mudar a lotação de um USER muda o que ele vê em `/atendimentos`.** A tela de usuários não
  avisa.
- **Compartilhar um grupo** tira da tela dos operadores os itens **sem local** daquele grupo.
  A tela de parametrização tem um aviso fixo.
- Deixa de valer a premissa "uma especialidade pertence a uma executante só": não deduza a
  executante de um agendamento pela especialidade.
- O risco aceito do ADR 0011 continua: o desfecho por id não confere a executante, e agora
  um USER que não vê nada ainda consegue lançar desfecho por chamada direta à API.
- Ver [[03 - Entidades e Classes de Domínio]] §3.15g.
