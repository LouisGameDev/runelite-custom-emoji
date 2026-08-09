package com.customemoji.model;

import java.time.Month;
import java.time.MonthDay;

import lombok.Getter;

public enum Holiday
{
	HALLOWEEN("Halloween", MonthDay.of(Month.OCTOBER, 1), MonthDay.of(Month.NOVEMBER, 1)),
	CHRISTMAS("Christmas", MonthDay.of(Month.DECEMBER, 1), MonthDay.of(Month.JANUARY, 1));

	@Getter
	private final String folderName;

	@Getter
	private final MonthDay start;

	@Getter
	private final MonthDay end;

	Holiday(String folderName, MonthDay start, MonthDay end)
	{
		this.folderName = folderName;
		this.start = start;
		this.end = end;
	}

	public boolean includes(MonthDay date)
	{
		boolean wrapsYearEnd = this.start.isAfter(this.end);

		if (wrapsYearEnd)
		{
			return !date.isBefore(this.start) || !date.isAfter(this.end);
		}

		return !date.isBefore(this.start) && !date.isAfter(this.end);
	}
}
