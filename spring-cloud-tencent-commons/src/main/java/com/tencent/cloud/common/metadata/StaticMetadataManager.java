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

package com.tencent.cloud.common.metadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.tencent.cloud.common.metadata.config.MetadataLocalProperties;
import com.tencent.cloud.common.spi.InstanceMetadataProvider;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.CollectionUtils;

/**
 * manage metadata from env/config file/custom spi.
 *
 * @author lepdou, Haotian Zhang
 */
public class StaticMetadataManager {
	/**
	 * the metadata key of region.
	 */
	public static final String LOCATION_KEY_REGION = "region";
	/**
	 * the metadata key of zone.
	 */
	public static final String LOCATION_KEY_ZONE = "zone";
	/**
	 * the metadata key of campus/datacenter.
	 */
	public static final String LOCATION_KEY_CAMPUS = "campus";
	private static final Logger LOGGER = LoggerFactory.getLogger(StaticMetadataManager.class);
	private static final String ENV_METADATA_PREFIX = "SCT_METADATA_CONTENT_";
	private static final int ENV_METADATA_PREFIX_LENGTH = ENV_METADATA_PREFIX.length();
	private static final String ENV_METADATA_CONTENT_TRANSITIVE = "SCT_METADATA_CONTENT_TRANSITIVE";
	private static final String ENV_METADATA_CONTENT_DISPOSABLE = "SCT_METADATA_CONTENT_DISPOSABLE";
	/**
	 * This is the key of the header's key list needed to be transmitted. The list is a string split with ,.
	 * The value mapped by this key was specified by user.
	 * This is configured in environment variables.
	 */
	private static final String ENV_TRAFFIC_CONTENT_RAW_TRANSHEADERS = "SCT_TRAFFIC_CONTENT_RAW_TRANSHEADERS";
	private static final String ENV_METADATA_ZONE = "SCT_METADATA_ZONE";
	private static final String ENV_METADATA_REGION = "SCT_METADATA_REGION";
	private static final String ENV_METADATA_CAMPUS = "SCT_METADATA_CAMPUS";
	private Map<String, String> envMetadata;
	private Map<String, String> envTransitiveMetadata;
	private Map<String, String> envDisposableMetadata;
	private Map<String, String> envNotReportMetadata;
	private Map<String, String> configMetadata;
	private Map<String, String> configTransitiveMetadata;
	private Map<String, String> configDisposableMetadata;
	private String configTransHeaders;
	private Map<String, String> customSPIMetadata;
	private Map<String, String> customSPITransitiveMetadata;
	private Map<String, String> customSPIDisposableMetadata;
	private Map<String, String> mergedStaticMetadata;
	private Map<String, String> mergedStaticTransitiveMetadata;
	private Map<String, String> mergedStaticDisposableMetadata;
	private String region;
	private String zone;
	private String campus;

	public StaticMetadataManager(MetadataLocalProperties metadataLocalProperties,
			List<InstanceMetadataProvider> instanceMetadataProviders) {
		parseConfigMetadata(metadataLocalProperties);

		parseEnvMetadata();

		parseCustomMetadata(instanceMetadataProviders);

		parseLocationMetadata(metadataLocalProperties, instanceMetadataProviders);

		merge();

		LOGGER.info("[SCT] Loaded static metadata info. {}", this);
	}

