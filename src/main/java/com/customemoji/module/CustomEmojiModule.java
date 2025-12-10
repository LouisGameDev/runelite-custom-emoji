package com.customemoji.module;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.features.animation.AnimationComponent;
import com.customemoji.features.chat.ChatMessageHandler;
import com.customemoji.features.chat.ChatSpacingComponent;
import com.customemoji.features.commands.CommandHandler;
import com.customemoji.features.loader.LoaderComponent;
import com.customemoji.features.panel.PanelComponent;
import com.customemoji.features.suggestions.SuggestionsComponent;
import com.customemoji.features.tooltip.TooltipComponent;
import com.customemoji.util.PluginUtils;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.Set;
import javax.inject.Named;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Guice module for the Custom Emoji plugin.
 * Configures dependency injection bindings and provides lifecycle components.
 */
@Slf4j
public class CustomEmojiModule extends AbstractModule
{
	@Override
	protected void configure()
	{
		bind(ComponentManager.class);
	}

	@Provides
	Set<PluginLifecycleComponent> provideLifecycleComponents(
		CommandHandler commandHandler,
		LoaderComponent loaderComponent,
		ChatMessageHandler chatMessageHandler,
		ChatSpacingComponent chatSpacingComponent,
		SuggestionsComponent suggestionsComponent,
		TooltipComponent tooltipComponent,
		AnimationComponent animationComponent,
		PanelComponent panelComponent
	)
	{
		return ImmutableSet.of(
			commandHandler,
			loaderComponent,
			chatMessageHandler,
			chatSpacingComponent,
			suggestionsComponent,
			tooltipComponent,
			animationComponent,
			panelComponent
		);
	}

	@Provides
	@Singleton
	CustomEmojiConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CustomEmojiConfig.class);
	}

	@Provides
	@Named("disabledEmojis")
	Set<String> provideDisabledEmojis(CustomEmojiConfig config)
	{
		return PluginUtils.parseDisabledEmojis(config.disabledEmojis());
	}

	@Provides
	@Named("resizingDisabledEmojis")
	Set<String> provideResizingDisabledEmojis(CustomEmojiConfig config)
	{
		return PluginUtils.parseResizingDisabledEmojis(config.resizingDisabledEmojis());
	}
}
