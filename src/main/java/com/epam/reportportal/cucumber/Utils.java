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

import com.epam.reportportal.annotations.TestCaseId;
import com.epam.reportportal.listeners.Statuses;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ.File;
import cucumber.api.HookTestStep;
import cucumber.api.PickleStepTestStep;
import cucumber.api.TestStep;
import cucumber.runtime.StepDefinitionMatch;
import gherkin.ast.Tag;
import gherkin.pickles.*;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rp.com.google.common.base.Function;
import rp.com.google.common.collect.ImmutableMap;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class Utils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);
    private static final String TABLE_INDENT = "          ";
    private static final String TABLE_SEPARATOR = "|";
    private static final String DOCSTRING_DECORATOR = "\n\"\"\"\n";
    private static final String PASSED = "passed";
    private static final String SKIPPED = "skipped";
    private static final String INFO = "INFO";
    private static final String WARN = "WARN";
    private static final String ERROR = "ERROR";
    private static final String EMPTY = "";
    private static final String ONE_SPACE = " ";
    private static final String HOOK_ = "Hook: ";
    private static final String NEW_LINE = "\r\n";

    private static final String DEFINITION_MATCH_FIELD_NAME = "definitionMatch";
    private static final String STEP_DEFINITION_FIELD_NAME = "stepDefinition";
    private static final String GET_LOCATION_METHOD_NAME = "getLocation";
    private static final String METHOD_OPENING_BRACKET = "(";
    private static final String METHOD_FIELD_NAME = "method";

    private Utils() {
        throw new AssertionError("No instances should exist for the class!");
    }

    //@formatter:off
    private static final Map<String, String> STATUS_MAPPING = ImmutableMap.<String, String>builder()
            .put(PASSED, Statuses.PASSED)
            .put(SKIPPED, Statuses.SKIPPED)
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
            Set<ItemAttributesRQ> attributes, String type) {
        StartTestItemRQ rq = new StartTestItemRQ();
        rq.setDescription(description);
        rq.setName(name);
        rq.setAttributes(attributes);
        rq.setStartTime(Calendar.getInstance().getTime());
        rq.setType(type);
        return rp.startTestItem(rootItemId, rq);
    }

    static void sendLog(final String message, final String level, final File file) {
        ReportPortal.emitLog(new Function<String, SaveLogRQ>() {
            @Override
            public SaveLogRQ apply(String itemUuid) {
                SaveLogRQ rq = new SaveLogRQ();
                rq.setMessage(message);
                rq.setItemUuid(itemUuid);
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
    static Set<ItemAttributesRQ> extractPickleTags(List<PickleTag> tags) {
        Set<ItemAttributesRQ> attributes = new HashSet<ItemAttributesRQ>();
        for (PickleTag tag : tags) {
            attributes.add(new ItemAttributesRQ(null, tag.getName()));
        }
        return attributes;
    }

    /**
     * Transform tags from Cucumber to RP format
     *
     * @param tags - Cucumber tags
     * @return set of tags
     */
    public static Set<ItemAttributesRQ> extractAttributes(List<Tag> tags) {
        Set<ItemAttributesRQ> attributes = new HashSet<ItemAttributesRQ>();
        for (Tag tag : tags) {
            attributes.add(new ItemAttributesRQ(null, tag.getName()));
        }
        return attributes;
    }

    /**
     * Map Cucumber statuses to RP log levels
     *
     * @param cukesStatus - Cucumber status
     * @return regular log level
     */
    static String mapLevel(String cukesStatus) {
        String mapped;
        if (cukesStatus.equalsIgnoreCase(PASSED)) {
            mapped = INFO;
        } else if (cukesStatus.equalsIgnoreCase(SKIPPED)) {
            mapped = WARN;
        } else {
            mapped = ERROR;
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
        return (prefix == null ? EMPTY : prefix) + infix + argument + (suffix == null ? EMPTY : suffix);
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
        String dockString = EMPTY;
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
            marg.append(NEW_LINE);
            for (PickleRow row : table) {
                marg.append(TABLE_INDENT).append(TABLE_SEPARATOR);
                for (PickleCell cell : row.getCells()) {
                    marg.append(ONE_SPACE).append(cell.getValue()).append(ONE_SPACE).append(TABLE_SEPARATOR);
                }
                marg.append(NEW_LINE);
            }
        }

        if (!dockString.isEmpty()) {
            marg.append(DOCSTRING_DECORATOR).append(dockString).append(DOCSTRING_DECORATOR);
        }
        return marg.toString();
    }

    static String getStepName(TestStep step) {
        return step instanceof HookTestStep ?
                HOOK_ + ((HookTestStep) step).getHookType().toString() :
                ((PickleStepTestStep) step).getPickleStep().getText();
    }

    public static String getCodeRef(TestStep testStep) {

        Field definitionMatchField = getDefinitionMatchField(testStep);

        if (definitionMatchField != null) {

            try {
                StepDefinitionMatch stepDefinitionMatch = (StepDefinitionMatch) definitionMatchField.get(testStep);
                Field stepDefinitionField = stepDefinitionMatch.getClass().getDeclaredField(STEP_DEFINITION_FIELD_NAME);
                stepDefinitionField.setAccessible(true);
                Object javaStepDefinition = stepDefinitionField.get(stepDefinitionMatch);
                Method getLocationMethod = javaStepDefinition.getClass().getDeclaredMethod(GET_LOCATION_METHOD_NAME, boolean.class);
                getLocationMethod.setAccessible(true);
                String fullCodeRef = String.valueOf(getLocationMethod.invoke(javaStepDefinition, true));
                return fullCodeRef != null ? fullCodeRef.substring(0, fullCodeRef.indexOf(METHOD_OPENING_BRACKET)) : null;
            } catch (NoSuchFieldException e) {
                return null;
            } catch (NoSuchMethodException e) {
                return null;
            } catch (IllegalAccessException e) {
                return null;
            } catch (InvocationTargetException e) {
                return null;
            }

        } else {
            return null;
        }

    }

	public static int getTestCaseId(TestStep testStep, String codeRef) {
		Field definitionMatchField = getDefinitionMatchField(testStep);
		if (definitionMatchField != null) {
			try {
				StepDefinitionMatch stepDefinitionMatch = (StepDefinitionMatch) definitionMatchField.get(testStep);
				Field stepDefinitionField = stepDefinitionMatch.getClass().getDeclaredField(STEP_DEFINITION_FIELD_NAME);
				stepDefinitionField.setAccessible(true);
				Object javaStepDefinition = stepDefinitionField.get(stepDefinitionMatch);
                Field methodField = javaStepDefinition.getClass().getDeclaredField(METHOD_FIELD_NAME);
				methodField.setAccessible(true);
				Method method = (Method) methodField.get(javaStepDefinition);
				TestCaseId testCaseIdAnnotation = method.getAnnotation(TestCaseId.class);
				return testCaseIdAnnotation != null ?
						testCaseIdAnnotation.value() :
						getTestCaseId(codeRef, ((PickleStepTestStep) testStep).getDefinitionArgument());
			} catch (NoSuchFieldException e) {
				return getTestCaseId(codeRef, ((PickleStepTestStep) testStep).getDefinitionArgument());
			} catch (IllegalAccessException e) {
				return getTestCaseId(codeRef, ((PickleStepTestStep) testStep).getDefinitionArgument());
			}
		} else {
			return getTestCaseId(codeRef, ((PickleStepTestStep) testStep).getDefinitionArgument());
		}
	}

	private static int getTestCaseId(String codeRef, List<cucumber.api.Argument> arguments) {
		List<String> values = new ArrayList<String>(arguments.size());
		for (cucumber.api.Argument argument : arguments) {
			values.add(argument.getValue());
		}
		return Arrays.deepHashCode(new Object[] { codeRef, values });
	}

	private static Field getDefinitionMatchField(TestStep testStep) {

        Class<?> clazz = testStep.getClass();

        try {
            return clazz.getField(DEFINITION_MATCH_FIELD_NAME);
        } catch (NoSuchFieldException e) {
            do {
                try {
                    Field definitionMatchField = clazz.getDeclaredField(DEFINITION_MATCH_FIELD_NAME);
                    definitionMatchField.setAccessible(true);
                    return definitionMatchField;
                } catch (NoSuchFieldException ignore) {
                }

                clazz = clazz.getSuperclass();
            } while (clazz != null);

            return null;
        }
    }
}