	@SuppressWarnings("DuplicatedCode")
	private void parseEnvMetadata() {
		Map<String, String> allEnvs = System.getenv();

		envMetadata = new HashMap<>();
		envNotReportMetadata = new HashMap<>();
		// parse all metadata
		for (Map.Entry<String, String> entry : allEnvs.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			if (StringUtils.isNotBlank(key)
					&& (key.startsWith(ENV_METADATA_PREFIX) || key.equals(ENV_TRAFFIC_CONTENT_RAW_TRANSHEADERS))
					&& !key.equals(ENV_METADATA_CONTENT_TRANSITIVE)
					&& !key.equals(ENV_METADATA_CONTENT_DISPOSABLE)) {
				String sourceKey = "";
				if (key.equals(ENV_TRAFFIC_CONTENT_RAW_TRANSHEADERS)) {
					sourceKey = key;
					envNotReportMetadata.put(sourceKey, value);
				}
				else {
					sourceKey = StringUtils.substring(key, ENV_METADATA_PREFIX_LENGTH);
					envMetadata.put(sourceKey, value);
				}

				LOGGER.info("[SCT] resolve metadata from env. key = {}, value = {}", sourceKey, value);
			}
		}
		envMetadata = Collections.unmodifiableMap(envMetadata);

		envTransitiveMetadata = new HashMap<>();
		// parse transitive metadata
		String transitiveKeys = allEnvs.get(ENV_METADATA_CONTENT_TRANSITIVE);
		if (StringUtils.isNotBlank(transitiveKeys)) {
			String[] keyArr = StringUtils.split(transitiveKeys, ",");
			if (keyArr != null && keyArr.length > 0) {
				for (String key : keyArr) {
					String value = envMetadata.get(key);
					if (StringUtils.isNotBlank(value)) {
						envTransitiveMetadata.put(key, value);
					}
				}
			}
		}
		envTransitiveMetadata = Collections.unmodifiableMap(envTransitiveMetadata);

		envDisposableMetadata = new HashMap<>();
		// parse disposable metadata
		String disposableKeys = allEnvs.get(ENV_METADATA_CONTENT_DISPOSABLE);
		if (StringUtils.isNotBlank(disposableKeys)) {
			String[] keyArr = StringUtils.split(disposableKeys, ",");
			if (keyArr != null && keyArr.length > 0) {
				for (String key : keyArr) {
					String value = envMetadata.get(key);
					if (StringUtils.isNotBlank(value)) {
						envDisposableMetadata.put(key, value);
					}
				}
			}
		}
		envDisposableMetadata = Collections.unmodifiableMap(envDisposableMetadata);
	}

	private void parseConfigMetadata(MetadataLocalProperties metadataLocalProperties) {
		Map<String, String> allMetadata = metadataLocalProperties.getContent();
		List<String> transitiveKeys = metadataLocalProperties.getTransitive();
		List<String> disposableKeys = metadataLocalProperties.getDisposable();
		List<String> headers = metadataLocalProperties.getHeaders();

		Map<String, String> transitiveResult = new HashMap<>();
		for (String key : transitiveKeys) {
			if (allMetadata.containsKey(key)) {
				transitiveResult.put(key, allMetadata.get(key));
			}
		}

		Map<String, String> disposableResult = new HashMap<>();
		for (String key : disposableKeys) {
			if (allMetadata.containsKey(key)) {
				disposableResult.put(key, allMetadata.get(key));
			}
		}

		configTransitiveMetadata = Collections.unmodifiableMap(transitiveResult);
		configDisposableMetadata = Collections.unmodifiableMap(disposableResult);
		configTransHeaders = CollectionUtils.isEmpty(headers) ? null : String.join(",", headers);
		configMetadata = Collections.unmodifiableMap(allMetadata);
	}

	@SuppressWarnings("DuplicatedCode")
	private void parseCustomMetadata(List<InstanceMetadataProvider> instanceMetadataProviders) {
		// init customSPIMetadata
		customSPIMetadata = new HashMap<>();
		customSPITransitiveMetadata = new HashMap<>();
		customSPIDisposableMetadata = new HashMap<>();
		if (!CollectionUtils.isEmpty(instanceMetadataProviders)) {
			instanceMetadataProviders.forEach(this::parseCustomMetadata);
		}
		customSPIMetadata = Collections.unmodifiableMap(customSPIMetadata);
		customSPITransitiveMetadata = Collections.unmodifiableMap(customSPITransitiveMetadata);
		customSPIDisposableMetadata = Collections.unmodifiableMap(customSPIDisposableMetadata);
	}

	@SuppressWarnings("DuplicatedCode")
	private void parseCustomMetadata(InstanceMetadataProvider instanceMetadataProvider) {
		// resolve all metadata
		Map<String, String> allMetadata = instanceMetadataProvider.getMetadata();
		if (!CollectionUtils.isEmpty(allMetadata)) {
			customSPIMetadata.putAll(allMetadata);
		}

		// resolve transitive metadata
		Set<String> transitiveKeys = instanceMetadataProvider.getTransitiveMetadataKeys();
		Map<String, String> transitiveMetadata = new HashMap<>();
		if (!CollectionUtils.isEmpty(transitiveKeys)) {
			for (String key : transitiveKeys) {
				if (customSPIMetadata.containsKey(key)) {
					transitiveMetadata.put(key, customSPIMetadata.get(key));
				}
			}
		}
		customSPITransitiveMetadata.putAll(transitiveMetadata);

		Set<String> disposableKeys = instanceMetadataProvider.getDisposableMetadataKeys();
		Map<String, String> disposableMetadata = new HashMap<>();
		if (!CollectionUtils.isEmpty(disposableKeys)) {
			for (String key : disposableKeys) {
				if (customSPIMetadata.containsKey(key)) {
					disposableMetadata.put(key, customSPIMetadata.get(key));
				}
			}
		}
		customSPIDisposableMetadata.putAll(disposableMetadata);
	}

