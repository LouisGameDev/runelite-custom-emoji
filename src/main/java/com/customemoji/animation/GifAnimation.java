package com.customemoji.animation;

import java.awt.image.BufferedImage;

import lombok.Getter;

@Getter
public class GifAnimation
{
	private final BufferedImage[] frames;
	private final int[] frameDelays;
	private final int totalDuration;

	private int currentFrameIndex;
	private long animationStartTime;

	public GifAnimation(BufferedImage[] frames, int[] frameDelays)
	{
		this.frames = frames;
		this.frameDelays = frameDelays;
		this.currentFrameIndex = 0;
		this.animationStartTime = System.currentTimeMillis();

		int duration = 0;
		for (int delay : frameDelays)
		{
			duration += delay;
		}
		this.totalDuration = duration;
	}

	public BufferedImage getCurrentFrame()
	{
		if (this.frames.length == 0)
		{
			return null;
		}

		if (this.frames.length == 1)
		{
			return this.frames[0];
		}

		this.updateCurrentFrameIndex();
		return this.frames[this.currentFrameIndex];
	}

	private void updateCurrentFrameIndex()
	{
		if (this.totalDuration <= 0)
		{
			this.currentFrameIndex = 0;
			return;
		}

		long elapsed = System.currentTimeMillis() - this.animationStartTime;
		long timeInCycle = elapsed % this.totalDuration;

		int accumulatedTime = 0;
		for (int i = 0; i < this.frameDelays.length; i++)
		{
			accumulatedTime += this.frameDelays[i];
			if (timeInCycle < accumulatedTime)
			{
				this.currentFrameIndex = i;
				return;
			}
		}

		this.currentFrameIndex = this.frames.length - 1;
	}

	public int getFrameCount()
	{
		return this.frames.length;
	}
}
