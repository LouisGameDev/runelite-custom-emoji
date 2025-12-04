package com.customemoji.replacer;

import com.customemoji.model.Soundoji;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Replaces soundoji triggers and plays associated sounds.
 */
@Slf4j
public class SoundojiReplacer implements MessageReplacer
{
    private final Function<String, Soundoji> soundojiLookup;
    private final Consumer<Soundoji> playSoundoji;

    public SoundojiReplacer(
        Function<String, Soundoji> soundojiLookup,
        Consumer<Soundoji> playSoundoji)
    {
        this.soundojiLookup = soundojiLookup;
        this.playSoundoji = playSoundoji;
    }

    @Override
    public boolean tryReplace(String[] messageWords, int index, String trigger, boolean playSound)
    {
        Soundoji soundoji = this.soundojiLookup.apply(trigger.toLowerCase());

        if (soundoji == null)
        {
            return false;
        }

        if (playSound)
        {
            this.playSoundoji.accept(soundoji);
        }

        messageWords[index] = messageWords[index].replace(trigger, "*" + trigger + "*");
        log.debug("Processing soundoji {}", trigger);
        return true;
    }
}