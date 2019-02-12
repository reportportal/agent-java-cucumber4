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
import com.epam.ta.reportportal.ws.model.ItemAttributeResource;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ.File;
import cucumber.api.*;
import cucumber.api.event.*;
import cucumber.api.formatter.Formatter;
import io.reactivex.Maybe;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rp.com.google.common.base.Supplier;
import rp.com.google.common.base.Suppliers;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;

/**
 * Abstract Cucumber 4.x formatter for Report Portal
 *
 * @author Sergey Gvozdyukevich
 * @author Andrei Varabyeu
 * @author Serhii Zharskyi
 * @author Vitaliy Tsvihun
 */
public abstract class AbstractReporter implements Formatter {

	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractReporter.class);

	protected static final String COLON_INFIX = ": ";

	private static final String SKIPPED_ISSUE_KEY = "skippedIssue";

	/* feature context */
	protected RunningContext.FeatureContext currentFeatureContext;

	/* scenario context */
	protected RunningContext.ScenarioContext currentScenarioContext;

	protected Supplier<Launch> RP;

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
		RP.get().finish(finishLaunchRq);
	}

	/**
	 * Manipulations before the feature starts
	 */
	protected void beforeFeature() {
		startFeature();
	}

	/**
	 * Finish Cucumber feature
	 */
	protected void afterFeature() {
		Utils.finishTestItem(RP.get(), currentFeatureContext.getFeatureId());
		currentFeatureContext = null;
	}

	/**
	 * Start Cucumber scenario
	 */
	protected void beforeScenario() {
		Maybe<Long> id = Utils.startNonLeafNode(RP.get(),
				currentFeatureContext.getFeatureId(),
				Utils.buildNodeName(currentScenarioContext.getKeyword(),
						AbstractReporter.COLON_INFIX,
						currentScenarioContext.getName(),
						currentScenarioContext.getOutlineIteration()
				),
				currentFeatureContext.getUri() + ":" + currentScenarioContext.getLine(),
				currentScenarioContext.getAttributes(),
				getScenarioTestItemType()
		);
		currentScenarioContext.setId(id);
	}

	/**
	 * Finish Cucumber scenario
	 */
	protected void afterScenario(TestCaseFinished event) {
		Utils.finishTestItem(RP.get(), currentScenarioContext.getId(), event.result.getStatus().toString());
		currentScenarioContext = null;
	}

	/**
	 * Start Cucumber feature
	 */
	protected void startFeature() {
		StartTestItemRQ rq = new StartTestItemRQ();
		Maybe<Long> root = getRootItemId();
		rq.setDescription(currentFeatureContext.getUri());
		rq.setName(Utils.buildNodeName(currentFeatureContext.getFeature().getKeyword(),
				AbstractReporter.COLON_INFIX,
				currentFeatureContext.getFeature().getName(),
				null
		));
		rq.setAttributes(currentFeatureContext.getTags());
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(getFeatureTestItemType());
		if (null == root) {
			currentFeatureContext.setFeatureId(RP.get().startTestItem(rq));
		} else {
			currentFeatureContext.setFeatureId(RP.get().startTestItem(root, rq));
		}
	}

	/**
	 * Start RP launch
	 */
	protected void startLaunch() {
		RP = Suppliers.memoize(new Supplier<Launch>() {

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
				rq.setAttributes(parameters.getAttributes() == null ? new HashSet<ItemAttributeResource>() : parameters.getAttributes());
				rq.setDescription(parameters.getDescription());

				final ItemAttributeResource skippedIssueAttr = new ItemAttributeResource(SKIPPED_ISSUE_KEY,
						String.valueOf(parameters.getSkippedAnIssue()),
						true
				);
				rq.getAttributes().add(skippedIssueAttr);

				Launch launch = reportPortal.newLaunch(rq);
				return launch;
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
	 * Return RP test item name mapped to Cucumber feature
	 *
	 * @return test item name
	 */
	protected abstract String getFeatureTestItemType();

	/**
	 * Return RP test item name mapped to Cucumber scenario
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

	protected abstract Maybe<Long> getRootItemId();

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
				if (currentFeatureContext != null) {
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

	private void handleStartOfFeature(TestCase testCase) {
		currentFeatureContext = new RunningContext.FeatureContext().processTestSourceReadEvent(testCase);
		beforeFeature();
	}

	private void handleEndOfFeature() {
		afterFeature();
	}

	private void handleStartOfTestCase(TestCaseStarted event) {
		TestCase testCase = event.testCase;
		if (currentFeatureContext != null && !testCase.getUri().equals(currentFeatureContext.getUri())) {
			handleEndOfFeature();
		}
		if (currentFeatureContext == null) {
			handleStartOfFeature(testCase);
		}
		if (!currentFeatureContext.getUri().equals(testCase.getUri())) {
			throw new IllegalStateException("Scenario URI does not match Feature URI.");
		}
		if (currentScenarioContext == null) {
			currentScenarioContext = currentFeatureContext.getScenarioContext(testCase);
		}
		beforeScenario();
	}

	private void handleTestStepStarted(TestStepStarted event) {
		TestStep testStep = event.testStep;
		if (testStep instanceof HookTestStep) {
			beforeHooks(isBefore(testStep));
		} else {
			if (currentScenarioContext.withBackground()) {
				currentScenarioContext.nextBackgroundStep();
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
