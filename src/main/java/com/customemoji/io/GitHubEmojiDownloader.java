package com.customemoji.io;

import javax.inject.Inject;

import com.customemoji.CustomEmojiPlugin;
import com.customemoji.event.LoadingProgress;
import com.customemoji.event.LoadingProgress.LoadingStage;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import net.runelite.client.eventbus.EventBus;

@Slf4j
public class GitHubEmojiDownloader
{
	// Only these two GitHub domains are ever contacted - user input is restricted to "owner/repo" format
	private static final HttpUrl GITHUB_API_BASE = HttpUrl.parse("https://api.github.com");
	private static final HttpUrl GITHUB_RAW_BASE = HttpUrl.parse("https://raw.githubusercontent.com");
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif");
	private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

	public static final File GITHUB_PACK_FOLDER = new File(CustomEmojiPlugin.EMOJIS_FOLDER, "github-pack");
	private static final File METADATA_FILE = new File(GITHUB_PACK_FOLDER, "github-download.json");

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private EventBus eventBus;

	private ScheduledExecutorService executor;
	public final AtomicBoolean isDownloading = new AtomicBoolean(false);
	private final AtomicReference<Future<?>> currentTask = new AtomicReference<>();
	private volatile boolean cancelled = false;

	@Value
	public static class RepoConfig
	{
		String owner;
		String repo;
		String branch;
	}

	@Value
	public static class DownloadMetadata
	{
		String repoIdentifier;
		String branch;
		Map<String, String> files;
		long lastDownload;
	}

	@Value
	public static class TreeEntry
	{
		String path;
		String sha;
		long size;
	}

	@Value
	public static class DownloadResult
	{
		boolean success;
		int downloaded;
		int failed;
		int deleted;
		String errorMessage;
		List<String> changedEmojiNames;

		public boolean hasChanges()
		{
			return this.downloaded > 0 || this.deleted > 0;
		}

		public String formatMessage()
		{
			if (!this.success)
			{
				return "Custom Emoji: <col=FF0000>" + this.errorMessage;
			}

			List<String> parts = new ArrayList<>();
			if (this.downloaded > 0)
			{
				parts.add(String.format("Downloaded %d emoji%s", this.downloaded, this.downloaded == 1 ? "" : "s"));
			}
			if (this.deleted > 0)
			{
				parts.add(String.format("Removed %d emoji%s", this.deleted, this.deleted == 1 ? "" : "s"));
			}
			if (this.failed > 0)
			{
				parts.add(String.format("<col=FF6600>(%d failed)", this.failed));
			}

			String message = parts.isEmpty() ? "Already up to date" : String.join(", ", parts);
			return "Custom Emoji: <col=00FF00>" + message;
		}

		public String formatPanelMessage()
		{
			if (!this.success)
			{
				return this.errorMessage;
			}

			List<String> parts = new ArrayList<>();
			if (this.downloaded > 0)
			{
				parts.add(String.format("Downloaded %d emoji%s", this.downloaded, this.downloaded == 1 ? "" : "s"));
			}
			if (this.deleted > 0)
			{
				parts.add(String.format("Removed %d emoji%s", this.deleted, this.deleted == 1 ? "" : "s"));
			}
			if (this.failed > 0)
			{
				parts.add(String.format("(%d failed)", this.failed));
			}

			return parts.isEmpty() ? "Already up to date" : String.join(", ", parts);
		}
	}


	public void startUp()
	{
		this.executor = Executors.newSingleThreadScheduledExecutor(r ->
		{
			Thread t = new Thread(r, "GitHubEmoji-Downloader");
			t.setDaemon(true);
			return t;
		});
	}

	public RepoConfig parseRepoIdentifier(String input)
	{
		if (input == null || input.trim().isEmpty())
		{
			return null;
		}

		// Reject URLs - only "owner/repo" or "owner/repo/tree/branch" allowed
		String trimmed = input.trim();
		boolean looksLikeUrl = trimmed.contains("://") || trimmed.startsWith("http") || trimmed.startsWith("www.");
		if (looksLikeUrl)
		{
			return null;
		}

		String[] parts = trimmed.split("/");

		if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty())
		{
			return null;
		}

		if (parts.length == 2)
		{
			return new RepoConfig(parts[0], parts[1], null);
		}

		if (parts.length == 4 && parts[2].equals("tree") && !parts[3].isEmpty())
		{
			return new RepoConfig(parts[0], parts[1], parts[3]);
		}

