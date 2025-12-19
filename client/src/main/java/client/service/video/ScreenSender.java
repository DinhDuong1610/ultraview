package client.service.video;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import client.network.NetworkClient;
import client.service.ai.WindowSensor;
import protocol.media.VideoPacket;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ScreenSender {

    private NetworkClient networkClient;
    private String myId;
    private String targetId;
    private ScheduledExecutorService executor;
    private boolean isStreaming = false;
    private Robot robot;
    private Rectangle screenRect;

    private static final int MAX_CHUNK_SIZE = 45000;
    private long frameIdCounter = 0;

    private static final String[] SENSITIVE_TITLES = {
            // --- NHÓM 1: ĐĂNG NHẬP & XÁC THỰC (Authentication) ---
            "password", "mật khẩu", "mat khau",
            "login", "log in", "đăng nhập", "dang nhap",
            "signin", "sign in", "sign-in",
            "signup", "sign up", "đăng ký",
            "verify", "xác thực", "verification",
            "otp", "2fa", "mfa", "authenticator", "authy",
            "recover", "khôi phục tài khoản", "reset password",
            "pin code", "mã pin", "security code",

            // --- NHÓM 2: TÀI CHÍNH & NGÂN HÀNG (Finance & Banking) ---
            // Từ khóa chung
            "bank", "banking", "ngân hàng",
            "wallet", "ví điện tử", "e-wallet",
            "payment", "thanh toán", "checkout", "billing",
            "credit card", "debit card", "thẻ tín dụng", "thẻ ghi nợ",
            "transaction", "giao dịch", "chuyển khoản", "transfer",
            "balance", "số dư", "sao kê",
            // Các ngân hàng/Ví phổ biến tại VN (Thêm nếu cần)
            "vietcombank", "vcb", "techcombank", "tcb", "mbbank", "vib", "acb", "sacombank", "bidv", "vietinbank",
            "tpbank", "agribank",
            "momo", "zalopay", "vnpay", "shopeepay", "paypal", "stripe", "payoneer",
            // Crypto
            "binance", "metamask", "coinbase", "trust wallet", "crypto", "bitcoin",

            // --- NHÓM 3: QUẢN LÝ MẬT KHẨU (Password Managers) ---
            "lastpass", "1password", "bitwarden", "dashlane", "keepass", "roboform", "password manager",

            // --- NHÓM 4: CODE & SERVER CONFIG (Developer Secrets) ---
            "config", "configuration", "cấu hình",
            ".env", "environment",
            "secret", "bí mật",
            "api key", "apikey", "access token", "bearer",
            "private key", "public key", "ssh-rsa", "id_rsa", ".pem", ".ppk",
            "database", "cơ sở dữ liệu", "phpmyadmin", "navicat", "dbeaver", // Quản lý DB thường lộ data
            "aws console", "google cloud", "azure portal", // Cloud Console

            // --- NHÓM 5: GIẤY TỜ TÙY THÂN (Identity) ---
            "căn cước", "cccd", "cmnd", "chứng minh nhân dân",
            "passport", "hộ chiếu",
            "driver license", "bằng lái",
            "sổ hộ khẩu", "giấy khai sinh",
            "profile", "hồ sơ cá nhân", "thông tin cá nhân", "personal info",

            // --- NHÓM 6: RIÊNG TƯ & ẨN DANH (Privacy Modes) ---
            "incognito", "ẩn danh", // Chrome
            "inprivate", // Edge
            "private window", // Firefox
            "tor browser",

            // --- NHÓM 7: DEMO & GHI CHÚ ---
            "notepad", "sticky notes", "ghi chú", "untitled - paint"
    };

    public ScreenSender(NetworkClient networkClient, String myId, String targetId) {
        this.networkClient = networkClient;
        this.myId = myId;
        this.targetId = targetId;
        try {
            this.screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            this.robot = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    public void startStreaming() {
        if (isStreaming)
            return;
        isStreaming = true;
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::captureAndSend, 0, 30, TimeUnit.MILLISECONDS);
    }

    public void stopStreaming() {
        isStreaming = false;
        if (executor != null)
            executor.shutdownNow();
    }

    private void captureAndSend() {
        try {
            BufferedImage capture = robot.createScreenCapture(screenRect);

            checkAndMaskWindow(capture);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            ImageWriter writer = writers.next();
            ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
            writer.setOutput(ios);

            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.5f);

            writer.write(null, new IIOImage(capture, null, null), param);
            ios.close();
            writer.dispose();

            byte[] fullImageData = baos.toByteArray();
            int totalLength = fullImageData.length;
            int totalChunks = (int) Math.ceil((double) totalLength / MAX_CHUNK_SIZE);
            long currentFrameId = frameIdCounter++;

            for (int i = 0; i < totalChunks; i++) {
                int start = i * MAX_CHUNK_SIZE;
                int end = Math.min(totalLength, start + MAX_CHUNK_SIZE);
                byte[] chunkData = Arrays.copyOfRange(fullImageData, start, end);
                VideoPacket packet = new VideoPacket(
                        myId, targetId, chunkData, System.currentTimeMillis(),
                        currentFrameId, i, totalChunks);
                networkClient.sendVideoPacket(packet);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkAndMaskWindow(BufferedImage image) {
        try {
            String title = WindowSensor.getActiveWindowTitle().toLowerCase();
            if (title.isEmpty())
                return;

            boolean isSensitive = false;
            for (String key : SENSITIVE_TITLES) {
                if (title.contains(key)) {
                    isSensitive = true;
                    break;
                }
            }

            if (isSensitive) {
                Rectangle winRect = WindowSensor.getActiveWindowRect();
                if (winRect != null) {
                    pixelateRegion(image, winRect, 15); // Độ mờ 15

                    // Vẽ thêm cảnh báo
                    // Graphics2D g2d = image.createGraphics();
                    // g2d.setColor(Color.RED);
                    // g2d.setFont(new Font("Arial", Font.BOLD, 24));
                    // g2d.drawString("🔒 PROTECTED APP: " + title.toUpperCase(), winRect.x + 20,
                    // winRect.y + 40);
                    // g2d.dispose();
                }
            }

        } catch (Exception e) {
        }
    }

    private void pixelateRegion(BufferedImage img, Rectangle rect, int blockSize) {
        int xStart = Math.max(0, rect.x);
        int yStart = Math.max(0, rect.y);
        int xEnd = Math.min(img.getWidth(), rect.x + rect.width);
        int yEnd = Math.min(img.getHeight(), rect.y + rect.height);

        for (int y = yStart; y < yEnd; y += blockSize) {
            for (int x = xStart; x < xEnd; x += blockSize) {
                if (x < img.getWidth() && y < img.getHeight()) {
                    int pixelColor = img.getRGB(x, y);
                    int w = Math.min(blockSize, xEnd - x);
                    int h = Math.min(blockSize, yEnd - y);
                    int[] data = new int[w * h];
                    Arrays.fill(data, pixelColor);
                    img.setRGB(x, y, w, h, data, 0, w);
                }
            }
        }
    }
}