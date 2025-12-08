package com.customemoji;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.customemoji.animation.GifAnimation;
import com.customemoji.io.EmojiLoader;
import com.customemoji.model.AnimatedEmoji;
import com.customemoji.model.Emoji;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.IconID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.BackgroundComponent;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ImageUtil;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;

@Slf4j
@Singleton
public class CustomEmojiTooltip extends Overlay
{
    @Inject
    private Client client;

    @Inject
    private CustomEmojiConfig config;

    @Inject
    private TooltipManager tooltipManager;

    @Inject
    private MouseManager mouseManager;

    @Inject
    private ChatIconManager chatIconManager;

    @Inject
    private EmojiLoader emojiLoader;

    @Inject
    private ConfigManager configManager;

    @Inject
    private EventBus eventBus;

    public static final String PLUGIN_MESSAGE_NAMESPACE = "custom-emoji";
    public static final String TOOLTIP_HOVER_MESSAGE = "tooltip-hover";
    public static final String TOOLTIP_HOVER_EMOJI_KEY = "emoji";
    public static final String TOOLTIP_HOVER_IMAGE_ID_KEY = "imageId";
    public static final String TOOLTIP_HOVER_FRAME_COUNT_KEY = "frameCount";
    public static final String TOOLTIP_HOVER_CURRENT_FRAME_KEY = "currentFrame";

    private static final String RUNELITE_CONFIG_GROUP = "runelite";
    private static final String OVERLAY_BACKGROUND_COLOR_KEY = "overlayBackgroundColor";

    // Tooltip state
    private String hoveredEmojiName = null;
    private Emoji hoveredEmoji = null;
    private Point mousePosition = null;

    private final MouseListener mouseListener = new MouseListener()
	{
		@Override
		public MouseEvent mouseClicked(MouseEvent mouseEvent)
		{
			return mouseEvent;
		}

		@Override
		public MouseEvent mousePressed(MouseEvent mouseEvent)
		{
			return mouseEvent;
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent mouseEvent)
		{
			return mouseEvent;
		}

		@Override
		public MouseEvent mouseEntered(MouseEvent mouseEvent)
		{
			return mouseEvent;
		}

		@Override
		public MouseEvent mouseExited(MouseEvent mouseEvent)
		{
			return mouseEvent;
		}

		@Override
		public MouseEvent mouseDragged(MouseEvent mouseEvent)
		{
			return mouseEvent;
		}

		@Override
		public MouseEvent mouseMoved(MouseEvent mouseEvent)
		{
			Point currentPoint = mouseEvent.getPoint();

			// Only update if mouse actually moved a good bit
			if (mousePosition == null ||
				Math.abs(currentPoint.x - mousePosition.x) > 2 ||
				Math.abs(currentPoint.y - mousePosition.y) > 2)
			{
				mousePosition = currentPoint;

				// Delegate to overlay for tooltip handling
				updateHoveredEmoji(currentPoint);
			}
			return mouseEvent;
		}
	};

    protected void startUp()
    {
        this.setPosition(OverlayPosition.DYNAMIC);
        this.setLayer(OverlayLayer.ALWAYS_ON_TOP);

        if (this.mouseManager != null)
        {
            this.mouseManager.registerMouseListener(this.mouseListener);
        }
    }

    protected void shutDown()
    {
        if (this.mouseManager != null)
        {
            this.mouseManager.unregisterMouseListener(this.mouseListener);
        }
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        this.sendTooltipHoverMessage();

        boolean shouldShowTooltip = this.hoveredEmojiName != null &&
                                     !this.hoveredEmojiName.isEmpty() &&
                                     this.config.showEmojiTooltips();
        if (!shouldShowTooltip)
        {
            return null;
        }

        boolean shouldShowImage = this.config.showTooltipImage() && this.hoveredEmoji != null;
        if (shouldShowImage)
        {
            Tooltip imageTooltip = this.createImageTooltip();
            if (imageTooltip != null)
            {
                this.tooltipManager.add(imageTooltip);
            }
            else
            {
                this.tooltipManager.add(new Tooltip(this.hoveredEmojiName));
            }
        }
        else
        {
            this.tooltipManager.add(new Tooltip(this.hoveredEmojiName));
        }

        return null;
    }

