# Projeto CorreiosRedes: Sistema de Encomendas Cliente-Servidor com FTP

## 1. Visão Geral do Projeto

O **CorreiosRedes** é um sistema cliente-servidor desenvolvido em Java que simula o serviço de envio, rastreamento e download de encomendas. A comunicação entre o cliente e o servidor é baseada nos princípios do **FTP (File Transfer Protocol)**, utilizando Sockets TCP para garantir uma troca de dados confiável.

O sistema é composto por duas aplicações principais:
* **Servidor (`FtpServer.java`)**: Uma aplicação Java Swing multithreaded que gerencia as conexões, processa os comandos, armazena os arquivos e mantém o estado das encomendas.
* **Cliente (`FtpClientGUI.java`)**: Uma aplicação Java Swing que oferece uma interface gráfica para o usuário interagir com o servidor, permitindo realizar operações como upload, download e consulta de status.

## 2. Tecnologias Utilizadas
* **Linguagem**: Java
* **Interface Gráfica**: Java Swing
* **Rede**: Implementação do protocolo **FTP** (Camada de Aplicação). A comunicação de transporte é realizada sobre **Sockets TCP/IP** (`java.net.Socket`, `java.net.ServerSocket`) para garantir a transferência confiável dos dados.
* **Concorrência**: Java Concurrency (`ExecutorService`, `Thread`)

## 3. Arquitetura

### 3.1. Lado do Servidor (`correio.server`)

O servidor é projetado para ser robusto e atender múltiplos clientes de forma concorrente.

#### `FtpServer.java` (O Maestro)
* **Função**: É a classe principal que inicializa e gerencia todo o ciclo de vida do servidor.
* **Inicialização**:
  * Cria a interface gráfica do servidor, que funciona como um console de logs em tempo real.
  * Abre um `ServerSocket` na porta **2121**, aguardando conexões de controle dos clientes.
  * Ao iniciar, chama o método `loadShipmentsFromDisk()` para carregar o estado de encomendas já existentes a partir do diretório `uploads/`.
* **Gerenciamento de Clientes**:
  * Utiliza um `Executors.newCachedThreadPool()` para gerenciar um pool de threads. Isso significa que não há um limite fixo de threads; elas são criadas sob demanda, e o limite prático é a memória do sistema.
  * Para cada cliente que se conecta (`serverSocket.accept()`), ele cria e submete uma nova instância de `ClientHandler` ao pool, permitindo o atendimento simultâneo.
* **Encerramento Seguro (`shutdown()`):** Implementa um desligamento gradual, fechando o socket principal e encerrando as threads ativas de forma organizada.

#### `ClientHandler.java` (O Trabalhador)
* **Função**: Cada instância desta classe é uma thread que lida com a comunicação de um único cliente e executa a lógica de negócio principal do servidor.
* **Loop de Comandos**: Entra em um loop `while` para ler continuamente os comandos enviados pelo cliente (ex: `STOR`, `LIST`, `RETR`).
* **Modo Passivo (PASV)**: Para cada transferência de dados (upload, download ou listagem), o `handlePasv()` abre um `ServerSocket` em uma porta aleatória do sistema e informa essa porta ao cliente, que então se conecta a ela para a troca de dados.


* **Geração do ID de Rastreio:**
  O ID é gerado no servidor no momento do upload (`handleStor` em `ClientHandler.java`):
  * **Geração Aleatória:** Um número entre 0 e 9999 é gerado.
  * **Formatação:** O número é formatado para ter sempre 4 dígitos (ex: `12` se torna `0012`).
  * **Verificação de Unicidade:** O código verifica se o ID já está em uso. Se estiver, um novo número é gerado até que um ID único seja encontrado.

