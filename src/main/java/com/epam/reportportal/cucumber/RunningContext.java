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

import com.epam.ta.reportportal.ws.model.ItemAttributeResource;
import cucumber.api.PickleStepTestStep;
import cucumber.api.TestCase;
import cucumber.api.TestStep;
import cucumber.api.event.TestSourceRead;
import gherkin.AstBuilder;
import gherkin.Parser;
import gherkin.ParserException;
import gherkin.TokenMatcher;
import gherkin.ast.*;
import gherkin.pickles.PickleTag;
import io.reactivex.Maybe;

import java.util.*;

import static com.epam.reportportal.cucumber.Utils.extractPickleTags;
import static com.epam.reportportal.cucumber.Utils.extractTags;

/**
 * Running context that contains mostly manipulations with Gherkin objects.
 * Keeps necessary information regarding current Feature, Scenario and Step
 *
 * @author Serhii Zharskyi
 * @author Vitaliy Tsvihun
 */
public class RunningContext {

	public static class FeatureContext {

		private static Map<String, TestSourceRead> pathToReadEventMap = new HashMap<String, TestSourceRead>();

		private String currentFeatureUri;
		private Maybe<Long> currentFeatureId;
		private Feature currentFeature;
		private Set<ItemAttributeResource> tags;

		FeatureContext() {
			tags = new HashSet<ItemAttributeResource>();
		}

		static void addTestSourceReadEvent(String path, TestSourceRead event) {
			pathToReadEventMap.put(path, event);
		}

		ScenarioContext getScenarioContext(TestCase testCase) {
			ScenarioDefinition scenario = getScenario(testCase);
			ScenarioContext context = new ScenarioContext();
			context.processScenario(scenario);
			context.setTestCase(testCase);
			context.processBackground(getBackground());
			context.processScenarioOutline(scenario);
			context.processTags(testCase.getTags());
			return context;
		}

		FeatureContext processTestSourceReadEvent(TestCase testCase) {
			TestSourceRead event = pathToReadEventMap.get(testCase.getUri());
			currentFeature = getFeature(event.source);
			currentFeatureUri = event.uri;
			tags = extractTags(currentFeature.getTags());
			return this;
		}

		Feature getFeature(String source) {
			Parser<GherkinDocument> parser = new Parser<GherkinDocument>(new AstBuilder());
			TokenMatcher matcher = new TokenMatcher();
			GherkinDocument gherkinDocument;
			try {
				gherkinDocument = parser.parse(source, matcher);
			} catch (ParserException e) {
				// Ignore exceptions
				return null;
			}
			return gherkinDocument.getFeature();
		}

		Background getBackground() {
			ScenarioDefinition background = getFeature().getChildren().get(0);
			if (background instanceof Background) {
				return (Background) background;
			} else {
				return null;
			}
		}

		Feature getFeature() {
			return currentFeature;
		}

		Set<ItemAttributeResource> getTags() {
			return tags;
		}

		String getUri() {
			return currentFeatureUri;
		}

		Maybe<Long> getFeatureId() {
			return currentFeatureId;
		}

		void setFeatureId(Maybe<Long> featureId) {
			this.currentFeatureId = featureId;
		}

		<T extends ScenarioDefinition> T getScenario(TestCase testCase) {
			List<ScenarioDefinition> featureScenarios = getFeature().getChildren();
			for (ScenarioDefinition scenario : featureScenarios) {
				if (scenario instanceof Background) {
					continue;
				}
				if (testCase.getLine() == scenario.getLocation().getLine() && testCase.getName().equals(scenario.getName())) {
					return (T) scenario;
				} else {
					if (scenario instanceof ScenarioOutline) {
						for (Examples example : ((ScenarioOutline) scenario).getExamples()) {
							for (TableRow tableRow : example.getTableBody()) {
								if (tableRow.getLocation().getLine() == testCase.getLine()) {
									return (T) scenario;
								}
							}
						}
					}
				}
			}
			throw new IllegalStateException("Scenario can't be null!");
		}
	}

	public static class ScenarioContext {