    private Tooltip createImageTooltip()
    {
        BufferedImage image = this.getTooltipImage();
        if (image == null)
        {
            return null;
        }

        // Mark animation as visible for memory management
        if (this.hoveredEmoji instanceof AnimatedEmoji)
        {
            this.emojiLoader.markAnimationVisible(this.hoveredEmoji.getId());
        }

        // Scale image to fit within configured max dimensions while preserving aspect ratio
        // Cap upscaling to 2x original size to avoid blurry small images
        int maxWidth = this.config.tooltipImageMaxWidth();
        int maxHeight = this.config.tooltipImageMaxHeight();
        int originalWidth = image.getWidth();
        int originalHeight = image.getHeight();
        double scale = Math.min((double) maxWidth / originalWidth, (double) maxHeight / originalHeight);
        double maxUpscale = 2.0;
        scale = Math.min(scale, maxUpscale);
        int scaledWidth = (int) (originalWidth * scale);
        int scaledHeight = (int) (originalHeight * scale);
        BufferedImage scaledImage = ImageUtil.resizeImage(image, scaledWidth, scaledHeight);

        // Create custom tooltip component that renders background, image, and text
        String emojiName = this.hoveredEmojiName;
        Color backgroundColor = this.getOverlayBackgroundColor();
        LayoutableRenderableEntity tooltipContent = new LayoutableRenderableEntity()
        {
            private static final int PADDING = 4;
            private static final int IMAGE_TEXT_GAP = 4;
            private Point position = new Point();

            @Override
            public Dimension render(Graphics2D graphics)
            {
                FontMetrics fm = graphics.getFontMetrics();
                int textWidth = fm.stringWidth(emojiName);
                int textHeight = fm.getHeight();

                int contentWidth = Math.max(scaledWidth, textWidth);
                int contentHeight = scaledImage.getHeight() + IMAGE_TEXT_GAP + textHeight;
                int totalWidth = contentWidth + PADDING * 2;
                int totalHeight = contentHeight + PADDING * 2;

                // Render background using RuneLite's configured overlay color
                BackgroundComponent background = new BackgroundComponent();
                background.setBackgroundColor(backgroundColor);
                background.setRectangle(new Rectangle(position.x, position.y, totalWidth, totalHeight));
                background.render(graphics);

                // Render image (centered)
                int imageX = position.x + PADDING + (contentWidth - scaledWidth) / 2;
                int imageY = position.y + PADDING;
                graphics.drawImage(scaledImage, imageX, imageY, null);

                // Render text (centered)
                TextComponent text = new TextComponent();
                text.setText(emojiName);
                text.setColor(Color.WHITE);
                int textX = position.x + PADDING + (contentWidth - textWidth) / 2;
                int textY = position.y + PADDING + scaledImage.getHeight() + IMAGE_TEXT_GAP + fm.getAscent();
                text.setPosition(new Point(textX, textY));
                text.render(graphics);

                return new Dimension(totalWidth, totalHeight);
            }

            @Override
            public Rectangle getBounds()
            {
                return null;
            }

            @Override
            public void setPreferredLocation(Point position)
            {
                this.position = position;
            }

            @Override
            public void setPreferredSize(Dimension dimension)
            {
            }
        };

        return new Tooltip(tooltipContent);
    }

    private BufferedImage getTooltipImage()
    {
        if (this.hoveredEmoji == null)
        {
            return null;
        }

        boolean isAnimated = this.hoveredEmoji instanceof AnimatedEmoji;
        if (isAnimated)
        {
            AnimatedEmoji animatedEmoji = (AnimatedEmoji) this.hoveredEmoji;
            GifAnimation animation = this.emojiLoader.getOrLoadAnimation(animatedEmoji);

            if (animation != null)
            {
                BufferedImage frame = animation.getCurrentFrame();
                if (frame != null)
                {
                    return frame;
                }
            }

            // Fall back to loading first frame from original file while animation loads
            try
            {
                return ImageIO.read(animatedEmoji.getFile());
            }
            catch (IOException e)
            {
                log.warn("Failed to load tooltip image for {}", animatedEmoji.getText(), e);
                return animatedEmoji.getStaticImage();
            }
        }

        // Load original full-size image from file for static emojis
        try
        {
            return ImageIO.read(this.hoveredEmoji.getFile());
        }
        catch (IOException e)
        {
            log.warn("Failed to load tooltip image for {}", this.hoveredEmoji.getText(), e);
            return null;
        }
    }

    private void updateHoveredEmoji(Point mousePoint)
    {
        this.mousePosition = mousePoint;

        String foundEmoji = null;

        Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (chatbox == null || !this.isPointInWidget(chatbox, mousePoint))
        {
            this.hoveredEmojiName = null;
            this.hoveredEmoji = null;
            return;
        }

        Widget[] dynamicChildren = chatbox.getDynamicChildren();

        foundEmoji = this.checkWidgetsForEmoji(dynamicChildren, mousePoint);
        this.hoveredEmojiName = foundEmoji;
    }

    private String checkWidgetsForEmoji(Widget[] widgets, Point mousePoint)
    {
        if (widgets == null)
        {
            return null;
        }

        for (Widget widget : widgets)
        {
            if (widget == null)
            {
                continue;
            }

            // Check if mouse is within widget bounds (with expanded Y for tall emojis)
            if (this.isPointInWidgetWithEmojiPadding(widget, mousePoint))
            {
                String text = widget.getText();
                if (text != null && text.contains("<img="))
                {
                    String foundEmojiName = this.findEmojiAtPosition(widget, text, mousePoint);
                    if (foundEmojiName != null)
                    {
                        return foundEmojiName;
                    }
                }
            }
        }
        return null;
    }

