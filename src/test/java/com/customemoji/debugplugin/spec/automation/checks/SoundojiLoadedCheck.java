package com.customemoji.debugplugin.spec.automation.checks;

import com.customemoji.debugplugin.spec.automation.AutomatedCheck;

import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;

public class SoundojiLoadedCheck implements AutomatedCheck
{
    private static final String PLUGIN_MESSAGE_NAMESPACE = "custom-emoji";
    private static final String SOUNDOJI_STATE_MESSAGE = "soundoji-state";

    private int soundojiCount = -1;

    private final EventBus eventBus;

    public SoundojiLoadedCheck(EventBus eventBus)
    {
        this.eventBus = eventBus;
    }

    public int getSoundojiCount()
    {
        return this.soundojiCount;
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

        if (SOUNDOJI_STATE_MESSAGE.equals(message.getName()))
        {
            Object countObj = message.getData().get("count");
            if (countObj instanceof Integer)
            {
                this.soundojiCount = (Integer) countObj;
            }
        }
    }

    @Override
    public boolean verify()
    {
        return this.soundojiCount > 0;
    }

    @Override
    public String getResultDescription()
    {
        if (this.soundojiCount < 0)
        {
            return "No soundoji count received from plugin";
        }
        return this.soundojiCount > 0
            ? "Loaded " + this.soundojiCount + " soundojis"
            : "No soundojis loaded";
    }

    @Override
    public String getPassDescription()
    {
        return "Soundojis loaded: " + this.soundojiCount;
    }

    @Override
    public String getFailDescription()
    {
        return this.soundojiCount < 0
            ? "Plugin not responding"
            : "No soundojis loaded (count: " + this.soundojiCount + ")";
    }
}
