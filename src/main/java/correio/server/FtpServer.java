package correio.server;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.stream.Stream;

public class FtpServer extends JFrame {
    private static final int PORT = 2121;
    private static final Path uploadsDir = Paths.get("uploads");

    private final ConcurrentMap<String, ShipmentInfo> tracking = new ConcurrentHashMap<>();
    private ServerSocket serverSocket;
    private ExecutorService pool;
    private volatile boolean running = true;

    private JTextArea logArea;
    private JButton shutdownButton;

    public FtpServer() {
        super("Servidor FTP Correios (Modo Passivo)");
        initUI();
        redirectSystemStreams();
        startServer();
    }

    private void initUI() {
        setSize(700, 500);
        setMinimumSize(new Dimension(500, 300));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setMargin(new Insets(5, 5, 5, 5));

        JScrollPane scrollPane = new JScrollPane(logArea);

        shutdownButton = new JButton("Encerrar Servidor");
        shutdownButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        shutdownButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        shutdownButton.addActionListener(e -> shutdown());

        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        southPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        southPanel.add(shutdownButton);

        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(scrollPane, BorderLayout.CENTER);
        contentPane.add(southPanel, BorderLayout.SOUTH);
    }

    private void redirectSystemStreams() {
        OutputStream out = new TextAreaOutputStream(logArea);
        System.setOut(new PrintStream(out, true));
        System.setErr(new PrintStream(out, true));
    }

    public void startServer() {
        new Thread(() -> {
            try {
                Files.createDirectories(uploadsDir);
                loadShipmentsFromDisk(tracking);

                serverSocket = new ServerSocket(PORT);
                pool = Executors.newCachedThreadPool();
                running = true;

                String ipAddress;
                try {
                    ipAddress = InetAddress.getLocalHost().getHostAddress();
                } catch (UnknownHostException e) {
                    ipAddress = "localhost";
                }

                System.out.println("Servidor FTP (PASV) iniciado em: " + ipAddress + ":" + PORT);
                System.out.println("Aguardando conexões de controle...");
                System.out.println("------------------------------------");

                while (running) {
                    try {
                        Socket clientControlSocket = serverSocket.accept();
                        System.out.println("Nova conexão de controle de: " + clientControlSocket.getInetAddress().getHostAddress());
                        pool.submit(new ClientHandler(clientControlSocket, tracking));
                    } catch (SocketException e) {
                        if (running) System.err.println("Erro no socket de controle: " + e.getMessage());
                        else System.out.println("Servidor de controle encerrado.");
                    }
                }
            } catch (IOException e) {
                if (running) e.printStackTrace();
            }
        }).start();
    }

    public void shutdown() {
        if (JOptionPane.showConfirmDialog(this, "Tem certeza?", "Confirmar Encerramento", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }

        System.out.println("------------------------------------");
        System.out.println("Iniciando encerramento do servidor...");
        running = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Erro ao fechar o socket do servidor: " + e.getMessage());
        }

        if (pool != null) {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
            }
        }

        System.out.println("Servidor encerrado.");
        dispose();
        System.exit(0);
    }

    private void loadShipmentsFromDisk(ConcurrentMap<String, ShipmentInfo> trackingMap) {
        System.out.println("Verificando encomendas existentes no disco...");
        try (Stream<Path> paths = Files.list(uploadsDir)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                // Lógica para carregar arquivos do disco (inalterada)
                String fullFileName = path.getFileName().toString();
                String basePart;
                String extensionPart = "";
                int lastDot = fullFileName.lastIndexOf('.');
                if (lastDot > 0) {
                    basePart = fullFileName.substring(0, lastDot);
                    extensionPart = fullFileName.substring(lastDot);
                } else {
                    basePart = fullFileName;
                }
                String[] parts = basePart.split("_");
                if (parts.length < 3) return;
                String shipmentId = parts[0];
                String status = parts[parts.length - 1];
                String originalBaseName = String.join("_", Arrays.copyOfRange(parts, 1, parts.length - 1));
                String originalFilename = originalBaseName + extensionPart;
                try {
                    Instant instant = Files.getLastModifiedTime(path).toInstant();
                    LocalDateTime timestamp = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
                    ShipmentInfo info = new ShipmentInfo(shipmentId, originalFilename, timestamp, status);
                    trackingMap.put(shipmentId, info);
                    System.out.println(" -> Encomenda carregada: " + info);
                } catch (IOException e) {
                    System.err.println("Erro ao ler metadados do arquivo " + fullFileName + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            System.err.println("Erro ao ler o diretório de uploads: " + e.getMessage());
        }
        System.out.println(trackingMap.size() + " encomenda(s) carregada(s) do disco.");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                System.err.println("Não foi possível usar o Look and Feel do sistema.");
            }
            new FtpServer().setVisible(true);
        });
    }
}