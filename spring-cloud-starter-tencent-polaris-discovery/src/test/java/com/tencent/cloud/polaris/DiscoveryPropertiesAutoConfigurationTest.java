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
 */

package com.tencent.cloud.polaris;

import com.tencent.cloud.polaris.context.config.PolarisContextAutoConfiguration;
import com.tencent.cloud.polaris.discovery.PolarisDiscoveryHandler;
import com.tencent.cloud.polaris.extend.consul.ConsulContextProperties;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link DiscoveryPropertiesAutoConfiguration}.
 *
 * @author Haotian Zhang
 */
public class DiscoveryPropertiesAutoConfigurationTest {

	@Test
	public void testDefaultInitialization() {
		ApplicationContextRunner applicationContextRunner = new ApplicationContextRunner().withConfiguration(
				AutoConfigurations.of(
						PolarisContextAutoConfiguration.class,
						DiscoveryPropertiesAutoConfiguration.class));
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(DiscoveryPropertiesAutoConfiguration.class);
			assertThat(context).hasSingleBean(PolarisDiscoveryProperties.class);
			assertThat(context).hasSingleBean(ConsulContextProperties.class);
			assertThat(context).hasSingleBean(PolarisDiscoveryHandler.class);
			assertThat(context).hasSingleBean(DiscoveryConfigModifier.class);
		});
	}
}
