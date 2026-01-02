package com.customemoji;

import com.customemoji.animation.AnimationManager;
import com.customemoji.animation.GifAnimation;
import com.customemoji.model.AnimatedEmoji;
import com.customemoji.model.Emoji;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.*;

import javax.inject.Inject;

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
    private static final int BORDER_OFFSET = 4;
    private static final int GAP = 2;
    private static final int MIN_ROW_HEIGHT = 14;
    private static final int MIN_WORD_LENGTH = 2;

    @Inject
    private Client client;

    @Inject
    private CustomEmojiConfig config;

    @Inject
    private AnimationManager animationManager;

    @Inject
    private Map<String, Emoji> emojis;

    private String inputText;
    private Map<String, Emoji> emojiSuggestions = new HashMap<>();
    private final Map<String, BufferedImage> normalizedImageCache = new HashMap<>();
    private final List<AnimatedEmojiPosition> animatedEmojiPositions = new ArrayList<>();

    private static class AnimatedEmojiPosition
    {
        final AnimatedEmoji emoji;
        final int index;

        AnimatedEmojiPosition(AnimatedEmoji emoji, int index)
        {
            this.emoji = emoji;
            this.index = index;
        }
    }

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
        this.animatedEmojiPositions.clear();

        // Don't render overlay if it's disabled or a right-click context menu is open
        if (!this.config.showOverlay() || this.client.isMenuOpen())
        {
            return null;
        }

        // Don't render if input text is empty
        if (this.inputText == null || this.inputText.isEmpty())
        {
            return null;
        }

        // Check current chat input if its empty, user may have cleared it
        String currentInput = this.client.getVarcStrValue(VarClientID.CHATINPUT);
        if (currentInput == null || currentInput.isEmpty())
        {
            this.inputText = "";
            return null;
        }

        if (this.emojiSuggestions.isEmpty())
        {
            return null;
        }

        String[] words = this.inputText.split("\\s+");
        String lastWord = words[words.length - 1].toLowerCase();

        int index = 0;
        for (Emoji emoji : this.emojiSuggestions.values())
        {
            this.addEmojiToOverlay(emoji, lastWord);

            if (emoji instanceof AnimatedEmoji)
            {
                this.animatedEmojiPositions.add(new AnimatedEmojiPosition((AnimatedEmoji) emoji, index));
            }
            index++;
        }

        Dimension dimension = super.render(graphics);

        this.renderAnimations(graphics);

        return dimension;
    }

    private void addEmojiToOverlay(Emoji emoji, String searchTerm)
    {
        BufferedImage displayImage = emoji.getStaticImage();
        ImageComponent imageComponent = new ImageComponent(displayImage);

        // build line component with highlighted text
        String highlightedText = this.createHighlightedText(emoji.getText(), searchTerm);
        LineComponent lineComponent = LineComponent.builder().right(highlightedText).build();
        SplitComponent splitComponent = SplitComponent.builder().first(imageComponent).second(lineComponent).orientation(ComponentOrientation.HORIZONTAL).build();

        this.panelComponent.getChildren().add(splitComponent);
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

        if (lastWord.length() < MIN_WORD_LENGTH)
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

    private void renderAnimations(Graphics2D graphics)
    {
        if (!this.config.enableAnimatedEmojis() || this.animatedEmojiPositions.isEmpty())
        {
            return;
        }

        // Calculate Y positions for each row
        int[] yPositions = new int[this.emojiSuggestions.size()];
        int currentY = BORDER_OFFSET;
        int i = 0;
        for (Emoji emoji : this.emojiSuggestions.values())
        {
            int imageHeight = emoji.getDimension().height;
            int rowHeight = Math.max(imageHeight, MIN_ROW_HEIGHT);

            yPositions[i] = currentY;
            currentY += rowHeight + GAP;
            i++;
        }

        int x = BORDER_OFFSET;

        for (AnimatedEmojiPosition animatedPosition : this.animatedEmojiPositions)
        {
            if (animatedPosition.index >= yPositions.length)
            {
                continue;
            }

            AnimatedEmoji emoji = animatedPosition.emoji;

            // Mark visible before loading to prevent unloading
            this.animationManager.markAnimationVisible(emoji.getId());

            GifAnimation animation = this.animationManager.getOrLoadAnimation(emoji);
            BufferedImage frame = animation != null ? animation.getCurrentFrame() : null;

            if (frame != null)
            {
                int baseY = yPositions[animatedPosition.index];
                int imageHeight = emoji.getDimension().height;
                int rowHeight = Math.max(imageHeight, MIN_ROW_HEIGHT);
                int y = baseY + (rowHeight - imageHeight) / 2;

                graphics.drawImage(frame, x, y, emoji.getDimension().width, imageHeight, null);
            }
        }
    }

    private void clearImageCache()
    {
        this.normalizedImageCache.clear();
    }
}
