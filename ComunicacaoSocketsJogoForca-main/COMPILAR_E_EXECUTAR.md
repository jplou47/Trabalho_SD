# Compilar e Executar — Jogo da Forca

## Pré-requisitos

- **JDK 11 ou superior** — [https://adoptium.net](https://adoptium.net)
- **`make`** — normalmente disponível em Linux/macOS; no Windows usar WSL ou Git Bash

Verificar versão do Java:
```bash
java -version
javac -version
```

---

## Compilação

### Compilar tudo (servidor + clientes)

```bash
make
```

### Compilar separadamente

```bash
make server   # apenas o servidor
make client   # apenas os clientes (texto e GUI)
```

Os bytecodes são gerados em:
- `bin/server/` — servidor
- `bin/client/` — clientes

---

## Execução

### 1. Iniciar o servidor

Numa **primeira janela de terminal**:

```bash
make run-server
# ou manualmente:
java -cp bin/server server.ServidorForca
```

O servidor fica à escuta na porta **12345**.  
O jogo inicia automaticamente quando:
- **2 ou mais jogadores** se ligam, ou
- Expira o **timeout do lobby** (20 segundos) após o primeiro jogador entrar.

### 2. Ligar clientes

Numa **segunda janela de terminal** (modo texto):

```bash
make run-client
# ou manualmente:
java -cp bin/client client.ClienteForca
```

Numa **terceira janela de terminal** (modo gráfico):

```bash
make run-gui
# ou manualmente:
java -cp bin/client client.ClienteForcaGUI
```

> Repita o passo 2 para cada jogador adicional (até 4 jogadores no total).  
> Cada cliente deve ser executado numa janela de terminal separada.

### Ligar a um servidor remoto

Por omissão, os clientes ligam-se a `localhost:12345`.  
Para ligar a outro endereço, edite a constante `HOST` em:
- `src/client/ClienteForca.java`
- `src/client/ClienteForcaGUI.java`

---

## Comandos úteis

| Comando | Descrição |
|---|---|
| `make` | Compila servidor e clientes |
| `make server` | Compila apenas o servidor |
| `make client` | Compila apenas os clientes |
| `make run-server` | Compila (se necessário) e inicia o servidor |
| `make run-client` | Compila (se necessário) e inicia o cliente texto |
| `make run-gui` | Compila (se necessário) e inicia o cliente gráfico |
| `make kill` | Termina o processo do servidor na porta 12345 |
| `make clean` | Remove todos os ficheiros compilados (`bin/`) |

---

## Exemplo de sessão completa

```bash
# Terminal 1 — servidor
make run-server

# Terminal 2 — jogador 1 (texto)
make run-client

# Terminal 3 — jogador 2 (gráfico)
make run-gui
```

Os logs do servidor são guardados automaticamente em `logs/hangman-YYYYMMDD-HHmmss.log`.