    private boolean isPointInWidgetWithEmojiPadding(Widget widget, Point point)
    {
        net.runelite.api.Point canvasLocation = widget.getCanvasLocation();
        if (canvasLocation == null)
        {
            return false;
        }

        int x = canvasLocation.getX();
        int y = canvasLocation.getY();
        int width = widget.getWidth();
        int height = widget.getHeight();

        // Emojis can extend above and below the widget's 14px height
        // Add padding to account for taller emojis (up to ~32px tall emojis)
        int verticalPadding = this.config.chatMessageSpacing() + this.config.chatMessageSpacing();

        return point.x >= x && point.x <= x + width &&
               point.y >= y - verticalPadding && point.y <= y + height + verticalPadding;
    }

    private boolean isPointInWidget(Widget widget, Point point)
    {
        net.runelite.api.Point canvasLocation = widget.getCanvasLocation();
        if (canvasLocation == null)
        {
            return false;
        }

        int x = canvasLocation.getX();
        int y = canvasLocation.getY();
        int width = widget.getWidth();
        int height = widget.getHeight();

        return point.x >= x && point.x <= x + width &&
               point.y >= y && point.y <= y + height;
    }

    private String findEmojiAtPosition(Widget widget, String text, Point mousePoint)
    {
        int imageId = EmojiPositionCalculator.findEmojiAtPoint(
            widget,
            text,
            mousePoint.x,
            mousePoint.y,
            id -> PluginUtils.getEmojiDimension(this.client.getModIcons(), id)
        );

        if (imageId >= 0)
        {
            this.hoveredEmoji = this.findEmojiByImageId(imageId);
            return this.findEmojiNameById(imageId);
        }

        this.hoveredEmoji = null;
        return null;
    }

    private Emoji findEmojiByImageId(int imageId)
    {
        for (Emoji emoji : this.emojiLoader.getEmojis().values())
        {
            if (this.chatIconManager.chatIconIndex(emoji.getId()) == imageId)
            {
                return emoji;
            }
        }
        return null;
    }

    private String findEmojiNameById(int imageId)
    {
        // Check custom emojis first
        for (Emoji emoji : this.emojiLoader.getEmojis().values())
        {
            if (this.chatIconManager.chatIconIndex(emoji.getId()) == imageId)
            {
                return emoji.getText();
            }
        }

        // Check built-in RuneLite IconIDs
        for (IconID icon : IconID.values())
        {
            if (icon.getIndex() == imageId)
            {
                return this.formatIconName(icon.name());
            }
        }

        return null;
    }

    private String formatIconName(String enumName)
    {
        // Convert PLAYER_MODERATOR to "Player Moderator"
        String[] words = enumName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words)
        {
            if (result.length() > 0)
            {
                result.append(" ");
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            result.append(word.substring(1));
        }
        return result.toString();
    }

    private void sendTooltipHoverMessage()
    {
        java.util.Map<String, Object> data = new java.util.HashMap<>();

        if (this.hoveredEmoji == null)
        {
            data.put(TOOLTIP_HOVER_EMOJI_KEY, null);
            PluginMessage message = new PluginMessage(PLUGIN_MESSAGE_NAMESPACE, TOOLTIP_HOVER_MESSAGE, data);
            this.eventBus.post(message);
            return;
        }

        int imageId = this.chatIconManager.chatIconIndex(this.hoveredEmoji.getId());

        data.put(TOOLTIP_HOVER_EMOJI_KEY, this.hoveredEmoji);
        data.put(TOOLTIP_HOVER_IMAGE_ID_KEY, imageId);

        boolean isAnimated = this.hoveredEmoji instanceof AnimatedEmoji;
        if (isAnimated)
        {
            AnimatedEmoji animatedEmoji = (AnimatedEmoji) this.hoveredEmoji;
            GifAnimation animation = this.emojiLoader.getOrLoadAnimation(animatedEmoji);
            if (animation != null)
            {
                data.put(TOOLTIP_HOVER_FRAME_COUNT_KEY, animation.getFrameCount());
                data.put(TOOLTIP_HOVER_CURRENT_FRAME_KEY, animation.getCurrentFrameIndex());
            }
        }

        PluginMessage message = new PluginMessage(PLUGIN_MESSAGE_NAMESPACE, TOOLTIP_HOVER_MESSAGE, data);
        this.eventBus.post(message);
    }

    private Color getOverlayBackgroundColor()
    {
        Color color = this.configManager.getConfiguration(RUNELITE_CONFIG_GROUP, OVERLAY_BACKGROUND_COLOR_KEY, Color.class);
        if (color != null)
        {
            return color;
        }
        return ComponentConstants.STANDARD_BACKGROUND_COLOR;
    }
}
