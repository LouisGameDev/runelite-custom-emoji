package com.customemoji;

import lombok.NonNull;
import net.runelite.api.Client;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.*;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

class CustomEmojiOverlay extends OverlayPanel
{
    private final Client client;
    private final CustomEmojiConfig config;
    private final CustomEmojiPlugin plugin;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ChatIconManager chatIconManager;

    private String inputText;
    private Map<String, CustomEmojiPlugin.Emoji> emojiSuggestions = new HashMap<>();

    public final KeyListener typingListener = new KeyListener()
    {
        @Override
        public void focusLost()
        {
            KeyListener.super.focusLost();
        }

        @Override public void keyReleased(KeyEvent e)
        {
            clientThread.invoke(() ->
            {
                Widget input = client.getWidget(InterfaceID.Chatbox.INPUT);
                boolean isChatActive = input != null && !input.isHidden();

                if (!isChatActive)
                {
                    return;
                }

                inputText = extractChatInput(input.getText());
                emojiSuggestions = getEmojiSuggestions(inputText);
            });
        }

        @Override
        public void keyTyped(KeyEvent e)
        {
            // Do nothing
        }

        @Override
        public void keyPressed(KeyEvent e)
        {
            // Do nothing
        }

    };

    @Inject
    private CustomEmojiOverlay(Client client, CustomEmojiPlugin plugin, CustomEmojiConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;

        panelComponent.setGap(new Point(0,2));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay() || client.isMenuOpen())
        {
            return null;
        }

        if (emojiSuggestions.isEmpty())
        {
            return null;
        }

        final FontMetrics fm = graphics.getFontMetrics();

        for (CustomEmojiPlugin.Emoji emoji : emojiSuggestions.values())
        {

            final String text = emoji.getText();

            // build image component
            BufferedImage bufferedImage = CustomEmojiPlugin.loadImage(emoji.getFile()).unwrap();
            bufferedImage = scaleDown(bufferedImage, config.maxImageHeight());
            ImageComponent imageComponent = new ImageComponent(bufferedImage);

            // build line component
            LineComponent lineComponent = LineComponent.builder().right(text).build();
            SplitComponent splitComponent = SplitComponent.builder().first(imageComponent).second(lineComponent).orientation(ComponentOrientation.HORIZONTAL).build();

            panelComponent.getChildren().add(splitComponent);
        }

        return super.render(graphics);
    }


    public static BufferedImage scaleDown(BufferedImage originalImage, int targetHeight) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        // Do not scale if already short enough
        if (originalHeight <= targetHeight) {
            return originalImage;
        }

        // Compute new width while preserving aspect ratio
        double scaleFactor = (double) targetHeight / originalHeight;
        int newWidth = (int) Math.round(originalWidth * scaleFactor);

        // Create scaled image
        BufferedImage scaledImage = new BufferedImage(newWidth, targetHeight, originalImage.getType());
        Graphics2D graphics = scaledImage.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(originalImage, 0, 0, newWidth, targetHeight, null);
        graphics.dispose();

        return scaledImage;
    }

    private static String extractChatInput(String input)
    {
        input = Text.removeTags(input);
        input = removePlayerName(input);
        input = removeChatInputAsterisk(input);
        input = removeBeforeLastSpace(input);

        return input;
    }

    @NonNull
    private Map<String, CustomEmojiPlugin.Emoji> getEmojiSuggestions(@NonNull String searchTerm)
    {
        Map<String, CustomEmojiPlugin.Emoji> matches = new HashMap<>();

        if (searchTerm.trim().isEmpty() || searchTerm.length() < 3)
        {
            return matches;
        }

        String lowerSearch = searchTerm.toLowerCase();

        for (Map.Entry<String, CustomEmojiPlugin.Emoji> entry : this.plugin.emojis.entrySet())
        {
            if (matches.size() >= 10)
            {
                break;
            }

            if (entry.getKey().contains(lowerSearch))
            {
                matches.put(entry.getKey(), entry.getValue());
            }
        }

        return matches;
    }

    private static String removeBeforeLastSpace(String input)
    {
        if (input == null || input.isBlank())
        {
            return input;
        }

        int lastSpaceIndex = input.lastIndexOf(' ');
        if (lastSpaceIndex == -1)
        {
            return input; // No space found, return original string
        }

        return input.substring(lastSpaceIndex + 1);
    }

    private static String removeChatInputAsterisk(String input)
    {
        if (input != null && input.endsWith("*"))
        {
            return input.substring(0, input.length() - 1);
        }

        return input;
    }

    private static String removePlayerName(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        int colonIndex = input.indexOf(':');

        if (colonIndex == -1)
        {
            return input;
        }

        return input.substring(colonIndex + 2);
    }
}
