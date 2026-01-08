package com.customemoji.debugplugin;

import javax.inject.Inject;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.CustomEmojiPlugin;
import com.customemoji.service.EmojiStateManager;
import com.google.inject.Provides;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;

import java.awt.event.KeyEvent;

@PluginDescriptor(
    name = "Custom Emoji Debug",
    developerPlugin = true
)
public class CustomEmojiDebugPlugin extends Plugin implements KeyListener
{
    private static final String EMOJI_DEBUG_COMMAND = "emojidebug";
    private static final String EMOJI_BENCH_COMMAND = "emojibench";

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KeyManager keyManager;

    @Inject
    private EmojiHitboxOverlay hitboxOverlay;

    @Inject
    private RawTextTooltipOverlay rawTextTooltipOverlay;

    @Inject
    private AnimationCounterOverlay animationCounterOverlay;

    @Inject
    private OverheadDebugOverlay overheadDebugOverlay;

    @Inject
    private GifBenchmark gifBenchmark;

    @Inject
    private BenchmarkOverlay benchmarkOverlay;

    @Inject
    private BenchmarkLogger benchmarkLogger;

    @Inject
    private ScheduledExecutorService executor;

    @Inject
    private Client client;

    @Inject
    private PluginManager pluginManager;

    @Subscribe
    public void onCommandExecuted(CommandExecuted event)
    {
        String command = event.getCommand();

        if (command.equalsIgnoreCase(EMOJI_DEBUG_COMMAND))
        {
            this.handleEmojiDebugCommand(event.getArguments());
        }
        else if (command.equalsIgnoreCase(EMOJI_BENCH_COMMAND))
        {
            this.handleEmojiBenchCommand(event.getArguments());
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

    private void handleEmojiBenchCommand(String[] args)
    {
        if (args.length == 0)
        {
            this.client.addChatMessage(ChatMessageType.CONSOLE, "", "Usage: ::emojibench <emoji_name> [iterations]", null);
            return;
        }

        String emojiName = args[0];
        int iterations = 5;

        if (args.length > 1)
        {
            try
            {
                iterations = Integer.parseInt(args[1]);
                iterations = Math.max(1, Math.min(iterations, 100));
            }
            catch (NumberFormatException e)
            {
                this.client.addChatMessage(ChatMessageType.CONSOLE, "", "Invalid iteration count, using default: 5", null);
            }
        }

        this.client.addChatMessage(ChatMessageType.CONSOLE, "", "Starting GIF benchmark for: " + emojiName, null);
        this.benchmarkOverlay.show();

        int finalIterations = iterations;
        this.executor.submit(() ->
        {
            BenchmarkResult result = this.gifBenchmark.runBenchmark(emojiName, finalIterations);

            if (result != null)
            {
                File logFile = this.benchmarkLogger.writeLog(result);
                String logMessage = logFile != null
                    ? "Benchmark complete. Log: " + logFile.getName()
                    : "Benchmark complete. (log write failed)";
                this.client.addChatMessage(ChatMessageType.CONSOLE, "", logMessage, null);
            }
            else
            {
                String error = this.gifBenchmark.getErrorMessage();
                this.client.addChatMessage(ChatMessageType.CONSOLE, "", "Benchmark failed: " + error, null);
            }
        });
    }

    @Override
    public void keyTyped(KeyEvent e)
    {
        // Not used
    }

    @Override
    public void keyPressed(KeyEvent e)
    {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && this.benchmarkOverlay.isVisible())
        {
            this.benchmarkOverlay.hide();
            this.gifBenchmark.clearResult();
            e.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent e)
    {
        // Not used
    }

    @Override
    protected void startUp() throws Exception
    {
        this.overlayManager.add(this.hitboxOverlay);
        this.overlayManager.add(this.rawTextTooltipOverlay);
        this.overlayManager.add(this.animationCounterOverlay);
        this.overlayManager.add(this.overheadDebugOverlay);
        this.overlayManager.add(this.benchmarkOverlay);
        this.keyManager.registerKeyListener(this);

        this.configureBenchmark();
    }

    private void configureBenchmark()
    {
        CustomEmojiPlugin mainPlugin = this.findMainPlugin();
        if (mainPlugin == null)
        {
            return;
        }

        CustomEmojiConfig config = mainPlugin.getConfig();
        EmojiStateManager stateManager = mainPlugin.getEmojiStateManager();

        this.gifBenchmark.setEmojisSupplier(mainPlugin::getEmojis);
        this.gifBenchmark.setMaxHeightSupplier(config::maxImageHeight);
        this.gifBenchmark.setResizingEnabledLookup(stateManager::isResizingEnabled);
    }

    private CustomEmojiPlugin findMainPlugin()
    {
        for (Plugin plugin : this.pluginManager.getPlugins())
        {
            if (plugin instanceof CustomEmojiPlugin)
            {
                return (CustomEmojiPlugin) plugin;
            }
        }
        return null;
    }

    @Override
    protected void shutDown() throws Exception
    {
        this.keyManager.unregisterKeyListener(this);
        this.overlayManager.remove(this.hitboxOverlay);
        this.overlayManager.remove(this.rawTextTooltipOverlay);
        this.overlayManager.remove(this.animationCounterOverlay);
        this.overlayManager.remove(this.overheadDebugOverlay);
        this.overlayManager.remove(this.benchmarkOverlay);
        this.benchmarkOverlay.hide();
    }

    @Provides
    CustomEmojiDebugConfig provideDebugConfig(ConfigManager configManager)
    {
        return configManager.getConfig(CustomEmojiDebugConfig.class);
    }
}