		private static Map<Integer, ArrayDeque<String>> outlineIterationsMap = new HashMap<Integer, ArrayDeque<String>>();

		private Maybe<Long> id = null;
		private Background background;
		private ScenarioDefinition scenario;
		private Queue<Step> backgroundSteps;
		private Map<Integer, Step> scenarioLocationMap;
		private Set<ItemAttributeResource> attributes;
		private TestCase testCase;
		private boolean hasBackground = false;

		ScenarioContext() {
			backgroundSteps = new ArrayDeque<Step>();
			scenarioLocationMap = new HashMap<Integer, Step>();
			attributes = new HashSet<ItemAttributeResource>();
		}

		ScenarioContext processScenario(ScenarioDefinition scenario) {
			this.scenario = scenario;
			for (Step step : scenario.getSteps()) {
				scenarioLocationMap.put(step.getLocation().getLine(), step);
			}
			return this;
		}

		void processBackground(Background background) {
			if (background != null) {
				this.background = background;
				hasBackground = true;
				backgroundSteps.addAll(background.getSteps());
				mapBackgroundSteps(background);
			}
		}

		void processScenarioOutline(ScenarioDefinition scenarioOutline) {
			if (isScenarioOutline(scenarioOutline)) {
				if (!hasOutlineSteps()) {
					int num = 0;
					outlineIterationsMap.put(scenario.getLocation().getLine(), new ArrayDeque<String>());
					for (Examples example : ((ScenarioOutline) scenarioOutline).getExamples()) {
						num += example.getTableBody().size();
					}
					for (int i = 1; i <= num; i++) {
						outlineIterationsMap.get(scenario.getLocation().getLine()).add(" [" + i + "]");
					}
				}
			}
		}

		void processTags(List<PickleTag> pickleTags) {
			attributes = extractPickleTags(pickleTags);
		}

		void mapBackgroundSteps(Background background) {
			for (Step step : background.getSteps()) {
				scenarioLocationMap.put(step.getLocation().getLine(), step);
			}
		}

		String getName() {
			return scenario.getName();
		}

		String getKeyword() {
			return scenario.getKeyword();
		}

		int getLine() {
			if (isScenarioOutline(scenario)) {
				return testCase.getLine();
			}
			return scenario.getLocation().getLine();
		}

		Set<ItemAttributeResource> getAttributes() {
			return attributes;
		}

		String getStepPrefix() {
			if (hasBackground() && withBackground()) {
				return background.getKeyword().toUpperCase() + AbstractReporter.COLON_INFIX;
			} else {
				return "";
			}
		}

		Step getStep(TestStep testStep) {
			PickleStepTestStep pickleStepTestStep = (PickleStepTestStep) testStep;
			Step step = scenarioLocationMap.get(pickleStepTestStep.getStepLine());
			if (step != null) {
				return step;
			}
			throw new IllegalStateException(String.format("Trying to get step for unknown line in feature. Scenario: %s, line: %s",
					scenario.getName(),
					getLine()
			));
		}

		Maybe<Long> getId() {
			return id;
		}

		void setId(Maybe<Long> newId) {
			if (id != null) {
				throw new IllegalStateException("Attempting re-set scenario ID for unfinished scenario.");
			}
			id = newId;
		}

		void setTestCase(TestCase testCase) {
			this.testCase = testCase;
		}

		void nextBackgroundStep() {
			backgroundSteps.poll();
		}

		boolean isScenarioOutline(ScenarioDefinition scenario) {
			if (scenario != null) {
				return scenario instanceof ScenarioOutline;
			}
			return false;
		}

		boolean withBackground() {
			return !backgroundSteps.isEmpty();
		}

		boolean hasBackground() {
			return hasBackground && background != null;
		}

		boolean hasOutlineSteps() {
			return outlineIterationsMap.get(scenario.getLocation().getLine()) != null && !outlineIterationsMap.get(scenario.getLocation()
					.getLine()).isEmpty();
		}

		String getOutlineIteration() {
			if (hasOutlineSteps()) {
				return outlineIterationsMap.get(scenario.getLocation().getLine()).poll();
			}
			return null;
		}
	}
}
