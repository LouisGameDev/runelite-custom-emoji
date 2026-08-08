package com.customemoji.model;

/**
 * The space in pixels above and below a chat widget that needs to be added
 * so that the emoji inside it will not overlap other widgets.
 */
public class SpacingInfo
{
	private final int aboveSpacing;
	private final int belowSpacing;

	public SpacingInfo(int aboveSpacing, int belowSpacing)
	{
		this.aboveSpacing = aboveSpacing;
		this.belowSpacing = belowSpacing;
	}

	public int getAboveSpacing()
	{
		return this.aboveSpacing;
	}

	public int getBelowSpacing()
	{
		return this.belowSpacing;
	}
}
