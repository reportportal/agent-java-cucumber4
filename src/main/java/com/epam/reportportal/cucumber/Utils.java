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

import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.LogLevel;
import cucumber.api.Result;
import cucumber.api.TestStep;
import cucumber.runtime.StepDefinitionMatch;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Optional.ofNullable;

public class Utils {
	private static final String EMPTY = "";
	private static final String DEFINITION_MATCH_FIELD_NAME = "definitionMatch";
	private static final String STEP_DEFINITION_FIELD_NAME = "stepDefinition";
	private static final String METHOD_FIELD_NAME = "method";
	public static final String ONE_SPACE = "\u00A0";
	private static final String NEW_LINE = "\r\n";
	public static final String TABLE_INDENT = "\u00A0\u00A0\u00A0\u00A0";
	public static final String TABLE_COLUMN_SEPARATOR = "|";
	public static final String TABLE_ROW_SEPARATOR = "-";

	private Utils() {
		throw new AssertionError("No instances should exist for the class!");
	}

	private static final Map<Result.Type, ItemStatus> COMMON_STATUS_MAPPING = new HashMap<Result.Type, ItemStatus>() {{
		put(Result.Type.PASSED, ItemStatus.PASSED);
		put(Result.Type.FAILED, ItemStatus.FAILED);
		put(Result.Type.SKIPPED, ItemStatus.SKIPPED);
		put(Result.Type.PENDING, ItemStatus.SKIPPED);
		put(Result.Type.AMBIGUOUS, ItemStatus.SKIPPED);
		put(Result.Type.UNDEFINED, ItemStatus.SKIPPED);
	}};

	private static final Map<Result.Type, String> COMMON_LOG_LEVEL_MAPPING = new HashMap<Result.Type, String>() {{
		put(Result.Type.PASSED, LogLevel.INFO.name());
		put(Result.Type.FAILED, LogLevel.ERROR.name());
		put(Result.Type.SKIPPED, LogLevel.WARN.name());
		put(Result.Type.PENDING, LogLevel.WARN.name());
		put(Result.Type.AMBIGUOUS, LogLevel.WARN.name());
		put(Result.Type.UNDEFINED, LogLevel.WARN.name());
	}};

	public static final Map<Result.Type, ItemStatus> STATUS_MAPPING;
	public static final Map<Result.Type, String> LOG_LEVEL_MAPPING;

	static {
		Optional<Result.Type> unused = Arrays.stream(Result.Type.values()).filter(e -> "UNUSED".equals(e.name())).findAny();
		if (unused.isPresent()) {
			COMMON_STATUS_MAPPING.put(unused.get(), ItemStatus.SKIPPED);
			COMMON_LOG_LEVEL_MAPPING.put(unused.get(), LogLevel.WARN.name());
		}
		STATUS_MAPPING = Collections.unmodifiableMap(COMMON_STATUS_MAPPING);
		LOG_LEVEL_MAPPING = Collections.unmodifiableMap(COMMON_LOG_LEVEL_MAPPING);
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

	public static Method retrieveMethod(Field definitionMatchField, TestStep testStep) throws IllegalAccessException, NoSuchFieldException {
		StepDefinitionMatch stepDefinitionMatch = (StepDefinitionMatch) definitionMatchField.get(testStep);
		Field stepDefinitionField = stepDefinitionMatch.getClass().getDeclaredField(STEP_DEFINITION_FIELD_NAME);
		stepDefinitionField.setAccessible(true);
		Object javaStepDefinition = stepDefinitionField.get(stepDefinitionMatch);
		Field methodField = javaStepDefinition.getClass().getDeclaredField(METHOD_FIELD_NAME);
		methodField.setAccessible(true);
		return (Method) methodField.get(javaStepDefinition);
	}

	public static final java.util.function.Function<List<cucumber.api.Argument>, List<?>> ARGUMENTS_TRANSFORM = arguments -> ofNullable(
			arguments).map(args -> args.stream().map(cucumber.api.Argument::getValue).collect(Collectors.toList())).orElse(null);

	public static Field getDefinitionMatchField(TestStep testStep) {

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

	/**
	 * Converts a table represented as List of Lists to a formatted table string
	 *
	 * @param table a table object
	 * @return string representation of the table
	 */
	@Nonnull
	public static String formatDataTable(@Nonnull final List<List<String>> table) {
		StringBuilder result = new StringBuilder();
		int tableLength = table.stream().mapToInt(List::size).max().orElse(-1);
		List<Iterator<String>> iterList = table.stream().map(List::iterator).collect(Collectors.toList());
		List<Integer> colSizes = IntStream.range(0, tableLength)
				.mapToObj(n -> iterList.stream().filter(Iterator::hasNext).map(Iterator::next).collect(Collectors.toList()))
				.map(col -> col.stream().mapToInt(String::length).max().orElse(0))
				.collect(Collectors.toList());

		boolean header = true;
		for (List<String> row : table) {
			result.append(TABLE_INDENT).append(TABLE_COLUMN_SEPARATOR);
			for (int i = 0; i < row.size(); i++) {
				String cell = row.get(i);
				int maxSize = colSizes.get(i) - cell.length() + 2;
				int lSpace = maxSize / 2;
				int rSpace = maxSize - lSpace;
				IntStream.range(0, lSpace).forEach(j -> result.append(ONE_SPACE));
				result.append(cell);
				IntStream.range(0, rSpace).forEach(j -> result.append(ONE_SPACE));
				result.append(TABLE_COLUMN_SEPARATOR);
			}
			if (header) {
				header = false;
				result.append(NEW_LINE);
				result.append(TABLE_INDENT).append(TABLE_COLUMN_SEPARATOR);
				for (int i = 0; i < row.size(); i++) {
					int maxSize = colSizes.get(i) + 2;
					IntStream.range(0, maxSize).forEach(j -> result.append(TABLE_ROW_SEPARATOR));
					result.append(TABLE_COLUMN_SEPARATOR);
				}
			}
			result.append(NEW_LINE);
		}
		return result.toString().trim();
	}
}
