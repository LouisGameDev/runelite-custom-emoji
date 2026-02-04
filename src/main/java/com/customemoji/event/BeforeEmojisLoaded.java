package com.customemoji.event;

import com.customemoji.model.Emoji;
import lombok.Getter;
import java.util.Map;
import java.util.concurrent.Phaser;

/**
 * Event fired when the EmojiLoader has begun loading all emojis.
 * Subscribers doing async work should call {@link #registerParticipant()}
 * at the start and {@link #markComplete()} when finished.
 */
public class BeforeEmojisLoaded
{
    @Getter
    private final Phaser phaser = new Phaser(1); // poster is a participant

    @Getter
    private final Map<String, Emoji> oldEmojis;

    public BeforeEmojisLoaded(Map<String, Emoji> oldEmojis)
    {
        this.oldEmojis = oldEmojis;
    }

    /**
     * Called by subscribers that will do async work.
     */
    public void registerParticipant()
    {
        this.phaser.register();
    }

    /**
     * Called by subscribers when they have finished their async work.
     */
    public void markComplete()
    {
        this.phaser.arriveAndDeregister();
    }

    /**
     * Blocks until all registered participants have called {@link #markComplete()}.
     */
    public void awaitCompletion()
    {
        this.phaser.arriveAndAwaitAdvance();
    }
}
