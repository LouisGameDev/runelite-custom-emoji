package com.customemoji;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import net.runelite.client.util.ImageUtil;

/**
 * Utility class for preprocessing emoji images for RuneLite compatibility.
 * Handles image resizing, color quantization, and transparency corrections.
 */
public class CustomEmojiImageUtilities 
{
    // Constants
    private static final int MAX_PALETTE_SIZE = 255;
    private static final int NEAR_BLACK_VALUE = 1; // RGB(1,1,1) to avoid transparency issues
    
    /**
     * Normalizes an image by applying resizing, quantization, and black pixel fixes.
     * @param image The input image to normalize
     * @param config Configuration settings for resizing
     * @return The normalized image ready for RuneLite
     */
    public static BufferedImage normalizeImage(BufferedImage image, CustomEmojiConfig config)
    {
        BufferedImage sizedResult = image;
        int maxImageHeight = config.maxImageHeight();
        if (config.resizeEmotes() && image.getHeight() > maxImageHeight)
        {
            // Calculate new width while preserving aspect ratio
            double scaleFactor = (double) maxImageHeight / image.getHeight();
            int scaledWidth = (int) Math.round(image.getWidth() * scaleFactor);
            
            sizedResult = ImageUtil.resizeImage(image, scaledWidth, maxImageHeight, true);
        }

        BufferedImage quantizedResult = quantizeIfNeeded(sizedResult, MAX_PALETTE_SIZE);
        return fixPureBlackPixels(quantizedResult);
    }

    /**
     * Fixes pure black pixels by converting them to near-black to prevent
     * RuneLite from treating them as transparent.
     * @param image The input image
     * @return Image with corrected black pixels
     */
    public static BufferedImage fixPureBlackPixels(BufferedImage image)
    {
        BufferedImage result = new BufferedImage(
            image.getWidth(),
            image.getHeight(),
            BufferedImage.TYPE_INT_ARGB
        );
        
        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                int argb = image.getRGB(x, y);
                int alpha = (argb >> 24) & 0xff;
                int red = (argb >> 16) & 0xff;
                int green = (argb >> 8) & 0xff;
                int blue = argb & 0xff;
                
                // If pixel is opaque pure black, make it slightly not-black
                if (alpha > 0 && red == 0 && green == 0 && blue == 0)
                {
                    // Change to RGB (1,1,1) which is visually indistinguishable from black
                    // but won't be treated as transparent by RuneLite
                    int newArgb = (alpha << 24) | 
                                  (NEAR_BLACK_VALUE << 16) | 
                                  (NEAR_BLACK_VALUE << 8) | 
                                  NEAR_BLACK_VALUE;
                    result.setRGB(x, y, newArgb);
                }
                else
                {
                    result.setRGB(x, y, argb);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Checks if image has more colors than allowed and quantizes if needed.
     * @param image The input BufferedImage
     * @param maxColors Maximum number of colors allowed
     * @return A BufferedImage with maxColors or fewer
     */
    public static BufferedImage quantizeIfNeeded(BufferedImage image, int maxColors) {
        Set<Integer> uniqueColors = getUniqueColors(image);
        
        if (uniqueColors.size() <= maxColors) {
            return image; // No quantization needed
        }
        
        return quantizeToColors(image, maxColors);
    }
    
    /**
     * Gets all unique colors in the image
     * @param image The input BufferedImage
     * @return Set of unique RGB color values
     */
    private static Set<Integer> getUniqueColors(BufferedImage image) {
        Set<Integer> colors = new HashSet<>();
        
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                colors.add(image.getRGB(x, y));
            }
        }
        
        return colors;
    }
    
    /**
     * Quantizes image to specified number of colors using median cut algorithm.
     * Handles transparency separately to preserve alpha values.
     * @param image The input BufferedImage
     * @param maxColors Maximum number of colors
     * @return Quantized BufferedImage with preserved transparency
     */
    private static BufferedImage quantizeToColors(BufferedImage image, int maxColors) {
        // Separate opaque and transparent colors
        Set<Integer> uniqueColors = getUniqueColors(image);
        List<Color> opaqueColors = new ArrayList<>();
        boolean hasTransparency = false;
        
        for (Integer argb : uniqueColors) {
            Color color = new Color(argb, true);
            if (color.getAlpha() == 0) {
                hasTransparency = true;
            } else {
                // Only add non-transparent colors to quantization
                opaqueColors.add(color);
            }
        }
        
        // Account for transparent color in palette if present
        int effectiveMaxColors = hasTransparency ? maxColors - 1 : maxColors;
        
        // If already within limit, return original
        if (opaqueColors.size() <= effectiveMaxColors) {
            return image;
        }
        
        // Apply median cut quantization only to opaque colors
        List<Color> palette = medianCut(opaqueColors, effectiveMaxColors);
        
        // Add transparent color to palette if needed
        if (hasTransparency) {
            palette.add(new Color(0, 0, 0, 0));
        }
        
        // Create new quantized image
        return applyPalette(image, palette);
    }
    
