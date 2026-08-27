package cn.yeslab.platform.achievement;

import cn.yeslab.platform.achievement.service.AchievementFileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AchievementFileStorageServiceTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void repairsPreviouslyStoredBitmapWithJpgNameOnFirstRead() throws Exception {
        AchievementFileStorageService storage = new AchievementFileStorageService(temporaryDirectory.toString());
        Path legacy = temporaryDirectory.resolve("certificates/legacy.jpg");
        byte[] bitmap = bitmapBytes();
        Files.write(legacy, bitmap);

        byte[] repaired = storage.certificate("legacy.jpg").getContentAsByteArray();

        assertEquals(0xff, repaired[0] & 0xff);
        assertEquals(0xd8, repaired[1] & 0xff);
        assertTrue(repaired.length < bitmap.length);
        assertEquals(repaired.length, Files.size(legacy));
    }

    private byte[] bitmapBytes() throws Exception {
        BufferedImage image = new BufferedImage(1280, 830, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(161, 98, 7));
            graphics.fillRect(100, 100, 1080, 630);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "bmp", output);
        return output.toByteArray();
    }
}
