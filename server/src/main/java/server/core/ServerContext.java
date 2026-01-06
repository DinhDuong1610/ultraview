package server.core;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import server.model.ClientModel;
import server.ui.ServerApp;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;

public class ServerContext {

    private static final Map<String, Channel> clients = new ConcurrentHashMap<>();
    private static final Map<String, String> passwords = new ConcurrentHashMap<>();
    private static final Map<String, String> partners = new ConcurrentHashMap<>();
    private static final Map<String, Integer> controlPorts = new ConcurrentHashMap<>();
    private static final Map<String, String> sessions = new ConcurrentHashMap<>();

    private static final Map<String, InetSocketAddress> udpClients = new ConcurrentHashMap<>();

    public static final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    public static void addClient(String userId, String pass, Channel channel, String ip) {
        clients.put(userId, channel);
        passwords.put(userId, pass);

        ServerApp.updateClientList(new ClientModel(userId, ip, "Online"), true);
    }

    public static void removeClient(String userId) {
        if (userId == null)
            return;
        clients.remove(userId);
        passwords.remove(userId);
        udpClients.remove(userId);
        controlPorts.remove(userId);
        unpair(userId);

        ServerApp.updateClientList(new ClientModel(userId, "", ""), false);
    }

    public static Channel getClientChannel(String userId) {
        return clients.get(userId);
    }

    public static boolean checkPassword(String userId, String inputPass) {
        String realPass = passwords.get(userId);
        return realPass != null && realPass.equals(inputPass);
    }

    public static boolean isOnline(String userId) {
        return clients.containsKey(userId);
    }

    public static void registerUdp(String userId, InetSocketAddress address) {
        udpClients.put(userId, address);
    }

    public static InetSocketAddress getUdpAddress(String userId) {
        return udpClients.get(userId);
    }

    public static String getClientIdByChannel(Channel channel) {
        for (Map.Entry<String, Channel> entry : clients.entrySet()) {
            if (entry.getValue() == channel)
                return entry.getKey();
        }
        return null;
    }

    public static synchronized boolean pair(String a, String b, String sessionId) {
        if (a == null || b == null || a.equals(b))
            return false;
        if (partners.containsKey(a) || partners.containsKey(b))
            return false;

        partners.put(a, b);
        partners.put(b, a);

        sessions.put(a, sessionId);
        sessions.put(b, sessionId);
        return true;
    }

    public static synchronized String unpair(String userId) {
        if (userId == null)
            return null;
        String other = partners.remove(userId);
        if (other != null)
            partners.remove(other);

        sessions.remove(userId);
        if (other != null)
            sessions.remove(other);
        return other;
    }

    public static String getPartner(String userId) {
        return partners.get(userId);
    }

    public static String getSessionId(String userId) {
        return sessions.get(userId);
    }

    public static void setControlPort(String userId, int port) {
        controlPorts.put(userId, port);
    }

    public static Integer getControlPort(String userId) {
        return controlPorts.get(userId);
    }

    public static boolean isPaired(String userId) {
        return userId != null && partners.containsKey(userId);
    }
}