package com.customemoji.replacer;

/**
 * Strategy interface for replacing triggers in chat messages.
 * Implementations handle specific types of replacements (emojis, soundojis, etc.)
 */
@FunctionalInterface
public interface MessageReplacer
{
    /**
     * Attempts to replace a trigger word in the message.
     *
     * @param messageWords the array of words in the message
     * @param index the index of the current word being processed
     * @param trigger the trigger text (with formatting tags removed)
     * @param playSound whether sounds should be played for this message
     * @return true if a replacement was made, false otherwise
     */
    boolean tryReplace(String[] messageWords, int index, String trigger, boolean playSound);
}
