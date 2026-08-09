package com.customemoji.service;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.model.Holiday;
import com.customemoji.model.Lifecycle;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

@Singleton
public class SeasonalEmojiManager implements Lifecycle
{
	private final CustomEmojiConfig config;

	@Inject
	public SeasonalEmojiManager(CustomEmojiConfig config)
	{
		this.config = config;
	}

	@Override
	public void startUp()
	{
		// Do nothing
	}

	@Override
	public void shutDown()
	{
		// Do nothing
	}

	@Override
	public boolean isEnabled(CustomEmojiConfig config)
	{
		return config.halloweenEmojisEnabled() || config.christmasEmojisEnabled();
	}

	public static boolean isSeasonalConfigKey(String key)
	{
		boolean isHalloweenKey = CustomEmojiConfig.KEY_SEASONAL_HALLOWEEN_ENABLED.equals(key);
		boolean isChristmasKey = CustomEmojiConfig.KEY_SEASONAL_CHRISTMAS_ENABLED.equals(key);

		return isHalloweenKey || isChristmasKey;
	}

	public Set<Holiday> activeHolidays()
	{
		return this.activeHolidays(MonthDay.now(ZoneId.systemDefault()));
	}

	public Set<Holiday> activeHolidays(MonthDay date)
	{
		Set<Holiday> activeHolidays = EnumSet.noneOf(Holiday.class);

		for (Holiday holiday : Holiday.values())
		{
			if (this.isActive(holiday, date))
			{
				activeHolidays.add(holiday);
			}
		}

		return activeHolidays;
	}

	private boolean isActive(Holiday holiday, MonthDay date)
	{
		boolean isEnabled = this.isEnabled(holiday);
		if (!isEnabled)
		{
			return false;
		}

		return holiday.includes(date);
	}

	private boolean isEnabled(Holiday holiday)
	{
		switch (holiday)
		{
		case HALLOWEEN:
			return this.config.halloweenEmojisEnabled();
		case CHRISTMAS:
			return this.config.christmasEmojisEnabled();
		default:
			return false;
		}
	}
}