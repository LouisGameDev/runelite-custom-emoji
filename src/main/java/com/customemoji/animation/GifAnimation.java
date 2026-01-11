package com.customemoji.animation;

import com.customemoji.PluginUtils;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.w3c.dom.NodeList;

@Slf4j
public class GifAnimation implements Closeable
{
	private static final int DEFAULT_FRAME_DELAY_MS = 100;
	private static final int INITIAL_CAPACITY = 16;

	private static final String DISPOSAL_NONE = "none";
	private static final String DISPOSAL_DO_NOT_DISPOSE = "doNotDispose";
	private static final String DISPOSAL_RESTORE_TO_BACKGROUND = "restoreToBackgroundColor";
	private static final String DISPOSAL_RESTORE_TO_PREVIOUS = "restoreToPrevious";

	private byte[] gifData;
	private final int maxHeight;
	private final boolean shouldResize;
	private final boolean useLazyLoading;

	private ImageInputStream imageStream;
	private ImageReader reader;

	private BufferedImage canvas;
	private BufferedImage savedCanvas;
	private int canvasWidth;
	private int canvasHeight;
	private String previousDisposalMethod;
	private Rectangle previousFrameBounds;

	private final List<BufferedImage> frames = new ArrayList<>(INITIAL_CAPACITY);
	private final List<Integer> frameDelays = new ArrayList<>(INITIAL_CAPACITY);

	private int currentFrameIndex;
	private long lastFrameChangeTime;

	private boolean initialized;
	private boolean disposed;
	private boolean allFramesLoaded;
	private boolean backgroundLoadingStarted;
	private boolean preloadingInProgress;

	public GifAnimation(byte[] gifData, int maxHeight, boolean shouldResize, boolean useLazyLoading)
	{
		this.gifData = gifData;
		this.maxHeight = maxHeight;
		this.shouldResize = shouldResize;
		this.useLazyLoading = useLazyLoading;
		this.lastFrameChangeTime = System.currentTimeMillis();
		this.currentFrameIndex = 0;
		this.initialized = false;
		this.disposed = false;
		this.allFramesLoaded = false;
		this.backgroundLoadingStarted = false;
		this.preloadingInProgress = false;
	}

	public synchronized BufferedImage getCurrentFrame()
	{
		if (!this.ensureInitialized())
		{
			return null;
		}

		if (this.frames.isEmpty())
		{
			return null;
		}

		if (this.allFramesLoaded && this.frames.size() == 1)
		{
			return this.frames.get(0);
		}

		int frameIndex = this.calculateCurrentFrameIndex();
		return this.frames.get(frameIndex);
	}

	public synchronized boolean needsPreloading()
	{
		if (!this.useLazyLoading || this.allFramesLoaded || this.preloadingInProgress)
		{
			return false;
		}

		int loadedCount = this.frames.size();
		int framesRemaining = loadedCount - this.currentFrameIndex - 1;
		boolean needsMoreFrames = framesRemaining < 5;

		return needsMoreFrames;
	}

	public void preloadFrames()
	{
		synchronized (this)
		{
			if (this.preloadingInProgress || this.allFramesLoaded)
			{
				return;
			}
			this.preloadingInProgress = true;
		}

		try
		{
			for (int i = 0; i < 5 && !this.allFramesLoaded; i++)
			{
				this.loadNextFrame();
			}
		}
		finally
		{
			synchronized (this)
			{
				this.preloadingInProgress = false;
			}
		}
	}

	public synchronized boolean needsBackgroundLoading()
	{
		if (this.useLazyLoading)
		{
			return false;
		}
		if (!this.initialized || this.disposed || this.allFramesLoaded)
		{
			return false;
		}
		if (this.backgroundLoadingStarted)
		{
			return false;
		}
		this.backgroundLoadingStarted = true;
		return true;
	}

	public void loadAllFrames()
	{
		while (!this.allFramesLoaded && !this.disposed)
		{
			this.loadNextFrame();
		}
	}

	@Override
	public synchronized void close()
	{
		this.disposed = true;
		this.closeReader();
		this.frames.clear();
		this.frameDelays.clear();
	}

