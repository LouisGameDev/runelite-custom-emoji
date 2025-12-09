package com.customemoji.debugplugin;

import java.util.Map;

import javax.inject.Inject;

import com.google.inject.Provides;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
    name = "Custom Emoji Debug",
    developerPlugin = true
)
public class CustomEmojiDebugPlugin extends Plugin
{
    public static final String PLUGIN_MESSAGE_NAMESPACE = "custom-emoji";
    public static final String PRINT_EMOJIS_MESSAGE = "print-emojis";
    public static final String PRINT_EMOJIS_FILTER_KEY = "filter";

    private static final String EMOJI_DEBUG_COMMAND = "emojidebug";
    private static final String PRINT_EMOJI_COMMAND = "emojiprint";
    private static final String PRINT_STATIC_EMOJI_COMMAND = "emojiprintstatic";
    private static final String PRINT_ANIMATED_EMOJI_COMMAND = "emojiprintanimated";

    @Inject
    private EventBus eventBus;

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
    private DebugTooltipOverlay debugTooltipOverlay;

    @Inject
    private Client client;

    @Subscribe
    public void onCommandExecuted(CommandExecuted event)
    {
        String command = event.getCommand();

        switch (command)
        {
            case EMOJI_DEBUG_COMMAND:
                this.handleEmojiDebugCommand(event.getArguments());
                break;
            case PRINT_EMOJI_COMMAND:
                this.sendPrintEmojisMessage(null);
                break;
            case PRINT_STATIC_EMOJI_COMMAND:
                this.sendPrintEmojisMessage(false);
                break;
            case PRINT_ANIMATED_EMOJI_COMMAND:
                this.sendPrintEmojisMessage(true);
                break;
            default:
                break;
        }
    }

    private void handleEmojiDebugCommand(String[] args)
    {
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

    private void sendPrintEmojisMessage(Boolean animatedFilter)
    {
        Map<String, Object> data = Map.of(PRINT_EMOJIS_FILTER_KEY, animatedFilter != null ? animatedFilter : "all");
        PluginMessage message = new PluginMessage(PLUGIN_MESSAGE_NAMESPACE, PRINT_EMOJIS_MESSAGE, data);
        this.eventBus.post(message);
    }

    @Override
    protected void startUp() throws Exception
    {
        this.overlayManager.add(this.hitboxOverlay);
        this.overlayManager.add(this.rawTextTooltipOverlay);
        this.overlayManager.add(this.animationCounterOverlay);
        this.overlayManager.add(this.overheadDebugOverlay);
        this.overlayManager.add(this.debugTooltipOverlay);
        this.animationCounterOverlay.startUp();
        this.debugTooltipOverlay.startUp();
    }

    @Override
    protected void shutDown() throws Exception
    {
        this.animationCounterOverlay.shutDown();
        this.debugTooltipOverlay.shutDown();
        this.overlayManager.remove(this.hitboxOverlay);
        this.overlayManager.remove(this.rawTextTooltipOverlay);
        this.overlayManager.remove(this.animationCounterOverlay);
        this.overlayManager.remove(this.overheadDebugOverlay);
        this.overlayManager.remove(this.debugTooltipOverlay);
    }

    @Provides
    CustomEmojiDebugConfig provideDebugConfig(ConfigManager configManager)
    {
        return configManager.getConfig(CustomEmojiDebugConfig.class);
    }
}
