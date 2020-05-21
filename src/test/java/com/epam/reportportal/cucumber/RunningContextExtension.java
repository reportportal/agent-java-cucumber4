package com.epam.reportportal.cucumber;

import cucumber.api.TestStep;
import gherkin.ast.Step;
import io.reactivex.Maybe;

/**
 * @author <a href="mailto:ivan_budayeu@epam.com">Ivan Budayeu</a>
 */
public class RunningContextExtension {

	public static class FeatureContextExtension extends RunningContext.FeatureContext {

	}

	public static class ScenarioContextExtension extends RunningContext.ScenarioContext {

		@Override
		public Maybe<String> getId() {
			return super.getId();
		}

		@Override
		public String getStepPrefix() {
			return super.getStepPrefix();
		}

		@Override
		public Step getStep(TestStep testStep) {
			return super.getStep(testStep);
		}


	}



}
