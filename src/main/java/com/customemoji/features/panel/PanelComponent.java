package com.customemoji.features.panel;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.module.PluginLifecycleComponent;
import com.google.inject.Provider;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

/**
 * Manages the sidebar panel for the Custom Emoji plugin.
 */
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
@Slf4j
public class PanelComponent implements PluginLifecycleComponent
{
	private final ClientToolbar clientToolbar;
	private final Provider<CustomEmojiPanel> panelProvider;
	private final CustomEmojiConfig config;

	private CustomEmojiPanel panel;
	private NavigationButton navButton;

	@Override
	public boolean isEnabled(CustomEmojiConfig config)
	{
		return config.showPanel();
	}

	@Override
	public void startUp()
	{
		this.showButton();
	}

	@Override
	public void shutDown()
	{
		this.hideButton();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CustomEmojiConfig.KEY_CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}

		if (this.panel != null)
		{
			SwingUtilities.invokeLater(() -> this.panel.updateFromConfig());
		}
	}

	/**
	 * Called when emojis are initially loaded or reloaded.
	 */
	public void onEmojisLoaded()
	{
		if (this.config.showPanel() && this.panel == null)
		{
			this.showButton();
		}
	}

	/**
	 * Refreshes the panel's emoji tree.
	 */
	public void refreshPanel()
	{
		if (this.panel != null)
		{
			this.panel.refreshEmojiTree();
		}
	}

	private void showButton()
	{
		this.panel = this.panelProvider.get();

		BufferedImage icon = ImageUtil.loadImageResource(PanelComponent.class, "/com/customemoji/smiley.png");

		this.navButton = NavigationButton.builder()
			.tooltip("Custom Emoji")
			.icon(icon)
			.priority(5)
			.panel(this.panel)
			.build();

		this.clientToolbar.addNavigation(this.navButton);

		SwingUtilities.invokeLater(this.panel::refreshEmojiTree);
	}

	private void hideButton()
	{
		if (this.navButton != null)
		{
			this.clientToolbar.removeNavigation(this.navButton);
			this.navButton = null;
		}
		this.panel = null;
	}
}
