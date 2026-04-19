# Jogo da Forca Multijogador — Comunicação por Sockets TCP/IP

Trabalho Prático 1 — Sistemas Distribuídos  
Arquitetura Cliente–Servidor com TCP/IP em Java

---

## Índice

1. [Visão Geral](#visão-geral)
2. [Arquitetura](#arquitetura)
3. [Estrutura do Projeto](#estrutura-do-projeto)
4. [Protocolo de Comunicação](#protocolo-de-comunicação)
5. [Regras do Jogo](#regras-do-jogo)
6. [Compilação e Execução](#compilação-e-execução)
7. [Descrição das Classes](#descrição-das-classes)
8. [Concorrência e Sincronização](#concorrência-e-sincronização)
9. [Interface Gráfica](#interface-gráfica)

---

## Visão Geral

Implementação de um **Jogo da Forca multijogador síncrono** em modo cliente–servidor.  
De 2 a 4 jogadores participam em simultâneo, colaborando na tentativa de descobrir uma palavra oculta comum.

Características principais:

- Servidor central que gere a lógica do jogo e a comunicação.
- Cada jogador corre num cliente que se liga ao servidor via TCP/IP.
- Rondas síncronas: o estado é atualizado apenas após recolher todas as jogadas da ronda.
- Dois clientes disponíveis: modo texto (consola) e modo gráfico (GUI Swing).
- Banco de 100 palavras organizadas em 10 categorias.
- Temporizador por ronda (15 segundos) com contagem decrescente visual.

---

## Arquitetura

```
┌──────────────────────────────────────────────────┐
│                    SERVIDOR                      │
│                                                  │
│  ServidorForca ──► GestorJogo (singleton)       │
│       │                  │                       │
│       │            EstadoJogo (estado             │
│       │            partilhado sincronizado)      │
│       │                  │                       │
│       └─► GestorCliente ─┘  (uma thread/cliente) │
└──────────────────────────────────────────────────┘
              TCP/IP (porta 12345)
┌────────────────────┐   ┌────────────────────────┐
│  ClienteForca     │   │  ClienteForcaGUI       │
│  (modo texto)      │   │  (modo gráfico Swing)   │
└────────────────────┘   └────────────────────────┘
```

### Fluxo de uma partida

```
Servidor                          Cliente(s)
   │                                  │
   │◄── ligação TCP ─────────────────┤
   │──── WELCOME <id> <total> ───────►│
   │  (aguarda 2+ jogadores)          │
   │──── INFO À espera... ───────────►│
   │  (jogo inicia)                   │
   │──── START <máscara> <tent> <ms>─►│
   │  (ciclo de rondas)               │
   │──── ROUND <k> <máscara> <tent> ─►│
   │◄── GUESS <letra ou palavra> ─────│
   │──── STATE <máscara> <tent> ─────►│
   │  (fim do jogo)                   │
   │──── END WIN/LOSE <palavra> ─────►│
   │  (fecha ligações)                │
```

---

## Estrutura do Projeto

```
ComunicacaoSocketsJogoForca/
├── Makefile                        # Compilação e execução
├── README.md                       # Esta documentação
├── src/
│   ├── server/
│   │   ├── ServidorForca.java      # Ponto de entrada do servidor
│   │   ├── GestorJogo.java        # Orquestrador singleton do jogo
│   │   ├── EstadoJogo.java          # Estado partilhado sincronizado
│   │   ├── GestorCliente.java      # Gestor de ligação por cliente
│   │   ├── TemporizadorRonda.java         # Temporizador de contagem decrescente
│   │   ├── BancoPalavras.java           # Banco de 100 palavras por categoria
│   │   ├── EntradaPalavra.java          # Entrada no banco de palavras
│   │   ├── CategoriaPalavra.java       # Enumeração de categorias
│   │   ├── UtilitarioLog.java         # Registo centralizado de eventos
│   │   ├── Dificuldade.java         # Enumeração de dificuldades
│   │   ├── ExcecaoJogo.java      # Exceção base do jogo
│   │   └── ExcecaoJogadaInvalida.java  # Exceção de jogada inválida
│   └── client/
│       ├── ClienteForca.java      # Cliente modo texto (consola)
│       └── ClienteForcaGUI.java   # Cliente modo gráfico (Swing)
├── bin/                            # Bytecode compilado
│   ├── server/
│   └── client/
└── logs/                           # Ficheiros de registo do servidor
```

---

## Protocolo de Comunicação

A comunicação é baseada em **mensagens de texto terminadas em `\n`** (uma mensagem por linha).

### Servidor → Cliente

| Mensagem | Descrição |
|---|---|
| `WELCOME <id> <total>` | ID do jogador e número de jogadores ligados. |
| `START <máscara> <tentativas> <ms>` | Início do jogo com estado inicial e tempo limite por ronda. |
| `ROUND <k> <máscara> <tentativas> <letras>` | Início da ronda `k`. `letras` é `-` se não houver. |
| `STATE <máscara> <tentativas> <letras>` | Atualização do estado após processar todas as jogadas. |
| `END WIN <ids> <palavra>` | Fim com vitória. `ids`: lista de IDs vencedores separados por vírgula. |
| `END LOSE <palavra>` | Fim sem vencedores. Revela a palavra. |
| `FULL` | Servidor cheio ou jogo em curso — ligação rejeitada. |
| `INFO <mensagem>` | Mensagem informativa para o utilizador. |

### Cliente → Servidor

| Mensagem | Descrição | Exemplo |
|---|---|---|
| `GUESS <texto>` | Adivinhar uma letra ou a palavra completa. | `GUESS a` ou `GUESS girafa` |
| `NAME <nome>` | Definir o nome do jogador após receber WELCOME. | `NAME Maria` |

### Formato da máscara

A máscara é uma sequência de caracteres separados por espaço.  
Letras por descobrir são representadas por `_`.  
Exemplo: `_ a _ _` significa que a segunda letra é `a` e as restantes estão ocultas.

---

## Regras do Jogo

### Início

- O servidor seleciona aleatoriamente uma palavra de um banco de 100 palavras.
- Aceita até **4 jogadores** por partida.
- O jogo inicia quando:
  - Estão ligados **pelo menos 2 jogadores**, ou
  - Expira o **timeout do lobby** (20 segundos) após a entrada do primeiro jogador.
- Após o início, novas ligações são rejeitadas com `FULL`.

### Tentativas

- Cada partida tem **6 tentativas** disponíveis.
- Uma tentativa é consumida quando a jogada **não contribui** para o progresso:
  - A letra não existe na palavra.
  - A palavra proposta está incorreta.
  - O jogador não respondeu dentro do tempo limite (timeout → jogada vazia `""`).

### Tipos de jogada

- **Letra**: um único caractere (ex: `a`, `M`).
- **Palavra**: a palavra completa (ex: `girafa`).
- A comparação é **insensível a maiúsculas/minúsculas**.

### Rondas síncronas

1. O servidor envia `ROUND` com o estado atual a todos os jogadores.
2. Cada jogador tem **15 segundos** para submeter uma jogada (`GUESS`).
3. Jogadas não submetidas a tempo são tratadas como `""` (sem jogada).
4. O servidor processa **todas as jogadas** antes de atualizar o estado.
5. O servidor envia `STATE` com o novo estado a todos os jogadores.

### Condições de fim

| Condição | Resultado |
|---|---|
| Um ou mais jogadores acertam a palavra na mesma ronda | `END WIN <ids> <palavra>` |
| As 6 tentativas chegam a 0 | `END LOSE <palavra>` |

---

## Compilação e Execução

### Pré-requisitos

- Java Development Kit (JDK) 11 ou superior.
- `make` instalado (ou executar os comandos manualmente).

### Compilar tudo

```bash
make
```

### Compilar separadamente

```bash
make server   # Compila apenas o servidor
make client   # Compila apenas os clientes
```

### Executar o servidor

```bash
make run-server
# ou manualmente:
java -cp bin/server server.ServidorForca
```

### Executar o cliente modo texto

```bash
make run-client
# ou manualmente:
java -cp bin/client client.ClienteForca
```

### Executar o cliente gráfico (GUI)

```bash
make run-gui
# ou manualmente:
java -cp bin/client client.ClienteForcaGUI
```

### Configurar endereço do servidor

Por omissão, os clientes ligam-se a `localhost:12345`.  
Para ligar a outro endereço, edite a constante `HOST` em `ClienteForca.java` ou `ClienteForcaGUI.java`.

### Limpar ficheiros compilados

```bash
make clean
```

---

## Descrição das Classes

### Servidor

#### `ServidorForca`
Ponto de entrada do servidor. Cria um `ServerSocket` na porta 12345 e aceita ligações em ciclo infinito. Cada ligação é delegada a um `GestorCliente` submetido a um conjunto de threads dinâmico (`CachedThreadPool`).

#### `GestorJogo`
Singleton que orquestra todo o ciclo de vida da partida:
- **Lobby**: aguarda jogadores e inicia o jogo por número mínimo ou timeout.
- **Ciclo de rondas**: envia `ROUND`, aguarda jogadas via `CountDownLatch`, processa e envia `STATE`.
- **Rejeição**: controla `joiningOpen` para rejeitar ligações após o início.
- **Envio coletivo**: método `broadcast()` sincronizado para enviar a todos os clientes.

#### `EstadoJogo`
Estado partilhado e sincronizado de uma partida:
- Guarda a palavra secreta, a máscara, tentativas restantes, letras usadas e pontuações.
- `processRound()`: regras de vitória, derrota e atualização da máscara.
- `submitGuess()`: regista uma jogada para a ronda atual.
- `validateGuess()`: valida que a jogada respeita o protocolo.
- `Estado` (enum): `A_AGUARDAR_JOGADORES`, `EM_PROGRESSO`, `TERMINADO`.

#### `GestorCliente`
Thread dedicada a um cliente TCP:
- Regista o jogador no `GestorJogo` e envia `WELCOME`.
- Lê mensagens `GUESS` e `NAME` em ciclo.
- `setSoTimeout()` evita bloqueio indefinido na leitura.
- `sendMessage()` sincronizado para envio thread-safe.

#### `TemporizadorRonda`
Temporizador de contagem decrescente por ronda (classe utilitária, não usada diretamente no ciclo principal — o timeout é gerido via `CountDownLatch.await()` no `GestorJogo`). Útil para extensões futuras.

#### `BancoPalavras`
Banco de 100 palavras organizadas por categoria:

| Categoria | Exemplos |
|---|---|
| Animal | elefante, girafa, pinguim, crocodilo |
| Fruta | morango, melancia, ananas, framboesa |
| Profissão | arquiteto, bombeiro, engenheiro, astronauta |
| País | portugal, espanha, franca, alemanha |
| Cidade | lisboa, porto, coimbra, faro |
| Desporto | futebol, basquetebol, natacao, ciclismo |
| Instrumento | guitarra, violino, trompete, clarinete |
| Comida | chocolate, esparguete, hamburger, lasanha |
| Tecnologia | computador, internet, programacao, algoritmo |
| Natureza | floresta, montanha, oceano, vulcao |

#### `UtilitarioLog`
Utilitário centralizado de registo de eventos. Escreve simultaneamente para um ficheiro `logs/hangman-YYYYMMDD-HHmmss.log` e para a consola.

#### `EntradaPalavra`, `CategoriaPalavra`, `Dificuldade`
Classes/enumerações de suporte para o banco de palavras e dificuldade.

#### `ExcecaoJogo`, `ExcecaoJogadaInvalida`
Exceções específicas do domínio do jogo para tratamento de erros tipificado.

---

### Clientes

#### `ClienteForca` (modo texto)
Cliente em modo consola com:
- Arte ASCII da forca (6 estágios).
- Caras ASCII: 😊 (acerto) e 😢 (erro).
- Leitura de jogadas via `Scanner`.
- Duas threads: leitura do servidor (daemon) + input do utilizador (principal).

#### `ClienteForcaGUI` (modo gráfico)
Cliente gráfico em Swing com:
- **Tema escuro** (azul meia-noite, ciano neão, dourado).
- **Painel da forca** (`HangmanPanel`): desenho com `Graphics2D` em 7 estágios.
- **Painel de caras** (`FacePanel`): caras animadas — feliz (acerto), triste (erro), vitória, derrota.
- **Cronómetro visual**: contador decrescente com mudança de cor (verde → laranja → vermelho).
- **Barra de vidas**: corações para tentativas restantes, X para tentativas consumidas.
- **Teclado de letras**: clique nos botões ou campo de texto para escrever a palavra completa.
- **Ecrã completo**: tecla `F11` ou botão na interface.
- **Bloqueio por ronda**: apenas uma jogada por ronda; botões desativados após a submissão.

---

## Concorrência e Sincronização

| Mecanismo | Onde | Para quê |
|---|---|---|
| `synchronized` (métodos) | `EstadoJogo`, `GestorCliente`, `GestorJogo` | Proteger estado partilhado de acessos simultâneos. |
| `CountDownLatch` | `GestorJogo.runGame()` | Aguardar que todos os jogadores submetam a jogada (ou timeout). |
| `volatile` | `GestorCliente.ativo`, `GestorJogo.joiningOpen`, `ClienteForca.running` | Visibilidade imediata de flags entre threads sem `synchronized`. |
| `socket.setSoTimeout()` | `GestorCliente.run()` | Evitar bloqueio indefinido na leitura de mensagens. |
| `ExecutorService` (CachedThreadPool) | `ServidorForca` | Gerir threads de clientes dinamicamente. |
| Thread daemon | Lobby, leitura de mensagens | Terminar automaticamente quando a JVM fecha. |

### Protocolo de ronda (sequência de sincronização)

```
GestorJogo                    GestorCliente(es)
     │                               │
     │── roundLatch = new CDL(N) ───►│
     │── broadcast(ROUND...) ────────►│
     │                               │  (aguarda GUESS do utilizador)
     │◄── submitGuess() ─────────────│  latch.countDown()
     │   (repete para cada cliente)  │
     │── latch.await(15s) ───────────│
     │  (timeout: applyTimeoutGuesses)│
     │── gameState.processRound() ───│
     │── broadcast(STATE...) ────────►│
```

---

## Interface Gráfica

### Atalhos de teclado (GUI)

| Tecla | Ação |
|---|---|
| `F11` | Alternar ecrã completo / janela |
| `Enter` (campo de texto) | Submeter a jogada atual |

### Caras animadas

| Situação | Cara exibida |
|---|---|
| Início / aguardar ronda | Neutra (sem cara) |
| Letra/palavra correta | 😊 Cara feliz (verde) |
| Letra/palavra errada | 😢 Cara triste (vermelho) |
| Vitória | 🏆 Cara de vitória (dourado) |
| Derrota | 💀 Cara de derrota (cinzento) |

### Cronómetro visual

O cronómetro começa a contagem decrescente quando o servidor envia `ROUND` e para quando recebe `STATE`.  
Muda de cor conforme o tempo diminui:

- **Verde**: mais de 7 segundos restantes.
- **Laranja**: 4–7 segundos restantes.
- **Vermelho**: 0–3 segundos restantes.
