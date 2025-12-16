package server.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class ClientModel {
    private final StringProperty id;
    private final StringProperty ip;
    private final StringProperty status;

    public ClientModel(String id, String ip, String status) {
        this.id = new SimpleStringProperty(id);
        this.ip = new SimpleStringProperty(ip);
        this.status = new SimpleStringProperty(status);
    }

    public String getId() {
        return id.get();
    }

    public StringProperty idProperty() {
        return id;
    }

    public String getIp() {
        return ip.get();
    }

    public StringProperty ipProperty() {
        return ip;
    }

    public String getStatus() {
        return status.get();
    }

    public StringProperty statusProperty() {
        return status;
    }

    public void setStatus(String s) {
        this.status.set(s);
    }
}