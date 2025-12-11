package com.customemoji.debugplugin.spec.automation.checks;

import java.util.Set;
import java.util.function.Supplier;

import com.customemoji.debugplugin.spec.automation.AutomatedCheck;
import com.customemoji.util.PluginUtils;

public class ResizingDisabledCheck implements AutomatedCheck
{
    private final String emojiName;
    private final boolean shouldBeDisabled;
    private final Supplier<String> resizingDisabledConfigSupplier;

    public ResizingDisabledCheck(String emojiName, boolean shouldBeDisabled, Supplier<String> resizingDisabledConfigSupplier)
    {
        this.emojiName = emojiName.toLowerCase();
        this.shouldBeDisabled = shouldBeDisabled;
        this.resizingDisabledConfigSupplier = resizingDisabledConfigSupplier;
    }

    @Override
    public boolean verify()
    {
        Set<String> resizingDisabledEmojis = PluginUtils.parseResizingDisabledEmojis(this.resizingDisabledConfigSupplier.get());
        boolean isResizingDisabled = resizingDisabledEmojis.contains(this.emojiName);
        return isResizingDisabled == this.shouldBeDisabled;
    }

    @Override
    public String getResultDescription()
    {
        Set<String> resizingDisabledEmojis = PluginUtils.parseResizingDisabledEmojis(this.resizingDisabledConfigSupplier.get());
        boolean isResizingDisabled = resizingDisabledEmojis.contains(this.emojiName);
        return "Resizing for '" + this.emojiName + "' is " + (isResizingDisabled ? "disabled" : "enabled");
    }

    @Override
    public String getPassDescription()
    {
        return "Resizing for '" + this.emojiName + "' is correctly " + (this.shouldBeDisabled ? "disabled" : "enabled");
    }

    @Override
    public String getFailDescription()
    {
        return "Resizing for '" + this.emojiName + "' should be " + (this.shouldBeDisabled ? "disabled" : "enabled");
    }
}
