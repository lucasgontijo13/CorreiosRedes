## Documentação do Projeto: Sistema de Encomendas Cliente-Servidor

### 1. Visão Geral do Projeto

O projeto **CorreiosRedes** implementa um sistema de cliente-servidor para simular o envio e rastreamento de encomendas. A arquitetura é baseada em um servidor central que gerencia as informações e os arquivos, e múltiplos clientes que podem se conectar para realizar operações.

* **Servidor (`FtpServer.java`)**: Uma aplicação Java Swing multithreaded que aguarda conexões de clientes em uma porta TCP específica. Ele é responsável por processar comandos, gerenciar o estado das encomendas e armazenar os arquivos enviados.
* **Cliente (`FtpClientGUI.java`)**: Uma aplicação Java Swing que fornece uma interface gráfica para o usuário interagir com o servidor. O cliente pode se conectar, listar encomendas, enviar arquivos (upload), baixar arquivos (download) e consultar o status de uma encomenda específica.

A comunicação entre eles é feita através de um **protocolo de camada de aplicação customizado**, que opera sobre uma conexão TCP/IP confiável.

### 2. Arquitetura de Rede e Comunicação (TCP/IP)

O núcleo do projeto reside na sua implementação do modelo cliente-servidor utilizando **Sockets TCP**. O TCP (Transmission Control Protocol) foi escolhido por ser um protocolo **orientado à conexão** e **confiável**, garantindo que os comandos e os dados dos arquivos cheguem em ordem e sem erros.

#### 2.1. Modelo de Sockets e Estabelecimento da Conexão

1.  **ServerSocket (O "Ouvinte")**: O `FtpServer` instancia um `java.net.ServerSocket` em uma porta específica (padrão 2121). Este socket entra em um estado de "escuta" (`LISTEN`), aguardando que clientes iniciem uma conexão.
2.  **Handshake TCP de 3 Vias**: Quando um cliente (`FtpClientGUI`) tenta se conectar, o TCP executa o seu *three-way handshake* para estabelecer uma conexão confiável:
    * **SYN**: O cliente envia um pacote TCP com a flag SYN (Synchronize) para o servidor.
    * **SYN-ACK**: O servidor, ao receber o SYN, responde com um pacote contendo as flags SYN e ACK (Acknowledgment).
    * **ACK**: O cliente finaliza o processo enviando um pacote ACK.
3.  **Criação do Socket de Comunicação**: Uma vez que o handshake é concluído, o método `serverSocket.accept()` no servidor retorna uma nova instância de `java.net.Socket`. Este novo socket é dedicado exclusivamente à comunicação com aquele cliente específico, em um canal full-duplex.

#### 2.2. Multithreading para Múltiplos Clientes

O servidor é projetado para ser **concorrente**. Para cada cliente que se conecta, o `FtpServer` cria e submete uma nova instância de `ClientHandler` a um `ExecutorService` (um pool de threads).

* **Por que isso é crucial?** Sem multithreading, o servidor só poderia atender um cliente por vez. O método `accept()` bloquearia o servidor, e ele não poderia processar outras conexões. Com o `ClientHandler` em uma thread separada, a thread principal do servidor pode voltar imediatamente a chamar `accept()`, ficando pronta para novas conexões.

#### 2.3. Protocolo de Camada de Aplicação

O projeto define um protocolo simples, baseado em texto e em linhas, inspirado em protocolos como FTP e SMTP. Cada comando enviado pelo cliente é uma string terminada com uma nova linha (`\n`), e o servidor responde com códigos numéricos e mensagens textuais.

**Comandos Suportados:**

* `PUT <filename>`: Inicia o envio de um arquivo para o servidor.
* `GET <id>`: Solicita o download de um arquivo com base em seu ID de rastreio.
* `LIST`: Pede ao servidor a lista de todas as encomendas.
* `STATUS <id>`: Consulta o status de uma encomenda específica.
* `QUIT`: Encerra a sessão TCP com o servidor.

**Respostas do Servidor:**

O servidor usa códigos de status numéricos para indicar sucesso, erro ou a necessidade de mais informações. Exemplos:

* `220 Bem-vindo ao FTP Correios`: Resposta inicial de boas-vindas.
* `150 Pronto para receber...` / `150 Iniciando download`: Indica que o canal de dados está pronto para a transferência.
* `226 Transferencia concluida`: Sucesso na operação de arquivo.
* `213 Arquivos disponíveis...`: Resposta ao comando `LIST`.
* `550 ID nao encontrado`: Erro, o recurso solicitado não existe.
* `221 Adeus`: Confirmação do encerramento da conexão.

