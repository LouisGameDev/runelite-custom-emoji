package com.customemoji.debugplugin.spec.automation.checks;

import java.util.Set;
import java.util.function.Supplier;

import com.customemoji.debugplugin.spec.automation.AutomatedCheck;
import com.customemoji.PluginUtils;

public class DisabledEmojiCheck implements AutomatedCheck
{
    private final String emojiName;
    private final boolean shouldBeDisabled;
    private final Supplier<String> disabledEmojisConfigSupplier;

    public DisabledEmojiCheck(String emojiName, boolean shouldBeDisabled, Supplier<String> disabledEmojisConfigSupplier)
    {
        this.emojiName = emojiName.toLowerCase();
        this.shouldBeDisabled = shouldBeDisabled;
        this.disabledEmojisConfigSupplier = disabledEmojisConfigSupplier;
    }

    @Override
    public boolean verify()
    {
        Set<String> disabledEmojis = PluginUtils.parseDisabledEmojis(this.disabledEmojisConfigSupplier.get());
        boolean isDisabled = disabledEmojis.contains(this.emojiName);
        return isDisabled == this.shouldBeDisabled;
    }

    @Override
    public String getResultDescription()
    {
        Set<String> disabledEmojis = PluginUtils.parseDisabledEmojis(this.disabledEmojisConfigSupplier.get());
        boolean isDisabled = disabledEmojis.contains(this.emojiName);
        return "Emoji '" + this.emojiName + "' is " + (isDisabled ? "disabled" : "enabled");
    }

    @Override
    public String getPassDescription()
    {
        return "Emoji '" + this.emojiName + "' is correctly " + (this.shouldBeDisabled ? "disabled" : "enabled");
    }

    @Override
    public String getFailDescription()
    {
        return "Emoji '" + this.emojiName + "' should be " + (this.shouldBeDisabled ? "disabled" : "enabled");
    }
}
