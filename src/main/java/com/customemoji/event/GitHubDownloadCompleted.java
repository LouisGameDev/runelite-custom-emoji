package com.customemoji.event;

import com.customemoji.io.GitHubEmojiDownloader;
import lombok.Value;

@Value
public class GitHubDownloadCompleted
{
	GitHubEmojiDownloader.DownloadResult result;
	boolean hadPreviousDownload;
}
