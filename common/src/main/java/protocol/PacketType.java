package protocol;

public enum PacketType {
    // Nhóm xác thực
    LOGIN_REQUEST, // Client xin đăng nhập
    LOGIN_RESPONSE, // Server trả lời (Thành công/Thất bại)

    // Nhóm chat
    CHAT_MESSAGE, // Tin nhắn chat text
    CONTROL_SIGNAL,
    CLIPBOARD_DATA,
    FILE_REQ, // Yêu cầu gửi file (Chứa tên, dung lượng)
    FILE_CHUNK, // Chứa dữ liệu file
    FILE_OFFER, // (Mới) Gửi thông tin file trước: Tên, Size
    FILE_ACCEPT, // (Mới) Người nhận đồng ý tải
    CONNECT_REQUEST, // B gửi yêu cầu kết nối kèm Pass
    CONNECT_RESPONSE, // Server trả lời B (Thành công/Thất bại)
    START_STREAM, // Server ra lệnh cho A bắt đầu share
    DISCONNECT_NOTICE,
    AUDIO_DATA,
    PEER_INFO
}