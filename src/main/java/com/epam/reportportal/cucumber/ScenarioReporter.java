/*
 * Copyright 2016 EPAM Systems
 *
 *
 * This file is part of EPAM Report Portal.
 * https://github.com/reportportal/agent-java-cucumber
 *
 * Report Portal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Report Portal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Report Portal.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.epam.reportportal.cucumber;

import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import cucumber.api.Result;
import cucumber.api.TestStep;
import gherkin.ast.Step;
import io.reactivex.Maybe;
import rp.com.google.common.base.Supplier;
import rp.com.google.common.base.Suppliers;

import java.util.Calendar;

/**
 * Cucumber reporter for ReportPortal that reports scenarios as test methods.
 * <p>
 * Mapping between Cucumber and ReportPortal is as follows:
 * <ul>
 * <li>feature - TEST</li>
 * <li>scenario - STEP</li>
 * <li>step - log item</li>
 * </ul>
 * <p>
 * Dummy "Root Test Suite" is created because in current implementation of RP
 * test items cannot be immediate children of a launch
 * <p>
 * Background steps and hooks are reported as part of corresponding scenarios.
 * Outline example rows are reported as individual scenarios with [ROW NUMBER]
 * after the name.
 *
 * @author Sergey_Gvozdyukevich
 * @author Serhii Zharskyi
 * @author Vitaliy Tsvihun
 */
public class ScenarioReporter extends AbstractReporter {
    private static final String SEPARATOR = "-------------------------";

    protected Supplier<Maybe<String>> rootSuiteId;

    @Override
    protected void beforeLaunch() {
        super.beforeLaunch();
        startRootItem();
    }

    @Override
    protected void beforeStep(TestStep testStep) {
        Step step = currentScenarioContext.getStep(testStep);
        String decoratedStepName = decorateMessage(Utils.buildNodeName(currentScenarioContext.getStepPrefix(), step.getKeyword(), Utils.getStepName(testStep), " "));
        String multilineArg = Utils.buildMultilineArgument(testStep);
        Utils.sendLog(decoratedStepName + multilineArg, "INFO", null);
    }

    @Override
    protected void afterStep(Result result) {
        reportResult(result, decorateMessage("STEP " + result.getStatus().toString().toUpperCase()));
    }

    @Override
    protected void beforeHooks(Boolean isBefore) {
        // noop
    }

    @Override
    protected void afterHooks(Boolean isBefore) {
        // noop
    }

    @Override
    protected void hookFinished(TestStep step, Result result, Boolean isBefore) {
        reportResult(result, (isBefore ? "@Before" : "@After") + "\n" + step.getCodeLocation());
    }

    @Override
    protected String getFeatureTestItemType() {
        return "TEST";
    }

    @Override
    protected String getScenarioTestItemType() {
        return "STEP";
    }


    @Override
    protected Maybe<String> getRootItemId() {
        return rootSuiteId.get();
    }

    @Override
    protected void afterLaunch() {
        finishRootItem();
        super.afterLaunch();
    }

    /**
     * Start root suite
     */
    protected void finishRootItem() {
        Utils.finishTestItem(RP.get(), rootSuiteId.get());
        rootSuiteId = null;
    }

    /**
     * Start root suite
     */
    protected void startRootItem() {
        rootSuiteId = Suppliers.memoize(new Supplier<Maybe<String>>() {
            @Override
            public Maybe<String> get() {
                StartTestItemRQ rq = new StartTestItemRQ();
                rq.setName("Root User Story");
                rq.setStartTime(Calendar.getInstance().getTime());
                rq.setType("STORY");
                return RP.get().startTestItem(rq);
            }
        });
    }

    /**
     * Add separators to log item to distinguish from real log messages
     *
     * @param message to decorate
     * @return decorated message
     */
    private String decorateMessage(String message) {
        return ScenarioReporter.SEPARATOR + message + ScenarioReporter.SEPARATOR;
    }
}
