package com.customemoji;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.*;

import javax.inject.Inject;

import com.customemoji.model.Emoji;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
class CustomEmojiOverlay extends OverlayPanel
{
    @Inject
    private Client client;

    @Inject
    private CustomEmojiConfig config;

    @Inject
    private ChatIconManager chatIconManager;

    @Inject
    private Map<String, Emoji> emojis;

    private String inputText;
    private Map<String, Emoji> emojiSuggestions = new HashMap<>();
    private final Map<String, BufferedImage> normalizedImageCache = new HashMap<>();

    @Inject
    public CustomEmojiOverlay(CustomEmojiPlugin plugin)
    {
        super(plugin);
        this.getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, "Configure", "Custom Emoji overlay"));
    }

    protected void updateChatInput(String input)
    {
        if (input == this.inputText)
        {
            return;
        }

        this.inputText = input;

        this.emojiSuggestions = getEmojiSuggestions(this.inputText);
        this.clearImageCache();
    }

    protected void startUp()
    {
        panelComponent.setGap(new Point(0, 2));
    }

    protected void shutDown()
    {
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

        String[] words = this.inputText.split("\\s+");
        String lastWord = words[words.length - 1].toLowerCase();

        for (Emoji emoji : this.emojiSuggestions.values())
        {
            this.addEmojiToOverlay(emoji, lastWord);
        }

        return super.render(graphics);
    }

    private void addEmojiToOverlay(Emoji emoji, String searchTerm)
    {
        ImageComponent imageComponent = new ImageComponent(emoji.getCacheImage(this.client, this.chatIconManager));

        // build line component with highlighted text
        String highlightedText = this.createHighlightedText(emoji.getText(), searchTerm);
        LineComponent lineComponent = LineComponent.builder().right(highlightedText).build();
        SplitComponent splitComponent = SplitComponent.builder().first(imageComponent).second(lineComponent).orientation(ComponentOrientation.HORIZONTAL).build();

        panelComponent.getChildren().add(splitComponent);
    }
    
    private String createHighlightedText(String text, String searchTerm)
    {
        if (searchTerm.isEmpty())
        {
            return text;
        }
        
        String lowerText = text.toLowerCase();
        String lowerSearch = searchTerm.toLowerCase();
        int index = lowerText.indexOf(lowerSearch);
        
        if (index == -1)
        {
            return text;
        }
        
        StringBuilder result = new StringBuilder();
        result.append(text.substring(0, index));
        result.append("<col=00ff00>");  // Green highlight
        result.append(text.substring(index, index + searchTerm.length()));
        result.append("<col=ffffff>");  // Reset to white
        result.append(text.substring(index + searchTerm.length()));
        
        return result.toString();
    }

    @NonNull
    private Map<String, Emoji> getEmojiSuggestions(@NonNull String searchTerm)
    {
        if (searchTerm.trim().isEmpty())
        {
            return new HashMap<>();
        }

        String[] words = searchTerm.split("\\s+");
        String lastWord = words[words.length - 1];

        if (lastWord.length() < 3)
        {
            return new HashMap<>();
        }

        String lowerSearch = lastWord.toLowerCase();

        // Get disabled emojis from config
        Set<String> disabledEmojis = PluginUtils.parseDisabledEmojis(this.config.disabledEmojis());

        // Get all matching entries (excluding disabled emojis)
        List<Map.Entry<String, Emoji>> matchingEntries = new ArrayList<>();
        for (Map.Entry<String, Emoji> entry : this.emojis.entrySet())
        {
            String emojiName = entry.getKey();
            boolean isDisabled = disabledEmojis.contains(emojiName);
            boolean matchesSearch = emojiName.toLowerCase().contains(lowerSearch);

            if (matchesSearch && !isDisabled)
            {
                matchingEntries.add(entry);
            }
        }
        
        // Sort by relevance
        this.sortByRelevance(matchingEntries, lowerSearch);

        // Build result map with limit
        Map<String, Emoji> matches = new LinkedHashMap<>();
        int limit = Math.min(matchingEntries.size(), this.config.maxImageSuggestions());

        for (int i = limit - 1; i >= 0; i--)
        {
            Map.Entry<String, Emoji> entry = matchingEntries.get(i);
            matches.put(entry.getKey(), entry.getValue());
        }

        return matches;
    }

    private void sortByRelevance(List<Map.Entry<String, Emoji>> entries, String searchTerm)
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
}
