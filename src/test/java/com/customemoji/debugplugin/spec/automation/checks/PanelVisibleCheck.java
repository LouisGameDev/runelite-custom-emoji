package com.customemoji.debugplugin.spec.automation.checks;

import java.util.function.BooleanSupplier;

import com.customemoji.debugplugin.spec.automation.AutomatedCheck;

public class PanelVisibleCheck implements AutomatedCheck
{
    private final BooleanSupplier panelVisibleSupplier;

    public PanelVisibleCheck(BooleanSupplier panelVisibleSupplier)
    {
        this.panelVisibleSupplier = panelVisibleSupplier;
    }

    @Override
    public boolean verify()
    {
        return this.panelVisibleSupplier.getAsBoolean();
    }

    @Override
    public String getResultDescription()
    {
        return this.panelVisibleSupplier.getAsBoolean()
            ? "Emoji panel is visible in sidebar"
            : "Emoji panel is not visible";
    }

    @Override
    public String getPassDescription()
    {
        return "Panel visible in sidebar";
    }

    @Override
    public String getFailDescription()
    {
        return "Panel not found in sidebar";
    }
}
