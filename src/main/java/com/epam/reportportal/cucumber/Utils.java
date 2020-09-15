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
import com.epam.reportportal.annotations.attribute.Attributes;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.item.TestCaseIdEntry;
import com.epam.reportportal.utils.AttributeParser;
import com.epam.reportportal.utils.ParameterUtils;
import com.epam.reportportal.utils.TestCaseIdUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.ParameterResource;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import cucumber.api.*;
import cucumber.runtime.StepDefinitionMatch;
import gherkin.ast.Tag;
import gherkin.pickles.Argument;
import gherkin.pickles.*;
import io.reactivex.Maybe;
import io.reactivex.annotations.Nullable;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rp.com.google.common.collect.ImmutableMap;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Optional.ofNullable;

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
	private static final String FILE_PREFIX = "file:";

	private static final String DEFINITION_MATCH_FIELD_NAME = "definitionMatch";
	private static final String STEP_DEFINITION_FIELD_NAME = "stepDefinition";
	private static final String GET_LOCATION_METHOD_NAME = "getLocation";
	private static final String METHOD_OPENING_BRACKET = "(";
	private static final String METHOD_FIELD_NAME = "method";

	private Utils() {
		throw new AssertionError("No instances should exist for the class!");
	}

	//@formatter:off
    private static final Map<Result.Type, ItemStatus> STATUS_MAPPING = ImmutableMap.<Result.Type, ItemStatus>builder()
            .put(Result.Type.PASSED, ItemStatus.PASSED)
            .put(Result.Type.FAILED, ItemStatus.FAILED)
            .put(Result.Type.SKIPPED, ItemStatus.SKIPPED)
            .put(Result.Type.PENDING, ItemStatus.SKIPPED)
            .put(Result.Type.AMBIGUOUS, ItemStatus.SKIPPED)
            .put(Result.Type.UNDEFINED, ItemStatus.SKIPPED)
            .put(Result.Type.UNUSED, ItemStatus.SKIPPED).build();
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

	static Date finishTestItem(Launch rp, Maybe<String> itemId, Result.Type status) {
		if (itemId == null) {
			LOGGER.error("BUG: Trying to finish unspecified test item.");
			return null;
		}
		FinishTestItemRQ rq = new FinishTestItemRQ();
		Date endTime = Calendar.getInstance().getTime();
		rq.setEndTime(endTime);
		rq.setStatus(mapItemStatus(status));
		rp.finishTestItem(itemId, rq);
		return endTime;
	}

	static Maybe<String> startNonLeafNode(Launch rp, Maybe<String> rootItemId, String name, String description, String codeRef,
			Set<ItemAttributesRQ> attributes, String type) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setDescription(description);
		rq.setCodeRef(codeRef);
		rq.setName(name);
		rq.setAttributes(attributes);
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setType(type);
		if ("STEP".equals(type)) {
			rq.setTestCaseId(ofNullable(Utils.getTestCaseId(codeRef, null)).map(TestCaseIdEntry::getId).orElse(null));
		}

		return rp.startTestItem(rootItemId, rq);
	}

	static void sendLog(final String message, final String level) {
		ReportPortal.emitLog(message, level, Calendar.getInstance().getTime());
	}

	/**
	 * Transform tags from Cucumber to RP format
	 *
	 * @param tags - Cucumber tags
	 * @return set of tags
	 */
	static Set<ItemAttributesRQ> extractPickleTags(List<PickleTag> tags) {
		return tags.stream().map(t -> new ItemAttributesRQ(null, t.getName())).collect(Collectors.toSet());
	}

	/**
	 * Transform tags from Cucumber to RP format
	 *
	 * @param tags - Cucumber tags
	 * @return set of tags
	 */
	public static Set<ItemAttributesRQ> extractAttributes(List<Tag> tags) {
		Set<ItemAttributesRQ> attributes = new HashSet<>();
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
	 * Map Cucumber statuses to RP item statuses
	 *
	 * @param status - Cucumber status
	 * @return RP test item status and null if status is null
	 */
	static String mapItemStatus(Result.Type status) {
		if (status == null) {
			return null;
		} else {
			if (STATUS_MAPPING.get(status) == null) {
				LOGGER.error(String.format("Unable to find direct mapping between Cucumber and ReportPortal for TestItem with status: '%s'.",
						status
				));
				return ItemStatus.SKIPPED.name();
			}
			return STATUS_MAPPING.get(status).name();
		}
	}

	/**
	 * Generate name representation
	 *
	 * @param prefix   - substring to be prepended at the beginning (optional)
	 * @param infix    - substring to be inserted between keyword and name
	 * @param argument - main text to process
	 * @return transformed string
	 */
	public static String buildName(String prefix, String infix, String argument) {
		return (prefix == null ? EMPTY : prefix) + infix + argument;
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

	@Nullable
	public static Set<ItemAttributesRQ> getAttributes(TestStep testStep) {
		Field definitionMatchField = getDefinitionMatchField(testStep);
		if (definitionMatchField != null) {
			try {
				Method method = retrieveMethod(definitionMatchField, testStep);
				Attributes attributesAnnotation = method.getAnnotation(Attributes.class);
				if (attributesAnnotation != null) {
					return AttributeParser.retrieveAttributes(attributesAnnotation);
				}
			} catch (NoSuchFieldException | IllegalAccessException e) {
				return null;
			}
		}
		return null;
	}

	@Nullable
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
			} catch (NoSuchFieldException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
				return null;
			}

		} else {
			return null;
		}

	}

	@Nonnull
	public static String getCodeRef(@Nonnull String uri, int line) {
		String myUri = uri.startsWith(FILE_PREFIX) ? uri.substring(FILE_PREFIX.length()) : uri;
		return myUri + ":" + line;
	}

	static List<ParameterResource> getParameters(String codeRef, List<cucumber.api.Argument> arguments) {
		List<Pair<String, String>> params = ofNullable(arguments).map(a -> IntStream.range(0, a.size())
				.mapToObj(i -> Pair.of("arg" + i, a.get(i).getValue()))
				.collect(Collectors.toList())).orElse(null);
		return ParameterUtils.getParameters(codeRef, params);
	}

	private static Method retrieveMethod(Field definitionMatchField, TestStep testStep)
			throws IllegalAccessException, NoSuchFieldException {
		StepDefinitionMatch stepDefinitionMatch = (StepDefinitionMatch) definitionMatchField.get(testStep);
		Field stepDefinitionField = stepDefinitionMatch.getClass().getDeclaredField(STEP_DEFINITION_FIELD_NAME);
		stepDefinitionField.setAccessible(true);
		Object javaStepDefinition = stepDefinitionField.get(stepDefinitionMatch);
		Field methodField = javaStepDefinition.getClass().getDeclaredField(METHOD_FIELD_NAME);
		methodField.setAccessible(true);
		return (Method) methodField.get(javaStepDefinition);
	}

	private static final java.util.function.Function<List<cucumber.api.Argument>, List<?>> ARGUMENTS_TRANSFORM = arguments -> ofNullable(
			arguments).map(args -> args.stream().map(cucumber.api.Argument::getValue).collect(Collectors.toList())).orElse(null);

	@SuppressWarnings("unchecked")
	public static TestCaseIdEntry getTestCaseId(TestStep testStep, String codeRef) {
		Field definitionMatchField = getDefinitionMatchField(testStep);
		List<cucumber.api.Argument> arguments = ((PickleStepTestStep) testStep).getDefinitionArgument();
		if (definitionMatchField != null) {
			try {
				Method method = retrieveMethod(definitionMatchField, testStep);
				return TestCaseIdUtils.getTestCaseId(method.getAnnotation(TestCaseId.class),
						method,
						codeRef,
						(List<Object>) ARGUMENTS_TRANSFORM.apply(arguments)
				);
			} catch (NoSuchFieldException | IllegalAccessException ignore) {
			}
		}
		return getTestCaseId(codeRef, arguments);
	}

	@SuppressWarnings("unchecked")
	private static TestCaseIdEntry getTestCaseId(String codeRef, List<cucumber.api.Argument> arguments) {
		return TestCaseIdUtils.getTestCaseId(codeRef, (List<Object>) ARGUMENTS_TRANSFORM.apply(arguments));
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

	@Nonnull
	public static String getDescription(@Nonnull String uri) {
		return uri;
	}

	public static Pair<String, String> getHookTypeAndName(HookType hookType) {
		String name = null;
		String type = null;
		switch (hookType) {
			case Before:
				name = "Before hooks";
				type = "BEFORE_TEST";
				break;
			case After:
				name = "After hooks";
				type = "AFTER_TEST";
				break;
			case AfterStep:
				name = "After step";
				type = "AFTER_METHOD";
				break;
			case BeforeStep:
				name = "Before step";
				type = "BEFORE_METHOD";
				break;
		}
		return Pair.of(type, name);
	}
}