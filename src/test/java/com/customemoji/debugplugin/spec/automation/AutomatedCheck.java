package com.customemoji.debugplugin.spec.automation;

public interface AutomatedCheck
{
    boolean verify();

    String getResultDescription();

    default String getPassDescription()
    {
        return "Check passed";
    }

    default String getFailDescription()
    {
        return "Check failed";
    }
}
