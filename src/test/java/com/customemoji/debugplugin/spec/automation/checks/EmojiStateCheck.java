package com.customemoji.debugplugin.spec.automation.checks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.customemoji.debugplugin.spec.automation.AutomatedCheck;

import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;

public class EmojiStateCheck implements AutomatedCheck
{
    private static final String PLUGIN_MESSAGE_NAMESPACE = "custom-emoji";
    private static final String EMOJI_STATE_MESSAGE = "emoji-state";

    private final EventBus eventBus;
    private final String emojiName;
    private final boolean shouldExist;

    private Set<String> loadedEmojiNames = new HashSet<>();
    private int emojiCount = -1;

    public EmojiStateCheck(EventBus eventBus, String emojiName, boolean shouldExist)
    {
        this.eventBus = eventBus;
        this.emojiName = emojiName.toLowerCase();
        this.shouldExist = shouldExist;
    }

    public void register()
    {
        this.eventBus.register(this);
    }

    public void unregister()
    {
        this.eventBus.unregister(this);
    }

    public Set<String> getLoadedEmojiNames()
    {
        return this.loadedEmojiNames;
    }

    public int getEmojiCount()
    {
        return this.emojiCount;
    }

    @Subscribe
    @SuppressWarnings("unchecked")
    public void onPluginMessage(PluginMessage message)
    {
        if (!PLUGIN_MESSAGE_NAMESPACE.equals(message.getNamespace()))
        {
            return;
        }

        if (EMOJI_STATE_MESSAGE.equals(message.getName()))
        {
            Object countObj = message.getData().get("count");
            if (countObj instanceof Integer)
            {
                this.emojiCount = (Integer) countObj;
            }

            Object namesObj = message.getData().get("emojiNames");
            if (namesObj instanceof List)
            {
                this.loadedEmojiNames = new HashSet<>((List<String>) namesObj);
            }
        }
    }

    @Override
    public boolean verify()
    {
        if (this.emojiCount < 0)
        {
            return false;
        }

        boolean exists = this.loadedEmojiNames.contains(this.emojiName);
        return exists == this.shouldExist;
    }

    @Override
    public String getResultDescription()
    {
        if (this.emojiCount < 0)
        {
            return "No emoji state received from plugin";
        }

        boolean exists = this.loadedEmojiNames.contains(this.emojiName);
        String existsText = exists ? "exists" : "does not exist";
        return "Emoji '" + this.emojiName + "' " + existsText + " in " + this.emojiCount + " loaded emojis";
    }

    @Override
    public String getPassDescription()
    {
        if (this.shouldExist)
        {
            return "Emoji '" + this.emojiName + "' found in loaded emojis";
        }
        else
        {
            return "Emoji '" + this.emojiName + "' correctly not found";
        }
    }

    @Override
    public String getFailDescription()
    {
        if (this.emojiCount < 0)
        {
            return "Plugin not responding";
        }

        if (this.shouldExist)
        {
            return "Emoji '" + this.emojiName + "' not found (expected to exist)";
        }
        else
        {
            return "Emoji '" + this.emojiName + "' found (expected not to exist)";
        }
    }
}
