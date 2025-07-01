package correio.client;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class FtpClientGUI extends JFrame {
    // --- UI Constants (sem alterações) ---
    private static final Color COLOR_PRIMARY = new Color(0, 123, 255);
    private static final Color COLOR_SUCCESS = new Color(40, 167, 69);
    private static final Color COLOR_ERROR = new Color(220, 53, 69);
    private static final Color COLOR_WARN = new Color(255, 193, 7);
    private static final Color COLOR_INFO = new Color(108, 117, 125);
    private static final Color COLOR_BACKGROUND = new Color(248, 249, 250);
    private static final Color COLOR_BORDER = new Color(222, 226, 230);
    private static final Color COLOR_TABLE_HEADER = new Color(232, 234, 237);
    private static final Color COLOR_TEXT_PRIMARY = new Color(33, 37, 41);
    private static final Font FONT_MAIN = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 22);
    private static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 13);

    private enum LogLevel { INFO, SENT, RECV, SUCCESS, ERROR, WARN }

    // --- Components ---
    private JTextField txtHost, txtPort, txtId;
    private JTextPane txtLog;
    private JTable shipmentTable;
    private DefaultTableModel tableModel;
    private JButton btnConnect, btnUpload, btnDownload, btnList, btnStatus, btnDisconnect;
    private JLabel lblStatus; // Removido lblConnectionIcon
    private JProgressBar progressBar;

    // --- Connection ---
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean isConnected = false;

    public FtpClientGUI() {
        super("Cliente FTP Correios - v2.3 (No-Icon)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 750);
        setMinimumSize(new Dimension(850, 600));
        setLocationRelativeTo(null);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        initUI();
        setupKeyboardShortcuts();
    }

    // --- Métodos de construção da UI (com alterações) ---
    private void initUI() {
        getContentPane().setBackground(COLOR_BACKGROUND);
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));
        add(createHeaderPanel(), BorderLayout.NORTH);
        add(createSideBarPanel(), BorderLayout.WEST);
        add(createMainContentPanel(), BorderLayout.CENTER);
        add(createStatusPanel(), BorderLayout.SOUTH);
        updateConnectionState();
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(15, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(5, 5, 15, 5));
        JLabel titleLabel = new JLabel("Cliente FTP Correios");
        titleLabel.setFont(FONT_TITLE);
        titleLabel.setForeground(COLOR_PRIMARY);
        JPanel connectionStatusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        connectionStatusPanel.setOpaque(false);
        lblStatus = new JLabel("Desconectado");
        lblStatus.setFont(FONT_MAIN);
        // Removida a criação e adição do lblConnectionIcon
        connectionStatusPanel.add(lblStatus);
        header.add(titleLabel, BorderLayout.WEST);
        header.add(connectionStatusPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel createSideBarPanel() {
        JPanel sideBar = new JPanel();
        sideBar.setLayout(new BoxLayout(sideBar, BoxLayout.Y_AXIS));
        sideBar.setOpaque(false);
        sideBar.setBorder(new EmptyBorder(0, 5, 0, 15));
        sideBar.add(createTitledPanel("Conexão", createConnectionPanel()));
        sideBar.add(Box.createRigidArea(new Dimension(0, 15)));
        sideBar.add(createTitledPanel("Operações", createCommandPanel()));
        sideBar.add(Box.createVerticalGlue());
        return sideBar;
    }

    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 0, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1;
        panel.add(new JLabel("Servidor:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        txtHost = createStyledTextField("localhost");
        setPlaceholder(txtHost, "ex: localhost ou IP");
        panel.add(txtHost, gbc);
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(new JLabel("Porta:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        txtPort = createStyledTextField("2121");
        setPlaceholder(txtPort, "ex: 2121");
        panel.add(txtPort, gbc);
        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        buttonPanel.setOpaque(false);
        btnConnect = createStyledButton("Conectar", COLOR_SUCCESS);
        btnDisconnect = createStyledButton("Desconectar", COLOR_ERROR);
        buttonPanel.add(btnConnect);
        buttonPanel.add(btnDisconnect);
        panel.add(buttonPanel, gbc);
        btnConnect.addActionListener(e -> connect());
        btnDisconnect.addActionListener(e -> disconnect());
        return panel;
    }

    private JPanel createCommandPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 0, 5, 0);
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("ID de Rastreio:"), gbc);
        gbc.gridy = 1;
        txtId = createStyledTextField("");
        setPlaceholder(txtId, "ID para Baixar/Status");
        panel.add(txtId, gbc);
        gbc.insets = new Insets(8, 0, 8, 0);
        gbc.gridy = 2; btnUpload = createStyledButton("Enviar Arquivo", COLOR_PRIMARY);
        panel.add(btnUpload, gbc);
        gbc.gridy = 3; btnDownload = createStyledButton("Baixar por ID", COLOR_PRIMARY);
        panel.add(btnDownload, gbc);
        gbc.gridy = 4; btnStatus = createStyledButton("Status por ID", COLOR_PRIMARY);
        panel.add(btnStatus, gbc);
        gbc.gridy = 5; btnList = createStyledButton("Listar Encomendas (F5)", COLOR_PRIMARY);
        panel.add(btnList, gbc);
        btnUpload.addActionListener(e -> sendFile());
        btnDownload.addActionListener(e -> getFile());
        btnList.addActionListener(e -> listFiles());
        btnStatus.addActionListener(e -> statusFile());
        return panel;
    }

    private JSplitPane createMainContentPanel() {
        JPanel tablePanel = createTitledPanel("Encomendas no Servidor", createShipmentTablePanel());
        JPanel logPanel = createTitledPanel("Console de Atividades", createLogPanel());
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tablePanel, logPanel);

        mainSplit.setBorder(null);
        mainSplit.setDividerSize(8);
        // A linha abaixo ajuda no comportamento de redimensionamento, vamos mantê-la.
        mainSplit.setResizeWeight(0.3);

        mainSplit.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // Remove o listener para que isso execute apenas uma vez.
                mainSplit.removeComponentListener(this);
                // Define a posição do divisor para 50% da altura total do painel.
                // Para mais espaço no console, use um valor menor (ex: 0.4).
                // Para menos espaço no console, use um valor maior (ex: 0.6).
                mainSplit.setDividerLocation(0.7);
            }
        });

        return mainSplit;
    }

    private JScrollPane createShipmentTablePanel() {
        String[] columnNames = {"ID", "Nome do Arquivo", "Status", "Data de Envio"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        shipmentTable = new JTable(tableModel);
        shipmentTable.setFont(FONT_MAIN);
        shipmentTable.setRowHeight(28);
        shipmentTable.getTableHeader().setFont(FONT_BOLD);
        shipmentTable.getTableHeader().setBackground(COLOR_TABLE_HEADER);
        shipmentTable.getTableHeader().setForeground(COLOR_TEXT_PRIMARY);
        shipmentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        shipmentTable.setFillsViewportHeight(true);
        shipmentTable.setShowGrid(true);
        shipmentTable.setGridColor(COLOR_BORDER);
        shipmentTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = shipmentTable.getSelectedRow();
                    if (row != -1) {
                        String id = tableModel.getValueAt(row, 0).toString();
                        txtId.setText(id);
                        txtId.setForeground(COLOR_TEXT_PRIMARY);
                        log("ID '" + id + "' copiado para o campo de rastreio.", LogLevel.INFO);
                    }
                }
            }
        });
        JScrollPane scrollPane = new JScrollPane(shipmentTable);
        scrollPane.setBorder(new LineBorder(COLOR_BORDER));
        return scrollPane;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        txtLog = new JTextPane();
        txtLog.setEditable(false);
        txtLog.setFont(FONT_MONO);
        txtLog.setBackground(Color.WHITE);
        txtLog.setBorder(new EmptyBorder(5, 8, 5, 8));
        JScrollPane scrollPane = new JScrollPane(txtLog);
        scrollPane.setBorder(null);
        JButton btnClear = new JButton("Limpar");
        btnClear.addActionListener(e -> txtLog.setText(""));
        JPanel toolPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        toolPanel.setOpaque(false);
        toolPanel.add(btnClear);
        panel.add(toolPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setOpaque(false);
        panel.setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, COLOR_BORDER), new EmptyBorder(8, 5, 0, 5)));
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        progressBar.setString("Pronto");
        progressBar.setVisible(false);
        panel.add(progressBar, BorderLayout.CENTER);
        return panel;
    }

    private JTextField createStyledTextField(String text) {
        JTextField field = new JTextField(text);
        field.setFont(FONT_MAIN);
        field.setBorder(new CompoundBorder(new LineBorder(COLOR_BORDER), new EmptyBorder(5, 8, 5, 8)));
        return field;
    }

    private JButton createStyledButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(FONT_BOLD);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(new EmptyBorder(10, 15, 10, 15));
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { if (button.isEnabled()) button.setBackground(color.darker()); }
            @Override public void mouseExited(MouseEvent e) { button.setBackground(color); }
        });
        return button;
    }

    private JPanel createTitledPanel(String title, Component content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new LineBorder(COLOR_BORDER));
        panel.setOpaque(false);
        JLabel titleLabel = new JLabel(title.toUpperCase());
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLabel.setForeground(COLOR_TEXT_PRIMARY);
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        titlePanel.setBackground(COLOR_TABLE_HEADER);
        titlePanel.setBorder(new MatteBorder(0, 0, 1, 0, COLOR_BORDER));
        titlePanel.add(titleLabel);
        JPanel contentWrapper = new JPanel(new BorderLayout());
        contentWrapper.setOpaque(false);
        contentWrapper.setBorder(new EmptyBorder(10, 10, 10, 10));
        contentWrapper.add(content, BorderLayout.CENTER);
        panel.add(titlePanel, BorderLayout.NORTH);
        panel.add(contentWrapper, BorderLayout.CENTER);
        return panel;
    }

    private void setPlaceholder(JTextField field, String placeholder) {
        field.setText(placeholder);
        field.setForeground(Color.GRAY);
        field.addFocusListener(new FocusListener() {
            @Override public void focusGained(FocusEvent e) { if (field.getText().equals(placeholder)) { field.setText(""); field.setForeground(COLOR_TEXT_PRIMARY); } }
            @Override public void focusLost(FocusEvent e) { if (field.getText().isEmpty()) { field.setForeground(Color.GRAY); field.setText(placeholder); } }
        });
    }

    // --- Core Logic & MUDANÇAS ---

    private void updateConnectionState() {
        btnConnect.setEnabled(!isConnected);
        btnDisconnect.setEnabled(isConnected);
        btnUpload.setEnabled(isConnected);
        btnDownload.setEnabled(isConnected);
        btnList.setEnabled(isConnected);
        btnStatus.setEnabled(isConnected);
        txtHost.setEnabled(!isConnected);
        txtPort.setEnabled(!isConnected);

        if (isConnected) {
            lblStatus.setText("Conectado a " + socket.getInetAddress().getHostAddress());
            lblStatus.setForeground(COLOR_SUCCESS);
        } else {
            lblStatus.setText("Desconectado");
            lblStatus.setForeground(COLOR_ERROR);
            tableModel.setRowCount(0);
        }
    }

    // NOVO: Desabilita os botões de ação durante uma operação de rede
    private void lockUIForOperation(String message) {
        showProgress(message);
        btnConnect.setEnabled(false);
        btnDisconnect.setEnabled(false);
        btnUpload.setEnabled(false);
        btnDownload.setEnabled(false);
        btnList.setEnabled(false);
        btnStatus.setEnabled(false);
    }

    // NOVO: Restaura o estado dos botões após a operação
    private void unlockUI() {
        hideProgress("Pronto");
        updateConnectionState(); // Restaura o estado correto dos botões
    }

    private void connect() {
        new Thread(() -> {
            // Garante que a atualização da UI ocorra na Event Dispatch Thread (EDT)
            SwingUtilities.invokeLater(() -> lockUIForOperation("Conectando..."));
            try {
                socket = new Socket(txtHost.getText(), Integer.parseInt(txtPort.getText()));
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                String welcome = in.readLine();
                log(welcome, LogLevel.RECV);
                isConnected = true;
                SwingUtilities.invokeLater(() -> {
                    log("Conexão estabelecida com sucesso.", LogLevel.SUCCESS);
                    listFiles(); // Chama listFiles, que já tem seu próprio lock/unlock
                });
            } catch (Exception ex) {
                log("Erro de conexão: " + ex.getMessage(), LogLevel.ERROR);
                isConnected = false;
            } finally {
                // Desbloqueia a UI no final, independentemente de sucesso ou falha
                if(!isConnected) SwingUtilities.invokeLater(this::unlockUI);
            }
        }).start();
    }

    private void disconnect() {
        if (!isConnected) return;
        new Thread(() -> {
            SwingUtilities.invokeLater(() -> lockUIForOperation("Desconectando..."));
            try {
                if (out != null) out.println("QUIT");
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ex) {
                log("Erro ao tentar desconectar: " + ex.getMessage(), LogLevel.ERROR);
            } finally {
                isConnected = false;
                SwingUtilities.invokeLater(() -> {
                    log("Desconectado do servidor.", LogLevel.WARN);
                    unlockUI(); // Desbloqueia a UI após desconectar
                });
            }
        }).start();
    }

    private void sendFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            new Thread(() -> {
                SwingUtilities.invokeLater(() -> lockUIForOperation("Enviando: " + file.getName()));
                try {
                    String command = "PUT " + file.getName();
                    out.println(command);
                    log(command, LogLevel.SENT);

                    String resp = in.readLine();
                    log(resp, resp.startsWith("150") ? LogLevel.RECV : LogLevel.ERROR);

                    if (resp.startsWith("150")) {
                        byte[] fileBytes = Files.readAllBytes(file.toPath());
                        String encodedString = Base64.getEncoder().encodeToString(fileBytes);
                        out.println(encodedString);
                        out.println("EOF");
                        out.flush();

                        String done = in.readLine();
                        log(done, LogLevel.SUCCESS);
                        listFiles(); // Já tem lock/unlock
                    }
                } catch (IOException e) {
                    log("Erro ao enviar arquivo: " + e.getMessage(), LogLevel.ERROR);
                } finally {
                    if(!isConnected) SwingUtilities.invokeLater(this::unlockUI);
                }
            }).start();
        }
    }

    private void getFile() {
        String id = txtId.getText().trim();
        if (id.isEmpty() || id.startsWith("ID para")) {
            log("ID de rastreio não informado.", LogLevel.WARN);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        String suggestedName = "download_" + id;
        int selectedRow = shipmentTable.getSelectedRow();
        if (selectedRow != -1 && shipmentTable.getValueAt(selectedRow, 0).toString().equals(id)) {
            suggestedName = shipmentTable.getValueAt(selectedRow, 1).toString();
        }
        chooser.setSelectedFile(new File(suggestedName));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            new Thread(() -> {
                SwingUtilities.invokeLater(() -> lockUIForOperation("Baixando ID: " + id));
                try {
                    String command = "GET " + id;
                    out.println(command);
                    log(command, LogLevel.SENT);

                    String line = in.readLine();
                    log(line, line.startsWith("150") ? LogLevel.RECV : LogLevel.ERROR);

                    if (line.startsWith("150")) {
                        String base64Data = in.readLine();
                        in.readLine(); // Consome EOF
                        byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
                        Files.write(selectedFile.toPath(), decodedBytes);

                        String finalResp = in.readLine();
                        log(finalResp + " - Salvo em: " + selectedFile.getAbsolutePath(), LogLevel.SUCCESS);
                        listFiles(); // Já tem lock/unlock
                    }
                } catch (IOException e) {
                    log("Erro ao baixar arquivo: " + e.getMessage(), LogLevel.ERROR);
                } finally {
                    if(!isConnected) SwingUtilities.invokeLater(this::unlockUI);
                }
            }).start();
        }
    }

    private void listFiles() {
        new Thread(() -> {
            SwingUtilities.invokeLater(() -> lockUIForOperation("Listando encomendas..."));
            try {
                out.println("LIST");
                log("LIST", LogLevel.SENT);
                String line = in.readLine();
                log(line, LogLevel.RECV);
                SwingUtilities.invokeLater(() -> tableModel.setRowCount(0));
                while (!(line = in.readLine()).equals("END")) {
                    if (line.startsWith("EMPTY")) {
                        log("Nenhuma encomenda registrada no servidor.", LogLevel.INFO);
                        continue;
                    }
                    String[] parts = line.split("\\|");
                    if (parts.length == 4) {
                        final Object[] rowData = { parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim() };
                        SwingUtilities.invokeLater(() -> tableModel.addRow(rowData));
                    }
                }
                log("Lista de encomendas atualizada.", LogLevel.SUCCESS);
            } catch (IOException e) {
                log("Erro ao listar encomendas: " + e.getMessage(), LogLevel.ERROR);
            } finally {
                SwingUtilities.invokeLater(this::unlockUI);
            }
        }).start();
    }

    private void statusFile() {
        String id = txtId.getText().trim();
        if (id.isEmpty() || id.startsWith("ID para")) {
            log("ID de rastreio não informado.", LogLevel.WARN);
            return;
        }
        new Thread(() -> {
            SwingUtilities.invokeLater(() -> lockUIForOperation("Verificando status do ID: " + id));
            try {
                String command = "STATUS " + id;
                out.println(command);
                log(command, LogLevel.SENT);
                String response = in.readLine();
                log(response, LogLevel.RECV);
            } catch (IOException e) {
                log("Erro ao verificar status: " + e.getMessage(), LogLevel.ERROR);
            } finally {
                SwingUtilities.invokeLater(this::unlockUI);
            }
        }).start();
    }

    // --- Métodos de log e progresso (sem alterações) ---
    private void log(String msg, LogLevel level) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = txtLog.getStyledDocument();
            SimpleAttributeSet keyWord = new SimpleAttributeSet();
            String prefix = "[" + level.name() + "] ";
            switch(level) {
                case INFO: StyleConstants.setForeground(keyWord, COLOR_INFO); break;
                case SENT: StyleConstants.setForeground(keyWord, COLOR_PRIMARY); break;
                case RECV: StyleConstants.setForeground(keyWord, new Color(13, 110, 253)); break;
                case SUCCESS: StyleConstants.setForeground(keyWord, COLOR_SUCCESS); break;
                case ERROR: StyleConstants.setForeground(keyWord, COLOR_ERROR); break;
                case WARN: StyleConstants.setForeground(keyWord, new Color(204, 139, 0)); break;
            }
            StyleConstants.setBold(keyWord, true);
            try {
                String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                doc.insertString(doc.getLength(), timestamp + " ", null);
                doc.insertString(doc.getLength(), prefix, keyWord);
                doc.insertString(doc.getLength(), msg + "\n", null);
                txtLog.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    private void showProgress(String message) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setString(message);
            progressBar.setIndeterminate(true);
            progressBar.setVisible(true);
        });
    }

    private void hideProgress(String message) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setValue(100);
            progressBar.setString(message);
            Timer timer = new Timer(2000, e -> progressBar.setVisible(false));
            timer.setRepeats(false);
            timer.start();
        });
    }

    private void setupKeyboardShortcuts() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "refreshList");
        getRootPane().getActionMap().put("refreshList", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // A verificação de 'isEnabled' previne o acionamento via atalho
                // enquanto uma operação já está em andamento.
                if (isConnected && btnList.isEnabled()) {
                    listFiles();
                }
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FtpClientGUI().setVisible(true));
    }
}