### 3. Análise Detalhada dos Componentes

#### 3.1. Servidor (`correio.server`)

* **`FtpServer.java`**:
    * **Função**: Atua como o "maestro". Sua principal responsabilidade é inicializar a UI, abrir o `ServerSocket` na porta `2121` e entrar em um loop `while (running)` para aceitar conexões.
    * **Gerenciamento de Threads**: Utiliza um `Executors.newCachedThreadPool()` para gerenciar as threads dos clientes de forma eficiente.
    * **Shutdown Gracioso**: O método `shutdown()` implementa um encerramento seguro. Ele para de aceitar novas conexões, tenta encerrar as threads existentes e fecha o `ServerSocket`.
* **`ClientHandler.java`**:
    * **Função**: É o "trabalhador". Cada instância lida com um único cliente durante toda a sua sessão.
    * **Loop de Comandos**: Entra em um loop `while ((line = in.readLine()) != null)` para ler os comandos enviados pelo cliente através do `InputStream` do socket.
    * **Processamento**: Utiliza um `switch` para interpretar o comando recebido e chamar o método apropriado (`handlePut`, `handleGet`, etc.).
* **`ShipmentInfo.java`**:
    * **Função**: É uma classe de dados simples (POJO) para armazenar metadados de uma encomenda: ID, nome do arquivo, status e data/hora.

#### 3.2. Cliente (`correio.client`)

* **`FtpClientGUI.java`**:
    * **Função**: Provê a interface gráfica e gerencia a lógica de comunicação do lado do cliente.
    * **Gerenciamento de Estado**: Mantém uma flag `isConnected` para habilitar/desabilitar botões da UI, garantindo que o usuário não tente enviar comandos sem uma conexão ativa.
    * **Operações em Background**: Todas as operações de rede (conectar, enviar, receber) são executadas em uma nova `Thread` (`new Thread(() -> { ... })`). Isso é fundamental para não congelar a Interface Gráfica do Usuário (GUI). As atualizações na UI são então postadas de volta para a Event Dispatch Thread (EDT) usando `SwingUtilities.invokeLater`.

### 4. Formato e Transferência de Dados

Uma decisão de design importante foi como transferir os arquivos. Em vez de abrir um segundo canal de dados (como no modo ativo do FTP tradicional), o projeto simplifica a transferência multiplexando controle e dados no mesmo socket TCP.

* **Codificação Base64**: Para enviar um arquivo, o conteúdo binário é lido, codificado em uma string **Base64** e enviado como uma única linha de texto pelo `PrintWriter`.
    * **Vantagem**: Isso simplifica o protocolo, pois tudo é tratado como texto, evitando a complexidade de gerenciar streams de bytes brutos misturados com comandos.
    * **Desvantagem**: A codificação Base64 aumenta o tamanho dos dados em aproximadamente 33%, consumindo mais largura de banda.
* **Delimitadores**:
    * Os comandos e respostas são delimitados por novas linhas.
    * Na transferência de arquivos, o cliente envia a string Base64 e, em seguida, uma linha contendo "EOF" para sinalizar o fim da transmissão dos dados do arquivo. O servidor lê até receber essa linha.

### 5. Ciclo de Vida Completo de uma Sessão

1.  O **Servidor** é iniciado e o `ServerSocket` começa a ouvir na porta 2121.
2.  O **Cliente** é iniciado. O usuário insere o IP/porta e clica em "Conectar".
3.  Um `Socket` é criado no cliente, e o **Handshake TCP** ocorre.
4.  O `serverSocket.accept()` do servidor retorna um novo `Socket`, e uma nova thread `ClientHandler` é iniciada para este cliente.
5.  O servidor envia a mensagem `220 Bem-vindo...`.
6.  O usuário clica em "Enviar Arquivo". O cliente envia `PUT nome_arquivo.txt`.
7.  O servidor responde `150 Pronto para receber...`.
8.  O cliente lê o arquivo, codifica em Base64 e envia a string resultante, seguida por uma linha "EOF".
9.  O servidor lê a string, decodifica para bytes e salva o arquivo no disco. Ao final, responde `226 Transferencia concluida...`.
10. O usuário clica em "Desconectar". O cliente envia o comando `QUIT`.
11. O servidor recebe `QUIT`, responde `221 Adeus` e fecha o `Socket` do lado dele (o que envia um pacote TCP com a flag **FIN**).
12. O cliente, ao receber a resposta, também fecha seu socket, completando o encerramento da conexão TCP.