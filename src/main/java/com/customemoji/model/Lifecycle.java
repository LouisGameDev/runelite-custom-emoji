package com.customemoji.model;

import com.customemoji.CustomEmojiConfig;

public interface Lifecycle
{
	void startUp();
	void shutDown();
	boolean isEnabled(CustomEmojiConfig config);
}
