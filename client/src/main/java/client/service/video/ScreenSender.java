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

    public enum ProtectionLevel {
        NORMAL, STRICT
    }

    private volatile boolean protectionEnabled = true;
    private volatile ProtectionLevel protectionLevel = ProtectionLevel.NORMAL;

    private static final long WINDOW_PROBE_INTERVAL_MS = 150;
    private long lastProbeMs = 0;

    private volatile String activeTitle = "";
    private volatile String activeProcess = "";
    private volatile Rectangle activeWinRect = null;

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
            Rectangle all = new Rectangle(0, 0, 0, 0);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            for (GraphicsDevice gd : ge.getScreenDevices()) {
                Rectangle b = gd.getDefaultConfiguration().getBounds();
                all = all.union(b);
            }
            this.screenRect = all;
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

            probeActiveWindowIfNeeded();
            applyProtectionMasks(capture);

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

    private void applyProtectionMasks(BufferedImage image) {
        if (!protectionEnabled)
            return;

        String title = activeTitle;
        String proc = activeProcess;
        Rectangle win = activeWinRect;
        if (win == null)
            return;

        // Map window rect -> capture coords (vì capture bắt đầu từ screenRect.x/y)
        Rectangle r = new Rectangle(win);
        r.translate(-screenRect.x, -screenRect.y);

        boolean isBrowser = isOneOf(proc, "chrome.exe", "msedge.exe", "firefox.exe", "brave.exe", "opera.exe");
        boolean isIDE = isOneOf(proc, "code.exe", "idea64.exe", "pycharm64.exe", "eclipse.exe", "devenv.exe",
                "notepad++.exe", "sublime_text.exe");

        // ===== CASE 1: Web login/password/OTP =====
        boolean isLoginLike = containsAny(title, "login", "log in", "signin", "sign in", "đăng nhập", "dang nhap",
                "password", "mật khẩu", "mat khau", "reset password",
                "otp", "2fa", "mfa", "verify", "verification", "xác thực", "authenticator");

        if (isBrowser && isLoginLike) {
            if (protectionLevel == ProtectionLevel.STRICT) {
                // Che toàn bộ nội dung web
                Rectangle content = browserContentRect(r);
                fillRegion(image, content); // nhanh & an toàn
            } else {
                // Normal: che address bar + vùng giữa (form login)
                pixelateRegion(image, addressBarRect(r), 14);
                pixelateRegion(image, centerModalRect(r), 16);
            }
            return; // đã match case nhạy cảm mạnh, khỏi check tiếp
        }

        // ===== CASE 2: .env / secrets trong IDE/editor =====
        boolean isEnvLike = containsAny(title,
                ".env", "dotenv", "secret", "secrets", "credential", "credentials",
                "apikey", "api key", "token", "private key", "id_rsa", ".pem", ".p12");

        if (isIDE && isEnvLike) {
            if (protectionLevel == ProtectionLevel.STRICT) {
                // Che gần như toàn cửa sổ (trừ title bar chút)
                Rectangle all = new Rectangle(r.x, (int) (r.y + r.height * 0.05), r.width, (int) (r.height * 0.95));
                fillRegion(image, all);
            } else {
                // Normal: che vùng editor pane (chừa sidebar để điều hướng)
                Rectangle editor = editorPaneRect(r);
                pixelateRegion(image, editor, 14);
            }
            return;
        }

        // ===== CASE 3: Gmail / OTP mail =====
        boolean isGmail = isBrowser && containsAny(title, "gmail", "mail.google.com", "inbox");
        if (isGmail) {
            if (protectionLevel == ProtectionLevel.STRICT) {
                // Che toàn bộ nội dung mail
                fillRegion(image, browserContentRect(r));
            } else {
                // Normal: che vùng đọc mail (center/right) để không lộ OTP
                pixelateRegion(image, gmailReadingPaneRect(r), 14);
            }
            return;
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

    private void probeActiveWindowIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastProbeMs < WINDOW_PROBE_INTERVAL_MS)
            return;
        lastProbeMs = now;

        try {
            activeTitle = WindowSensor.getActiveWindowTitle().toLowerCase();
            activeProcess = WindowSensor.getActiveProcessName(); // bạn vừa thêm
            activeWinRect = WindowSensor.getActiveWindowRect();
        } catch (Exception ignored) {
        }
    }

    private Rectangle browserContentRect(Rectangle w) {
        int y = w.y + (int) (w.height * 0.16);
        return new Rectangle(w.x, y, w.width, w.y + w.height - y);
    }

    private Rectangle addressBarRect(Rectangle w) {
        int y = w.y + (int) (w.height * 0.06);
        int h = (int) (w.height * 0.12);
        return new Rectangle(w.x, y, w.width, h);
    }

    private Rectangle centerModalRect(Rectangle w) {
        int x = w.x + (int) (w.width * 0.20);
        int y = w.y + (int) (w.height * 0.18);
        int ww = (int) (w.width * 0.60);
        int hh = (int) (w.height * 0.70);
        return new Rectangle(x, y, ww, hh);
    }

    private Rectangle editorPaneRect(Rectangle w) {
        int x = w.x + (int) (w.width * 0.18); // chừa sidebar
        int y = w.y + (int) (w.height * 0.12); // chừa menu
        return new Rectangle(x, y, w.x + w.width - x, w.y + w.height - y);
    }

    private Rectangle gmailReadingPaneRect(Rectangle w) {
        int y = w.y + (int) (w.height * 0.16);
        int x = w.x + (int) (w.width * 0.35); // che phần center/right
        return new Rectangle(x, y, w.x + w.width - x, w.y + w.height - y);
    }

    private boolean isOneOf(String v, String... arr) {
        if (v == null)
            return false;
        for (String s : arr)
            if (v.equals(s))
                return true;
        return false;
    }

    private boolean containsAny(String text, String... keys) {
        if (text == null)
            return false;
        for (String k : keys)
            if (text.contains(k))
                return true;
        return false;
    }

    private void fillRegion(BufferedImage img, Rectangle rect) {
        if (rect == null)
            return;

        int xStart = Math.max(0, rect.x);
        int yStart = Math.max(0, rect.y);
        int xEnd = Math.min(img.getWidth(), rect.x + rect.width);
        int yEnd = Math.min(img.getHeight(), rect.y + rect.height);

        if (xEnd <= xStart || yEnd <= yStart)
            return;

        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(xStart, yStart, xEnd - xStart, yEnd - yStart);
        g.dispose();
    }

    public void setProtectionEnabled(boolean enabled) {
        this.protectionEnabled = enabled;
    }

    public void setProtectionLevel(ProtectionLevel level) {
        if (level != null)
            this.protectionLevel = level;
    }
}