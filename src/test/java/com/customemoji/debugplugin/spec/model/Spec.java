package com.customemoji.debugplugin.spec.model;

import java.util.List;

public class Spec
{
    private final String id;
    private final SpecCategory category;
    private final String name;
    private final String description;
    private final List<String> steps;
    private final String expectedResult;
    private final String automatedCheckClass;
    private final String automatedCheckParams;

    private Spec(Builder builder)
    {
        this.id = builder.id;
        this.category = builder.category;
        this.name = builder.name;
        this.description = builder.description;
        this.steps = builder.steps;
        this.expectedResult = builder.expectedResult;
        this.automatedCheckClass = builder.automatedCheckClass;
        this.automatedCheckParams = builder.automatedCheckParams;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public String getId()
    {
        return this.id;
    }

    public SpecCategory getCategory()
    {
        return this.category;
    }

    public String getName()
    {
        return this.name;
    }

    public String getDescription()
    {
        return this.description;
    }

    public List<String> getSteps()
    {
        return this.steps;
    }

    public String getExpectedResult()
    {
        return this.expectedResult;
    }

    public String getAutomatedCheckClass()
    {
        return this.automatedCheckClass;
    }

    public String getAutomatedCheckParams()
    {
        return this.automatedCheckParams;
    }

    public boolean hasAutomatedCheck()
    {
        return this.automatedCheckClass != null && !this.automatedCheckClass.isEmpty();
    }

    public String getFullId()
    {
        return this.category.getPrefix() + "_" + this.id;
    }

    @Override
    public String toString()
    {
        return this.getFullId() + ": " + this.name;
    }

    public static class Builder
    {
        private String id;
        private SpecCategory category;
        private String name;
        private String description;
        private List<String> steps;
        private String expectedResult;
        private String automatedCheckClass;
        private String automatedCheckParams;

        public Builder id(String id)
        {
            this.id = id;
            return this;
        }

        public Builder category(SpecCategory category)
        {
            this.category = category;
            return this;
        }

        public Builder name(String name)
        {
            this.name = name;
            return this;
        }

        public Builder description(String description)
        {
            this.description = description;
            return this;
        }

        public Builder steps(List<String> steps)
        {
            this.steps = steps;
            return this;
        }

        public Builder expectedResult(String expectedResult)
        {
            this.expectedResult = expectedResult;
            return this;
        }

        public Builder automatedCheckClass(String automatedCheckClass)
        {
            this.automatedCheckClass = automatedCheckClass;
            return this;
        }

        public Builder automatedCheckParams(String automatedCheckParams)
        {
            this.automatedCheckParams = automatedCheckParams;
            return this;
        }

        public Spec build()
        {
            return new Spec(this);
        }
    }
}