		return null;
	}

	public void downloadEmojis(String repoIdentifier, Consumer<DownloadResult> onComplete)
	{
		this.downloadEmojis(repoIdentifier, null, onComplete);
	}

	public void downloadEmojis(String repoIdentifier, Runnable onStarted, Consumer<DownloadResult> onComplete)
	{
		this.cancelCurrentDownload();

		this.cancelled = false;
		this.isDownloading.set(true);

		Future<?> task = this.executor.submit(() ->
		{
			try
			{
				DownloadResult result = this.performDownload(repoIdentifier, onStarted);
				if (!this.cancelled)
				{
					onComplete.accept(result);
				}
			}
			catch (Exception e)
			{
				if (!this.cancelled)
				{
					log.error("GitHub download failed", e);
					onComplete.accept(new DownloadResult(false, 0, 0, 0, e.getMessage(), List.of()));
				}
			}
			finally
			{
				this.isDownloading.set(false);
				this.eventBus.post(new LoadingProgress(LoadingStage.COMPLETE, 0, 0, null));
				this.currentTask.set(null);
			}
		});

		this.currentTask.set(task);
	}

	private void cancelCurrentDownload()
	{
		this.cancelled = true;
		this.eventBus.post(new LoadingProgress(LoadingStage.COMPLETE, 0, 0, null));
		Future<?> task = this.currentTask.getAndSet(null);
		if (task != null)
		{
			task.cancel(true);
		}
	}

	public void shutDown()
	{
		this.cancelCurrentDownload();
		if (this.executor != null)
		{
			this.executor.shutdownNow();
			this.executor = null;
		}
	}

	private DownloadResult cancelledResult()
	{
		return new DownloadResult(false, 0, 0, 0, "Download cancelled", List.of());
	}

	private DownloadResult performDownload(String repoIdentifier, Runnable onStarted)
	{
		this.eventBus.post(new LoadingProgress(LoadingStage.FETCHING_METADATA, 0, 0, null));

		RepoConfig config = this.parseRepoIdentifier(repoIdentifier);
		if (config == null)
		{
			return new DownloadResult(false, 0, 0, 0, "Invalid format. Use: user/repo or user/repo/tree/branch", List.of());
		}

		if (this.cancelled)
		{
			return this.cancelledResult();
		}

		String repoPath = config.getOwner() + "/" + config.getRepo();
		String branch = config.getBranch() != null ? config.getBranch() : this.fetchDefaultBranch(config);
		if (this.cancelled)
		{
			return this.cancelledResult();
		}
		if (branch == null)
		{
			return new DownloadResult(false, 0, 0, 0, "Repository not found: " + repoPath, List.of());
		}

		List<TreeEntry> remoteFiles = this.fetchRepoTree(config, branch);
		if (this.cancelled)
		{
			return this.cancelledResult();
		}
		if (remoteFiles == null)
		{
			String errorMessage = config.getBranch() != null
				? "Branch '" + branch + "' not found in " + repoPath
				: "Could not access repository: " + repoPath;
			return new DownloadResult(false, 0, 0, 0, errorMessage, List.of());
		}

		if (onStarted != null)
		{
			onStarted.run();
		}

		GITHUB_PACK_FOLDER.mkdirs();

		DownloadMetadata localMetadata = this.loadMetadata();
		boolean repoChanged = localMetadata != null && !repoIdentifier.equals(localMetadata.getRepoIdentifier());

		if (repoChanged)
		{
			this.clearGitHubPackFolder();
		}

		Map<String, String> localFiles = repoChanged || localMetadata == null ? new HashMap<>() : localMetadata.getFiles();

		Set<String> remoteFilePaths = new HashSet<>();
		List<TreeEntry> toDownload = new ArrayList<>();

		for (TreeEntry entry : remoteFiles)
		{
			remoteFilePaths.add(entry.getPath());
			String localSha = localFiles.get(entry.getPath());
			File localFile = this.toLocalFile(entry.getPath());

			if (localFile.exists())
			{
				boolean shaMismatch = localSha == null || !localSha.equals(entry.getSha());
				boolean sizeMismatch = localFile.length() != entry.getSize();

				if (shaMismatch || sizeMismatch)
				{
					try
					{
						Files.delete(localFile.toPath());
					}
					catch (IOException e)
					{
						log.warn("Failed to delete mismatched file: {}", localFile.getPath());
					}
				}
			}

			boolean shaChanged = localSha == null || !localSha.equals(entry.getSha());
			boolean fileMissing = !localFile.exists();

			if (shaChanged || fileMissing)
			{
				toDownload.add(entry);
			}
		}

		this.eventBus.post(new LoadingProgress(LoadingStage.DELETING_OLD, 0, 0, null));
		int deleted = this.deleteRemovedFiles(localFiles.keySet(), remoteFilePaths);

		int downloaded = 0;
		int failed = 0;
		int fileIndex = 0;
		int totalToDownload = toDownload.size();
		Map<String, String> newFileHashes = new HashMap<>();
		List<String> changedEmojiNames = new ArrayList<>();

		for (TreeEntry entry : toDownload)
		{
			if (this.cancelled)
			{
				break;
			}

			fileIndex++;
			String fileName = this.extractFileName(entry.getPath());
			this.eventBus.post(new LoadingProgress(LoadingStage.DOWNLOADING, totalToDownload, fileIndex, fileName));

			if (this.downloadFile(config.getOwner(), config.getRepo(), branch, entry))
			{
				downloaded++;
				newFileHashes.put(entry.getPath(), entry.getSha());
				String emojiName = this.extractEmojiName(fileName);
				changedEmojiNames.add(emojiName);
			}
			else
			{
				failed++;
			}
		}

		if (this.cancelled)
		{
			return this.cancelledResult();
		}

		Map<String, String> allFiles = new HashMap<>(localFiles);
		allFiles.keySet().retainAll(remoteFilePaths);
		allFiles.putAll(newFileHashes);

		this.saveMetadata(new DownloadMetadata(repoIdentifier, branch, allFiles, System.currentTimeMillis()));

		return new DownloadResult(true, downloaded, failed, deleted, null, changedEmojiNames);
	}

	private String fetchDefaultBranch(RepoConfig config)
	{
		HttpUrl url = GITHUB_API_BASE.newBuilder()
			.addPathSegment("repos")
			.addPathSegment(config.getOwner())
			.addPathSegment(config.getRepo())
			.build();

		JsonObject json = this.fetchJson(url);
		return json != null && json.has("default_branch") ? json.get("default_branch").getAsString() : null;
	}

	private List<TreeEntry> fetchRepoTree(RepoConfig config, String branch)
	{
		HttpUrl url = GITHUB_API_BASE.newBuilder()
			.addPathSegment("repos")
			.addPathSegment(config.getOwner())
			.addPathSegment(config.getRepo())
			.addPathSegment("git")
			.addPathSegment("trees")
			.addPathSegment(branch)
			.addQueryParameter("recursive", "1")
			.build();

		JsonObject json = this.fetchJson(url);
		if (json == null || !json.has("tree"))
		{
			return null;
		}

		List<TreeEntry> entries = new ArrayList<>();
		JsonArray treeArray = json.getAsJsonArray("tree");

		for (JsonElement element : treeArray)
		{
			JsonObject entry = element.getAsJsonObject();
			String type = entry.get("type").getAsString();
			String path = entry.get("path").getAsString();
			String sha = entry.get("sha").getAsString();
			long size = entry.has("size") ? entry.get("size").getAsLong() : 0;

			boolean isFile = "blob".equals(type);
			boolean validFile = isFile && this.isAllowedExtension(path) && this.isPathSafe(path) && size <= MAX_FILE_SIZE_BYTES;
			if (validFile)
			{
				entries.add(new TreeEntry(path, sha, size));
			}
		}

		return entries;
	}

	private JsonObject fetchJson(HttpUrl url)
	{
		Request request = new Request.Builder()
			.url(url)
			.header("Accept", "application/vnd.github.v3+json")
			.build();

		try (Response response = this.okHttpClient.newCall(request).execute())
		{
			ResponseBody body = response.body();
			if (!response.isSuccessful() || body == null)
			{
				log.error("GitHub API error: {}", response.code());
				return null;
			}
			return this.gson.fromJson(body.string(), JsonObject.class);
		}
		catch (IOException e)
		{
			log.error("GitHub API request failed", e);
			return null;
		}
	}

	private boolean downloadFile(String owner, String repo, String branch, TreeEntry entry)
	{
		HttpUrl.Builder urlBuilder = GITHUB_RAW_BASE.newBuilder()
			.addPathSegment(owner)
			.addPathSegment(repo)
			.addPathSegment(branch);

		for (String segment : entry.getPath().split("/"))
		{
			urlBuilder.addPathSegment(segment);
		}

		File destination = this.toLocalFile(entry.getPath());
		if (!this.isDestinationSafe(destination))
		{
			return false;
		}

		File parentDir = destination.getParentFile();
		if (parentDir != null)
		{
			parentDir.mkdirs();
		}

		Request request = new Request.Builder().url(urlBuilder.build()).build();

		try (Response response = this.okHttpClient.newCall(request).execute())
		{
			ResponseBody body = response.body();
			if (!response.isSuccessful() || body == null)
			{
				return false;
			}

			if (destination.exists())
			{
				Files.delete(destination.toPath());
			}

			Files.copy(new BufferedInputStream(body.byteStream()), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);

			long actualSize = destination.length();
			long entrySize = entry.getSize();
			if (actualSize != entrySize)
			{
				log.warn("Size mismatch for {}: expected {} but got {}", entry.getPath(), entry.getSize(), actualSize);
				Files.delete(destination.toPath());
				return false;
			}

			return true;
		}
		catch (IOException e)
		{
			log.error("Download failed: {}", entry.getPath(), e);
			return false;
		}
	}

	private boolean isAllowedExtension(String path)
	{
		String lower = path.toLowerCase();
		return ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
	}

	private boolean isPathSafe(String path)
	{
		return path != null && !path.isEmpty() && !path.contains("..") && !path.startsWith("/") && !path.startsWith("\\");
	}

	private String extractFileName(String path)
	{
		int lastSlash = path.lastIndexOf('/');
		return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
	}

	private String extractEmojiName(String fileName)
	{
		int lastDot = fileName.lastIndexOf('.');
		return lastDot >= 0 ? fileName.substring(0, lastDot).toLowerCase() : fileName.toLowerCase();
	}

	private boolean isDestinationSafe(File destination)
	{
		try
		{
			return destination.getCanonicalPath().startsWith(GITHUB_PACK_FOLDER.getCanonicalPath());
		}
		catch (IOException e)
		{
			return false;
		}
	}

	private File toLocalFile(String remotePath)
	{
		return new File(GITHUB_PACK_FOLDER, remotePath.replace("/", File.separator));
	}

	private int deleteRemovedFiles(Set<String> localPaths, Set<String> remotePaths)
	{
		int deleted = 0;
		for (String path : localPaths)
		{
			boolean stillExistsRemotely = remotePaths.contains(path);
			if (stillExistsRemotely)
			{
				continue;
			}

			File fileToDelete = this.toLocalFile(path);
			boolean safeToDelete = this.isDestinationSafe(fileToDelete);
			if (safeToDelete)
			{
				try
				{
					Files.deleteIfExists(fileToDelete.toPath());
					deleted++;
				}
				catch (IOException e)
				{
					log.warn("Failed to delete: {}", path);
				}
			}
			else
			{
				log.warn("Skipping unsafe delete path: {}", path);
			}
		}
		return deleted;
	}

	private void clearGitHubPackFolder()
	{
		File[] files = GITHUB_PACK_FOLDER.listFiles();
		if (files == null)
		{
			return;
		}

		for (File file : files)
		{
			this.deleteRecursively(file);
		}
	}

	private void deleteRecursively(File file)
	{
		if (file.isDirectory())
		{
			File[] children = file.listFiles();
			if (children != null)
			{
				for (File child : children)
				{
					this.deleteRecursively(child);
				}
			}
		}

		try
		{
			Files.deleteIfExists(file.toPath());
		}
		catch (IOException e)
		{
			log.warn("Failed to delete: {}", file.getPath());
		}
	}

	public boolean hasDownloadedBefore()
	{
		return METADATA_FILE.exists();
	}

	private DownloadMetadata loadMetadata()
	{
		if (!METADATA_FILE.exists())
		{
			return null;
		}

		try
		{
			String json = Files.readString(METADATA_FILE.toPath());
			return this.gson.fromJson(json, DownloadMetadata.class);
		}
		catch (Exception e)
		{
			log.error("Failed to load metadata", e);
			return null;
		}
	}

	private void saveMetadata(DownloadMetadata metadata)
	{
		try
		{
			Files.writeString(METADATA_FILE.toPath(), this.gson.toJson(metadata));
		}
		catch (IOException e)
		{
			log.error("Failed to save metadata", e);
		}
	}
}
