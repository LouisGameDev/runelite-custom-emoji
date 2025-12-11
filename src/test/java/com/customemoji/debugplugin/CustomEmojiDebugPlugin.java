package com.customemoji.debugplugin;

import javax.inject.Inject;

import com.customemoji.debugplugin.spec.SpecValidationManager;
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
<<<<<<< Updated upstream
=======
    private static final String PRINT_EMOJI_COMMAND = "emojiprint";
    private static final String PRINT_STATIC_EMOJI_COMMAND = "emojiprintstatic";
    private static final String PRINT_ANIMATED_EMOJI_COMMAND = "emojiprintanimated";
    private static final String SPEC_VALIDATION_COMMAND = "specvalidation";

    @Inject
    private EventBus eventBus;
>>>>>>> Stashed changes

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private EmojiHitboxOverlay hitboxOverlay;

    @Inject
    private RawTextTooltipOverlay rawTextTooltipOverlay;

    @Inject
    private Client client;

    @Inject
    private SpecValidationManager specValidationManager;

    @Subscribe
    public void onCommandExecuted(CommandExecuted event)
    {
        if (!event.getCommand().equalsIgnoreCase(EMOJI_DEBUG_COMMAND))
        {
<<<<<<< Updated upstream
            return;
=======
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
            case SPEC_VALIDATION_COMMAND:
                this.specValidationManager.toggleFrame();
                break;
            default:
                break;
>>>>>>> Stashed changes
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
<<<<<<< Updated upstream
=======
        this.overlayManager.add(this.animationCounterOverlay);
        this.overlayManager.add(this.overheadDebugOverlay);
        this.overlayManager.add(this.debugTooltipOverlay);
        this.animationCounterOverlay.startUp();
        this.debugTooltipOverlay.startUp();
        this.specValidationManager.startUp();
>>>>>>> Stashed changes
    }

    @Override
    protected void shutDown() throws Exception
    {
<<<<<<< Updated upstream
=======
        this.specValidationManager.shutDown();
        this.animationCounterOverlay.shutDown();
        this.debugTooltipOverlay.shutDown();
>>>>>>> Stashed changes
        this.overlayManager.remove(this.hitboxOverlay);
        this.overlayManager.remove(this.rawTextTooltipOverlay);
    }

    @Provides
    CustomEmojiDebugConfig provideDebugConfig(ConfigManager configManager)
    {
        return configManager.getConfig(CustomEmojiDebugConfig.class);
    }
}