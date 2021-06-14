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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link ApplicationContextInitializer} to report warnings for common misconfiguration
 * mistakes.
 * <p>
 * 对于配置错误目的是记录警告的 {@link ApplicationContextInitializer}
 *
 * @author Phillip Webb
 * @since 1.2.0
 */
public class ConfigurationWarningsApplicationContextInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	private static final Log logger = LogFactory.getLog(ConfigurationWarningsApplicationContextInitializer.class);

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		// 注册 ConfigurationWarningsPostProcessor 到 Spring 容器中
		context.addBeanFactoryPostProcessor(new ConfigurationWarningsPostProcessor(getChecks()));
	}

	/**
	 * Returns the checks that should be applied.
	 * <p>
	 * 返回应用这个检查
	 *
	 * @return the checks to apply
	 */
	protected Check[] getChecks() {
		return new Check[]{new ComponentScanPackageCheck()};
	}

	/**
	 * {@link BeanDefinitionRegistryPostProcessor} to report warnings.
	 */
	protected static final class ConfigurationWarningsPostProcessor
			implements PriorityOrdered, BeanDefinitionRegistryPostProcessor {
		/**
		 * 获取检查数组
		 */
		private Check[] checks;

		/**
		 * 构造方法
		 * @param checks
		 */
		public ConfigurationWarningsPostProcessor(Check[] checks) {
			this.checks = checks;
		}

		@Override
		/*
		 * 获取顺序
		 */
		public int getOrder() {
			return Ordered.LOWEST_PRECEDENCE - 1;
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		}

		@Override
		/**
		 * 注册
		 */
		public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
			for (Check check : this.checks) {
				String message = check.getWarning(registry);
				if (StringUtils.hasLength(message)) {
					warn(message);
				}
			}

		}

		private void warn(String message) {
			// 记录日志
			if (logger.isWarnEnabled()) {
				logger.warn(String.format("%n%n** WARNING ** : %s%n%n", message));
			}
		}

	}

	/**
	 * A single check that can be applied.
	 */
	@FunctionalInterface
	protected interface Check {

		/**
		 * Returns a warning if the check fails or {@code null} if there are no problems.
		 * <p>
		 * 返回一个警告如果检查失败或者 null,
		 *
		 * @param registry the {@link BeanDefinitionRegistry}
		 * @return a warning message or {@code null}
		 */
		String getWarning(BeanDefinitionRegistry registry);

	}

	/**
	 * {@link Check} for {@code @ComponentScan} on problematic package.
	 */
	protected static class ComponentScanPackageCheck implements Check {
		/**
		 * 有问题包的集合
		 *
		 * 即禁止使用 @CompoentScan 注解扫描这个集合中的包
		 */
		private static final Set<String> PROBLEM_PACKAGES;

		static {
			Set<String> packages = new HashSet<>();
			packages.add("org.springframework");
			packages.add("org");
			PROBLEM_PACKAGES = Collections.unmodifiableSet(packages);
		}

		@Override
		public String getWarning(BeanDefinitionRegistry registry) {
			// 获得需要扫描的包
			Set<String> scannedPackages = getComponentScanningPackages(registry);
			// 获得要扫描的包中，有问题的包
			List<String> problematicPackages = getProblematicPackages(scannedPackages);
			// 如果 problematicPackages 为空，说明不存在问题
			if (problematicPackages.isEmpty()) {
				return null;
			}
			// 如果 problematicPackages 非空，说明有问题，返回错误提示
			return "Your ApplicationContext is unlikely to start due to a @ComponentScan of "
					+ StringUtils.collectionToDelimitedString(problematicPackages, ", ") + ".";
		}

		protected Set<String> getComponentScanningPackages(BeanDefinitionRegistry registry) {
			// 需要扫描包的集合
			Set<String> packages = new LinkedHashSet<>();
			// 获取所有的 BeanDefinition 的名字们
			String[] names = registry.getBeanDefinitionNames();
			for (String name : names) {
				// 如果是 AnnotatedBeanDefinition
				BeanDefinition definition = registry.getBeanDefinition(name);
				if (definition instanceof AnnotatedBeanDefinition) {
					AnnotatedBeanDefinition annotatedDefinition = (AnnotatedBeanDefinition) definition;
					// 如果有 @ComponentScan 注解，则添加到 packages 中
					addComponentScanningPackages(packages, annotatedDefinition.getMetadata());
				}
			}
			return packages;
		}

		private void addComponentScanningPackages(Set<String> packages, AnnotationMetadata metadata) {
			// 获得 @ComponentScan 数组
			AnnotationAttributes attributes = AnnotationAttributes
					.fromMap(metadata.getAnnotationAttributes(ComponentScan.class.getName(), true));
			// 如果存在，则添加到 packages 中
			if (attributes != null) {
				addPackages(packages, attributes.getStringArray("value"));
				addPackages(packages, attributes.getStringArray("basePackages"));
				addClasses(packages, attributes.getStringArray("basePackageClasses"));
				if (packages.isEmpty()) {
					packages.add(ClassUtils.getPackageName(metadata.getClassName()));
				}
			}
		}

		private void addPackages(Set<String> packages, String[] values) {
			if (values != null) {
				Collections.addAll(packages, values);
			}
		}

		private void addClasses(Set<String> packages, String[] values) {
			if (values != null) {
				for (String value : values) {
					packages.add(ClassUtils.getPackageName(value));
				}
			}
		}

		private List<String> getProblematicPackages(Set<String> scannedPackages) {
			// 获取有问题的包
			List<String> problematicPackages = new ArrayList<>();
			for (String scannedPackage : scannedPackages) {
				// 如果是有问题的包
				if (isProblematicPackage(scannedPackage)) {
					problematicPackages.add(getDisplayName(scannedPackage));
				}
			}
			// 返回
			return problematicPackages;
		}

		private boolean isProblematicPackage(String scannedPackage) {
			if (scannedPackage == null || scannedPackage.isEmpty()) {
				return true;
			}
			return PROBLEM_PACKAGES.contains(scannedPackage);
		}

		private String getDisplayName(String scannedPackage) {
			// 加入默认包
			if (scannedPackage == null || scannedPackage.isEmpty()) {
				return "the default package";
			}
			return "'" + scannedPackage + "'";
		}

	}

}
