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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final Map<String, BufferedImage> normalizedImageCache = new HashMap<>();

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
            clearImageCache();
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

        String searchTerm = extractChatInput(inputText).toLowerCase();
        
        for (CustomEmojiPlugin.Emoji emoji : emojiSuggestions.values())
        {
            addEmojiToOverlay(emoji, searchTerm);
        }

        return super.render(graphics);
    }

    private void addEmojiToOverlay(Emoji emoji, String searchTerm)
    {       
        String cacheKey = emoji.getFile().getAbsolutePath() + "_" + config.maxImageHeight() + "_" + config.resizeEmotes();
        BufferedImage normalizedImage = normalizedImageCache.get(cacheKey);
        
        if (normalizedImage == null)
        {
            BufferedImage bufferedImage = CustomEmojiPlugin.loadImage(emoji.getFile()).unwrap();
            bufferedImage = CustomEmojiPlugin.scaleDown(bufferedImage, config.maxImageHeight());
            // normalizedImage = CustomEmojiImageUtilities.normalizeImage(bufferedImage, config); TODO: will be implemented in a later PR
            normalizedImageCache.put(cacheKey, bufferedImage);
        }
        
        ImageComponent imageComponent = new ImageComponent(normalizedImage);

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
        if (searchTerm.trim().isEmpty() || searchTerm.length() < 3)
        {
            return new HashMap<>();
        }

        String lowerSearch = searchTerm.toLowerCase();
        
        // Get all matching entries
        List<Map.Entry<String, CustomEmojiPlugin.Emoji>> matchingEntries = new ArrayList<>();
        for (Map.Entry<String, CustomEmojiPlugin.Emoji> entry : this.plugin.emojis.entrySet())
        {
            if (entry.getKey().toLowerCase().contains(lowerSearch))
            {
                matchingEntries.add(entry);
            }
        }
        
        // Sort by relevance
        sortByRelevance(matchingEntries, lowerSearch);
        
        // Build result map with limit
        Map<String, CustomEmojiPlugin.Emoji> matches = new LinkedHashMap<>();
        int limit = Math.min(matchingEntries.size(), config.maxImageSuggestions());
        
        for (int i = limit - 1; i >= 0; i--)
        {
            Map.Entry<String, CustomEmojiPlugin.Emoji> entry = matchingEntries.get(i);
            matches.put(entry.getKey(), entry.getValue());
        }

        return matches;
    }
    
    private void sortByRelevance(List<Map.Entry<String, CustomEmojiPlugin.Emoji>> entries, String searchTerm)
    {
        entries.sort((a, b) -> {
            String nameA = a.getKey().toLowerCase();
            String nameB = b.getKey().toLowerCase();
            
            // Exact matches come first
            boolean exactA = nameA.equals(searchTerm);
            boolean exactB = nameB.equals(searchTerm);
            if (exactA != exactB) return exactA ? -1 : 1;
            
            // Then prefix matches
            boolean prefixA = nameA.startsWith(searchTerm);
            boolean prefixB = nameB.startsWith(searchTerm);
            if (prefixA != prefixB) return prefixA ? -1 : 1;
            
            // Then by position of match (earlier is better)
            int posA = nameA.indexOf(searchTerm);
            int posB = nameB.indexOf(searchTerm);
            if (posA != posB) return Integer.compare(posA, posB);
            
            // Finally by length (shorter names are better)
            return Integer.compare(nameA.length(), nameB.length());
        });
    }

    private void clearImageCache()
    {
        normalizedImageCache.clear();
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
