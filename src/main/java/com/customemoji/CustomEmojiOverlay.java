package com.customemoji;

import lombok.NonNull;
import net.runelite.api.Client;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.*;
import net.runelite.client.util.Text;

import javax.inject.Inject;

import com.customemoji.CustomEmojiPlugin.Emoji;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

class CustomEmojiOverlay extends OverlayPanel
{
    @Inject
    private Client client;

    @Inject
    private CustomEmojiConfig config;

    @Inject
    private CustomEmojiPlugin plugin;

    @Inject
	private KeyManager keyManager;

    private String inputText;
    private Map<String, CustomEmojiPlugin.Emoji> emojiSuggestions = new HashMap<>();


    protected final KeyListener typingListener = new KeyListener()
    {
        @Override
        public void focusLost()
        {
            KeyListener.super.focusLost();
        }

        @Override public void keyReleased(KeyEvent e)
        {
            Widget input = client.getWidget(InterfaceID.Chatbox.INPUT);

            if (input == null)
            {
                return;
            }

            inputText = extractChatInput(input.getText());
            emojiSuggestions = getEmojiSuggestions(inputText);
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

    protected void startUp()
    {
        panelComponent.setGap(new Point(0,2));

        if (keyManager != null)
        {
            keyManager.registerKeyListener(typingListener);
        }
    }

    protected void shutDown()
    {
        if (keyManager != null)
        {
            keyManager.unregisterKeyListener(typingListener);
        }
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        // Don't render suggestions overlay if tooltips are being shown or if disabled
        if (!config.showOverlay() || client.isMenuOpen())
        {
            return null;
        }

        if (emojiSuggestions.isEmpty())
        {
            return null;
        }

        for (CustomEmojiPlugin.Emoji emoji : emojiSuggestions.values())
        {
            addEmojiToOverlay(emoji);
        }

        return super.render(graphics);
    }

    private void addEmojiToOverlay(Emoji emoji)
    {
        BufferedImage bufferedImage = CustomEmojiPlugin.loadImage(emoji.getFile()).unwrap();

        if (config.resizeEmotes())
        {
            bufferedImage = CustomEmojiPlugin.scaleDown(bufferedImage, config.maxImageHeight());
        }

        ImageComponent imageComponent = new ImageComponent(bufferedImage);

        // build line component
        LineComponent lineComponent = LineComponent.builder().right(emoji.getText()).build();
        SplitComponent splitComponent = SplitComponent.builder().first(imageComponent).second(lineComponent).orientation(ComponentOrientation.HORIZONTAL).build();

        panelComponent.getChildren().add(splitComponent);
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
            if (matches.size() >= config.maxImageSuggestions())
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