	private synchronized boolean ensureInitialized()
	{
		if (this.disposed)
		{
			return false;
		}

		if (this.initialized)
		{
			return true;
		}

		try
		{
			ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.gifData);
			this.imageStream = ImageIO.createImageInputStream(byteArrayInputStream);
			if (this.imageStream == null)
			{
				return false;
			}

			Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
			if (!readers.hasNext())
			{
				this.imageStream.close();
				return false;
			}

			this.reader = readers.next();
			this.reader.setInput(this.imageStream);

			this.initializeCanvas();

			boolean loaded = this.loadNextFrame();
			if (!loaded)
			{
				this.close();
				return false;
			}

			this.initialized = true;
			return true;
		}
		catch (IOException e)
		{
			log.debug("Failed to initialize GIF animation", e);
			return false;
		}
	}

	private void initializeCanvas() throws IOException
	{
		IIOMetadata streamMetadata = this.reader.getStreamMetadata();
		if (streamMetadata != null)
		{
			String formatName = streamMetadata.getNativeMetadataFormatName();
			IIOMetadataNode root = (IIOMetadataNode) streamMetadata.getAsTree(formatName);

			NodeList screenDescList = root.getElementsByTagName("LogicalScreenDescriptor");
			if (screenDescList.getLength() > 0)
			{
				IIOMetadataNode screenDesc = (IIOMetadataNode) screenDescList.item(0);
				String widthStr = screenDesc.getAttribute("logicalScreenWidth");
				String heightStr = screenDesc.getAttribute("logicalScreenHeight");

				if (widthStr != null && !widthStr.isEmpty())
				{
					this.canvasWidth = Integer.parseInt(widthStr);
				}
				if (heightStr != null && !heightStr.isEmpty())
				{
					this.canvasHeight = Integer.parseInt(heightStr);
				}
			}
		}

		if (this.canvasWidth == 0 || this.canvasHeight == 0)
		{
			BufferedImage firstFrame = this.reader.read(0);
			this.canvasWidth = firstFrame.getWidth();
			this.canvasHeight = firstFrame.getHeight();
		}

		this.canvas = new BufferedImage(this.canvasWidth, this.canvasHeight, BufferedImage.TYPE_INT_ARGB);
		this.previousDisposalMethod = DISPOSAL_NONE;
		this.previousFrameBounds = null;
	}

	private boolean loadNextFrame()
	{
		int nextIndex;
		synchronized (this)
		{
			if (this.allFramesLoaded || this.disposed)
			{
				return false;
			}
			nextIndex = this.frames.size();
		}

		BufferedImage compositedFrame;
		int delay;
		try
		{
			BufferedImage rawFrame = this.reader.read(nextIndex);

			FrameMetadata metadata = this.getFrameMetadata(nextIndex);
			delay = metadata.delayMs;

			this.applyDisposalMethod();

			boolean needsSaveState = DISPOSAL_RESTORE_TO_PREVIOUS.equals(metadata.disposalMethod);
			if (needsSaveState)
			{
				this.savedCanvas = this.copyCanvas();
			}

			Graphics2D g = this.canvas.createGraphics();
			try
			{
				g.drawImage(rawFrame, metadata.x, metadata.y, null);
			}
			finally
			{
				g.dispose();
			}

			this.previousDisposalMethod = metadata.disposalMethod;
			this.previousFrameBounds = new Rectangle(metadata.x, metadata.y, rawFrame.getWidth(), rawFrame.getHeight());

			BufferedImage canvasCopy = this.copyCanvas();

			compositedFrame = canvasCopy;
			if (this.shouldResize)
			{
				compositedFrame = PluginUtils.resizeImage(canvasCopy, this.maxHeight);
			}
		}
		catch (IndexOutOfBoundsException e)
		{
			synchronized (this)
			{
				this.allFramesLoaded = true;
				this.closeReader();
			}
			return false;
		}
		catch (IOException e)
		{
			log.debug("Failed to load frame {}", nextIndex, e);
			synchronized (this)
			{
				this.allFramesLoaded = true;
				this.closeReader();
			}
			return false;
		}

		synchronized (this)
		{
			if (this.disposed)
			{
				return false;
			}
			this.frames.add(compositedFrame);
			this.frameDelays.add(delay);
		}

		return true;
	}

	private void applyDisposalMethod()
	{
		if (this.previousFrameBounds == null)
		{
			return;
		}

		switch (this.previousDisposalMethod)
		{
			case DISPOSAL_RESTORE_TO_BACKGROUND:
				this.clearPreviousFrameArea();
				break;
			case DISPOSAL_RESTORE_TO_PREVIOUS:
				this.restoreToPreviousCanvas();
				break;
			case DISPOSAL_NONE:
			case DISPOSAL_DO_NOT_DISPOSE:
			default:
				break;
		}
	}

	private void clearPreviousFrameArea()
	{
		Graphics2D g = this.canvas.createGraphics();
		try
		{
			g.setBackground(new Color(0, 0, 0, 0));
			g.clearRect(
				this.previousFrameBounds.x,
				this.previousFrameBounds.y,
				this.previousFrameBounds.width,
				this.previousFrameBounds.height
			);
		}
		finally
		{
			g.dispose();
		}
	}

	private void restoreToPreviousCanvas()
	{
		if (this.savedCanvas == null)
		{
			return;
		}

		Graphics2D g = this.canvas.createGraphics();
		try
		{
			g.setBackground(new Color(0, 0, 0, 0));
			g.clearRect(0, 0, this.canvasWidth, this.canvasHeight);
			g.drawImage(this.savedCanvas, 0, 0, null);
		}
		finally
		{
			g.dispose();
		}
	}

	private BufferedImage copyCanvas()
	{
		BufferedImage copy = new BufferedImage(this.canvasWidth, this.canvasHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = copy.createGraphics();
		try
		{
			g.drawImage(this.canvas, 0, 0, null);
		}
		finally
		{
			g.dispose();
		}
		return copy;
	}

	private FrameMetadata getFrameMetadata(int frameIndex)
	{
		FrameMetadata metadata = new FrameMetadata();
		metadata.x = 0;
		metadata.y = 0;
		metadata.delayMs = DEFAULT_FRAME_DELAY_MS;
		metadata.disposalMethod = DISPOSAL_NONE;

		try
		{
			IIOMetadata frameMetadata = this.reader.getImageMetadata(frameIndex);
			if (frameMetadata == null)
			{
				return metadata;
			}

			String formatName = frameMetadata.getNativeMetadataFormatName();
			IIOMetadataNode root = (IIOMetadataNode) frameMetadata.getAsTree(formatName);

			this.parseImageDescriptor(root, metadata);
			this.parseGraphicControlExtension(root, metadata);
		}
		catch (Exception e)
		{
			log.debug("Failed to get frame metadata for frame {}", frameIndex, e);
		}

		return metadata;
	}

	private void parseImageDescriptor(IIOMetadataNode root, FrameMetadata metadata)
	{
		NodeList imageDescList = root.getElementsByTagName("ImageDescriptor");
		if (imageDescList.getLength() == 0)
		{
			return;
		}

		IIOMetadataNode imageDesc = (IIOMetadataNode) imageDescList.item(0);
		metadata.x = this.parseIntAttribute(imageDesc, "imageLeftPosition", 0);
		metadata.y = this.parseIntAttribute(imageDesc, "imageTopPosition", 0);
	}

	private void parseGraphicControlExtension(IIOMetadataNode root, FrameMetadata metadata)
	{
		NodeList gceList = root.getElementsByTagName("GraphicControlExtension");
		if (gceList.getLength() == 0)
		{
			return;
		}

		IIOMetadataNode gce = (IIOMetadataNode) gceList.item(0);

		int delayHundredths = this.parseIntAttribute(gce, "delayTime", 0);
		int delayMs = delayHundredths * 10;
		metadata.delayMs = delayMs > 0 ? delayMs : DEFAULT_FRAME_DELAY_MS;

		String disposalStr = gce.getAttribute("disposalMethod");
		if (disposalStr != null && !disposalStr.isEmpty())
		{
			metadata.disposalMethod = disposalStr;
		}
	}

	private int parseIntAttribute(IIOMetadataNode node, String attributeName, int defaultValue)
	{
		String value = node.getAttribute(attributeName);
		if (value == null || value.isEmpty())
		{
			return defaultValue;
		}
		return Integer.parseInt(value);
	}

	private int calculateCurrentFrameIndex()
	{
		int loadedCount = this.frames.size();
		if (loadedCount == 0)
		{
			return 0;
		}

		if (loadedCount == 1)
		{
			return 0;
		}

		long now = System.currentTimeMillis();
		int currentDelay = this.frameDelays.get(this.currentFrameIndex);
		long elapsed = now - this.lastFrameChangeTime;

		while (elapsed >= currentDelay)
		{
			elapsed -= currentDelay;
			int nextIndex = this.currentFrameIndex + 1;

			if (nextIndex >= loadedCount)
			{
				if (this.allFramesLoaded)
				{
					nextIndex = 0;
				}
				else
				{
					this.lastFrameChangeTime = now;
					return this.currentFrameIndex;
				}
			}

			this.currentFrameIndex = nextIndex;
			this.lastFrameChangeTime = now - elapsed;
			currentDelay = this.frameDelays.get(this.currentFrameIndex);
		}

		return this.currentFrameIndex;
	}

	private void closeReader()
	{
		if (this.reader != null)
		{
			this.reader.dispose();
			this.reader = null;
		}

		if (this.imageStream != null)
		{
			try
			{
				this.imageStream.close();
			}
			catch (IOException e)
			{
				log.debug("Error closing image stream", e);
			}
			this.imageStream = null;
		}

		this.canvas = null;
		this.savedCanvas = null;
		this.gifData = null;
	}

	private static class FrameMetadata
	{
		int x;
		int y;
		int delayMs;
		String disposalMethod;
	}
}
