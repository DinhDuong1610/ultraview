package codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import protocol.core.NetworkPacket;

public class NettyKryoEncoder extends MessageToByteEncoder<NetworkPacket> {
    @Override
    protected void encode(ChannelHandlerContext ctx, NetworkPacket msg, ByteBuf out) throws Exception {
        byte[] body = KryoSerializer.serialize(msg);
        int dataLength = body.length;

        out.writeInt(dataLength);

        out.writeBytes(body);
    }
}