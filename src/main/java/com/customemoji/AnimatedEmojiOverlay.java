package com.customemoji;

import com.customemoji.model.AnimatedEmoji;
import com.customemoji.model.Emoji;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.ImageObserver;
import java.util.List;
import java.util.Map;

/**
 * Overlay that renders animated GIF emojis over their static chat icon positions.
 * Uses ImageIcon's Image which automatically handles GIF animation frames.
 */
@Slf4j
@Singleton
public class AnimatedEmojiOverlay extends Overlay
{
    @Inject
    private Client client;

    @Inject
    private CustomEmojiConfig config;

    @Inject
    private ChatIconManager chatIconManager;

    @Inject
    private Map<String, Emoji> emojis;

    @Inject
    private CustomEmojiPlugin plugin;

    /**
     * ImageObserver that triggers client repaint when new animation frames are available.
     */
    private final ImageObserver imageObserver = (img, infoflags, x, y, width, height) -> {
        // FRAMEBITS indicates a new frame is available
        boolean hasNewFrame = (infoflags & ImageObserver.FRAMEBITS) != 0;
        if (hasNewFrame)
        {
            // Request a repaint to show the new frame
            return true;
        }
        // ALLBITS means image is complete, keep observing for animation
        boolean isComplete = (infoflags & ImageObserver.ALLBITS) != 0;
        return !isComplete;
    };

    @Inject
    public AnimatedEmojiOverlay()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!this.config.enableAnimatedEmojis())
        {
            return null;
        }

        Map<String, AnimatedEmoji> animatedEmojis = this.plugin.getAnimatedEmojis();
        if (animatedEmojis.isEmpty())
        {
            return null;
        }

        Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (chatbox == null)
        {
            return null;
        }

        Widget[] dynamicChildren = chatbox.getDynamicChildren();
        if (dynamicChildren == null)
        {
            return null;
        }

        // Get chatbox bounds for clipping
        net.runelite.api.Point chatboxLocation = chatbox.getCanvasLocation();
        if (chatboxLocation == null)
        {
            return null;
        }

        Rectangle chatboxBounds = new Rectangle(
            chatboxLocation.getX(),
            chatboxLocation.getY(),
            chatbox.getWidth(),
            chatbox.getHeight()
        );

        // Save original clip and set clip to chatbox bounds
        Shape originalClip = graphics.getClip();
        graphics.setClip(chatboxBounds);

        try
        {
            for (Widget widget : dynamicChildren)
            {
                if (widget == null)
                {
                    continue;
                }

                String text = widget.getText();
                if (text == null || !text.contains("<img="))
                {
                    continue;
                }

                this.renderAnimatedEmojisInWidget(graphics, widget, text, animatedEmojis, chatboxBounds);
            }
        }
        finally
        {
            // Restore original clip
            graphics.setClip(originalClip);
        }

        return null;
    }

    private void renderAnimatedEmojisInWidget(Graphics2D graphics, Widget widget, String text, Map<String, AnimatedEmoji> animatedEmojis, Rectangle chatboxBounds)
    {
        List<EmojiPosition> positions = EmojiPositionCalculator.calculateEmojiPositions(
            widget,
            text,
            this::getEmojiDimension
        );

        for (EmojiPosition position : positions)
        {
            AnimatedEmoji animatedEmoji = this.findAnimatedEmojiByImageId(position.getImageId(), animatedEmojis);
            if (animatedEmoji != null)
            {
                Image animatedImage = animatedEmoji.getAnimatedImage();
                int x = position.getX();
                int y = position.getY();
                int height = position.getHeight();

                // Skip if completely outside chatbox bounds
                boolean isOutsideBounds = (y + height < chatboxBounds.y) || (y > chatboxBounds.y + chatboxBounds.height);
                if (isOutsideBounds)
                {
                    continue;
                }

                // Draw the animated image (static icon is transparent, so no background needed)
                graphics.drawImage(animatedImage, x, y, this.imageObserver);
            }
        }
    }

    private AnimatedEmoji findAnimatedEmojiByImageId(int imageId, Map<String, AnimatedEmoji> animatedEmojis)
    {
        for (AnimatedEmoji animatedEmoji : animatedEmojis.values())
        {
            int iconIndex = this.chatIconManager.chatIconIndex(animatedEmoji.getStaticIconId());
            if (iconIndex == imageId)
            {
                return animatedEmoji;
            }
        }
        return null;
    }

    private Dimension getEmojiDimension(int imageId)
    {
        // First check animated emojis
        Map<String, AnimatedEmoji> animatedEmojis = this.plugin.getAnimatedEmojis();
        for (AnimatedEmoji animatedEmoji : animatedEmojis.values())
        {
            int iconIndex = this.chatIconManager.chatIconIndex(animatedEmoji.getStaticIconId());
            if (iconIndex == imageId)
            {
                return new Dimension(animatedEmoji.getWidth(), animatedEmoji.getHeight());
            }
        }

        // Fall back to regular emojis
        for (Emoji emoji : this.emojis.values())
        {
            int iconIndex = this.chatIconManager.chatIconIndex(emoji.getId());
            if (iconIndex == imageId)
            {
                return emoji.getDimension();
            }
        }

        return null;
    }
}
