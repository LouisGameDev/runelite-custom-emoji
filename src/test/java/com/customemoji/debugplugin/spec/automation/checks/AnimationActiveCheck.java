package com.customemoji.debugplugin.spec.automation.checks;

import javax.inject.Inject;

import com.customemoji.debugplugin.spec.automation.AutomatedCheck;

import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;

public class AnimationActiveCheck implements AutomatedCheck
{
    private static final String PLUGIN_MESSAGE_NAMESPACE = "custom-emoji";
    private static final String ANIMATION_COUNT_MESSAGE = "animation-count";

    private int animationCount = -1;

    private final EventBus eventBus;

    @Inject
    public AnimationActiveCheck(EventBus eventBus)
    {
        this.eventBus = eventBus;
    }

    public int getAnimationCount()
    {
        return this.animationCount;
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

        if (ANIMATION_COUNT_MESSAGE.equals(message.getName()))
        {
            Object countObj = message.getData().get("count");
            if (countObj instanceof Integer)
            {
                this.animationCount = (Integer) countObj;
            }
        }
    }

    @Override
    public boolean verify()
    {
        return this.animationCount > 0;
    }

    @Override
    public String getResultDescription()
    {
        if (this.animationCount < 0)
        {
            return "No animation count received";
        }
        return this.animationCount > 0
            ? this.animationCount + " animations active"
            : "No active animations";
    }

    @Override
    public String getPassDescription()
    {
        return "Active animations: " + this.animationCount;
    }

    @Override
    public String getFailDescription()
    {
        return this.animationCount < 0
            ? "Animation count not available"
            : "No animations currently active";
    }
}