    /**
     * Median cut algorithm for color quantization
     */
    private static List<Color> medianCut(List<Color> colors, int maxColors) {
        List<ColorBox> boxes = new ArrayList<>();
        boxes.add(new ColorBox(colors));
        
        while (boxes.size() < maxColors) {
            // Find box with largest range
            ColorBox largestBox = null;
            int largestRange = -1;
            
            for (ColorBox box : boxes) {
                int range = box.getLargestRange();
                if (range > largestRange) {
                    largestRange = range;
                    largestBox = box;
                }
            }
            
            if (largestBox == null || largestRange == 0) {
                break; // Can't split further
            }
            
            // Split the box
            ColorBox[] split = largestBox.split();
            boxes.remove(largestBox);
            boxes.add(split[0]);
            boxes.add(split[1]);
        }
        
        // Get average color from each box
        List<Color> palette = new ArrayList<>();
        for (ColorBox box : boxes) {
            palette.add(box.getAverageColor());
        }
        
        return palette;
    }
    
    /**
     * Apply palette to image using nearest color matching
     */
    private static BufferedImage applyPalette(BufferedImage original, List<Color> palette) {
        BufferedImage result = new BufferedImage(
            original.getWidth(), 
            original.getHeight(), 
            BufferedImage.TYPE_INT_ARGB
        );
        
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                int argb = original.getRGB(x, y);
                Color originalColor = new Color(argb, true);
                
                // Keep fully transparent pixels as-is
                if (originalColor.getAlpha() == 0) {
                    result.setRGB(x, y, 0); // Fully transparent
                } else {
                    // For non-transparent pixels, find nearest opaque color
                    List<Color> opaquePalette = new ArrayList<>();
                    for (Color c : palette) {
                        if (c.getAlpha() > 0) {
                            opaquePalette.add(c);
                        }
                    }
                    Color nearestColor = findNearestColor(originalColor, opaquePalette);
                    // Combine nearest RGB with original alpha
                    int newArgb = (originalColor.getAlpha() << 24) | 
                                  (nearestColor.getRed() << 16) | 
                                  (nearestColor.getGreen() << 8) | 
                                  nearestColor.getBlue();
                    result.setRGB(x, y, newArgb);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Find nearest color in palette using Euclidean distance
     */
    private static Color findNearestColor(Color target, List<Color> palette) {
        Color nearest = palette.get(0);
        double minDistance = colorDistance(target, nearest);
        
        for (Color color : palette) {
            double distance = colorDistance(target, color);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = color;
            }
        }
        
        return nearest;
    }
    
    /**
     * Calculate Euclidean distance between two colors (RGB only, not alpha)
     */
    private static double colorDistance(Color c1, Color c2) {
        int dr = c1.getRed() - c2.getRed();
        int dg = c1.getGreen() - c2.getGreen();
        int db = c1.getBlue() - c2.getBlue();
        // Don't include alpha in distance calculation since we preserve original alpha
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }
    
    /**
     * Helper class for median cut algorithm
     */
    private static class ColorBox {
        private List<Color> colors;
        
        public ColorBox(List<Color> colors) {
            this.colors = new ArrayList<>(colors);
        }
        
        public int getLargestRange() {
            if (colors.isEmpty()) return 0;
            
            int minR = 255, maxR = 0;
            int minG = 255, maxG = 0;
            int minB = 255, maxB = 0;
            
            for (Color color : colors) {
                minR = Math.min(minR, color.getRed());
                maxR = Math.max(maxR, color.getRed());
                minG = Math.min(minG, color.getGreen());
                maxG = Math.max(maxG, color.getGreen());
                minB = Math.min(minB, color.getBlue());
                maxB = Math.max(maxB, color.getBlue());
            }
            
            int rangeR = maxR - minR;
            int rangeG = maxG - minG;
            int rangeB = maxB - minB;
            
            return Math.max(rangeR, Math.max(rangeG, rangeB));
        }
        
        public ColorBox[] split() {
            if (colors.size() <= 1) {
                return new ColorBox[]{this, new ColorBox(new ArrayList<>())};
            }
            
            // Find dimension with largest range
            int minR = 255, maxR = 0;
            int minG = 255, maxG = 0;
            int minB = 255, maxB = 0;
            
            for (Color color : colors) {
                minR = Math.min(minR, color.getRed());
                maxR = Math.max(maxR, color.getRed());
                minG = Math.min(minG, color.getGreen());
                maxG = Math.max(maxG, color.getGreen());
                minB = Math.min(minB, color.getBlue());
                maxB = Math.max(maxB, color.getBlue());
            }
            
            int rangeR = maxR - minR;
            int rangeG = maxG - minG;
            int rangeB = maxB - minB;
            
            // Sort by the dimension with largest range (RGB only)
            if (rangeR >= rangeG && rangeR >= rangeB) {
                colors.sort(Comparator.comparingInt(Color::getRed));
            } else if (rangeG >= rangeB) {
                colors.sort(Comparator.comparingInt(Color::getGreen));
            } else {
                colors.sort(Comparator.comparingInt(Color::getBlue));
            }
            
            // Split at median
            int median = colors.size() / 2;
            List<Color> left = colors.subList(0, median);
            List<Color> right = colors.subList(median, colors.size());
            
            return new ColorBox[]{new ColorBox(left), new ColorBox(right)};
        }
        
        public Color getAverageColor() {
            if (colors.isEmpty()) return Color.BLACK;
            
            long sumR = 0, sumG = 0, sumB = 0;
            for (Color color : colors) {
                sumR += color.getRed();
                sumG += color.getGreen();
                sumB += color.getBlue();
            }
            
            int avgR = (int) (sumR / colors.size());
            int avgG = (int) (sumG / colors.size());
            int avgB = (int) (sumB / colors.size());
            
            // Return opaque color since we handle alpha separately
            return new Color(avgR, avgG, avgB);
        }
    }
}