package codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;

public class NettyKryoDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Cần ít nhất 4 byte để đọc được độ dài (header)
        if (in.readableBytes() < 4) {
            return;
        }

        // Đánh dấu vị trí đọc hiện tại (để nếu thiếu dữ liệu thì rollback lại)
        in.markReaderIndex();

        // Đọc độ dài gói tin
        int dataLength = in.readInt();

        // Nếu số byte hiện có < độ dài gói tin -> Chưa nhận đủ, chờ tiếp
        if (in.readableBytes() < dataLength) {
            in.resetReaderIndex(); // Quay lui lại vị trí mark ban nãy
            return;
        }

        // Đọc đủ dữ liệu
        byte[] body = new byte[dataLength];
        in.readBytes(body);

        // Deserialize thành Object và đẩy sang handler tiếp theo
        Object obj = KryoSerializer.deserialize(body);
        out.add(obj);
    }
}