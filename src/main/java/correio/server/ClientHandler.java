package correio.server;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.Base64; // ADICIONADO: Para codificar/decodificar arquivos
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

public class ClientHandler implements Runnable {
	private final Socket socket;
	private final Map<String, ShipmentInfo> tracking;
	private final Path uploadsDir = Paths.get("uploads");
	private static final Random random = new Random();

	public ClientHandler(Socket socket, Map<String, ShipmentInfo> tracking) {
		this.socket = socket;
		this.tracking = tracking;
		try {
			Files.createDirectories(uploadsDir);
		} catch (IOException e) {
			System.err.println("Erro ao criar diretório de uploads: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private String buildPersistentFilename(String id, String originalName, String status) {
		String baseName = originalName;
		String extension = "";
		int dotIndex = originalName.lastIndexOf('.');
		if (dotIndex > 0 && dotIndex < originalName.length() - 1) {
			baseName = originalName.substring(0, dotIndex);
			extension = originalName.substring(dotIndex);
		}
		return String.format("%s_%s_%s%s", id, baseName, status, extension);
	}

	@Override
	public void run() {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
			out.println("220 Bem-vindo ao FTP Correios");
			String line;
			while ((line = in.readLine()) != null) {
				System.out.println("[Servidor] Comando recebido: " + line);
				String[] parts = line.split(" ", 2);
				String cmd = parts[0].toUpperCase();
				String arg = parts.length > 1 ? parts[1] : null;
				switch (cmd) {
					case "PUT": handlePut(arg, in, out); break;
					case "GET": handleGet(arg, out); break;
					case "LIST": handleList(out); break;
					case "STATUS": handleStatus(arg, out); break;
					case "QUIT": out.println("221 Adeus"); socket.close(); return;
					default: out.println("500 Comando desconhecido");
				}
			}
		} catch (IOException e) {
			// Ignorar erro de "Connection reset" que é comum quando o cliente desconecta abruptamente
			if (!e.getMessage().contains("Connection reset")) {
				e.printStackTrace();
			}
		}
	}

	private void handlePut(String filename, BufferedReader in, PrintWriter out) throws IOException {
		String shipmentId;
		do {
			int randomId = random.nextInt(10000);
			shipmentId = String.format("%04d", randomId);
		} while (tracking.containsKey(shipmentId));

		String persistentFilename = buildPersistentFilename(shipmentId, filename, "ENVIADA");
		Path filePath = uploadsDir.resolve(persistentFilename);

		System.out.println("[Servidor] Recebendo arquivo: " + filename + " (ID: " + shipmentId + ")");
		out.println("150 Pronto para receber: " + filename);

		// MODIFICADO: Lê os dados como uma única string Base64 e os decodifica para bytes.
		try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
			String base64Data = in.readLine(); // Lê a linha única com os dados codificados
			if (base64Data != null && !base64Data.equals("EOF")) {
				byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
				fos.write(decodedBytes);
			}
			// A próxima linha deve ser "EOF", vamos consumi-la
			in.readLine();
		}

		ShipmentInfo info = new ShipmentInfo(shipmentId, filename);
		tracking.put(shipmentId, info);
		out.println("226 Transferencia concluida. ID de rastreio: " + shipmentId);
		System.out.println("[Servidor] Arquivo " + filename + " recebido com sucesso. ID: " + shipmentId);
	}

	private void handleGet(String shipmentId, PrintWriter out) throws IOException {
		ShipmentInfo info = tracking.get(shipmentId);
		if (info == null) {
			out.println("550 ID nao encontrado");
			return;
		}

		Path filePath;
		try (Stream<Path> stream = Files.list(uploadsDir)) {
			filePath = stream
					.filter(p -> p.getFileName().toString().startsWith(shipmentId + "_"))
					.findFirst()
					.orElse(null);
		}

		if (filePath == null || !Files.exists(filePath)) {
			out.println("550 Arquivo fisico nao encontrado no servidor para o ID: " + shipmentId);
			return;
		}

		out.println("150 Iniciando download");

		// MODIFICADO: Lê o arquivo como bytes, codifica para Base64 e envia como uma única string.
		byte[] fileBytes = Files.readAllBytes(filePath);
		String encodedString = Base64.getEncoder().encodeToString(fileBytes);
		out.println(encodedString);
		out.println("EOF"); // Sinaliza o fim da transmissão de dados

		out.println("226 Concluido");

		if (!info.getStatus().equals("ENTREGUE")) {
			String newFilename = buildPersistentFilename(info.getId(), info.getFilename(), "ENTREGUE");
			Path newFilePath = uploadsDir.resolve(newFilename);
			try {
				Files.move(filePath, newFilePath, StandardCopyOption.REPLACE_EXISTING);
				info.setStatus("ENTREGUE");
				System.out.println("[Servidor] Status atualizado para ENTREGUE. Arquivo renomeado para: " + newFilename);
			} catch (IOException e) {
				System.err.println("[Servidor] ERRO: Falha ao renomear arquivo para " + newFilename + ": " + e.getMessage());
			}
		}
	}

	private void handleList(PrintWriter out) {
		out.println("213 Arquivos disponíveis no servidor:");
		if (tracking.isEmpty()) {
			out.println("EMPTY Nenhuma encomenda registrada");
		} else {
			tracking.values().stream()
					.sorted((i1, i2) -> i2.getTimestamp().compareTo(i1.getTimestamp())) // Ordena do mais novo para o mais antigo
					.forEach(info ->
							out.println(String.format("%s | %s | %s | %s",
									info.getId(),
									info.getFilename(),
									info.getStatus(),
									info.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
							)
					);
		}
		out.println("END");
	}

	private void handleStatus(String shipmentId, PrintWriter out) {
		ShipmentInfo info = tracking.get(shipmentId);
		if (info == null) {
			out.println("550 ID nao encontrado");
		} else {
			out.println("214 " + info.toString());
		}
	}
}