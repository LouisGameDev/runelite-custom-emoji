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
	private Integer id;
	private Integer iconId;
	private boolean isAnimated;

	public boolean isValid()
	{
		return file != null && 
			   text != null && 
			   dimension != null && 
			   lastModified > 0 && 
			   staticImage != null &&
			   id != null &&
			   iconId != null;
	}

	public Emoji toEmoji()
	{

		if (this.isAnimated)
		{
			AnimatedEmojiBuilder builder = AnimatedEmoji.builder();

			if (this.text.endsWith("00"))
			{
				builder.zeroWidthId(this.id).zeroWidthImageId(this.iconId);
			}
			else
			{
				builder.id(this.id).imageId(this.iconId);
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
			StaticEmojiBuilder builder = StaticEmoji.builder();

			if (this.text.endsWith("00"))
			{
				builder.zeroWidthId(this.id).zeroWidthImageId(this.iconId);
			}
			else
			{
				builder.id(this.id).imageId(this.iconId);
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
