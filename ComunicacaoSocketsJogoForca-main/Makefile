SRC_SERVER = src/server
SRC_CLIENT = src/client
BIN_SERVER = bin/server
BIN_CLIENT = bin/client

SERVER_MAIN  = server.ServidorForca
CLIENT_MAIN  = client.ClienteForca
GUI_MAIN     = client.ClienteForcaGUI

SERVER_SRCS  = $(wildcard $(SRC_SERVER)/*.java)
CLIENT_SRCS  = $(wildcard $(SRC_CLIENT)/*.java)

.PHONY: all server client run-server run-client run-gui kill clean

all: server client

server: $(BIN_SERVER)/.compiled

$(BIN_SERVER)/.compiled: $(SERVER_SRCS)
	@mkdir -p $(BIN_SERVER)
	javac -d $(BIN_SERVER) -sourcepath $(SRC_SERVER) $(SERVER_SRCS)
	@touch $(BIN_SERVER)/.compiled
	@echo "[OK] Servidor compilado."

client: $(BIN_CLIENT)/.compiled

$(BIN_CLIENT)/.compiled: $(CLIENT_SRCS)
	@mkdir -p $(BIN_CLIENT)
	javac -d $(BIN_CLIENT) -sourcepath $(SRC_CLIENT) $(CLIENT_SRCS)
	@touch $(BIN_CLIENT)/.compiled
	@echo "[OK] Cliente compilado."

run-server: server
	@echo "[Servidor] A iniciar na porta 12345..."
	java -cp $(BIN_SERVER) $(SERVER_MAIN)

run-client: client
	@echo "[Cliente texto] A ligar ao servidor..."
	java -cp $(BIN_CLIENT) $(CLIENT_MAIN)

run-gui: client
	@echo "[Cliente GUI] A iniciar interface grafica..."
	java -cp $(BIN_CLIENT) $(GUI_MAIN)

kill:
	@fuser -k 12345/tcp 2>/dev/null && echo "[OK] Servidor terminado." || echo "[INFO] Nenhum servidor na porta 12345."

clean:
	rm -rf $(BIN_SERVER) $(BIN_CLIENT)
	@echo "[OK] Binarios removidos."
