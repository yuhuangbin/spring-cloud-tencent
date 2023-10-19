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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.CollectionUtils;

/**
 * The util for key/value pair.
 * @author lepdou 2022-07-11
 */
public final class KVPairUtils {

	private KVPairUtils() {
	}

	public static Map<String, String> toMap(List<KVPair> labels) {
		if (CollectionUtils.isEmpty(labels)) {
			return Collections.emptyMap();
		}

		Map<String, String> result = new HashMap<>();
		labels.forEach(label -> {
			result.put(label.getKey(), label.getValue());
		});

		return result;
	}
}
