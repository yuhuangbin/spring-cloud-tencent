/*
 * Tencent is pleased to support the open source community by making Spring Cloud Tencent available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

package com.tencent.cloud.common.rule;

import java.util.List;
import java.util.Map;

/**
 * The util for condition expression.
 * @author lepdou 2022-07-11
 */
public final class ConditionUtils {

	private ConditionUtils() {
	}

	public static boolean match(Map<String, String> actualValues, List<Condition> conditions) {
		for (Condition condition : conditions) {
			List<String> expectedValues = condition.getValues();
			String operation = condition.getOperation();
			String key = condition.getKey();
			String actualValue = actualValues.get(key);

			if (!Operation.match(expectedValues, actualValue, operation)) {
				return false;
			}
		}
		return true;
	}
}
