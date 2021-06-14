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

package org.springframework.boot.context.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * {@link ApplicationListener} that delegates to other listeners that are specified under
 * a {@literal context.listener.classes} environment property.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @since 1.0.0
 */
public class DelegatingApplicationListener implements ApplicationListener<ApplicationEvent>, Ordered {

	// NOTE: Similar to org.springframework.web.context.ContextLoader
	// 相似的 org.springframework.web.context.ContextLoader
	private static final String PROPERTY_NAME = "context.listener.classes";
	// 获取顺序
	private int order = 0;
	// 简单的应用监听器广播器
	private SimpleApplicationEventMulticaster multicaster;

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		// 如果 ApplicationEnvironmentPreparedEvent 实践
		if (event instanceof ApplicationEnvironmentPreparedEvent) {
			// 获取环境
			List<ApplicationListener<ApplicationEvent>> delegates = getListeners(
					((ApplicationEnvironmentPreparedEvent) event).getEnvironment());
			// 如果为空，直接返回
			if (delegates.isEmpty()) {
				return;
			}
			// 加入管理器
			this.multicaster = new SimpleApplicationEventMulticaster();
			for (ApplicationListener<ApplicationEvent> listener : delegates) {
				this.multicaster.addApplicationListener(listener);
			}
		}
		if (this.multicaster != null) {
			// 广播事件
			this.multicaster.multicastEvent(event);
		}
	}

	@SuppressWarnings("unchecked")
	private List<ApplicationListener<ApplicationEvent>> getListeners(ConfigurableEnvironment environment) {
		// 如果环境为空，返回空列表
		if (environment == null) {
			return Collections.emptyList();
		}
		// 获取类名
		String classNames = environment.getProperty(PROPERTY_NAME);
		List<ApplicationListener<ApplicationEvent>> listeners = new ArrayList<>();
		if (StringUtils.hasLength(classNames)) {
			for (String className : StringUtils.commaDelimitedListToSet(classNames)) {
				try {
					Class<?> clazz = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
					Assert.isAssignable(ApplicationListener.class, clazz,
							"class [" + className + "] must implement ApplicationListener");
					listeners.add((ApplicationListener<ApplicationEvent>) BeanUtils.instantiateClass(clazz));
				}
				catch (Exception ex) {
					throw new ApplicationContextException("Failed to load context listener class [" + className + "]",
							ex);
				}
			}
		}
		AnnotationAwareOrderComparator.sort(listeners);
		return listeners;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

}
