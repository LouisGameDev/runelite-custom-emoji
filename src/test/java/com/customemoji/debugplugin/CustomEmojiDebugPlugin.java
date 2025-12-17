package com.customemoji.debugplugin;

import javax.inject.Inject;

import com.google.inject.Provides;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
    name = "Custom Emoji Debug",
    developerPlugin = true
)
public class CustomEmojiDebugPlugin extends Plugin
{
    private static final String EMOJI_DEBUG_COMMAND = "emojidebug";

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private EmojiHitboxOverlay hitboxOverlay;

    @Inject
    private RawTextTooltipOverlay rawTextTooltipOverlay;

    @Inject
    private AnimationCounterOverlay animationCounterOverlay;

    @Inject
    private OverheadDebugOverlay overheadDebugOverlay;

    @Inject
    private Client client;

    @Subscribe
    public void onCommandExecuted(CommandExecuted event)
    {
        if (!event.getCommand().equalsIgnoreCase(EMOJI_DEBUG_COMMAND))
        {
            return;
        }

        String[] args = event.getArguments();
        if (args.length == 0)
        {
            this.client.addChatMessage(ChatMessageType.CONSOLE, "", "Usage: ::emojidebug <icon_id>", null);
            return;
        }

        try
        {
            int iconId = Integer.parseInt(args[0]);
            String message = "Icon " + iconId + ": <img=" + iconId + ">";
            this.client.addChatMessage(ChatMessageType.CONSOLE, "", message, null);
        }
        catch (NumberFormatException e)
        {
            this.client.addChatMessage(ChatMessageType.CONSOLE, "", "Invalid icon ID: " + args[0], null);
        }
    }

    @Override
    protected void startUp() throws Exception
    {
        this.overlayManager.add(this.hitboxOverlay);
        this.overlayManager.add(this.rawTextTooltipOverlay);
        this.overlayManager.add(this.animationCounterOverlay);
        this.overlayManager.add(this.overheadDebugOverlay);
    }

    @Override
    protected void shutDown() throws Exception
    {
        this.overlayManager.remove(this.hitboxOverlay);
        this.overlayManager.remove(this.rawTextTooltipOverlay);
        this.overlayManager.remove(this.animationCounterOverlay);
        this.overlayManager.remove(this.overheadDebugOverlay);
    }

    @Provides
    CustomEmojiDebugConfig provideDebugConfig(ConfigManager configManager)
    {
        return configManager.getConfig(CustomEmojiDebugConfig.class);
    }
}