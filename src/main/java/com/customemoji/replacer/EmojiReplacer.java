package com.customemoji.replacer;

import com.customemoji.model.Emoji;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * Replaces emoji triggers with image tags in chat messages.
 */
@Slf4j
public class EmojiReplacer implements MessageReplacer
{
    private final Function<String, Emoji> emojiLookup;
    private final Predicate<String> isEmojiEnabled;
    private final IntUnaryOperator getChatIconIndex;

    public EmojiReplacer(
        Function<String, Emoji> emojiLookup,
        Predicate<String> isEmojiEnabled,
        IntUnaryOperator getChatIconIndex)
    {
        this.emojiLookup = emojiLookup;
        this.isEmojiEnabled = isEmojiEnabled;
        this.getChatIconIndex = getChatIconIndex;
    }

    @Override
    public boolean tryReplace(String[] messageWords, int index, String trigger, boolean playSound)
    {
        Emoji emoji = this.emojiLookup.apply(trigger.toLowerCase());

        if (emoji == null)
        {
            return false;
        }

        boolean emojiEnabled = this.isEmojiEnabled.test(emoji.getText());

        if (!emojiEnabled)
        {
            return false;
        }

        int iconIndex = this.getChatIconIndex.applyAsInt(emoji.getId());
        String imageTag = "<img=" + iconIndex + ">";
        messageWords[index] = messageWords[index].replace(trigger, imageTag);
        log.debug("Replacing {} with emoji {}", trigger, emoji.getText());
        return true;
    }
}
