package com.customemoji;

import com.customemoji.model.Emoji;

import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.annotations.VarCInt;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import net.runelite.client.util.ImageUtil;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PluginUtils
{
	public static final Pattern IMAGE_TAG_PATTERN = Pattern.compile("<img=(\\d+)>");

	private PluginUtils()
	{
	}

	public static Dimension getEmojiDimension(IndexedSprite[] modIcons, int imageId)
	{
		if (modIcons == null || imageId < 0 || imageId >= modIcons.length)
		{
			return null;
		}

		IndexedSprite sprite = modIcons[imageId];
		if (sprite == null)
		{
			return null;
		}

		return new Dimension(sprite.getWidth(), sprite.getHeight());
	}

	public static List<Integer> getImageIdsFromText(String text)
	{
		List<Integer> imageIds = new ArrayList<>();

		if (text == null || text.isEmpty())
		{
			return imageIds;
		}

		Matcher matcher = IMAGE_TAG_PATTERN.matcher(text);
		while (matcher.find())
		{
			String idString = matcher.group(1);
			int id = Integer.parseInt(idString);
			imageIds.add(id);
		}

		return imageIds;
	}

	public static int findMaxEmojiHeightInWidget(Widget widget, IndexedSprite[] modIcons)
	{
		if (widget == null)
		{
			return 0;
		}

		int maxHeight = 0;
		String text = widget.getText();
		List<Integer> imageIds = PluginUtils.getImageIdsFromText(text);

		for (int imageId : imageIds)
		{
			Dimension dimension = PluginUtils.getEmojiDimension(modIcons, imageId);
			if (dimension != null)
			{
				maxHeight = Math.max(maxHeight, dimension.height);
			}
		}

		return maxHeight;
	}

	public static Set<String> parseDisabledEmojis(String disabledEmojisString)
	{
		Set<String> result = new HashSet<>();

		if (disabledEmojisString != null && !disabledEmojisString.trim().isEmpty())
		{
			String[] parts = disabledEmojisString.split(",");
			for (String part : parts)
			{
				String trimmed = part.trim();
				if (!trimmed.isEmpty())
				{
					result.add(trimmed);
				}
			}
		}

		return result;
	}

	public static Set<String> parseResizingDisabledEmojis(String resizingDisabledEmojisString)
	{
		Set<String> result = new HashSet<>();

		if (resizingDisabledEmojisString != null && !resizingDisabledEmojisString.trim().isEmpty())
		{
			String[] parts = resizingDisabledEmojisString.split(",");
			for (String part : parts)
			{
				String trimmed = part.trim();
				if (!trimmed.isEmpty())
				{
					result.add(trimmed);
				}
			}
		}

		return result;
	}

	public static Map<Integer, Emoji> buildEmojiLookup(Supplier<Map<String, Emoji>> emojisSupplier)
	{
		Map<Integer, Emoji> lookup = new HashMap<>();

		if (emojisSupplier == null)
		{
			return lookup;
		}

		for (Emoji emoji : emojisSupplier.get().values())
		{
			int imageId = emoji.getImageId();
			if (imageId < 0)
			{
				continue;
			}

			lookup.put(imageId, emoji);

			int zeroWidthImageId = emoji.getZeroWidthImageId();
			if (zeroWidthImageId >= 0)
			{
				lookup.put(zeroWidthImageId, emoji);
			}
		}

		return lookup;
	}

	public static boolean hasImgTag(String text)
	{
		return text != null && text.contains("<img=");
	}

	public static boolean isZeroWidthId(Emoji emoji, int imageId)
	{
		if (emoji == null)
		{
			return false;
		}
		int zeroWidthImageId = emoji.getZeroWidthImageId();
		return zeroWidthImageId >= 0 && zeroWidthImageId == imageId;
	}

	public static void linkZeroWidthEmojisToTarget(
		List<EmojiPosition> positions,
		Map<Integer, Emoji> emojiLookup)
	{
		java.awt.Rectangle lastBaseEmojiBounds = null;

		for (EmojiPosition position : positions)
		{
			Emoji emoji = emojiLookup.get(position.getImageId());
			boolean isZeroWidth = PluginUtils.isZeroWidthId(emoji, position.getImageId());

			if (isZeroWidth)
			{
				position.setBaseEmojiBounds(lastBaseEmojiBounds);
			}

			if (!isZeroWidth && emoji != null)
			{
				lastBaseEmojiBounds = position.getBounds();
			}
		}
	}

	public static Emoji findEmojiByImageId(int imageId, Map<String, Emoji> emojis)
	{
		for (Emoji emoji : emojis.values())
		{
			if (emoji.getImageId() == imageId)
			{
				return emoji;
			}

			int zeroWidthImageId = emoji.getZeroWidthImageId();
			if (zeroWidthImageId >= 0 && zeroWidthImageId == imageId)
			{
				return emoji;
			}
		}

		return null;
	}

	public static boolean getIsMouseInWidget(Client client, Widget widget)
	{
		net.runelite.api.Point mouseCanvasPosition = client.getMouseCanvasPosition();
		if (mouseCanvasPosition == null)
		{
			return false;
		}

		return widget != null && !widget.isHidden() && widget.contains(mouseCanvasPosition);
	}
	
	public static boolean getIsMouseInChatWidget(Client client)
	{
		Widget chatbox = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
		boolean inChatbox = PluginUtils.getIsMouseInWidget(client, chatbox);
		if (inChatbox)
		{
			return true;
		}

		Widget pmChat = client.getWidget(InterfaceID.PmChat.CONTAINER);
		return PluginUtils.getIsMouseInWidget(client, pmChat);
	}

	public static List<Widget> getVisibleChatWidgets(Client client)
	{
		if (client.getVarcIntValue(VarClientID.CHAT_VIEW) == 1337) // Chatbox is "minimized"
		{
			return null;
		}

		List<Widget> result = new ArrayList<>();

		Widget chatbox = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
		List<Widget> chatboxWidgets = PluginUtils.getVisibleChildWidgets(chatbox);
		if (chatboxWidgets != null)
		{
			result.addAll(chatboxWidgets);
		}

		Widget pmChat = client.getWidget(InterfaceID.PmChat.CONTAINER);
		List<Widget> pmChatWidgets = PluginUtils.getVisibleChildWidgets(pmChat);
		if (pmChatWidgets != null)
		{
			result.addAll(pmChatWidgets);
		}

		return result.isEmpty() ? null : result;
	}

	public static List<Widget> getVisibleChildWidgets(Widget parent)
	{
		if (parent == null || parent.isHidden())
		{
			return null;
		}

		List<Widget> result = new ArrayList<>();

		Widget[] dynamicChildren = parent.getChildren();
		if (dynamicChildren == null)
		{
			return result;
		}

		for (Widget widget : dynamicChildren)
		{
			if (widget == null || !PluginUtils.isWidgetVisible(parent, widget))
			{
				continue;
			}

			result.add(widget);
		}

		return result;
	}

	public static boolean isWidgetVisible(Widget parent, Widget child)
	{
		if (child.isHidden())
		{
			return false;
		}

        int widgetTop = child.getRelativeY();
        int widgetBottom = widgetTop + child.getHeight();

        int viewportTop = parent.getScrollY();
        int viewportBottom = viewportTop + parent.getHeight();

        boolean bottomInView = widgetBottom >= viewportTop;
        boolean topInView = widgetTop <= viewportBottom;

        return bottomInView && topInView;
    }

	public static boolean isAnimatedGif(File file)
	{
		String fileName = file.getName().toLowerCase();
		boolean isGif = fileName.endsWith(".gif");
		if (!isGif)
		{
			return false;
		}

		try (ImageInputStream stream = ImageIO.createImageInputStream(file))
		{
			if (stream == null)
			{
				return false;
			}

			Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
			if (!readers.hasNext())
			{
				return false;
			}

			ImageReader reader = readers.next();
			try
			{
				reader.setInput(stream);
				int frameCount = reader.getNumImages(true);
				return frameCount > 1;
			}
			finally
			{
				reader.dispose();
			}
		}
		catch (IOException e)
		{
			return false;
		}
	}

	public static BufferedImage resizeImage(BufferedImage image, int maxImageHeight)
	{
		if (image.getHeight() <= maxImageHeight)
		{
			return image;
		}

		double scaleFactor = (double) maxImageHeight / image.getHeight();
		int scaledWidth = (int) Math.round(image.getWidth() * scaleFactor);

		return ImageUtil.resizeImage(image, scaledWidth, maxImageHeight, true);
	}
}
