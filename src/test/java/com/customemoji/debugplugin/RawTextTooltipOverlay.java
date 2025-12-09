package com.customemoji.debugplugin;

import javax.inject.Inject;
import javax.inject.Singleton;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

@Singleton
public class RawTextTooltipOverlay extends Overlay
{
    private static final int TOOLTIP_PADDING = 4;
    private static final int TOOLTIP_OFFSET_Y = 20;
    private static final Color BACKGROUND_COLOR = new Color(70, 61, 50, 220);
    private static final Color BORDER_COLOR = new Color(40, 35, 30);
    private static final Color TEXT_COLOR = Color.WHITE;

    @Inject
    private Client client;

    @Inject
    private CustomEmojiDebugConfig config;

    public RawTextTooltipOverlay()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!this.config.showRawTextTooltip())
        {
            return null;
        }

        Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (chatbox == null || chatbox.isHidden())
        {
            return null;
        }

        net.runelite.api.Point mousePos = this.client.getMouseCanvasPosition();
        if (mousePos == null)
        {
            return null;
        }

        int mouseX = mousePos.getX();
        int mouseY = mousePos.getY();

        Rectangle chatboxBounds = chatbox.getBounds();
        if (chatboxBounds == null || !chatboxBounds.contains(mouseX, mouseY))
        {
            return null;
        }

        Widget hoveredWidget = this.findHoveredWidget(chatbox, mouseX, mouseY, chatboxBounds);
        if (hoveredWidget == null)
        {
            return null;
        }

        String rawText = hoveredWidget.getText();
        if (rawText == null || rawText.isEmpty())
        {
            return null;
        }

        this.drawTooltip(graphics, rawText, mouseX, mouseY);

        return null;
    }

    private void drawTooltip(Graphics2D graphics, String text, int mouseX, int mouseY)
    {
        Font originalFont = graphics.getFont();
        Font tooltipFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        graphics.setFont(tooltipFont);

        FontMetrics fm = graphics.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();

        int tooltipX = mouseX;
        int tooltipY = mouseY + TOOLTIP_OFFSET_Y;

        int boxWidth = textWidth + TOOLTIP_PADDING * 2;
        int boxHeight = textHeight + TOOLTIP_PADDING * 2;

        graphics.setColor(BACKGROUND_COLOR);
        graphics.fillRect(tooltipX, tooltipY, boxWidth, boxHeight);

        graphics.setColor(BORDER_COLOR);
        graphics.drawRect(tooltipX, tooltipY, boxWidth, boxHeight);

        graphics.setColor(TEXT_COLOR);
        graphics.drawString(text, tooltipX + TOOLTIP_PADDING, tooltipY + TOOLTIP_PADDING + fm.getAscent());

        graphics.setFont(originalFont);
    }

    private Widget findHoveredWidget(Widget chatbox, int mouseX, int mouseY, Rectangle chatboxBounds)
    {
        Widget[] dynamicChildren = chatbox.getDynamicChildren();
        if (dynamicChildren == null)
        {
            return null;
        }

        for (Widget widget : dynamicChildren)
        {
            if (widget == null)
            {
                continue;
            }

            Rectangle widgetBounds = widget.getBounds();
            if (widgetBounds == null)
            {
                continue;
            }

            boolean isOutsideChatbox = !chatboxBounds.intersects(widgetBounds);
            if (isOutsideChatbox)
            {
                continue;
            }

            boolean isHovered = widgetBounds.contains(mouseX, mouseY);
            if (isHovered)
            {
                return widget;
            }
        }

        return null;
    }
}