package correio.server;

import java.time.LocalDateTime;

public class ShipmentInfo {
    private final String id;
    private final String filename;
    private final LocalDateTime timestamp;
    private String status;

    // Construtor original, usado para novas encomendas
    public ShipmentInfo(String id, String filename) {
        this.id = id;
        this.filename = filename;
        this.timestamp = LocalDateTime.now();
        this.status = "ENVIADA";
    }


    public ShipmentInfo(String id, String filename, LocalDateTime timestamp, String status) {
        this.id = id;
        this.filename = filename;
        this.timestamp = timestamp;
        this.status = status;
    }


    public String getId() { return id; }
    public String getFilename() { return filename; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public synchronized String getStatus() { return status; }
    public synchronized void setStatus(String status) { this.status = status; }
    @Override
    public String toString() {
        return id + ": " + filename + " (" + status + ")";
    }
}