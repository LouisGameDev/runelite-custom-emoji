package com.customemoji.model;

/**
 * A piece of a chat message, cut where the game is allowed to start a new line.
 */
public class TextSegment
{
	private final String text;
	private final boolean forcesLineBreak;

	public TextSegment(String text, boolean forcesLineBreak)
	{
		this.text = text;
		this.forcesLineBreak = forcesLineBreak;
	}

	public String getText()
	{
		return this.text;
	}

	public boolean forcesLineBreak()
	{
		return this.forcesLineBreak;
	}
}
