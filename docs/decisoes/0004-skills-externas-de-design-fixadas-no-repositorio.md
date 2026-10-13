---
titulo: Skills externas de design fixadas no repositório
tags:
  - sirg/adr
status: aceita
data: 2026-10-12
---

# 0004 — Skills externas de design fixadas no repositório

## Contexto

O subagente `ux-ui-designer` (acionado por `/design`, `/feature` e `/ajuste`) decidia tudo
a partir de uma lista de princípios escrita no próprio arquivo e verificava responsividade
"mentalmente", sem abrir a tela. O pedido foi elevar o nível do design com cinco recursos
externos: Taste Skill, Web Design Guidelines, Image to Code, Awesome DESIGN.md e Playwright
CLI, com uma condição: nenhuma regra inventada e nenhuma ferramenta citada sem ter sido
usada.

A pesquisa nas fontes mostrou que os cinco não se encaixam do mesmo jeito:

- A skill principal do Taste Skill declara que não serve para dashboards, tabelas e painéis
  administrativos, e pressupõe React/Next.
- A Image to Code foi escrita para o Codex e exige que o agente gere imagens, o que o Claude
  Code não faz.
- A Web Design Guidelines busca as regras da `main` de um repositório a cada uso.
- A Awesome DESIGN.md não é skill: é uma coleção de arquivos de identidade visual de marcas.
- A skill do Playwright CLI libera todos os subcomandos e documenta preenchimento de
  formulário e gravação de estado de autenticação em arquivo.

Há ainda o contexto do sistema: skill é texto de terceiro instruindo um agente que edita
código de um sistema de saúde em produção, e as telas mostram dados de pacientes.

## Decisão

As skills ficam em `.claude/skills/`, versionadas, copiadas à mão de um commit específico
depois de lidas por inteiro, com origem, licença e modificações em
`.claude/skills/PROVENIENCIA.md`. O `ux-ui-designer` as aciona sob demanda (ferramenta
`Skill`), com precedência escrita: instrução do usuário, convenções do SIRG e
`regulacao-frontend/DESIGN.md`, WCAG 2.2 AA, princípios do agente e, por último, skills
externas.

Por recurso:

- **Taste Skill:** a `redesign-existing-projects`, que trabalha em projeto existente sem
  trocar de stack, e a principal `design-taste-frontend` restrita à landing pública
  (`sirg-landing/`), que é o tipo de página para o qual ela foi escrita.
- **Motion:** a skill oficial do Motion AI Kit, só nos arquivos de boas práticas (o servidor
  MCP dela não é conectado), e o pacote `motion` em versão exata apenas na landing, que
  mantém o GSAP nas cenas de rolagem. A documentação da biblioteca fica em cópia local fora
  do git, por falta de licença de redistribuição.
- **Web Design Guidelines:** a skill, lendo uma cópia fixada do `command.md`.
- **Image to Code:** só a fase de análise, e só quando o usuário fornece a imagem.
- **Awesome DESIGN.md:** só o formato, usado para escrever um `DESIGN.md` próprio e
  descritivo em `regulacao-frontend/`.
- **Playwright CLI:** versão exata instalada globalmente, apenas contra `localhost`, numa
  sessão em que o login é feito pelo usuário. Navegar, redimensionar, capturar e clicar são
  permitidos; preencher, digitar, executar script, inspecionar requisições, gravar trace ou
  vídeo e ler ou gravar cookies e storage estão na lista `deny` de `.claude/settings.json`,
  assim como `npx playwright`. A linha `allowed-tools` da skill, que pré-aprovava todos os
  subcomandos, foi removida da cópia local.

O relatório do agente ganhou o campo "Skills e ferramentas usadas de fato", que só pode
citar o que foi acionado naquela execução.

## Alternativas consideradas

- **Instalar por `npx skills add` ou plugin do Claude Code.** Descartado: instalam o estado
  da `main` no momento e podem atualizar depois, trazendo instruções que ninguém leu.
- **Manter a busca ao vivo das regras da Vercel.** Descartado pelo mesmo motivo; a cópia
  fixada dispensa também liberar acesso de rede ao subagente.
- **Pré-carregar as skills no subagente (campo `skills:`).** Descartado: aumentaria o
  contexto de toda execução, inclusive de ajustes pontuais que não precisam de nenhuma.
- **Usar a skill principal do Taste Skill nas telas internas.** Descartado porque a própria
  skill exclui esse tipo de interface. Ela vale só para a landing, e mesmo ali sem o stack
  que pressupõe (React/Next, Motion), sem instalar pacote e sem imagem de domínio externo.
- **Adotar o DESIGN.md de uma marca como regra.** Descartado: criaria um estilo paralelo ao
  do sistema e reproduziria identidade visual de terceiro num sistema público.
- **Playwright como `devDependency` do frontend.** Descartado para não alterar
  `package.json` nem o build das VPS.
- **O agente fazer o login ou injetar o token.** Descartado: colocaria credencial na mão do
  agente. O usuário autentica a sessão; o agente só a reutiliza.
- **Proibir `click` por completo.** Era a recomendação inicial. O responsável pelo produto
  optou por liberar o clique para capturar modais e abas.

## Consequências

- A verificação de responsividade passa a ser real quando o ambiente local está no ar, e é
  declarada como não executada quando não está.
- Atualizar uma skill é trabalho manual: ler o diff, recopiar e atualizar a proveniência.
- **O clique é o ponto fraco.** Em várias telas "Salvar" é um `onclick` fora de `<form>`,
  então um clique grava. O bloqueio é por instrução ao agente, não por mecanismo. O banco
  local usado nessas sessões deve ter apenas dados fictícios e não pode estar ligado à fila
  de federação nem ao WhatsApp reais; o `/design` pergunta isso antes de chamar o agente, e
  sem a confirmação o agente não clica.
- **Os bloqueios não são uma caixa de areia.** A lista `deny` casa padrões no texto do
  comando, e a própria documentação do Playwright CLI diz que `allowedOrigins` não é
  fronteira de segurança e não cobre redirecionamentos. O proxy do Vite (`BACKEND_URL`)
  escapa dela, e o padrão que restringe `--filename` a `.playwright-cli/` ainda aceita
  `../`.
- As capturas ficam em `.playwright-cli/`, ignorada pelo git, e podem conter dados do banco
  local. Não devem ser anexadas a PR nem enviadas a serviço externo.
- As descrições das skills entram no contexto de toda sessão do Claude Code neste
  repositório, inclusive as de backend.
- `/feature` e `/ajuste` herdam o comportamento, porque usam o mesmo subagente.
- Qualquer push na `main` reimplanta as duas VPS, mesmo sendo só configuração; esta mudança
  deve entrar junto de uma entrega normal.
- **Em aberto:** as inconsistências de cor listadas em `regulacao-frontend/DESIGN.md`
  (segundo acento azul, dois verdes de status) aguardam decisão de produto.
