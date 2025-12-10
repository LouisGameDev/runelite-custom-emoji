package com.customemoji.features.loader;

import com.customemoji.features.panel.PanelComponent;
import com.customemoji.module.PluginLifecycleComponent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;

/**
 * Handles emoji and soundoji loading, file watching, and reload notifications.
 */
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
@Slf4j
public class LoaderComponent implements PluginLifecycleComponent
{
	private final Client client;
	private final EmojiLoader emojiLoader;
	private final SoundojiLoader soundojiLoader;
	private final PanelComponent panelComponent;

	@Override
	public void startUp()
	{
		this.emojiLoader.loadInitialEmojis(this::onInitialLoadComplete);
		this.soundojiLoader.loadSoundojis();

		try
		{
			Path[] watchPaths = new Path[]{
				EmojiLoader.EMOJIS_FOLDER.toPath(),
				SoundojiLoader.SOUNDOJIS_FOLDER.toPath()
			};
			this.emojiLoader.startWatching(watchPaths, this::onReloadComplete);
		}
		catch (IOException e)
		{
			log.error("Failed to setup file watcher", e);
		}
	}

	@Override
	public void shutDown()
	{
		this.emojiLoader.shutdown();
		this.soundojiLoader.clear();
		log.debug("Loader shutdown complete");
	}

	private void onInitialLoadComplete()
	{
		this.panelComponent.onEmojisLoaded();
		this.displayLoadErrors();
	}

	private void onReloadComplete(int emojiCount)
	{
		String message = String.format("<col=00FF00>Custom Emoji: Reloaded %d emojis", emojiCount);
		this.client.addChatMessage(ChatMessageType.CONSOLE, "", message, null);

		SwingUtilities.invokeLater(this.panelComponent::refreshPanel);
	}

	private void displayLoadErrors()
	{
		List<String> loadErrors = this.emojiLoader.getErrors();
		if (!loadErrors.isEmpty())
		{
			String message = "<col=FF0000>Custom Emoji: There were " + loadErrors.size()
				+ " errors loading emojis.<br><col=FF0000>Use <col=00FFFF>::emojierror <col=FF0000>to see them.";
			this.client.addChatMessage(ChatMessageType.CONSOLE, "", message, null);
		}
	}

	public void reloadSelectedEmojis(List<String> emojiNames, Runnable onComplete)
	{
		this.emojiLoader.reloadSelectedEmojis(emojiNames, onComplete);
	}
}
