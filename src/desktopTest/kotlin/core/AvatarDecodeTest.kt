package core

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.*

class AvatarDecodeTest {
    @Test fun largeAvatarIsDecodedAtDisplaySizeAndKeepsAspectRatio() {
        val output = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(2048,1024,BufferedImage.TYPE_INT_RGB),"png",output)
        ImageIO.createImageInputStream(ByteArrayInputStream(output.toByteArray())).use { input ->
            val image = assertNotNull(DesktopImageLoader.decode(input,128))
            assertEquals(128,image.width);assertEquals(64,image.height)
        }
    }
    @Test fun smallAvatarIsNotUpscaled() {
        val output = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(12,8,BufferedImage.TYPE_INT_RGB),"png",output)
        ImageIO.createImageInputStream(ByteArrayInputStream(output.toByteArray())).use { input ->
            val image = assertNotNull(DesktopImageLoader.decode(input,128))
            assertEquals(12,image.width);assertEquals(8,image.height)
        }
    }
}