	private void merge() {
		// the priority is : custom > env > config
		Map<String, String> mergedMetadataResult = new HashMap<>();

		mergedMetadataResult.putAll(configMetadata);
		mergedMetadataResult.putAll(envMetadata);
		mergedMetadataResult.putAll(customSPIMetadata);

		this.mergedStaticMetadata = Collections.unmodifiableMap(mergedMetadataResult);

		Map<String, String> mergedTransitiveMetadataResult = new HashMap<>();
		mergedTransitiveMetadataResult.putAll(configTransitiveMetadata);
		mergedTransitiveMetadataResult.putAll(envTransitiveMetadata);
		mergedTransitiveMetadataResult.putAll(customSPITransitiveMetadata);
		this.mergedStaticTransitiveMetadata = Collections.unmodifiableMap(mergedTransitiveMetadataResult);

		Map<String, String> mergedDisposableMetadataResult = new HashMap<>();
		mergedDisposableMetadataResult.putAll(configDisposableMetadata);
		mergedDisposableMetadataResult.putAll(envDisposableMetadata);
		mergedDisposableMetadataResult.putAll(customSPIDisposableMetadata);
		this.mergedStaticDisposableMetadata = Collections.unmodifiableMap(mergedDisposableMetadataResult);
	}

	private void parseLocationMetadata(MetadataLocalProperties metadataLocalProperties,
			List<InstanceMetadataProvider> instanceMetadataProviders) {
		// resolve region info
		if (!CollectionUtils.isEmpty(instanceMetadataProviders)) {
			Set<String> providerRegions = instanceMetadataProviders.stream().map(InstanceMetadataProvider::getRegion).filter(region -> !StringUtils.isBlank(region)).collect(Collectors.toSet());
			if (!CollectionUtils.isEmpty(providerRegions)) {
				if (providerRegions.size() > 1) {
					throw new IllegalArgumentException("Multiple Regions Provided in InstanceMetadataProviders");
				}
				region = providerRegions.iterator().next();
			}
		}
		if (StringUtils.isBlank(region)) {
			region = System.getenv(ENV_METADATA_REGION);
		}
		if (StringUtils.isBlank(region)) {
			region = metadataLocalProperties.getContent().get(LOCATION_KEY_REGION);
		}

		// resolve zone info
		if (!CollectionUtils.isEmpty(instanceMetadataProviders)) {
			Set<String> providerZones = instanceMetadataProviders.stream().map(InstanceMetadataProvider::getZone).filter(zone -> !StringUtils.isBlank(zone)).collect(Collectors.toSet());
			if (!CollectionUtils.isEmpty(providerZones)) {
				if (providerZones.size() > 1) {
					throw new IllegalArgumentException("Multiple Zones Provided in InstanceMetadataProviders");
				}
				zone = providerZones.iterator().next();
			}
		}
		if (StringUtils.isBlank(zone)) {
			zone = System.getenv(ENV_METADATA_ZONE);
		}
		if (StringUtils.isBlank(zone)) {
			zone = metadataLocalProperties.getContent().get(LOCATION_KEY_ZONE);
		}

		// resolve campus info
		if (!CollectionUtils.isEmpty(instanceMetadataProviders)) {
			Set<String> providerCampus = instanceMetadataProviders.stream().map(InstanceMetadataProvider::getCampus).filter(campus -> !StringUtils.isBlank(campus)).collect(Collectors.toSet());
			if (!CollectionUtils.isEmpty(providerCampus)) {
				if (providerCampus.size() > 1) {
					throw new IllegalArgumentException("Multiple Campus Provided in InstanceMetadataProviders");
				}
				campus = providerCampus.iterator().next();
			}
		}
		if (StringUtils.isBlank(campus)) {
			campus = System.getenv(ENV_METADATA_CAMPUS);
		}
		if (StringUtils.isBlank(campus)) {
			campus = metadataLocalProperties.getContent().get(LOCATION_KEY_CAMPUS);
		}
	}

