package client.service.ai;

import client.service.video.SensitiveMask;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class TextScanner implements Runnable {

    private final Tesseract tesseract;
    private BufferedImage currentScreen;

    // Hệ số thu nhỏ ảnh để tăng tốc OCR (0.5 nghĩa là thu nhỏ một nửa)
    private static final double SCALE_FACTOR = 0.8;

    private static final Pattern[] PATTERNS = {
            Pattern.compile("\\b(?:\\d{4}[- ]?){3}\\d{4}\\b"), // Visa
            Pattern.compile("(?i)password"),
            Pattern.compile("(?i)mật\\s?khẩu"),
            Pattern.compile("sk_live_[0-9a-zA-Z]+"),
            Pattern.compile("AIza[0-9A-Za-z-_]{35}"),
            Pattern.compile("(?i)secret")
    };

    public TextScanner() {
        tesseract = new Tesseract();
        try {
            File tempFolder = new File(System.getProperty("java.io.tmpdir"), "tessdata_custom");
            if (!tempFolder.exists())
                tempFolder.mkdirs();
            File targetFile = new File(tempFolder, "eng.traineddata");
            if (!targetFile.exists() || targetFile.length() < 1024) {
                try (InputStream in = getClass().getResourceAsStream("/tessdata/eng.traineddata")) {
                    if (in != null)
                        Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            tesseract.setDatapath(tempFolder.getAbsolutePath());
            tesseract.setLanguage("eng");
            tesseract.setTessVariable("user_defined_dpi", "70");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateImage(BufferedImage image) {
        // Chỉ cập nhật nếu luồng đang rảnh (để tránh queue quá nhiều ảnh)
        this.currentScreen = image;
    }

    @Override
    public void run() {
        while (true) {
            try {
                if (currentScreen != null) {

                    // 1. [TỐI ƯU] Thu nhỏ ảnh trước khi xử lý
                    int w = (int) (currentScreen.getWidth() * SCALE_FACTOR);
                    int h = (int) (currentScreen.getHeight() * SCALE_FACTOR);
                    BufferedImage scaledImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = scaledImg.createGraphics();
                    g.drawImage(currentScreen, 0, 0, w, h, null);
                    g.dispose();

                    List<Rectangle> detectedList = new ArrayList<>();

                    try {
                        List<Word> words = tesseract.getWords(scaledImg, ITessAPI.TessPageIteratorLevel.RIL_WORD);

                        for (Word word : words) {
                            String text = word.getText().trim();
                            if (text.length() < 3)
                                continue;

                            for (Pattern pattern : PATTERNS) {
                                if (pattern.matcher(text).find()) {
                                    System.out.println(">>> AI Detect (Fast): " + text);

                                    // Lấy khung bao trên ảnh nhỏ
                                    Rectangle smallRect = word.getBoundingBox();

                                    // 2. [TỐI ƯU] Phóng to tọa độ lại kích thước gốc
                                    Rectangle originalRect = new Rectangle(
                                            (int) (smallRect.x / SCALE_FACTOR),
                                            (int) (smallRect.y / SCALE_FACTOR),
                                            (int) (smallRect.width / SCALE_FACTOR),
                                            (int) (smallRect.height / SCALE_FACTOR));

                                    // Mở rộng vùng che (padding) cho an toàn
                                    originalRect.grow(10, 5);

                                    detectedList.add(originalRect);
                                    break;
                                }
                            }
                        }

                        if (!detectedList.isEmpty()) {
                            SensitiveMask.addMasks(detectedList);
                        }

                    } catch (Exception e) {
                        // Ignore OCR errors
                    }
                }

                // [QUAN TRỌNG] Giảm thời gian ngủ xuống cực thấp
                // Vì OCR đã nhanh hơn, ta có thể quét tần suất cao hơn
                Thread.sleep(100);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}