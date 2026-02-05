package com.customemoji.model;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;

import com.customemoji.model.AnimatedEmoji.AnimatedEmojiBuilder;
import com.customemoji.model.StaticEmoji.StaticEmojiBuilder;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Builder
@Getter
@Slf4j
public class EmojiDto
{
	private String text;
	private File file;
	private Dimension dimension;
	private long lastModified;
	private BufferedImage staticImage;
	private Integer index;
	private Integer iconId;
	private Integer zeroWidthIndex;
	private Integer zeroWidthIconId;
	private boolean isZeroWidth;
	private boolean isAnimated;

	public boolean isValid()
	{
		return file != null && 
			   text != null && 
			   dimension != null && 
			   lastModified > 0 && 
			   staticImage != null &&
			   index != null &&
			   iconId != null &&
			   (!isZeroWidth || (zeroWidthIndex != null && zeroWidthIconId != null));
	}
	
	public void setIndex(Integer index)
	{
		this.index = index;
	}

	public void setIconId(Integer iconId)
	{
		this.iconId = iconId;
	}

	public void setZeroWidthIndex(Integer zeroWidthIndex)
	{
		this.zeroWidthIndex = zeroWidthIndex;
	}

	public void setZeroWidthIconId(Integer zeroWidthIconId)
	{
		this.zeroWidthIconId = zeroWidthIconId;
	}
	
	// TODO: Entire project has iconId and index backwards.
	// Once that is fixed, iconId and index assignments in this method can be flip-flopped to the correct properties
	public Emoji toEmoji()
	{
		if (this.isAnimated)
		{
			AnimatedEmojiBuilder builder = AnimatedEmoji.builder()
														.index(this.iconId)   													
														.iconId(this.index);

			if (this.isZeroWidth)
			{
				builder.zeroWidthIndex(this.zeroWidthIconId)
					   .zeroWidthIconId(this.zeroWidthIndex);
			}

			return builder.file(this.file)
						  .text(this.text)
						  .dimension(this.dimension)
						  .lastModified(this.lastModified)
						  .staticImage(this.staticImage)
						  .build();		 
		}
		else
		{
			StaticEmojiBuilder builder = StaticEmoji.builder()
													.index(this.iconId)
													.iconId(this.index);

			if (this.isZeroWidth)
			{
				builder.zeroWidthIndex(this.zeroWidthIconId)
					   .zeroWidthIconId(this.zeroWidthIndex);
			}

			return builder.file(this.file)
						  .text(this.text)
						  .dimension(this.dimension)
						  .lastModified(this.lastModified)
						  .staticImage(this.staticImage)
						  .build();
		}
	}
}
