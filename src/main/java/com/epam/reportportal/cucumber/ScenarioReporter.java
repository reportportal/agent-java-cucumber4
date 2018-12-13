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
