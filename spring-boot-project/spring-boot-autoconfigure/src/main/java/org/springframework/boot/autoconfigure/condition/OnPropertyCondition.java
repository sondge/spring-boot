/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.boot.autoconfigure.condition;

import org.springframework.boot.autoconfigure.condition.ConditionMessage.Style;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link Condition} that checks if properties are defined in environment.
 *
 * @author Maciej Walkowiak
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 * @see ConditionalOnProperty
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
class OnPropertyCondition extends SpringBootCondition {

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		// 获得 @ConditionalProperty 注解的属性
		List<AnnotationAttributes> allAnnotationAttributes = annotationAttributesFromMultiValueMap(
				metadata.getAllAnnotationAttributes(ConditionalOnProperty.class.getName()));
		// 定义不匹配的列表
		List<ConditionMessage> noMatch = new ArrayList<>();
		// 定义匹配的列表
		List<ConditionMessage> match = new ArrayList<>();
		// 遍历 allAnnotationAttributes 属性数组，逐个判断是否匹配，并添加到结果
		for (AnnotationAttributes annotationAttributes : allAnnotationAttributes) {
			ConditionOutcome outcome = determineOutcome(annotationAttributes, context.getEnvironment());
			(outcome.isMatch() ? match : noMatch).add(outcome.getConditionMessage());
		}
		// 如果有不匹配的，则返回不匹配
		if (!noMatch.isEmpty()) {
			return ConditionOutcome.noMatch(ConditionMessage.of(noMatch));
		}
		// 如果都匹配，则返回匹配
		return ConditionOutcome.match(ConditionMessage.of(match));
	}

	private List<AnnotationAttributes> annotationAttributesFromMultiValueMap(
			MultiValueMap<String, Object> multiValueMap) {
		// 获取属性 Map 列表
		List<Map<String, Object>> maps = new ArrayList<>();
		// 对属性遍历
		multiValueMap.forEach((key, value) -> {
			// 循环遍历
			for (int i = 0; i < value.size(); i++) {
				Map<String, Object> map;
				if (i < maps.size()) {
					map = maps.get(i);
				}
				else {
					map = new HashMap<>();
					maps.add(map);
				}
				map.put(key, value.get(i));
			}
		});
		// 转化将属性 Map 转化为 AnnotationAttributes 列表
		List<AnnotationAttributes> annotationAttributes = new ArrayList<>(maps.size());
		for (Map<String, Object> map : maps) {
			annotationAttributes.add(AnnotationAttributes.fromMap(map));
		}
		return annotationAttributes;
	}

	private ConditionOutcome determineOutcome(AnnotationAttributes annotationAttributes, PropertyResolver resolver) {
		// 解析成 Spec
		Spec spec = new Spec(annotationAttributes);
		// 创建结果数组
		List<String> missingProperties = new ArrayList<>();
		List<String> nonMatchingProperties = new ArrayList<>();
		// 收集是否不匹配的信息，到 missingProperties、nonMatchingProperties 中
		spec.collectProperties(resolver, missingProperties, nonMatchingProperties);
		// 如果有属性缺失，则返回不匹配
		if (!missingProperties.isEmpty()) {
			return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnProperty.class, spec)
					.didNotFind("property", "properties").items(Style.QUOTE, missingProperties));
		}
		// 如果有属性不匹配，则返回不匹配
		if (!nonMatchingProperties.isEmpty()) {
			return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnProperty.class, spec)
					.found("different value in property", "different value in properties")
					.items(Style.QUOTE, nonMatchingProperties));
		}
		// 返回匹配
		return ConditionOutcome
				.match(ConditionMessage.forCondition(ConditionalOnProperty.class, spec).because("matched"));
	}

	private static class Spec {
		/**
		 * 属性前缀
		 */
		private final String prefix;
		/**
		 * 是否有指定值
		 */
		private final String havingValue;
		/**
		 * 属性名
		 */
		private final String[] names;
		/**
		 * 如果属性不存在，是否认为是匹配的
		 *
		 * 如果为 false 时， 就认为属性缺失，即不匹配
		 */
		private final boolean matchIfMissing;

		Spec(AnnotationAttributes annotationAttributes) {
			String prefix = annotationAttributes.getString("prefix").trim();
			if (StringUtils.hasText(prefix) && !prefix.endsWith(".")) {
				prefix = prefix + ".";
			}
			this.prefix = prefix;
			this.havingValue = annotationAttributes.getString("havingValue");
			this.names = getNames(annotationAttributes);
			this.matchIfMissing = annotationAttributes.getBoolean("matchIfMissing");
		}

		/**
		 * 从 value 或者 name 属性中，获取值
		 */
		private String[] getNames(Map<String, Object> annotationAttributes) {
			String[] value = (String[]) annotationAttributes.get("value");
			String[] name = (String[]) annotationAttributes.get("name");
			Assert.state(value.length > 0 || name.length > 0,
					"The name or value attribute of @ConditionalOnProperty must be specified");
			Assert.state(value.length == 0 || name.length == 0,
					"The name and value attributes of @ConditionalOnProperty are exclusive");
			return (value.length > 0) ? value : name;
		}

		private void collectProperties(PropertyResolver resolver, List<String> missing, List<String> nonMatching) {
			// 收集属性
			for (String name : this.names) {
				// 获取名称
				String key = this.prefix + name;
				// 获取属性解析器
				if (resolver.containsProperty(key)) {
					// 如果是不匹配的
					if (!isMatch(resolver.getProperty(key), this.havingValue)) {
						// 加入不匹配的名称
						nonMatching.add(name);
					}
				}
				else {
					// 如果是缺失的
					if (!this.matchIfMissing) {
						missing.add(name);
					}
				}
			}
		}

		private boolean isMatch(String value, String requiredValue) {
			// 如果 requiredValue 非空，则进行匹配
			if (StringUtils.hasLength(requiredValue)) {
				return requiredValue.equalsIgnoreCase(value);
			}
			// 如果 requiredValue 为空， 要求值不为 false
			return !"false".equalsIgnoreCase(value);
		}

		@Override
		public String toString() {
			StringBuilder result = new StringBuilder();
			result.append("(");
			result.append(this.prefix);
			if (this.names.length == 1) {
				result.append(this.names[0]);
			}
			else {
				result.append("[");
				result.append(StringUtils.arrayToCommaDelimitedString(this.names));
				result.append("]");
			}
			if (StringUtils.hasLength(this.havingValue)) {
				result.append("=").append(this.havingValue);
			}
			result.append(")");
			return result.toString();
		}

	}

}
