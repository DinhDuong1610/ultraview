package codec;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import protocol.AudioPacket;
import protocol.ChatMessage;
import protocol.ClipboardPacket;
import protocol.ConnectRequestPacket;
import protocol.ConnectResponsePacket;
import protocol.ControlPayload;
import protocol.DisconnectPacket;
import protocol.FileAcceptPacket;
import protocol.FileChunkPacket;
import protocol.FileOfferPacket;
import protocol.FileReqPacket;
import protocol.LoginRequest;
import protocol.NetworkPacket;
import protocol.PacketType;
import protocol.PeerInfoPacket;
import protocol.StartStreamPacket;
import protocol.VideoPacket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class KryoSerializer {
    // ThreadLocal để đảm bảo thread-safety cho Kryo
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false); // Cho phép serialize cả những class chưa đăng ký (tiện cho dev)

        // Đăng ký các class để tối ưu hiệu năng (Kryo sẽ dùng ID thay vì gửi nguyên tên
        // class string dài ngoằng)
        kryo.register(NetworkPacket.class);
        kryo.register(PacketType.class);
        kryo.register(LoginRequest.class);
        kryo.register(ChatMessage.class);
        kryo.register(VideoPacket.class);
        kryo.register(ControlPayload.class);
        kryo.register(ClipboardPacket.class);
        kryo.register(FileReqPacket.class);
        kryo.register(FileChunkPacket.class);
        kryo.register(ConnectRequestPacket.class);
        kryo.register(ConnectResponsePacket.class);
        kryo.register(StartStreamPacket.class);
        kryo.register(FileOfferPacket.class);
        kryo.register(FileAcceptPacket.class);
        kryo.register(DisconnectPacket.class);
        kryo.register(AudioPacket.class);
        kryo.register(PeerInfoPacket.class);

        return kryo;
    });

    public static byte[] serialize(Object object) {
        Kryo kryo = kryoThreadLocal.get();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Output output = new Output(outputStream);
        kryo.writeClassAndObject(output, object);
        output.close();
        return outputStream.toByteArray();
    }

    public static Object deserialize(byte[] bytes) {
        Kryo kryo = kryoThreadLocal.get();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        Input input = new Input(inputStream);
        return kryo.readClassAndObject(input);
    }
}