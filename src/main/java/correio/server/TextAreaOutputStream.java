package correio.server;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.io.OutputStream;
import java.io.IOException;

/**
 * Esta classe redireciona a saída de um OutputStream (como o System.out)
 * para um JTextArea, garantindo que a atualização da UI ocorra na
 * Event Dispatch Thread (EDT) do Swing.
 */
public class TextAreaOutputStream extends OutputStream {

    private final JTextArea textArea;
    private final StringBuilder sb = new StringBuilder();

    public TextAreaOutputStream(final JTextArea textArea) {
        this.textArea = textArea;
    }

    @Override
    public void flush() {
        // Método não precisa de implementação complexa aqui
    }

    @Override
    public void close() {
        // Método não precisa de implementação complexa aqui
    }

    @Override
    public void write(int b) throws IOException {
        // Redireciona os bytes para o JTextArea
        if (b == '\r') {
            return; // Ignora o caractere de retorno de carro
        }

        if (b == '\n') {
            final String text = sb.toString() + "\n";
            // Usa invokeLater para garantir que a atualização da UI seja thread-safe
            SwingUtilities.invokeLater(() -> {
                textArea.append(text);
                textArea.setCaretPosition(textArea.getDocument().getLength()); // Rola para o final
            });
            sb.setLength(0); // Limpa o buffer
            return;
        }

        sb.append((char) b);
    }
}