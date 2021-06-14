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

package org.springframework.boot.context;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link ApplicationContextInitializer} that sets the Spring
 * {@link ApplicationContext#getId() ApplicationContext ID}. The
 * {@code spring.application.name} property is used to create the ID. If the property is
 * not set {@code application} is used.
 *
 * @author Dave Syer
 * @author Andy Wilkinson
 * @since 1.0.0
 */
public class ContextIdApplicationContextInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {
	/**
	 * 定义优先级
	 */
	private int order = Ordered.LOWEST_PRECEDENCE - 10;

	/**
	 * 设置优先级
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	/**
	 * 设置优先级
	 */
	public int getOrder() {
		return this.order;
	}

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		// 获取 ContextId
		ContextId contextId = getContextId(applicationContext);
		// 设置到 applicationContext 中
		applicationContext.setId(contextId.getId());
		// 注册到 contextId 到 Spring 容器中
		applicationContext.getBeanFactory().registerSingleton(ContextId.class.getName(), contextId);
	}

	private ContextId getContextId(ConfigurableApplicationContext applicationContext) {
		// 获取父 parent
		ApplicationContext parent = applicationContext.getParent();
		// 获取对应的 Bean 并创建 Id
		if (parent != null && parent.containsBean(ContextId.class.getName())) {
			return parent.getBean(ContextId.class).createChildId();
		}
		// 创建 ContextId
		return new ContextId(getApplicationId(applicationContext.getEnvironment()));
	}

	private String getApplicationId(ConfigurableEnvironment environment) {
		// 获取 spring.application.name 属性
		String name = environment.getProperty("spring.application.name");
		// 返回名称
		return StringUtils.hasText(name) ? name : "application";
	}

	/**
	 * The ID of a context.
	 */
	static class ContextId {
		/**
		 * 递增序列
		 */
		private final AtomicLong children = new AtomicLong(0);
		/**
		 * 编号
		 */
		private final String id;

		ContextId(String id) {
			this.id = id;
		}

		/**
		 *  创建子 Context 的编号
		 */
		ContextId createChildId() {
			return new ContextId(this.id + "-" + this.children.incrementAndGet());
		}

		String getId() {
			return this.id;
		}

	}

}
