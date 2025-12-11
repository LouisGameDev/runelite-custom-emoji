package com.customemoji.debugplugin.spec.automation.checks;

import java.util.function.BooleanSupplier;

import com.customemoji.debugplugin.spec.automation.AutomatedCheck;

public class ConfigEnabledCheck implements AutomatedCheck
{
    private final String configName;
    private final BooleanSupplier configSupplier;
    private final boolean expectedValue;

    public ConfigEnabledCheck(String configName, BooleanSupplier configSupplier, boolean expectedValue)
    {
        this.configName = configName;
        this.configSupplier = configSupplier;
        this.expectedValue = expectedValue;
    }

    public ConfigEnabledCheck(String configName, BooleanSupplier configSupplier)
    {
        this(configName, configSupplier, true);
    }

    @Override
    public boolean verify()
    {
        return this.configSupplier.getAsBoolean() == this.expectedValue;
    }

    @Override
    public String getResultDescription()
    {
        boolean currentValue = this.configSupplier.getAsBoolean();
        return this.configName + " is " + (currentValue ? "enabled" : "disabled");
    }

    @Override
    public String getPassDescription()
    {
        return this.configName + " is correctly " + (this.expectedValue ? "enabled" : "disabled");
    }

    @Override
    public String getFailDescription()
    {
        return this.configName + " should be " + (this.expectedValue ? "enabled" : "disabled");
    }
}
