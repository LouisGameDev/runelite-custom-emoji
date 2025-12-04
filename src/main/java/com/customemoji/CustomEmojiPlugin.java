package com.customemoji;

import com.customemoji.events.EmojisReloaded;
import com.customemoji.io.EmojiLoader;
import com.customemoji.io.FileWatcher;
import com.customemoji.io.SoundojiLoader;
import com.customemoji.lifecycle.LifecycleCoordinator;
import com.customemoji.model.Emoji;
import com.customemoji.model.Soundoji;
import com.customemoji.replacer.EmojiReplacer;
import com.customemoji.replacer.MessageReplacer;
import com.customemoji.replacer.SoundojiReplacer;
import com.google.inject.Provides;
import com.google.inject.Provider;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.SwingUtilities;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.RuneLite;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import com.customemoji.Panel.CustomEmojiPanel;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;

import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;

@Slf4j
@PluginDescriptor(
		name="Custom Emoji",
		description="Allows you to use custom emojis in chat messages",
		tags={"emoji", "chat", "message", "custom", "icon", "emote", "text", "clan", "notification"}
)
public class CustomEmojiPlugin extends Plugin
{
	public static final String EMOJI_ERROR_COMMAND = "emojierror";
	public static final String EMOJI_FOLDER_COMMAND = "emojifolder";
	public static final String SOUNDOJI_FOLDER_COMMAND = "soundojifolder";
	public static final String PRINT_ALL_EMOJI_COMMAND = "emojiprint";

	public static final File SOUNDOJIS_FOLDER = RuneLite.RUNELITE_DIR.toPath().resolve("soundojis").toFile();
	public static final File EMOJIS_FOLDER = RuneLite.RUNELITE_DIR.toPath().resolve("emojis").toFile();

	private static final Pattern WHITESPACE_REGEXP = Pattern.compile("[\\s\\u00A0]");

	private final Map<String, Emoji> emojis = new ConcurrentHashMap<>();
	private final Map<String, Soundoji> soundojis = new HashMap<>();
	private final List<String> errors = new ArrayList<>();
	private final List<MessageReplacer> messageReplacers = new ArrayList<>();
	private CustomEmojiPanel panel;
	private NavigationButton navButton;

	@Inject
	private EventBus eventBus;

	@Inject
	private CustomEmojiOverlay overlay;

	@Inject
	private CustomEmojiTooltip tooltip;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private CustomEmojiConfig config;

	@Inject
	private ChatIconManager chatIconManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private AudioPlayer audioPlayer;

	@Inject
	private ChatSpacingManager chatSpacingManager;

	@Inject
	private Provider<CustomEmojiPanel> panelProvider;

	@Inject
	private EmojiLoader emojiLoader;

	@Inject
	private SoundojiLoader soundojiLoader;

	@Inject
	private FileWatcher fileWatcher;

	@Inject
	private LifecycleCoordinator lifecycleCoordinator;