	public Map<String, String> getAllEnvMetadata() {
		return envMetadata;
	}

	public String getTransHeaderFromEnv() {
		return envNotReportMetadata.get(ENV_TRAFFIC_CONTENT_RAW_TRANSHEADERS);
	}

	public String getTransHeaderFromConfig() {
		return configTransHeaders;
	}

	public String getTransHeader() {
		Set<String> transHeaderSet = new HashSet<>();

		String transHeaderFromEnv = getTransHeaderFromEnv();
		String transHeaderFromConfig = getTransHeaderFromConfig();

		Set<String> transHeaderFromEnvSet = StringUtils.isNotBlank(transHeaderFromEnv)
				? Arrays.stream(transHeaderFromEnv.split(",")).collect(Collectors.toSet())
				: Collections.emptySet();
		Set<String> transHeaderFromConfigSet = StringUtils.isNotBlank(transHeaderFromConfig)
				? Arrays.stream(transHeaderFromConfig.split(",")).collect(Collectors.toSet())
				: Collections.emptySet();

		transHeaderSet.addAll(transHeaderFromEnvSet);
		transHeaderSet.addAll(transHeaderFromConfigSet);

		return new ArrayList<>(transHeaderSet).stream().sorted().collect(Collectors.joining(","));
	}

	public Map<String, String> getEnvTransitiveMetadata() {
		return envTransitiveMetadata;
	}

	public Map<String, String> getEnvDisposableMetadata() {
		return envDisposableMetadata;
	}

	public Map<String, String> getAllConfigMetadata() {
		return configMetadata;
	}

	public Map<String, String> getConfigTransitiveMetadata() {
		return configTransitiveMetadata;
	}

	public Map<String, String> getConfigDisposableMetadata() {
		return configDisposableMetadata;
	}

	public Map<String, String> getAllCustomMetadata() {
		return customSPIMetadata;
	}

	public Map<String, String> getCustomSPITransitiveMetadata() {
		return customSPITransitiveMetadata;
	}

	public Map<String, String> getCustomSPIDisposableMetadata() {
		return customSPIDisposableMetadata;
	}

	public Map<String, String> getMergedStaticMetadata() {
		return mergedStaticMetadata;
	}

	public Map<String, String> getMergedStaticTransitiveMetadata() {
		return mergedStaticTransitiveMetadata;
	}

	public Map<String, String> getMergedStaticDisposableMetadata() {
		return mergedStaticDisposableMetadata;
	}

	public String getZone() {
		return zone;
	}

	public String getRegion() {
		return region;
	}

	public String getCampus() {
		return campus;
	}

	public Map<String, String> getLocationMetadata() {
		Map<String, String> locationMetadata = new HashMap<>();
		if (StringUtils.isNotBlank(region)) {
			locationMetadata.put(LOCATION_KEY_REGION, region);
		}
		if (StringUtils.isNotBlank(zone)) {
			locationMetadata.put(LOCATION_KEY_ZONE, zone);
		}
		if (StringUtils.isNotBlank(campus)) {
			locationMetadata.put(LOCATION_KEY_CAMPUS, campus);
		}
		return locationMetadata;
	}

	@Override
	public String toString() {
		return "StaticMetadataManager{" +
				"envMetadata=" + envMetadata +
				", envTransitiveMetadata=" + envTransitiveMetadata +
				", configMetadata=" + configMetadata +
				", configTransitiveMetadata=" + configTransitiveMetadata +
				", configTransHeaders='" + configTransHeaders + '\'' +
				", customSPIMetadata=" + customSPIMetadata +
				", customSPITransitiveMetadata=" + customSPITransitiveMetadata +
				", mergedStaticMetadata=" + mergedStaticMetadata +
				", mergedStaticTransitiveMetadata=" + mergedStaticTransitiveMetadata +
				", zone='" + zone + '\'' +
				", region='" + region + '\'' +
				", campus='" + campus + '\'' +
				'}';
	}
}
