package com.customemoji.debugplugin;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.customemoji.EmojiPosition;
import com.customemoji.EmojiPositionCalculator;
import com.customemoji.PluginUtils;

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

@Singleton
public class EmojiHitboxOverlay extends Overlay
{
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
        if (chatbox == null || chatbox.isHidden())
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
        List<Widget> visibleWidgets = PluginUtils.getVisibleChatWidgets(chatbox);

        for (Widget widget : visibleWidgets)
        {
            String text = widget.getText();
            if (PluginUtils.hasImgTag(text))
            {
                this.collectEmojiRectanglesFromWidget(widget, text, rectangles);
            }
        }
    }

    private void collectEmojiRectanglesFromWidget(Widget widget, String text, List<Rectangle> rectangles)
    {
        List<EmojiPosition> positions = EmojiPositionCalculator.calculateEmojiPositions(
            widget,
            text,
            this::getSpriteDimension
        );

        for (EmojiPosition position : positions)
        {
            rectangles.add(position.getBounds());
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
}