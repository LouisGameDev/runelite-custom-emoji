package com.customemoji.debugplugin.spec.model;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SpecSession
{
    private final Map<String, SpecResult> results = new LinkedHashMap<>();
    private final Instant startTime;
    private String currentSpecId;
    private SpecCategory currentCategory;

    public SpecSession()
    {
        this.startTime = Instant.now();
        this.currentCategory = SpecCategory.EMOJI_LOADING;
    }

    public Map<String, SpecResult> getResults()
    {
        return this.results;
    }

    public Instant getStartTime()
    {
        return this.startTime;
    }

    public String getCurrentSpecId()
    {
        return this.currentSpecId;
    }

    public void setCurrentSpecId(String currentSpecId)
    {
        this.currentSpecId = currentSpecId;
    }

    public SpecCategory getCurrentCategory()
    {
        return this.currentCategory;
    }

    public void setCurrentCategory(SpecCategory currentCategory)
    {
        this.currentCategory = currentCategory;
    }

    public void setResult(String specId, SpecResult result)
    {
        this.results.put(specId, result);
    }

    public SpecResult getResult(String specId)
    {
        return this.results.getOrDefault(specId, SpecResult.NOT_TESTED);
    }

    public int getTestedCount()
    {
        return (int) this.results.values().stream()
            .filter(SpecResult::isTested)
            .count();
    }

    public int getPassedCount()
    {
        return (int) this.results.values().stream()
            .filter(SpecResult::isPassed)
            .count();
    }

    public int getFailedCount()
    {
        return (int) this.results.values().stream()
            .filter(SpecResult::isFailed)
            .count();
    }

    public int getSkippedCount()
    {
        return (int) this.results.values().stream()
            .filter(r -> r == SpecResult.SKIPPED)
            .count();
    }

    public Duration getElapsedTime()
    {
        return Duration.between(this.startTime, Instant.now());
    }

    public void initializeSpecs(List<Spec> allSpecs)
    {
        for (Spec spec : allSpecs)
        {
            if (!this.results.containsKey(spec.getFullId()))
            {
                this.results.put(spec.getFullId(), SpecResult.NOT_TESTED);
            }
        }
    }
}
