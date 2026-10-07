---
titulo: Envio pelo WhatsApp por fila em banco, com registro de status
tags:
  - sirg/adr
status: aceita
data: 2026-10-06
---

# 0003 — Envio pelo WhatsApp por fila em banco, com registro de status

## Contexto

Com o webhook em produção ([[0002-webhook-whatsapp-por-instancia]]), o pedido seguinte foi
enviar mensagens ao paciente: confirmação quando é agendado, aviso em remarcação e
cancelamento, lembrete 3 dias antes, um painel de admin com chave para desligar, disparo
manual e uma lista para acompanhar o volume.

Três fatos do sistema condicionaram o desenho:

- **Não existe remarcação.** Remarcar é excluir o agendamento (hard delete) e criar outro.
- **O agendamento é onde a cota é consumida.** Nada ligado a mensagem pode travar, atrasar ou
  desfazer essa transação.
- **Não havia tarefa agendada, evento pós-commit nem chamada HTTP de saída com efeito sobre o
  paciente.** Esta entrega introduz os três.

As decisões abaixo foram tomadas com o usuário durante o `/feature`.

## Decisão

**1. O envio passa por uma fila em banco (`whatsapp_mensagem`), despachada por tarefa
agendada.** O service de agendamento só publica um evento. Depois do commit, um listener grava
uma linha `PENDENTE` (sem HTTP). A cada 30 segundos uma tarefa reivindica as linhas, remonta o
conteúdo e chama a Meta. A mesma tabela é o registro de cada tentativa.

**2. A fila não guarda texto, nome nem telefone.** O conteúdo é remontado na hora do envio,
relendo agendamento e paciente; ficam só os 4 últimos dígitos do número. Como efeito, toda
regra (chave, opt-out, limite, lista de teste, telefone) vale no momento em que a mensagem
sai, não no momento em que foi enfileirada.

**3. Remarcação é detectada por janela de tempo.** O cancelamento espera 10 minutos na fila
(`app.whatsapp.envio.cancelamento-atraso-minutos`). Se um item do agendamento excluído for
reagendado nesse intervalo, o cancelamento é descartado e sai uma única mensagem de
remarcação. Agendamento criado e excluído antes de a confirmação sair não gera mensagem.

**4. O envio nasce desligado e a chave fica em banco** (`whatsapp_config`, linha única). Só
ADMIN altera, em `/admin/whatsapp`. A chave desliga o **envio**, nunca o recebimento do
webhook: recusar eventos faria a Meta reenviar por dias e poder desativar a inscrição.

**5. Lembrete: lote às 8h (fuso `America/Bahia`), 3 dias corridos antes, com recuperação de
um dia.** Se o lote da véspera não rodou, o de hoje inclui também os atendimentos de daqui a 2
dias. Um índice único sobre `chave_idempotencia` garante no máximo um lembrete por agendamento
e data. Agendamento criado depois das 8h do dia do lote não recebe lembrete — acabou de
receber a confirmação.

**6. Todos os pacientes com telefone recebem, com opção de sair.** `solicitacao.whatsapp_opt_out`
marca quem pediu para não receber; vale para qualquer ficha com os mesmos dígitos de CPF. A
base legal dessa escolha (envio sem consentimento prévio registrado) foi decisão do usuário,
responsável pela instância, e não uma decisão técnica.

**7. O que a mensagem não diz.** Nunca CPF, CNS, quem agendou, nem a observação do operador
(o template traz um texto fixo no lugar). Só o primeiro nome. Especialidade marcada como
`sensivel` não é citada, nem o local e o profissional. Atendimento com data já passada não
gera mensagem. A lista do painel não mostra nome nem telefone inteiro.

**8. Status de entrega passam a ser gravados — revoga a decisão 2 do ADR 0002.** O webhook
atualiza a linha pelo id da mensagem (enviado → entregue → lido, só avança) e guarda se foi
cobrável e a categoria. Das mensagens **recebidas** de pacientes grava-se apenas a contagem do
dia; texto, remetente e id continuam sem ser lidos nem gravados.

**9. Duas travas contra envio em massa**, por variável de ambiente: limite diário (padrão 200)
e lista de números de teste (enquanto preenchida, só eles recebem).

**10. Transporte sanitário ficou fora desta entrega.**

## Alternativas consideradas

