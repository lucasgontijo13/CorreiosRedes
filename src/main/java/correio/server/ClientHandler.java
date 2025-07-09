package correio.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

public class ClientHandler implements Runnable {
	private final Socket controlSocket; // Socket para comandos
	private final Map<String, ShipmentInfo> tracking;
	private final Path uploadsDir = Paths.get("uploads");
	private static final Random random = new Random();

	// Estado para o Modo Passivo
	private ServerSocket dataServerSocket;

	public ClientHandler(Socket socket, Map<String, ShipmentInfo> tracking) {
		this.controlSocket = socket;
		this.tracking = tracking;
	}

	private String buildPersistentFilename(String id, String originalName, String status) {
		String baseName = originalName;
		String extension = "";
		int dotIndex = originalName.lastIndexOf('.');
		if (dotIndex > 0) {
			baseName = originalName.substring(0, dotIndex);
			extension = originalName.substring(dotIndex);
		}
		return String.format("%s_%s_%s%s", id, baseName, status, extension);
	}

	@Override
	public void run() {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
			 PrintWriter out = new PrintWriter(controlSocket.getOutputStream(), true)) {

			out.println("220 Bem-vindo ao Servidor FTP (Java-Based).");
			String line;
			while ((line = in.readLine()) != null) {
				System.out.println("[Controle] Comando recebido: " + line);
				String[] parts = line.split(" ", 2);
				String cmd = parts[0].toUpperCase();
				String arg = parts.length > 1 ? parts[1] : null;

				switch (cmd) {
					case "USER": out.println("331 Usuario OK, precisa de senha."); break;
					case "PASS": out.println("230 Login do usuario efetuado."); break;
					case "TYPE": out.println("200 Tipo mudado para I (Binary)."); break;
					case "PASV": handlePasv(out); break;
					case "LIST": handleList(out); break;
					case "STOR": handleStor(arg, out); break; // STOR é o comando FTP para upload (PUT)
					case "RETR": handleRetr(arg, out); break; // RETR é o comando FTP para download (GET)
					case "STAT": handleStatus(arg, out); break; // Usando STAT para nosso status customizado
					case "QUIT":
						out.println("221 Adeus.");
						controlSocket.close();
						return;
					default:
						out.println("502 Comando não implementado.");
				}
			}
		} catch (IOException e) {
			if (!e.getMessage().contains("Connection reset")) {
				e.printStackTrace();
			}
		} finally {
			try {
				if (dataServerSocket != null && !dataServerSocket.isClosed()) dataServerSocket.close();
				if (controlSocket != null && !controlSocket.isClosed()) controlSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void handlePasv(PrintWriter out) throws IOException {
		// Fecha qualquer socket de dados anterior
		if (dataServerSocket != null && !dataServerSocket.isClosed()) {
			dataServerSocket.close();
		}
		// Abre um novo ServerSocket em uma porta aleatória
		dataServerSocket = new ServerSocket(0); // 0 = porta aleatória livre
		int dataPort = dataServerSocket.getLocalPort();
		System.out.println("[Dados] Modo passivo. Escutando na porta: " + dataPort);

		// Prepara a resposta para o comando PASV
		byte[] ip = controlSocket.getLocalAddress().getAddress();
		String ipStr = String.format("%d,%d,%d,%d", ip[0] & 0xFF, ip[1] & 0xFF, ip[2] & 0xFF, ip[3] & 0xFF);
		String portStr = String.format("%d,%d", dataPort / 256, dataPort % 256);

		out.println("227 Entrando em Modo Passivo (" + ipStr + "," + portStr + ").");
	}

	// handleStor (Store)
	private void handleStor(String filename, PrintWriter controlOut) throws IOException {
		controlOut.println("150 Ok para enviar dados.");

		try (Socket dataConnection = dataServerSocket.accept(); // Aguarda o cliente conectar no canal de dados
			 InputStream dataIn = dataConnection.getInputStream()) {

			String shipmentId;
			do {
				shipmentId = String.format("%04d", random.nextInt(10000));
			} while (tracking.containsKey(shipmentId));

			String persistentFilename = buildPersistentFilename(shipmentId, filename, "ENVIADA");
			Path filePath = uploadsDir.resolve(persistentFilename);

			// Transfere os bytes brutos, sem Base64
			Files.copy(dataIn, filePath, StandardCopyOption.REPLACE_EXISTING);

			ShipmentInfo info = new ShipmentInfo(shipmentId, filename);
			tracking.put(shipmentId, info);
			System.out.println("[Dados] Arquivo " + filename + " recebido com sucesso. ID: " + shipmentId);
			controlOut.println("226 Transferencia concluida. ID de rastreio: " + shipmentId);

		} catch (IOException e) {
			controlOut.println("426 Conexao fechada; transferencia abortada.");
			e.printStackTrace();
		} finally {
			if (dataServerSocket != null && !dataServerSocket.isClosed()) {
				dataServerSocket.close();
			}
		}
	}

	// handleRetr (Retrieve)
	private void handleRetr(String shipmentId, PrintWriter controlOut) throws IOException {
		ShipmentInfo info = tracking.get(shipmentId);
		if (info == null) {
			controlOut.println("550 ID nao encontrado.");
			return;
		}

		Path filePath = findFileById(shipmentId);
		if (filePath == null) {
			controlOut.println("550 Arquivo fisico nao encontrado para o ID: " + shipmentId);
			return;
		}

		controlOut.println("150 Abrindo conexao de dados em modo BINARY.");

		try (Socket dataConnection = dataServerSocket.accept();
			 OutputStream dataOut = dataConnection.getOutputStream()) {

			// Envia os bytes brutos do arquivo
			Files.copy(filePath, dataOut);
			dataOut.flush();

			System.out.println("[Dados] Arquivo ID " + shipmentId + " enviado com sucesso.");
			controlOut.println("226 Transferencia de dados concluida.");

			// Atualiza status se necessário
			if (!info.getStatus().equals("ENTREGUE")) {
				Path newFilePath = uploadsDir.resolve(buildPersistentFilename(info.getId(), info.getFilename(), "ENTREGUE"));
				Files.move(filePath, newFilePath, StandardCopyOption.REPLACE_EXISTING);
				info.setStatus("ENTREGUE");
			}

		} catch (IOException e) {
			controlOut.println("426 Conexao fechada; transferencia abortada.");
			e.printStackTrace();
		} finally {
			if (dataServerSocket != null && !dataServerSocket.isClosed()) {
				dataServerSocket.close();
			}
		}
	}

	private void handleList(PrintWriter controlOut) throws IOException {
		controlOut.println("150 Aqui vem a listagem de arquivos.");

		try (Socket dataConnection = dataServerSocket.accept();
			 PrintWriter dataOut = new PrintWriter(dataConnection.getOutputStream(), true)) {

			if (tracking.isEmpty()) {
				dataOut.println("Nenhuma encomenda registrada.");
			} else {
				tracking.values().stream()
						.sorted((i1, i2) -> i2.getTimestamp().compareTo(i1.getTimestamp()))
						.forEach(info -> dataOut.println(
								String.format("%s | %-30s | %-10s | %s",
										info.getId(),
										info.getFilename(),
										info.getStatus(),
										info.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
								)
						));
			}
			System.out.println("[Dados] Listagem enviada ao cliente.");
			controlOut.println("226 Listagem de diretorio enviada.");

		} catch (IOException e) {
			controlOut.println("425 Nao foi possivel abrir a conexao de dados.");
			e.printStackTrace();
		} finally {
			if (dataServerSocket != null && !dataServerSocket.isClosed()) {
				dataServerSocket.close();
			}
		}
	}

	private void handleStatus(String shipmentId, PrintWriter out) {
		ShipmentInfo info = tracking.get(shipmentId);
		if (info == null) {
			out.println("550 ID nao encontrado");
		} else {
			// No FTP, a resposta a STAT deve ser multiline
			out.println("211-Status do sistema ou resposta de ajuda:");
			out.println("  " + info.toString());
			out.println("211 Fim do status");
		}
	}

	private Path findFileById(String shipmentId) throws IOException {
		try (Stream<Path> stream = Files.list(uploadsDir)) {
			return stream
					.filter(p -> p.getFileName().toString().startsWith(shipmentId + "_"))
					.findFirst()
					.orElse(null);
		}
	}
}