* **Gerenciamento de Status:**
  O status de uma encomenda é persistido no nome do arquivo e gerenciado em memória.
  * **Status Inicial (`ENVIADA`):** Ao receber um novo arquivo, o servidor cria um objeto `ShipmentInfo` com o status padrão **"ENVIADA"** e salva o arquivo no disco com o nome `ID_NomeArquivo_ENVIADA.ext`.
  * **Mudança de Status (`ENTREGUE`):** Quando um cliente baixa um arquivo com sucesso (`handleRetr`), o servidor atualiza o status do objeto em memória para **"ENTREGUE"** e renomeia o arquivo no disco para `ID_NomeArquivo_ENTREGUE.ext`.
  * **Persistência:** Ao reiniciar, o servidor lê os nomes dos arquivos no diretório `uploads/` para reconstruir o estado e o status de todas as encomendas.


#### `ShipmentInfo.java` (O Modelo de Dados)
* **Função**: É uma classe POJO (Plain Old Java Object) que armazena os metadados de uma encomenda.
* **Atributos**: `id`, `filename`, `timestamp` e `status`.

#### `TextAreaOutputStream.java` (O Logger da GUI)
* **Função**: Classe utilitária que redireciona os fluxos `System.out` e `System.err` para a `JTextArea` na interface do servidor, permitindo que todos os logs apareçam na tela.

### 3.2. Lado do Cliente (`correio.client`)

#### `FtpClientGUI.java` (A Interface com o Usuário)
* **Função**: Provê a interface gráfica e gerencia toda a lógica de comunicação do lado do cliente.
* **Gerenciamento de Threads**: Todas as operações de rede são executadas em uma nova `Thread` (`new Thread(...)`). Isso é fundamental para não congelar a interface gráfica enquanto o cliente espera por respostas do servidor.
* **Comunicação de Dados**: Para realizar uma transferência, o cliente primeiro envia o comando `PASV`, processa a resposta para extrair o IP e a porta de dados, e então estabelece uma nova conexão `Socket` para essa porta.
* **Atualização da UI**: Utiliza `SwingUtilities.invokeLater` para garantir que todas as atualizações nos componentes da interface (tabela, logs, etc.) sejam feitas de forma segura na thread de eventos do Swing (EDT).


## 4. Protocolo de Comunicação

A comunicação é feita por um protocolo customizado inspirado no FTP, sobre TCP.

* **Canal de Controle**: Conexão principal (porta 2121) para troca de comandos e respostas.
* **Canal de Dados**: Conexões secundárias e temporárias (via `PASV`) para a transferência de arquivos e listagens.

### Comandos Suportados

* `USER <username>`, `PASS <password>`: Autenticação.
* `TYPE I`: Define o modo de transferência para Binário.
* `PASV`: Entra no modo passivo, solicitando ao servidor uma porta para conexão de dados.
* `STOR <filename>`: Inicia o upload de um arquivo.
* `RETR <id>`: Solicita o download de um arquivo pelo seu ID.
* `LIST`: Solicita a lista de todas as encomendas.
* `STAT <id>`: Consulta o status de uma encomenda específica.
* `QUIT`: Encerra a sessão.

## 5. Manual de Uso

### Como Executar

1.  Inicie a classe `FtpServer.java` para ligar o servidor. Uma janela com logs aparecerá.
2.  Inicie a classe `FtpClientGUI.java` para abrir o cliente. Você pode iniciar múltiplos clientes.

### Operações do Cliente

1.  **Conectar**: Insira o IP e a Porta do servidor e clique em "Conectar".
2.  **Enviar Arquivo (STOR)**: Clique no botão, selecione um arquivo, e ele será enviado. Um ID de rastreio será exibido no console.
3.  **Listar Encomendas (LIST)**: Clique no botão para atualizar a tabela com todas as encomendas no servidor.
4.  **Baixar por ID (RETR)**: Digite um ID de rastreio no campo de texto e clique no botão para baixar o arquivo correspondente.
5.  **Status por ID (STAT)**: Digite um ID e clique no botão para ver os detalhes da encomenda no console.
6.  **Desconectar**: Clique para encerrar a conexão com o servidor.