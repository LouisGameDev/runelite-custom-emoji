package com.customemoji.features.commands;

import com.customemoji.features.loader.EmojiLoader;
import com.customemoji.features.loader.SoundojiLoader;
import com.customemoji.module.PluginLifecycleComponent;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.LinkBrowser;

/**
 * Handles chat commands for the Custom Emoji plugin.
 */
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class CommandHandler implements PluginLifecycleComponent
{
	public static final String EMOJI_ERROR_COMMAND = "emojierror";
	public static final String EMOJI_FOLDER_COMMAND = "emojifolder";
	public static final String SOUNDOJI_FOLDER_COMMAND = "soundojifolder";

	private final Client client;
	private final EmojiLoader emojiLoader;

	@Subscribe
	public void onCommandExecuted(CommandExecuted e)
	{
		switch (e.getCommand())
		{
			case EMOJI_FOLDER_COMMAND:
				LinkBrowser.open(EmojiLoader.EMOJIS_FOLDER.toString());
				break;
			case SOUNDOJI_FOLDER_COMMAND:
				LinkBrowser.open(SoundojiLoader.SOUNDOJIS_FOLDER.toString());
				break;
			case EMOJI_ERROR_COMMAND:
				this.displayErrors();
				break;
			default:
				break;
		}
	}

	private void displayErrors()
	{
		for (String error : this.emojiLoader.getErrors())
		{
			this.client.addChatMessage(ChatMessageType.CONSOLE, "", error, null);
		}
	}
}
