package com.customemoji;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.customemoji.util.ImageUtils;

public class ImageUtilsTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void resizeImage_imageUnderMaxHeight_unchanged()
    {
        BufferedImage small = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);

        BufferedImage result = ImageUtils.resizeImage(small, 24);

        assertEquals(20, result.getHeight());
        assertEquals(20, result.getWidth());
    }

    @Test
    public void resizeImage_tallImage_scaledToMaxHeight()
    {
        BufferedImage tall = new BufferedImage(50, 100, BufferedImage.TYPE_INT_ARGB);

        BufferedImage result = ImageUtils.resizeImage(tall, 24);

        assertEquals(24, result.getHeight());
    }

    @Test
    public void resizeImage_tallImage_maintainsAspectRatio()
    {
        BufferedImage tall = new BufferedImage(50, 100, BufferedImage.TYPE_INT_ARGB);

        BufferedImage result = ImageUtils.resizeImage(tall, 24);

        double originalRatio = 50.0 / 100.0;
        double resultRatio = (double) result.getWidth() / result.getHeight();
        assertEquals(originalRatio, resultRatio, 0.1);
    }

    @Test
    public void resizeImage_squareImage_remainsSquare()
    {
        BufferedImage square = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);

        BufferedImage result = ImageUtils.resizeImage(square, 24);

        assertEquals(result.getWidth(), result.getHeight());
    }

    @Test
    public void resizeImage_wideImage_scaledByHeight()
    {
        BufferedImage wide = new BufferedImage(200, 50, BufferedImage.TYPE_INT_ARGB);

        BufferedImage result = ImageUtils.resizeImage(wide, 24);

        assertEquals(24, result.getHeight());
        assertTrue(result.getWidth() > result.getHeight());
    }

    @Test
    public void fixPureBlackPixels_pureBlack_becomesNearBlack()
    {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        int pureBlack = (255 << 24) | (0 << 16) | (0 << 8) | 0;
        image.setRGB(0, 0, pureBlack);

        BufferedImage result = ImageUtils.fixPureBlackPixels(image);

        int resultArgb = result.getRGB(0, 0);
        int red = (resultArgb >> 16) & 0xFF;
        int green = (resultArgb >> 8) & 0xFF;
        int blue = resultArgb & 0xFF;

        assertEquals(1, red);
        assertEquals(1, green);
        assertEquals(1, blue);
    }

    @Test
    public void fixPureBlackPixels_transparentBlack_unchanged()
    {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        int transparentBlack = 0;
        image.setRGB(0, 0, transparentBlack);

        BufferedImage result = ImageUtils.fixPureBlackPixels(image);

        int resultArgb = result.getRGB(0, 0);
        assertEquals(0, resultArgb);
    }

    @Test
    public void fixPureBlackPixels_nonBlackColor_unchanged()
    {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        int red = (255 << 24) | (255 << 16) | (0 << 8) | 0;
        image.setRGB(0, 0, red);

        BufferedImage result = ImageUtils.fixPureBlackPixels(image);

        int resultArgb = result.getRGB(0, 0);
        assertEquals(red, resultArgb);
    }

    @Test
    public void fixPureBlackPixels_nearBlack_unchanged()
    {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        int nearBlack = (255 << 24) | (1 << 16) | (1 << 8) | 1;
        image.setRGB(0, 0, nearBlack);

        BufferedImage result = ImageUtils.fixPureBlackPixels(image);

        int resultArgb = result.getRGB(0, 0);
        assertEquals(nearBlack, resultArgb);
    }

    @Test
    public void fixPureBlackPixels_preservesAlpha()
    {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        int semiTransparentBlack = (128 << 24) | (0 << 16) | (0 << 8) | 0;
        image.setRGB(0, 0, semiTransparentBlack);

        BufferedImage result = ImageUtils.fixPureBlackPixels(image);

        int resultArgb = result.getRGB(0, 0);
        int alpha = (resultArgb >> 24) & 0xFF;
        assertEquals(128, alpha);
    }

    @Test
    public void isAnimatedGif_nonGifFile_returnsFalse() throws IOException
    {
        File pngFile = this.tempFolder.newFile("test.png");
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "png", pngFile);

        assertFalse(ImageUtils.isAnimatedGif(pngFile));
    }

    @Test
    public void isAnimatedGif_nonExistentFile_returnsFalse()
    {
        File nonExistent = new File(this.tempFolder.getRoot(), "nonexistent.gif");
        assertFalse(ImageUtils.isAnimatedGif(nonExistent));
    }

    @Test
    public void quantizeIfNeeded_fewColors_unchanged()
    {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 10; y++)
        {
            for (int x = 0; x < 10; x++)
            {
                image.setRGB(x, y, Color.RED.getRGB());
            }
        }

        BufferedImage result = ImageUtils.quantizeIfNeeded(image, 255);

        assertNotNull(result);
        assertEquals(image.getWidth(), result.getWidth());
        assertEquals(image.getHeight(), result.getHeight());
    }

    @Test
    public void indexedSpriteToBufferedImage_nullSprite_returnsNull()
    {
        BufferedImage result = ImageUtils.indexedSpriteToBufferedImage(null);
        assertEquals(null, result);
    }
}
