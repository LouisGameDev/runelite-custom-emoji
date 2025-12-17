package com.customemoji.debugplugin.spec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.customemoji.debugplugin.spec.automation.AutomatedCheck;
import com.customemoji.debugplugin.spec.automation.checks.AnimationActiveCheck;
import com.customemoji.debugplugin.spec.automation.checks.EmojiLoadedCheck;
import com.customemoji.debugplugin.spec.automation.checks.EmojiStateCheck;
import com.customemoji.debugplugin.spec.automation.checks.SoundojiLoadedCheck;
import com.customemoji.debugplugin.spec.data.SpecDefinitions;
import com.customemoji.debugplugin.spec.model.Spec;
import com.customemoji.debugplugin.spec.model.SpecResult;
import com.customemoji.debugplugin.spec.model.SpecSession;
import com.customemoji.debugplugin.spec.ui.SpecValidationFrame;

import net.runelite.client.eventbus.EventBus;

@Singleton
public class SpecValidationManager
{
    private static final Logger log = LoggerFactory.getLogger(SpecValidationManager.class);

    private final EventBus eventBus;
    private final EmojiLoadedCheck emojiLoadedCheck;
    private final AnimationActiveCheck animationActiveCheck;
    private final SoundojiLoadedCheck soundojiLoadedCheck;
    private final Map<String, AutomatedCheck> automatedChecks;
    private final List<EmojiStateCheck> emojiStateChecks;

    private SpecSession session;
    private SpecValidationFrame frame;
    private boolean started = false;

    @Inject
    public SpecValidationManager(EventBus eventBus)
    {
        this.eventBus = eventBus;
        this.emojiLoadedCheck = new EmojiLoadedCheck(eventBus);
        this.animationActiveCheck = new AnimationActiveCheck(eventBus);
        this.soundojiLoadedCheck = new SoundojiLoadedCheck(eventBus);
        this.automatedChecks = new HashMap<>();
        this.emojiStateChecks = new ArrayList<>();

        this.automatedChecks.put("EmojiLoadedCheck", this.emojiLoadedCheck);
        this.automatedChecks.put("AnimationActiveCheck", this.animationActiveCheck);
        this.automatedChecks.put("SoundojiLoadedCheck", this.soundojiLoadedCheck);
    }

    public SpecSession getSession()
    {
        return this.session;
    }

    public SpecValidationFrame getFrame()
    {
        return this.frame;
    }

    public void startUp()
    {
        if (this.started)
        {
            return;
        }

        this.started = true;
        this.emojiLoadedCheck.register();
        this.animationActiveCheck.register();
        this.soundojiLoadedCheck.register();

        this.session = new SpecSession();
        this.session.initializeSpecs(SpecDefinitions.getAllSpecs());
    }

    public void shutDown()
    {
        if (!this.started)
        {
            return;
        }

        this.started = false;
        this.emojiLoadedCheck.unregister();
        this.animationActiveCheck.unregister();
        this.soundojiLoadedCheck.unregister();

        for (EmojiStateCheck check : this.emojiStateChecks)
        {
            check.unregister();
        }
        this.emojiStateChecks.clear();

        if (this.frame != null)
        {
            this.frame.close();
            this.frame.dispose();
            this.frame = null;
        }
    }

    public void openFrame()
    {
        if (!this.started)
        {
            this.startUp();
        }

        if (this.frame == null)
        {
            this.frame = new SpecValidationFrame(this);
        }

        this.frame.open();
    }

    public void closeFrame()
    {
        if (this.frame != null)
        {
            this.frame.close();
        }
    }

    public void toggleFrame()
    {
        if (this.frame != null && this.frame.isVisible())
        {
            this.closeFrame();
        }
        else
        {
            this.openFrame();
        }
    }

    public void runAllAutomatedChecks()
    {
        List<Spec> allSpecs = SpecDefinitions.getAllSpecs();

        for (Spec spec : allSpecs)
        {
            if (!spec.hasAutomatedCheck())
            {
                continue;
            }

            String checkClassName = spec.getAutomatedCheckClass();
            String checkParams = spec.getAutomatedCheckParams();
            AutomatedCheck check;

            boolean hasParams = checkParams != null && !checkParams.isEmpty();
            if (hasParams)
            {
                check = this.createParameterizedCheck(checkClassName, checkParams);
            }
            else
            {
                check = this.automatedChecks.get(checkClassName);
            }

            if (check == null)
            {
                log.warn("No automated check found for class: {}", checkClassName);
                continue;
            }

            try
            {
                boolean passed = check.verify();
                SpecResult result = passed ? SpecResult.AUTO_PASSED : SpecResult.AUTO_FAILED;
                this.session.setResult(spec.getFullId(), result);
                log.debug("Auto-check {} for spec {}: {}", checkClassName, spec.getFullId(), result);
            }
            catch (Exception e)
            {
                log.error("Error running auto-check {} for spec {}", checkClassName, spec.getFullId(), e);
                this.session.setResult(spec.getFullId(), SpecResult.AUTO_FAILED);
            }
        }
    }

    public void runAutoCheckForSpec(Spec spec)
    {
        if (!spec.hasAutomatedCheck())
        {
            return;
        }

        String checkClassName = spec.getAutomatedCheckClass();
        String checkParams = spec.getAutomatedCheckParams();
        AutomatedCheck check;

        boolean hasParams = checkParams != null && !checkParams.isEmpty();
        if (hasParams)
        {
            check = this.createParameterizedCheck(checkClassName, checkParams);
        }
        else
        {
            check = this.automatedChecks.get(checkClassName);
        }

        if (check == null)
        {
            log.warn("No automated check found for class: {}", checkClassName);
            return;
        }

        try
        {
            boolean passed = check.verify();
            SpecResult result = passed ? SpecResult.AUTO_PASSED : SpecResult.AUTO_FAILED;
            this.session.setResult(spec.getFullId(), result);
        }
        catch (Exception e)
        {
            log.error("Error running auto-check for spec {}", spec.getFullId(), e);
            this.session.setResult(spec.getFullId(), SpecResult.AUTO_FAILED);
        }
    }

    public AutomatedCheck getAutomatedCheck(String className)
    {
        return this.automatedChecks.get(className);
    }

    public AutomatedCheck createParameterizedCheck(String className, String params)
    {
        Map<String, String> paramMap = this.parseCheckParams(params);

        switch (className)
        {
            case "EmojiStateCheck":
            {
                String emojiName = paramMap.get("emojiName");
                boolean shouldExist = Boolean.parseBoolean(paramMap.getOrDefault("shouldExist", "true"));
                EmojiStateCheck check = new EmojiStateCheck(this.eventBus, emojiName, shouldExist);
                check.register();
                this.emojiStateChecks.add(check);
                return check;
            }
            default:
                log.warn("Unknown parameterized check class: {}", className);
                return null;
        }
    }

    private Map<String, String> parseCheckParams(String params)
    {
        Map<String, String> result = new HashMap<>();
        if (params == null || params.isEmpty())
        {
            return result;
        }

        String[] pairs = params.split(",");
        for (String pair : pairs)
        {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2)
            {
                result.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return result;
    }

    public void resetSession()
    {
        this.session = new SpecSession();
        this.session.initializeSpecs(SpecDefinitions.getAllSpecs());
    }
}