	@Override
	protected void startUp() throws Exception
	{
		this.emojiLoader.firstTimeSetup(EMOJIS_FOLDER);
		this.soundojiLoader.firstTimeSetup(SOUNDOJIS_FOLDER);

		this.initializeMessageReplacers();

		this.emojiLoader.loadAsync(EMOJIS_FOLDER, false, () -> this.onLoadComplete(this.emojis.size(), this.soundojis.size(), false));
		this.soundojiLoader.load(SOUNDOJIS_FOLDER);

		if (this.config.showPanel())
		{
			this.togglePanelButton(true);
		}

		// Configure and register lifecycle components
		this.fileWatcher.configure(EMOJIS_FOLDER, SOUNDOJIS_FOLDER, (emojiCount, soundojiCount) -> this.onLoadComplete(emojiCount, soundojiCount, true));

		this.lifecycleCoordinator.register(this.emojiLoader);
		this.lifecycleCoordinator.register(this.fileWatcher);
		this.lifecycleCoordinator.register(this.overlay);
		this.lifecycleCoordinator.register(this.tooltip);

		// Start all lifecycle components
		this.lifecycleCoordinator.startAll();

		// Add overlays to manager (separate from lifecycle)
		this.overlayManager.add(this.overlay);
		this.overlayManager.add(this.tooltip);

		// Apply initial chat spacing
		this.clientThread.invokeLater(this.chatSpacingManager::applyChatSpacing);

		if (!this.errors.isEmpty())
		{
			this.clientThread.invokeLater(() ->
			{
				String message =
						"<col=FF0000>Custom Emoji: There were " + this.errors.size() +
								" errors loading emojis and soundojis.<br><col=FF0000>Use <col=00FFFF>::emojierror <col=FF0000>to see them.";
				this.client.addChatMessage(ChatMessageType.CONSOLE, "", message, null);
			});
		}
		else
		{
			log.debug("<col=00FF00>Custom Emoji: Loaded {} emojis and {} soundojis.", this.emojis.size(), this.soundojis.size());
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		// Shut down all lifecycle components (in reverse order)
		this.lifecycleCoordinator.shutDownAll();

		// Remove overlays from manager
		this.overlayManager.remove(this.overlay);
		this.overlayManager.remove(this.tooltip);

		// Clear data
		this.emojis.clear();
		this.errors.clear();
		this.chatSpacingManager.clearStoredPositions();

		if (this.panel != null)
		{
			this.togglePanelButton(false);
		}

		this.soundojiLoader.clear();

		log.debug("Plugin shutdown complete - all containers cleared");
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted e) {
		switch (e.getCommand()) {
			case EMOJI_FOLDER_COMMAND:
				LinkBrowser.open(EMOJIS_FOLDER.toString());
				break;
			case SOUNDOJI_FOLDER_COMMAND:
				LinkBrowser.open(SOUNDOJIS_FOLDER.toString());
				break;
			case EMOJI_ERROR_COMMAND:

				for (String error : errors) {
					client.addChatMessage(ChatMessageType.CONSOLE, "", error, null);
				}
				break;
			case PRINT_ALL_EMOJI_COMMAND:
				StringBuilder sb = new StringBuilder();

				sb.append("Currently loaded emoji: ");

				for (Map.Entry<String, Emoji> entry : this.emojis.entrySet())
				{
					sb.append(entry.getKey() + " ");
				}

				String message = updateMessage(sb.toString(), false);
				client.addChatMessage(ChatMessageType.CONSOLE, "Currently loaded emoji", message, null);

				break;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		switch (chatMessage.getType())
		{
			case PUBLICCHAT:
			case MODCHAT:
			case FRIENDSCHAT:
			case CLAN_CHAT:
			case CLAN_GUEST_CHAT:
			case CLAN_GIM_CHAT:
			case PRIVATECHAT:
			case PRIVATECHATOUT:
			case MODPRIVATECHAT:
				break;
			default:
				return;
		}

		final MessageNode messageNode = chatMessage.getMessageNode();
		final String message = messageNode.getValue();
		final String updatedMessage = updateMessage(message, true);

		if (updatedMessage == null)
		{
			return;
		}

		messageNode.setValue(updatedMessage);
	}

	@Subscribe
	public void onOverheadTextChanged(final OverheadTextChanged event)
	{
		if (!(event.getActor() instanceof Player))
		{
			return;
		}

		final String message = event.getOverheadText();
		final String updatedMessage = updateMessage(message, false);

		if (updatedMessage == null)
		{
			return;
		}

		event.getActor().setOverheadText(updatedMessage);
	}

  	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		// Apply chat spacing when chat-related widgets are loaded
		if (event.getGroupId() == InterfaceID.Chatbox.SCROLLAREA)
		{
			clientThread.invokeLater(chatSpacingManager::applyChatSpacing);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		boolean isCustomEmoteConfig = event.getGroup().equals("custom-emote");
		if (!isCustomEmoteConfig)
		{
			return;
		}

		boolean shouldRefreshPanel = true;

		switch (event.getKey())
		{
			case CustomEmojiConfig.KEY_CHAT_MESSAGE_SPACING:
				clientThread.invokeLater(chatSpacingManager::applyChatSpacing);
				break;
			case CustomEmojiConfig.KEY_MAX_IMAGE_HEIGHT:
			case CustomEmojiConfig.KEY_RESIZE_EMOJI:
				this.fileWatcher.scheduleReload(true);
				break;
			case CustomEmojiConfig.KEY_SHOW_SIDE_PANEL:
				this.togglePanelButton(this.config.showPanel());
				break;
			case CustomEmojiConfig.KEY_DISABLED_EMOJIS:
				// Panel already updated itself, skip redundant refresh
				shouldRefreshPanel = false;
				break;
		}

		if (shouldRefreshPanel && this.panel != null)
		{
			SwingUtilities.invokeLater(() -> panel.updateFromConfig());
		}

	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event)
	{
		switch (event.getIndex()) {
			case VarClientID.CHAT_LASTREBUILD:
				this.chatSpacingManager.clearStoredPositions();
				this.clientThread.invokeAtTickEnd(this.chatSpacingManager::applyChatSpacing);
				break;
			case VarClientID.CHAT_LASTSCROLLPOS:
				this.clientThread.invokeAtTickEnd(this.chatSpacingManager::captureScrollPosition);
				break;
		}
	}

	@Provides
	CustomEmojiConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CustomEmojiConfig.class);
	}

