package com.epam.reportportal.cucumber;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
class TestStepReporter extends StepReporter {

	@Mock
	RunningContext.ScenarioContext scenarioContext;

	public TestStepReporter() {
		super();
		MockitoAnnotations.initMocks(this);
	}

	@Override
	protected RunningContext.ScenarioContext getCurrentScenarioContext() {
		return scenarioContext;
	}
}
