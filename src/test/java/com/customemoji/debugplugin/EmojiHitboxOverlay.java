package com.customemoji.debugplugin;

import javax.inject.Inject;
import javax.inject.Singleton;

import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class EmojiHitboxOverlay extends Overlay
{
    private static final Pattern IMG_PATTERN = Pattern.compile("<img=(\\d+)>");
    private static final int DEFAULT_ICON_SIZE = 18;
    private static final int BUILT_IN_ICON_THRESHOLD = 42; // OSRS built-in modicons are 0-41

    @Inject
    private Client client;

    @Inject
    private CustomEmojiDebugConfig config;

    public EmojiHitboxOverlay()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (this.config.showEmojiHitboxes())
        {
            this.drawEmojiHitBorders(graphics);
        }
        return null;
    }

    private void drawEmojiHitBorders(Graphics2D graphics)
    {
        Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (chatbox == null)
        {
            return;
        }

        List<Rectangle> rectangles = new ArrayList<>();
        this.collectAllEmojiRectangles(chatbox, rectangles);

        if (rectangles.isEmpty())
        {
            return;
        }

        // Get the chatbox visible bounds to clip drawing
        net.runelite.api.Point chatboxPos = chatbox.getCanvasLocation();
        int chatboxX = chatboxPos.getX();
        int chatboxY = chatboxPos.getY();
        int chatboxWidth = chatbox.getWidth();
        int chatboxHeight = chatbox.getHeight();
        Rectangle chatboxBounds = new Rectangle(chatboxX, chatboxY, chatboxWidth, chatboxHeight);

        // Save original clip and set clip to chatbox bounds
        Shape originalClip = graphics.getClip();
        graphics.setClip(chatboxBounds);

        Color originalColor = graphics.getColor();
        graphics.setColor(Color.CYAN);

        for (Rectangle rect : rectangles)
        {
            graphics.drawRect(rect.x, rect.y, rect.width, rect.height);
        }

        graphics.setColor(originalColor);
        graphics.setClip(originalClip);
    }

    private void collectAllEmojiRectangles(Widget chatbox, List<Rectangle> rectangles)
    {
        Widget[] dynamicChildren = chatbox.getDynamicChildren();
        if (dynamicChildren == null)
        {
            return;
        }

        for (Widget widget : dynamicChildren)
        {
            if (widget == null)
            {
                continue;
            }

            String text = widget.getText();
            if (text != null && text.contains("<img="))
            {
                this.collectEmojiRectanglesFromWidget(widget, text, rectangles);
            }
        }
    }

    private void collectEmojiRectanglesFromWidget(Widget widget, String text, List<Rectangle> rectangles)
    {
        Matcher matcher = IMG_PATTERN.matcher(text);

        net.runelite.api.Point widgetPos = widget.getCanvasLocation();
        net.runelite.api.FontTypeFace font = widget.getFont();

        int lineHeight = 14;
        int textIndex = 0;
        int currentX = 0;
        int currentLine = 0;

        while (matcher.find())
        {
            String textBefore = text.substring(textIndex, matcher.start());
            String cleanTextBefore = this.removeHtmlTags(textBefore);

            // Simulate word-based line wrapping (OSRS wraps at spaces, not mid-word)
            String[] words = cleanTextBefore.split("(?<= )");
            for (String word : words)
            {
                int wordWidth = font.getTextWidth(word);
                if (currentX + wordWidth > widget.getWidth() && currentX > 0)
                {
                    currentX = 0;
                    currentLine++;
                }
                currentX += wordWidth;
            }

            String imageIdStr = matcher.group(1);
            int imageId = Integer.parseInt(imageIdStr);

            // Skip built-in icons (ironman, moderator, etc.)
            boolean isBuiltInIcon = imageId <= BUILT_IN_ICON_THRESHOLD + 1;
            if (isBuiltInIcon)
            {
                currentX += DEFAULT_ICON_SIZE;
                textIndex = matcher.end();
                continue;
            }

            // Look up actual emoji dimensions from the sprite
            int emojiWidth = DEFAULT_ICON_SIZE;
            int emojiHeight = DEFAULT_ICON_SIZE;

            Dimension spriteDimension = this.getSpriteDimension(imageId);
            if (spriteDimension != null)
            {
                emojiWidth = spriteDimension.width;
                emojiHeight = spriteDimension.height;
            }

            // Check if emoji itself would wrap to next line
            if (currentX + emojiWidth > widget.getWidth() && currentX > 0)
            {
                currentX = 0;
                currentLine++;
            }

            int emojiStartX = currentX;

            // Calculate Y position based on which line the emoji is on
            // Emoji is bottom-aligned within the line, but offset 2px up from the bottom
            int lineBottomY = (currentLine + 1) * lineHeight;
            int emojiBottomY = lineBottomY - 2;
            int emojiTopY = emojiBottomY - emojiHeight;

            int absoluteX = widgetPos.getX() + emojiStartX;
            int absoluteY = widgetPos.getY() + emojiTopY;

            rectangles.add(new Rectangle(absoluteX, absoluteY, emojiWidth, emojiHeight));

            currentX = emojiStartX + emojiWidth;
            textIndex = matcher.end();
        }
    }

    private Dimension getSpriteDimension(int imageId)
    {
        IndexedSprite[] modIcons = this.client.getModIcons();
        if (modIcons == null || imageId < 0 || imageId >= modIcons.length)
        {
            return null;
        }

        IndexedSprite sprite = modIcons[imageId];
        if (sprite == null)
        {
            return null;
        }

        return new Dimension(sprite.getWidth(), sprite.getHeight());
    }

    private String removeHtmlTags(String text)
    {
        if (text == null)
        {
            return "";
        }
        return text.replaceAll("<[^>]*>", "");
    }
}