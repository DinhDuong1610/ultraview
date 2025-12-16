package codec;

import protocol.NetworkPacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class NettyKryoEncoder extends MessageToByteEncoder<NetworkPacket> {
    @Override
    protected void encode(ChannelHandlerContext ctx, NetworkPacket msg, ByteBuf out) throws Exception {
        // 1. Serialize object thành byte array
        byte[] body = KryoSerializer.serialize(msg);
        int dataLength = body.length;

        // 2. Ghi độ dài gói tin vào header (4 byte đầu tiên)
        out.writeInt(dataLength);

        // 3. Ghi nội dung gói tin
        out.writeBytes(body);
    }
}