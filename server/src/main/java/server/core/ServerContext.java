package server.core;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import server.model.ClientModel;
import server.ui.ServerApp; // Sẽ sửa ServerApp sau

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Singleton Pattern: Quản lý toàn bộ trạng thái Server
public class ServerContext {

    // 1. Dữ liệu TCP
    private static final Map<String, Channel> clients = new ConcurrentHashMap<>();
    private static final Map<String, String> passwords = new ConcurrentHashMap<>();

    // 2. Dữ liệu UDP (Chuyển từ UdpServerHandler sang đây cho dễ quản lý)
    private static final Map<String, InetSocketAddress> udpClients = new ConcurrentHashMap<>();

    // 3. Quản lý nhóm Channel (để broadcast)
    public static final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    // --- CÁC PHƯƠNG THỨC QUẢN LÝ CLIENT ---

    public static void addClient(String userId, String pass, Channel channel, String ip) {
        clients.put(userId, channel);
        passwords.put(userId, pass);

        // Cập nhật UI
        ServerApp.updateClientList(new ClientModel(userId, ip, "Online"), true);
    }

    public static void removeClient(String userId) {
        if (userId == null)
            return;
        clients.remove(userId);
        passwords.remove(userId);
        udpClients.remove(userId); // Xóa cả UDP khi thoát

        // Cập nhật UI
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

    // --- QUẢN LÝ UDP ---
    public static void registerUdp(String userId, InetSocketAddress address) {
        udpClients.put(userId, address);
    }

    public static InetSocketAddress getUdpAddress(String userId) {
        return udpClients.get(userId);
    }

    // --- UTILS ---
    public static String getClientIdByChannel(Channel channel) {
        for (Map.Entry<String, Channel> entry : clients.entrySet()) {
            if (entry.getValue() == channel)
                return entry.getKey();
        }
        return null;
    }
}