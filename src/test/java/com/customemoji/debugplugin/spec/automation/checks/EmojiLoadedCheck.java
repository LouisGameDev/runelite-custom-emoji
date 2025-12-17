package com.customemoji.debugplugin.spec.automation.checks;

import javax.inject.Inject;

import com.customemoji.debugplugin.spec.automation.AutomatedCheck;

import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;

public class EmojiLoadedCheck implements AutomatedCheck
{
    private static final String PLUGIN_MESSAGE_NAMESPACE = "custom-emoji";
    private static final String EMOJI_COUNT_MESSAGE = "emoji-count";

    private int emojiCount = -1;

    private final EventBus eventBus;

    @Inject
    public EmojiLoadedCheck(EventBus eventBus)
    {
        this.eventBus = eventBus;
    }

    public int getEmojiCount()
    {
        return this.emojiCount;
    }

    public void register()
    {
        this.eventBus.register(this);
    }

    public void unregister()
    {
        this.eventBus.unregister(this);
    }

    @Subscribe
    public void onPluginMessage(PluginMessage message)
    {
        if (!PLUGIN_MESSAGE_NAMESPACE.equals(message.getNamespace()))
        {
            return;
        }

        if (EMOJI_COUNT_MESSAGE.equals(message.getName()))
        {
            Object countObj = message.getData().get("count");
            if (countObj instanceof Integer)
            {
                this.emojiCount = (Integer) countObj;
            }
        }
    }

    @Override
    public boolean verify()
    {
        return this.emojiCount > 0;
    }

    @Override
    public String getResultDescription()
    {
        if (this.emojiCount < 0)
        {
            return "No emoji count received from plugin";
        }
        return this.emojiCount > 0
            ? "Loaded " + this.emojiCount + " emojis"
            : "No emojis loaded";
    }

    @Override
    public String getPassDescription()
    {
        return "Emojis loaded: " + this.emojiCount;
    }

    @Override
    public String getFailDescription()
    {
        return this.emojiCount < 0
            ? "Plugin not responding"
            : "No emojis loaded (count: " + this.emojiCount + ")";
    }
}