- **Enviar dentro da transação do agendamento.** Descartado: uma Meta lenta seguraria o lock
  da cota, e uma falha poderia desfazer o agendamento.
- **`@Async` depois do commit, sem fila.** Menos tabela, mas perde o que está em memória num
  reinício, não resolve a remarcação e faria a chave de desligar depender do instante do clique.
- **RabbitMQ**, que o sistema já usa na federação. Descartado: a fila precisa ser consultada
  pelo painel e cruzada com o webhook de status; em banco isso é uma tabela só.
- **Mandar "cancelado" e "confirmado" em sequência na remarcação.** Mais simples, mas o
  paciente lê primeiro que perdeu a vaga.
- **Guardar o texto enviado**, para auditoria. Descartado: criaria um acervo que liga paciente,
  telefone e especialidade, sem regra de retenção definida.
- **Presumir o DDD local em telefone sem DDD.** Descartado: número errado é dado de saúde no
  celular de outra pessoa.
- **Repetir o envio após timeout de leitura.** Descartado: a Meta pode ter aceitado, e repetir
  duplicaria a mensagem. Só se repete erro em que a mensagem com certeza não saiu (sem
  conexão, 429, 5xx), no máximo 3 vezes.
- **Guardar o texto das respostas dos pacientes.** Adiado para a fatia de dúvidas, que precisa
  decidir retenção, quem lê e como atende.

## Consequências

- Subir esta versão não envia nada: exige credenciais **e** um ADMIN ligar a chave. Em
  instância sem credenciais (Conceição do Almeida), listener e tarefas retornam na primeira
  linha e a fila fica vazia. A contagem diária de recebidas depende só do webhook estar ligado.
- O listener grava a linha da fila na thread da requisição, logo após o commit, e pede uma
  **segunda conexão do pool** enquanto a do agendamento ainda não foi devolvida. O agendamento
  já está gravado, mas com o pool (Hikari, padrão de 10) esgotado a resposta ao operador pode
  esperar. Improvável no volume atual; se o uso crescer, mover essa gravação para um executor
  próprio ou aumentar o pool.
- Todo cancelamento chega ao paciente com cerca de 10 minutos de atraso.
- Se a aplicação cair no meio de um envio, a mensagem fica como falha (`INTERROMPIDO`) em vez
  de ser repetida.
- O agendador padrão do Spring tem **uma thread**: tarefa nova e lenta atrasa o despacho do
  WhatsApp. Por isso lotes de 20 e timeouts de 3s/10s.
- A JVM do container roda em UTC. Qualquer regra de data nova deve usar
  `WhatsAppEnvioProperties.FUSO`; `LocalDate.now()` puro erra o dia entre 21h e 0h.
- Listener pós-commit que grava no banco precisa de `REQUIRES_NEW`; sem isso a escrita se
  perde em silêncio. `WhatsAppEnvioFluxoIT` cobre, e por isso **não** é `@Transactional`.
- Antes de excluir um agendamento, ler só os **ids** das especialidades (consulta escalar).
  Carregar as entidades as deixa gerenciadas, apontando para o agendamento removido, e quebra
  o flush em solicitação sem unidade.
- Paciente que responder à mensagem não é atendido: a resposta só entra na contagem. Os
  templates dizem que a mensagem é automática e mandam procurar a unidade.
- **Antes de ligar em produção**, o catálogo de especialidades precisa ser revisado e as
  sensíveis marcadas; nenhuma vem marcada. O checklist está em `INFRA.md`.
- **Limitações conhecidas da remarcação:** reagendar só parte dos itens de um agendamento
  excluído descarta o cancelamento inteiro (o paciente não é avisado do item que ficou sem
  data); e a mensagem de remarcação usa o template de confirmação, que não diz que a data
  anterior foi substituída.
- Telefone gravado como `55` + 9 dígitos, sem DDD, é indistinguível de um celular do DDD 55
  (RS) e é aceito como tal.
- Token expirado ou template reprovado fazem cada mensagem falhar em definitivo, sem pausa
  automática: quem percebe é quem olha o painel.
- **Em aberto:**
  - retenção de `whatsapp_mensagem` (sugestão: 12 meses; a limpeza é outra entrega);
  - transporte sanitário;
  - atendimento das respostas dos pacientes;
  - opt-out nas telas de cadastro (hoje só na edição, em `/paciente/[id]`);
  - a pendência do ADR 0002 sobre um app Meta por município.
