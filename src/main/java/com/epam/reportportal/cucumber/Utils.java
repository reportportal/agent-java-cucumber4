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

import com.epam.reportportal.listeners.Statuses;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ.File;
import cucumber.api.HookTestStep;
import cucumber.api.PickleStepTestStep;
import cucumber.api.TestStep;
import gherkin.ast.Tag;
import gherkin.pickles.Argument;
import gherkin.pickles.PickleCell;
import gherkin.pickles.PickleRow;
import gherkin.pickles.PickleString;
import gherkin.pickles.PickleTable;
import gherkin.pickles.PickleTag;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rp.com.google.common.base.Function;
import rp.com.google.common.collect.ImmutableMap;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class Utils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);
    private static final String TABLE_SEPARATOR = "|";
    private static final String DOCSTRING_DECORATOR = "\n\"\"\"\n";

    private Utils() {
        throw new AssertionError("No instances should exist for the class!");
    }

    //@formatter:off
    private static final Map<String, String> STATUS_MAPPING = ImmutableMap.<String, String>builder()
            .put("passed", Statuses.PASSED)
            .put("skipped", Statuses.SKIPPED)
            //TODO replace with NOT_IMPLEMENTED in future
            .put("undefined", Statuses.SKIPPED).build();
    //@formatter:on

    static void finishFeature(Launch rp, Maybe<String> itemId, Date dateTime) {
        if (itemId == null) {
            LOGGER.error("BUG: Trying to finish unspecified test item.");
            return;
        }
        FinishTestItemRQ rq = new FinishTestItemRQ();
        rq.setEndTime(dateTime);
        rp.finishTestItem(itemId, rq);
    }

    static void finishTestItem(Launch rp, Maybe<String> itemId) {
        finishTestItem(rp, itemId, null);
    }

    static Date finishTestItem(Launch rp, Maybe<String> itemId, String status) {
        if (itemId == null) {
            LOGGER.error("BUG: Trying to finish unspecified test item.");
            return null;
        }
        FinishTestItemRQ rq = new FinishTestItemRQ();
        Date endTime = Calendar.getInstance().getTime();
        rq.setEndTime(endTime);
        rq.setStatus(status);
        rp.finishTestItem(itemId, rq);
        return endTime;
    }

    static Maybe<String> startNonLeafNode(Launch rp, Maybe<String> rootItemId, String name, String description,
                                          Set<String> tags, String type) {
        StartTestItemRQ rq = new StartTestItemRQ();
        rq.setDescription(description);
        rq.setName(name);
        rq.setTags(tags);
        rq.setStartTime(Calendar.getInstance().getTime());
        rq.setType(type);
        return rp.startTestItem(rootItemId, rq);
    }

    static void sendLog(final String message, final String level, final File file) {
        ReportPortal.emitLog(new Function<String, SaveLogRQ>() {
            @Override
            public SaveLogRQ apply(String item) {
                SaveLogRQ rq = new SaveLogRQ();
                rq.setMessage(message);
                rq.setTestItemId(item);
                rq.setLevel(level);
                rq.setLogTime(Calendar.getInstance().getTime());
                if (file != null) {
                    rq.setFile(file);
                }
                return rq;
            }
        });
    }


    /**
     * Transform tags from Cucumber to RP format
     *
     * @param tags - Cucumber tags
     * @return set of tags
     */
    static Set<String> extractPickleTags(List<PickleTag> tags) {
        Set<String> returnTags = new HashSet<String>();
        for (PickleTag tag : tags) {
            returnTags.add(tag.getName());
        }
        return returnTags;
    }

    /**
     * Transform tags from Cucumber to RP format
     *
     * @param tags - Cucumber tags
     * @return set of tags
     */
    static Set<String> extractTags(List<Tag> tags) {
        Set<String> returnTags = new HashSet<String>();
        for (Tag tag : tags) {
            returnTags.add(tag.getName());
        }
        return returnTags;
    }

    /**
     * Map Cucumber statuses to RP log levels
     *
     * @param cukesStatus - Cucumber status
     * @return regular log level
     */
    static String mapLevel(String cukesStatus) {
        String mapped;
        if (cukesStatus.equalsIgnoreCase("passed")) {
            mapped = "INFO";
        } else if (cukesStatus.equalsIgnoreCase("skipped")) {
            mapped = "WARN";
        } else {
            mapped = "ERROR";
        }
        return mapped;
    }

    /**
     * Generate name representation
     *
     * @param prefix   - substring to be prepended at the beginning (optional)
     * @param infix    - substring to be inserted between keyword and name
     * @param argument - main text to process
     * @param suffix   - substring to be appended at the end (optional)
     * @return transformed string
     */
    //TODO: pass Node as argument, not test event
    static String buildNodeName(String prefix, String infix, String argument, String suffix) {
        return buildName(prefix, infix, argument, suffix);
    }

    private static String buildName(String prefix, String infix, String argument, String suffix) {
        return (prefix == null ? "" : prefix) + infix + argument + (suffix == null ? "" : suffix);
    }

    /**
     * Generate multiline argument (DataTable or DocString) representation
     *
     * @param step - Cucumber step object
     * @return - transformed multiline argument (or empty string if there is
     * none)
     */
    static String buildMultilineArgument(TestStep step) {
        List<PickleRow> table = null;
        String dockString = "";
        StringBuilder marg = new StringBuilder();
        PickleStepTestStep pickleStep = (PickleStepTestStep) step;
        if (!pickleStep.getStepArgument().isEmpty()) {
            Argument argument = pickleStep.getStepArgument().get(0);
            if (argument instanceof PickleString) {
                dockString = ((PickleString) argument).getContent();
            } else if (argument instanceof PickleTable) {
                table = ((PickleTable) argument).getRows();
            }
        }
        if (table != null) {
            marg.append("\r\n");
            for (PickleRow row : table) {
                marg.append(TABLE_SEPARATOR);
                for (PickleCell cell : row.getCells()) {
                    marg.append(" ").append(cell.getValue()).append(" ").append(TABLE_SEPARATOR);
                }
                marg.append("\r\n");
            }
        }

        if (!dockString.isEmpty()) {
            marg.append(DOCSTRING_DECORATOR).append(dockString).append(DOCSTRING_DECORATOR);
        }
        return marg.toString();
    }

    static String getStepName(TestStep step) {
        return step instanceof HookTestStep ? "Hook: " + ((HookTestStep) step).getHookType().toString() :
                ((PickleStepTestStep) step).getPickleStep().getText();
    }
}