package client.service.video;

import java.awt.Rectangle;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SensitiveMask {

    public static class MaskedArea {
        public Rectangle rect;
        public long expiryTime;

        public MaskedArea(Rectangle rect, long durationMs) {
            this.rect = rect;
            this.expiryTime = System.currentTimeMillis() + durationMs;
        }
    }

    public static final List<MaskedArea> activeMasks = new CopyOnWriteArrayList<>();

    public static void addMasks(List<Rectangle> newRects) {
        // [QUAN TRỌNG] Tăng lên 4000ms (4 giây)
        // Để đảm bảo mask luôn tồn tại cho đến khi AI quét xong lượt tiếp theo
        long duration = 6000;

        for (Rectangle r : newRects) {
            boolean updated = false;
            for (MaskedArea ma : activeMasks) {
                // Nếu vùng mới trùng với vùng cũ, gia hạn thêm thời gian
                if (ma.rect.intersects(r)) {
                    ma.rect = r; // Cập nhật vị trí mới (nếu có di chuyển nhẹ)
                    ma.expiryTime = System.currentTimeMillis() + duration;
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                activeMasks.add(new MaskedArea(r, duration));
            }
        }
    }

    public static void cleanupExpired() {
        long now = System.currentTimeMillis();
        activeMasks.removeIf(ma -> now > ma.expiryTime);
    }
}