package cn.yeslab.platform.achievement;
import cn.yeslab.platform.achievement.service.AchievementFileStorageService;
import cn.yeslab.platform.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
class RegistrationStorageTests {
    @TempDir Path directory;
    @Test void genuinelyDecodesAllThreeAcceptedImageFormatsInPrivateDirectory() throws Exception {
        var storage = new AchievementFileStorageService(directory.toString());
        for (String format : new String[]{"png", "jpeg", "webp"}) {
            byte[] bytes;
            if (format.equals("webp")) try(var input = getClass().getResourceAsStream("/registration.webp")) { bytes = input.readAllBytes(); }
            else { var out = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), format, out); bytes = out.toByteArray(); }
            var stored = storage.storeRegistration(new MockMultipartFile("registration", "报名." + format, "image/" + format, bytes));
            assertThat(storage.registration(stored.storedName()).getContentAsByteArray()).isEqualTo(bytes);
            assertThat(directory.resolve("registrations").resolve(stored.storedName())).exists();
            storage.deleteRegistration(stored.storedName());
            assertThat(directory.resolve("registrations").resolve(stored.storedName())).doesNotExist();
        }
    }
    @Test void rejectsCorruptUnsupportedAndOversizedUploads() throws Exception {
        var storage = new AchievementFileStorageService(directory.toString());
        var out = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "bmp", out);
        for (byte[] bytes : new byte[][]{new byte[]{(byte)0x89, 'P', 'N', 'G'}, out.toByteArray(), new byte[8 * 1024 * 1024 + 1]}) {
            assertThatThrownBy(() -> storage.storeRegistration(new MockMultipartFile("registration", "fake.png", "image/png", bytes))).isInstanceOf(ApiException.class);
        }
    }
}
