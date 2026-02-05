package com.customemoji.event;

import lombok.Getter;
import lombok.Value;

@Value
public class LoadingProgress
{
    LoadingStage stage;
    int totalFiles;
    int currentFileIndex;
    String currentFileName;

    public double getPercentage()
    {
        if (this.totalFiles == 0)
        {
            return 0.0;
        }
        return (double) this.currentFileIndex / this.totalFiles;
    }

    @Getter
    public enum LoadingStage
    {
        FETCHING_METADATA("Fetching repository info..."),
        DELETING_OLD("Removing old files..."),
        DOWNLOADING("Downloading..."),
        LOADING_IMAGES("Loading images..."),
        REGISTERING_EMOJIS("Registering emojis..."),
        COMPLETE("Complete");

        private final String displayText;

        LoadingStage(String displayText)
        {
            this.displayText = displayText;
        }
    }
}
