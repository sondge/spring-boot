/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.env;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.boot.origin.PropertySourceOrigin;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.support.StandardServletEnvironment;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An {@link EnvironmentPostProcessor} that parses JSON from
 * {@code spring.application.json} or equivalently {@code SPRING_APPLICATION_JSON} and
 * adds it as a map property source to the {@link Environment}. The new properties are
 * added with higher priority than the system properties.
 *
 * 解析 environment 中的 spring.application.json 或 SPRING_APPLICATION_JSON 对应的 JSON 格式的属性值，
 * 创建新的 PropertySource 对象，添加到其中
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @author Madhura Bhave
 * @author Artsiom Yudovin
 * @since 1.3.0
 */
public class SpringApplicationJsonEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	/**
	 * Name of the {@code spring.application.json} property.
	 *
	 * spring.application.json 属性名称
	 */
	public static final String SPRING_APPLICATION_JSON_PROPERTY = "spring.application.json";

	/**
	 * Name of the {@code SPRING_APPLICATION_JSON} environment variable.
	 *
	 * SPRING_APPLICATION_JSON 的环境名
	 */
	public static final String SPRING_APPLICATION_JSON_ENVIRONMENT_VARIABLE = "SPRING_APPLICATION_JSON";
	/**
	 * servlet 环境类名
	 */
	private static final String SERVLET_ENVIRONMENT_CLASS = "org.springframework.web."
			+ "context.support.StandardServletEnvironment";
	/**
	 * servlet 环境属性资源的名称
	 */
	private static final Set<String> SERVLET_ENVIRONMENT_PROPERTY_SOURCES = new LinkedHashSet<>(
			Arrays.asList(StandardServletEnvironment.JNDI_PROPERTY_SOURCE_NAME,
					StandardServletEnvironment.SERVLET_CONTEXT_PROPERTY_SOURCE_NAME,
					StandardServletEnvironment.SERVLET_CONFIG_PROPERTY_SOURCE_NAME));

	/**
	 * The default order for the processor.
	 *
	 * 这个处理的默认顺序
	 */
	public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 5;
	/**
	 * 这个处理的顺序
	 */
	private int order = DEFAULT_ORDER;

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		// 获取 MutablePropertySources 属性
		MutablePropertySources propertySources = environment.getPropertySources();
		// 执行 json
		propertySources.stream().map(JsonPropertyValue::get).filter(Objects::nonNull).findFirst()
				.ifPresent((v) -> processJson(environment, v));
	}

	private void processJson(ConfigurableEnvironment environment, JsonPropertyValue propertyValue) {
		// 获取 Json 解析器
		JsonParser parser = JsonParserFactory.getJsonParser();
		// 解析成 map
		Map<String, Object> map = parser.parseMap(propertyValue.getJson());
		// 如果 map 是非空，添加到 environment 中
		if (!map.isEmpty()) {
			addJsonPropertySource(environment, new JsonPropertySource(propertyValue, flatten(map)));
		}
	}

	/**
	 * Flatten the map keys using period separator.
	 * @param map the map that should be flattened
	 * @return the flattened map
	 */
	private Map<String, Object> flatten(Map<String, Object> map) {
		Map<String, Object> result = new LinkedHashMap<>();
		flatten(null, result, map);
		return result;
	}

	private void flatten(String prefix, Map<String, Object> result, Map<String, Object> map) {
		String namePrefix = (prefix != null) ? prefix + "." : "";
		map.forEach((key, value) -> extract(namePrefix + key, result, value));
	}

	@SuppressWarnings("unchecked")
	private void extract(String name, Map<String, Object> result, Object value) {
		// 内嵌的 Map 格式
		if (value instanceof Map) {
			if (CollectionUtils.isEmpty((Map<?, ?>) value)) {
				result.put(name, value);
				return;
			}
			flatten(name, result, (Map<String, Object>) value);
		}
		else if (value instanceof Collection) {
			// 内嵌的 Collection
			if (CollectionUtils.isEmpty((Collection<?>) value)) {
				result.put(name, value);
				return;
			}
			int index = 0;
			for (Object object : (Collection<Object>) value) {
				extract(name + "[" + index + "]", result, object);
				index++;
			}
		}
		else {
			// 普通格式，添加到 result 中
			result.put(name, value);
		}
	}

	private void addJsonPropertySource(ConfigurableEnvironment environment, PropertySource<?> source) {
		// 获取 MutablePropertySources
		MutablePropertySources sources = environment.getPropertySources();
		// 查找对应的属性资源
		String name = findPropertySource(sources);
		// 如果包含在之前
		if (sources.contains(name)) {
			// 将资源加入前面
			sources.addBefore(name, source);
		}
		else {
			// 加入第一个
			sources.addFirst(source);
		}
	}

	private String findPropertySource(MutablePropertySources sources) {
		// 在 Servlet 环境下，且有 JNDI_PROPERTY_SOURCE_NAME 属性，则返回 StandardServletEnvironment.JNDI_PROPERTY_SOURCE_NAME
		if (ClassUtils.isPresent(SERVLET_ENVIRONMENT_CLASS, null)) {
			PropertySource<?> servletPropertySource = sources.stream()
					.filter((source) -> SERVLET_ENVIRONMENT_PROPERTY_SOURCES.contains(source.getName())).findFirst()
					.orElse(null);
			if (servletPropertySource != null) {
				return servletPropertySource.getName();
			}
		}
		// 否则，返回 SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME 属性
		return StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME;
	}

	private static class JsonPropertySource extends MapPropertySource implements OriginLookup<String> {

		private final JsonPropertyValue propertyValue;

		JsonPropertySource(JsonPropertyValue propertyValue, Map<String, Object> source) {
			super(SPRING_APPLICATION_JSON_PROPERTY, source);
			this.propertyValue = propertyValue;
		}

		@Override
		public Origin getOrigin(String key) {
			return this.propertyValue.getOrigin();
		}

	}

	private static class JsonPropertyValue {

		// 获取候选数组
		private static final String[] CANDIDATES = { SPRING_APPLICATION_JSON_PROPERTY,
				SPRING_APPLICATION_JSON_ENVIRONMENT_VARIABLE };

		private final PropertySource<?> propertySource;

		private final String propertyName;

		private final String json;

		JsonPropertyValue(PropertySource<?> propertySource, String propertyName, String json) {
			this.propertySource = propertySource;
			this.propertyName = propertyName;
			this.json = json;
		}

		String getJson() {
			return this.json;
		}

		Origin getOrigin() {
			return PropertySourceOrigin.get(this.propertySource, this.propertyName);
		}

		static JsonPropertyValue get(PropertySource<?> propertySource) {
			// 遍历 CANDIDATES 数组
			for (String candidate : CANDIDATES) {
				// 获得 candidate 对应的属性值
				Object value = propertySource.getProperty(candidate);
				if (value instanceof String && StringUtils.hasLength((String) value)) {
					// 创建 JsonPropertyValue 对象，然后返回
					return new JsonPropertyValue(propertySource, candidate, (String) value);
				}
			}
			return null;
		}

	}

}
