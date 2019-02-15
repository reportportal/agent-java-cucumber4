/*
 * Copyright 2018 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.reportportal.cucumber;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ.File;
import cucumber.api.HookTestStep;
import cucumber.api.HookType;
import cucumber.api.Result;
import cucumber.api.TestCase;
import cucumber.api.TestStep;
import cucumber.api.event.*;
import io.reactivex.Maybe;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rp.com.google.common.base.Supplier;
import rp.com.google.common.base.Suppliers;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract Cucumber 4.x formatter for Report Portal
 *
 * @author Sergey Gvozdyukevich
 * @author Andrei Varabyeu
 * @author Serhii Zharskyi
 * @author Vitaliy Tsvihun
 */
public abstract class AbstractReporter implements ConcurrentEventListener {

    Supplier<Launch> launch;
    static final String COLON_INFIX = ": ";
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractReporter.class);

    private Map<String, RunningContext.FeatureContext> currentFeatureContextMap =
            new HashMap<String, RunningContext.FeatureContext>();

    private Map<String, RunningContext.ScenarioContext> currentScenarioContextMap =
            new HashMap<String, RunningContext.ScenarioContext>();

    private Map<Long, RunningContext.ScenarioContext> threadCurrentScenarioContextMap =
            new HashMap<Long, RunningContext.ScenarioContext>();


    /**
     * Registers an event handler for a specific event.
     * <p>
     * The available events types are:
     * <ul>
     * <li>{@link TestRunStarted} - the first event sent.
     * <li>{@link TestSourceRead} - sent for each feature file read, contains the feature file source.
     * <li>{@link TestCaseStarted} - sent before starting the execution of a Test Case(/Pickle/Scenario), contains the Test Case
     * <li>{@link TestStepStarted} - sent before starting the execution of a Test Step, contains the Test Step
     * <li>{@link TestStepFinished} - sent after the execution of a Test Step, contains the Test Step and its Result.
     * <li>{@link TestCaseFinished} - sent after the execution of a Test Case(/Pickle/Scenario), contains the Test Case and its Result.
     * <li>{@link TestRunFinished} - the last event sent.
     * <li>{@link EmbedEvent} - calling scenario.embed in a hook triggers this event.
     * <li>{@link WriteEvent} - calling scenario.write in a hook triggers this event.
     * </ul>
     */
    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestRunStarted.class, getTestRunStartedHandler());
        publisher.registerHandlerFor(TestSourceRead.class, getTestSourceReadHandler());
        publisher.registerHandlerFor(TestCaseStarted.class, getTestCaseStartedHandler());
        publisher.registerHandlerFor(TestStepStarted.class, getTestStepStartedHandler());
        publisher.registerHandlerFor(TestStepFinished.class, getTestStepFinishedHandler());
        publisher.registerHandlerFor(TestCaseFinished.class, getTestCaseFinishedHandler());
        publisher.registerHandlerFor(TestRunFinished.class, getTestRunFinishedHandler());
        publisher.registerHandlerFor(EmbedEvent.class, getEmbedEventHandler());
        publisher.registerHandlerFor(WriteEvent.class, getWriteEventHandler());
    }

    protected RunningContext.ScenarioContext getCurrentScenarioContext() {
        return threadCurrentScenarioContextMap.get(Thread.currentThread().getId());
    }

    /**
     * Manipulations before the launch starts
     */
    protected void beforeLaunch() {
        startLaunch();
    }

    /**
     * Finish RP launch
     */
    protected void afterLaunch() {
        FinishExecutionRQ finishLaunchRq = new FinishExecutionRQ();
        finishLaunchRq.setEndTime(Calendar.getInstance().getTime());
        launch.get().finish(finishLaunchRq);
    }

    /**
     * Start Cucumber scenario
     */
    private void beforeScenario(RunningContext.FeatureContext currentFeatureContext,
                                RunningContext.ScenarioContext currentScenarioContext, String scenarioName) {
        Maybe<String> id = Utils.startNonLeafNode(
                launch.get(),
                currentFeatureContext.getFeatureId(),
                scenarioName,
                currentFeatureContext.getUri() + ":" + currentScenarioContext.getLine(),
                currentScenarioContext.getTags(),
                getScenarioTestItemType());
        currentScenarioContext.setId(id);
    }

    /**
     * Finish Cucumber scenario
     */
    protected void afterScenario(TestCaseFinished event) {
        RunningContext.ScenarioContext currentScenarioContext = getCurrentScenarioContext();
        String scenarioNameToDeleteFromContextMap = "";
        for (Map.Entry<String, RunningContext.ScenarioContext> scenarioContext : currentScenarioContextMap.entrySet()) {
            if (scenarioContext.getValue().getLine() == currentScenarioContext.getLine()) {
                scenarioNameToDeleteFromContextMap = scenarioContext.getKey();
                break;
            }
        }
        Utils.finishTestItem(launch.get(), currentScenarioContext.getId(), event.result.getStatus().toString());
        currentScenarioContextMap.remove(scenarioNameToDeleteFromContextMap);
    }

    private String getFeatureName(TestCase testCase) {
        RunningContext.FeatureContext featureContext = new RunningContext.FeatureContext().processTestSourceReadEvent(testCase);
        String featureKeyword = featureContext.getFeature().getKeyword();
        String featureName = featureContext.getFeature().getName();
        return Utils.buildNodeName(featureKeyword, AbstractReporter.COLON_INFIX, featureName, null);
    }

    /**
     * Start RP launch
     */
    protected void startLaunch() {
        launch = Suppliers.memoize(new Supplier<Launch>() {

            /* should no be lazy */
            private final Date startTime = Calendar.getInstance().getTime();

            @Override
            public Launch get() {
                final ReportPortal reportPortal = ReportPortal.builder().build();
                ListenerParameters parameters = reportPortal.getParameters();

                StartLaunchRQ rq = new StartLaunchRQ();
                rq.setName(parameters.getLaunchName());
                rq.setStartTime(startTime);
                rq.setMode(parameters.getLaunchRunningMode());
                rq.setTags(parameters.getTags());
                rq.setDescription(parameters.getDescription());

                return reportPortal.newLaunch(rq);
            }
        });
    }

    /**
     * Start Cucumber step
     *
     * @param step Step object
     */
    protected abstract void beforeStep(TestStep step);

    /**
     * Finish Cucumber step
     *
     * @param result Step result
     */
    protected abstract void afterStep(Result result);

    /**
     * Called when before/after-hooks are started
     *
     * @param isBefore - if true, before-hook is started, if false - after-hook
     */
    protected abstract void beforeHooks(Boolean isBefore);

    /**
     * Called when before/after-hooks are finished
     *
     * @param isBefore - if true, before-hook is finished, if false - after-hook
     */
    protected abstract void afterHooks(Boolean isBefore);

    /**
     * Called when a specific before/after-hook is finished
     *
     * @param step     TestStep object
     * @param result   Hook result
     * @param isBefore - if true, before-hook, if false - after-hook
     */
    protected abstract void hookFinished(TestStep step, Result result, Boolean isBefore);

    /**
     * Return RP launch test item name mapped to Cucumber feature
     *
     * @return test item name
     */
    protected abstract String getFeatureTestItemType();

    /**
     * Return RP launch test item name mapped to Cucumber scenario
     *
     * @return test item name
     */
    protected abstract String getScenarioTestItemType();

    /**
     * Report test item result and error (if present)
     *
     * @param result  - Cucumber result object
     * @param message - optional message to be logged in addition
     */
    protected void reportResult(Result result, String message) {
        String cukesStatus = result.getStatus().toString();
        String level = Utils.mapLevel(cukesStatus);
        String errorMessage = result.getErrorMessage();
        if (errorMessage != null) {
            Utils.sendLog(errorMessage, level, null);
        }
        if (message != null) {
            Utils.sendLog(message, level, null);
        }
    }

    protected void embedding(String mimeType, byte[] data) {
        File file = new File();
        String embeddingName;
        try {
            embeddingName = MimeTypes.getDefaultMimeTypes().forName(mimeType).getType().getType();
        } catch (MimeTypeException e) {
            LOGGER.warn("Mime-type not found", e);
            embeddingName = "embedding";
        }
        file.setName(embeddingName);
        file.setContent(data);
        Utils.sendLog(embeddingName, "UNKNOWN", file);
    }

    protected void write(String text) {
        Utils.sendLog(text, "INFO", null);
    }

    protected boolean isBefore(TestStep step) {
        return HookType.Before == ((HookTestStep) step).getHookType();
    }

    protected abstract Maybe<String> getRootItemId();


    /**
     * Private part that responsible for handling events
     */

    private EventHandler<TestRunStarted> getTestRunStartedHandler() {
        return new EventHandler<TestRunStarted>() {
            @Override
            public void receive(TestRunStarted event) {
                beforeLaunch();
            }
        };
    }

    private EventHandler<TestSourceRead> getTestSourceReadHandler() {
        return new EventHandler<TestSourceRead>() {
            @Override
            public void receive(TestSourceRead event) {
                RunningContext.FeatureContext.addTestSourceReadEvent(event.uri, event);
            }
        };
    }

    private EventHandler<TestCaseStarted> getTestCaseStartedHandler() {
        return new EventHandler<TestCaseStarted>() {
            @Override
            public void receive(TestCaseStarted event) {
                handleStartOfTestCase(event);
            }
        };
    }

    private EventHandler<TestStepStarted> getTestStepStartedHandler() {
        return new EventHandler<TestStepStarted>() {
            @Override
            public void receive(TestStepStarted event) {
                handleTestStepStarted(event);
            }
        };
    }

    private EventHandler<TestStepFinished> getTestStepFinishedHandler() {
        return new EventHandler<TestStepFinished>() {
            @Override
            public void receive(TestStepFinished event) {
                handleTestStepFinished(event);
            }
        };
    }

    private EventHandler<TestCaseFinished> getTestCaseFinishedHandler() {
        return new EventHandler<TestCaseFinished>() {
            @Override
            public void receive(TestCaseFinished event) {
                afterScenario(event);
            }
        };
    }

    private EventHandler<TestRunFinished> getTestRunFinishedHandler() {
        return new EventHandler<TestRunFinished>() {
            @Override
            public void receive(TestRunFinished event) {
                if (!currentFeatureContextMap.isEmpty()) {
                    handleEndOfFeature();
                }
                afterLaunch();
            }
        };
    }

    private EventHandler<EmbedEvent> getEmbedEventHandler() {
        return new EventHandler<EmbedEvent>() {
            @Override
            public void receive(EmbedEvent event) {
                embedding(event.mimeType, event.data);
            }
        };
    }

    private EventHandler<WriteEvent> getWriteEventHandler() {
        return new EventHandler<WriteEvent>() {
            @Override
            public void receive(WriteEvent event) {
                write(event.text);
            }
        };
    }

    private void handleEndOfFeature() {
        for (RunningContext.FeatureContext value : currentFeatureContextMap.values()) {
            Utils.finishTestItem(launch.get(), value.getFeatureId());
        }
        currentFeatureContextMap.clear();
    }

    private void handleStartOfTestCase(TestCaseStarted event) {
        TestCase testCase = event.testCase;
        String featureName = getFeatureName(testCase);
        RunningContext.FeatureContext currentFeatureContext = currentFeatureContextMap.get(featureName);
        if (currentFeatureContext != null && !testCase.getUri().equals(currentFeatureContext.getUri())) {
            Utils.finishTestItem(launch.get(), currentFeatureContext.getFeatureId());
            currentFeatureContextMap.remove(featureName);
        }
        if (currentFeatureContext == null) {
            currentFeatureContext = createFeatureContext(testCase, featureName);
        }
        if (!currentFeatureContext.getUri().equals(testCase.getUri())) {
            throw new IllegalStateException("Scenario URI does not match Feature URI.");
        }

        RunningContext.ScenarioContext scenarioContext = currentFeatureContext.getScenarioContext(testCase);

        String scenarioName = Utils.buildNodeName(scenarioContext.getKeyword(),
                AbstractReporter.COLON_INFIX,
                scenarioContext.getName(), scenarioContext.getOutlineIteration());

        RunningContext.ScenarioContext currentScenarioContext = currentScenarioContextMap.get(scenarioName);

        if (currentScenarioContext == null) {
            currentScenarioContext = currentFeatureContext.getScenarioContext(testCase);
            currentScenarioContextMap.put(scenarioName, currentScenarioContext);
            threadCurrentScenarioContextMap.put(Thread.currentThread().getId(), currentScenarioContext);
        }

        beforeScenario(currentFeatureContext, currentScenarioContext, scenarioName);
    }

    private RunningContext.FeatureContext createFeatureContext(TestCase testCase, String featureName) {
        RunningContext.FeatureContext currentFeatureContext;
        currentFeatureContext = new RunningContext.FeatureContext().processTestSourceReadEvent(testCase);
        currentFeatureContextMap.put(featureName, currentFeatureContext);

        StartTestItemRQ rq = new StartTestItemRQ();
        Maybe<String> root = getRootItemId();
        rq.setDescription(currentFeatureContext.getUri());
        rq.setName(featureName);
        rq.setTags(currentFeatureContext.getTags());
        rq.setStartTime(Calendar.getInstance().getTime());
        rq.setType(getFeatureTestItemType());
        if (null == root) {
            currentFeatureContext.setFeatureId(launch.get().startTestItem(rq));
        } else {
            currentFeatureContext.setFeatureId(launch.get().startTestItem(root, rq));
        }
        return currentFeatureContext;
    }

    private void handleTestStepStarted(TestStepStarted event) {
        TestStep testStep = event.testStep;
        if (testStep instanceof HookTestStep) {
            beforeHooks(isBefore(testStep));
        } else {
            if (getCurrentScenarioContext().withBackground()) {
                getCurrentScenarioContext().nextBackgroundStep();
            }
            beforeStep(testStep);
        }
    }

    private void handleTestStepFinished(TestStepFinished event) {
        if (event.testStep instanceof HookTestStep) {
            hookFinished(event.testStep, event.result, isBefore(event.testStep));
            afterHooks(isBefore(event.testStep));
        } else {
            afterStep(event.result);
        }
    }
}