	@Provides
	Map<String, Emoji> provideEmojis()
	{
		return this.emojis;
	}

	@Provides
	Map<String, Soundoji> provideSoundojis()
	{
		return this.soundojis;
	}

	@Provides
	Set<String> provideDisabledEmojis()
	{
		return PluginUtils.parseDisabledEmojis(this.config.disabledEmojis());
	}

	public void openConfiguration()
	{
		// We don't have access to the ConfigPlugin so let's just emulate an overlay click
		this.eventBus.post(new OverlayMenuClicked(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, null, null), this.overlay));
	}

	@Nullable
	private String updateMessage(final String message, boolean playSound)
	{
		final String[] messageWords = WHITESPACE_REGEXP.split(message);

		boolean editedMessage = false;
		for (int i = 0; i < messageWords.length; i++)
		{
			// Remove tags except for <lt> and <gt>
			final String trigger = Text.removeFormattingTags(messageWords[i]);

			for (MessageReplacer replacer : this.messageReplacers)
			{
				boolean replaced = replacer.tryReplace(messageWords, i, trigger, playSound);
				editedMessage = editedMessage || replaced;
			}
		}

		if (!editedMessage)
		{
			return null;
		}

		return String.join(" ", messageWords);
	}

	private void initializeMessageReplacers()
	{
		this.messageReplacers.clear();

		MessageReplacer emojiReplacer = new EmojiReplacer(
			this.emojis::get,
			emojiName -> PluginUtils.isEmojiEnabled(emojiName, this.config.disabledEmojis()),
			this.chatIconManager::chatIconIndex
		);

		MessageReplacer soundojiReplacer = new SoundojiReplacer(
			this.soundojis::get,
			soundoji -> soundoji.play(this.audioPlayer, this.config.volume())
		);

		this.messageReplacers.add(emojiReplacer);
		this.messageReplacers.add(soundojiReplacer);
	}

	private void onLoadComplete(int emojiCount, int soundojiCount, boolean isReload)
	{
		if (isReload)
		{
			String message = "<col=00FF00>Custom Emoji: Reloaded " + emojiCount + " emojis and " + soundojiCount + " soundojis";
			this.client.addChatMessage(ChatMessageType.CONSOLE, "", message, null);
		}
		else
		{
			log.info("Initial emoji load complete");
		}

		this.eventBus.post(new EmojisReloaded(emojiCount, soundojiCount));
	}

	private void togglePanelButton(boolean visible)
	{
		if (visible)
		{
			this.panel = this.panelProvider.get();
			this.panel.startUp();

			BufferedImage icon = ImageUtil.loadImageResource(this.getClass(), "../../com/customemoji/smiley.png");

			this.navButton = NavigationButton.builder()
				.tooltip("Custom Emoji")
				.icon(icon)
				.priority(5)
				.panel(this.panel)
				.build();

			this.clientToolbar.addNavigation(this.navButton);
		}
		else
		{
			if (this.navButton != null)
			{
				this.clientToolbar.removeNavigation(this.navButton);
				this.navButton = null;
			}
			if (this.panel != null)
			{
				this.panel.shutDown();
			}
			this.panel = null;
		}
	}
}
