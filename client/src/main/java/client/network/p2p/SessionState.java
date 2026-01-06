package client.network.p2p;

public class SessionState {
    private volatile String sessionId;
    private volatile String partnerId;

    public void set(String sessionId, String partnerId) {
        this.sessionId = sessionId;
        this.partnerId = partnerId;
    }

    public boolean isValid(String sid, String pid) {
        return sid != null && pid != null && sid.equals(this.sessionId) && pid.equals(this.partnerId);
    }

    public void clear() {
        sessionId = null;
        partnerId = null;
    }
}
