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

package org.springframework.boot.context.logging;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.logging.LogFile;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerGroup;
import org.springframework.boot.logging.LoggerGroups;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.LoggingSystemProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogMessage;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * An {@link ApplicationListener} that configures the {@link LoggingSystem}. If the
 * environment contains a {@code logging.config} property it will be used to bootstrap the
 * logging system, otherwise a default configuration is used. Regardless, logging levels
 * will be customized if the environment contains {@code logging.level.*} entries and
 * logging groups can be defined with {@code logging.group}.
 * <p>
 * Debug and trace logging for Spring, Tomcat, Jetty and Hibernate will be enabled when
 * the environment contains {@code debug} or {@code trace} properties that aren't set to
 * {@code "false"} (i.e. if you start your application using
 * {@literal java -jar myapp.jar [--debug | --trace]}). If you prefer to ignore these
 * properties you can set {@link #setParseArgs(boolean) parseArgs} to {@code false}.
 * <p>
 * By default, log output is only written to the console. If a log file is required, the
 * {@code logging.file.path} and {@code logging.file.name} properties can be used.
 * <p>
 * Some system properties may be set as side effects, and these can be useful if the
 * logging configuration supports placeholders (i.e. log4j or logback):
 * <ul>
 * <li>{@code LOG_FILE} is set to the value of path of the log file that should be written
 * (if any).</li>
 * <li>{@code PID} is set to the value of the current process ID if it can be determined.
 * </li>
 * </ul>
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @author Madhura Bhave
 * @author HaiTao Zhang
 * @since 2.0.0
 * @see LoggingSystem#get(ClassLoader)
 */
public class LoggingApplicationListener implements GenericApplicationListener {
	/**
	 * 日志级别
	 */
	private static final ConfigurationPropertyName LOGGING_LEVEL = ConfigurationPropertyName.of("logging.level");
	/**
	 * 日志组
	 */
	private static final ConfigurationPropertyName LOGGING_GROUP = ConfigurationPropertyName.of("logging.group");
	/**
	 * String 日志级别 Map
	 */
	private static final Bindable<Map<String, LogLevel>> STRING_LOGLEVEL_MAP = Bindable.mapOf(String.class,
			LogLevel.class);
	/**
	 * String 类型的 Map
	 */
	private static final Bindable<Map<String, List<String>>> STRING_STRINGS_MAP = Bindable
			.of(ResolvableType.forClassWithGenerics(MultiValueMap.class, String.class, String.class).asMap());

	/**
	 * The default order for the LoggingApplicationListener.
	 *
	 * 默认的排序
	 */
	public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

	/**
	 * The name of the Spring property that contains a reference to the logging
	 * configuration to load.
	 *
	 * Spring 属性名称，包含日志配置引用
	 */
	public static final String CONFIG_PROPERTY = "logging.config";

	/**
	 * The name of the Spring property that controls the registration of a shutdown hook
	 * to shut down the logging system when the JVM exits.
	 * @see LoggingSystem#getShutdownHandler
	 */
	public static final String REGISTER_SHUTDOWN_HOOK_PROPERTY = "logging.register-shutdown-hook";

	/**
	 * The name of the {@link LoggingSystem} bean.
	 *
	 * 日志系统 Bean 名称
	 */
	public static final String LOGGING_SYSTEM_BEAN_NAME = "springBootLoggingSystem";

	/**
	 * The name of the {@link LogFile} bean.
	 *
	 * LogFile Bean 名称
	 * @since 2.2.0
	 */
	public static final String LOG_FILE_BEAN_NAME = "springBootLogFile";

	/**
	 * The name of the{@link LoggerGroups} bean.
	 *
	 * LoggerGroups 的 Bean 名称
	 * @since 2.2.0
	 */
	public static final String LOGGER_GROUPS_BEAN_NAME = "springBootLoggerGroups";

	/**
	 * The name of the {@link LogFile} bean.
	 * @deprecated since 2.2.0 in favor of {@link #LOG_FILE_BEAN_NAME}
	 */
	@Deprecated
	public static final String LOGFILE_BEAN_NAME = LOG_FILE_BEAN_NAME;

	private static final Map<String, List<String>> DEFAULT_GROUP_LOGGERS;
	static {
		MultiValueMap<String, String> loggers = new LinkedMultiValueMap<>();
		loggers.add("web", "org.springframework.core.codec");
		loggers.add("web", "org.springframework.http");
		loggers.add("web", "org.springframework.web");
		loggers.add("web", "org.springframework.boot.actuate.endpoint.web");
		loggers.add("web", "org.springframework.boot.web.servlet.ServletContextInitializerBeans");
		loggers.add("sql", "org.springframework.jdbc.core");
		loggers.add("sql", "org.hibernate.SQL");
		loggers.add("sql", "org.jooq.tools.LoggerListener");
		DEFAULT_GROUP_LOGGERS = Collections.unmodifiableMap(loggers);
	}

	private static final Map<LogLevel, List<String>> SPRING_BOOT_LOGGING_LOGGERS;
	static {
		MultiValueMap<LogLevel, String> loggers = new LinkedMultiValueMap<>();
		loggers.add(LogLevel.DEBUG, "sql");
		loggers.add(LogLevel.DEBUG, "web");
		loggers.add(LogLevel.DEBUG, "org.springframework.boot");
		loggers.add(LogLevel.TRACE, "org.springframework");
		loggers.add(LogLevel.TRACE, "org.apache.tomcat");
		loggers.add(LogLevel.TRACE, "org.apache.catalina");
		loggers.add(LogLevel.TRACE, "org.eclipse.jetty");
		loggers.add(LogLevel.TRACE, "org.hibernate.tool.hbm2ddl");
		SPRING_BOOT_LOGGING_LOGGERS = Collections.unmodifiableMap(loggers);
	}

	/**
	 * 事件类型
	 */
	private static final Class<?>[] EVENT_TYPES = { ApplicationStartingEvent.class,
			ApplicationEnvironmentPreparedEvent.class, ApplicationPreparedEvent.class, ContextClosedEvent.class,
			ApplicationFailedEvent.class };

	private static final Class<?>[] SOURCE_TYPES = { SpringApplication.class, ApplicationContext.class };

	private static final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);

	private final Log logger = LogFactory.getLog(getClass());

	private LoggingSystem loggingSystem;

	private LogFile logFile;

	private LoggerGroups loggerGroups;

	private int order = DEFAULT_ORDER;

	private boolean parseArgs = true;

	private LogLevel springBootLogging = null;

	@Override
	public boolean supportsEventType(ResolvableType resolvableType) {
		return isAssignableFrom(resolvableType.getRawClass(), EVENT_TYPES);
	}

	@Override
	public boolean supportsSourceType(Class<?> sourceType) {
		return isAssignableFrom(sourceType, SOURCE_TYPES);
	}

	private boolean isAssignableFrom(Class<?> type, Class<?>... supportedTypes) {
		if (type != null) {
			for (Class<?> supportedType : supportedTypes) {
				if (supportedType.isAssignableFrom(type)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		// 在 Spring Boot 应用启动的时候
		if (event instanceof ApplicationStartingEvent) {
			onApplicationStartingEvent((ApplicationStartingEvent) event);
		}
		// 在 Spring Boot 的 Environment 环境准备完成的时候
		else if (event instanceof ApplicationEnvironmentPreparedEvent) {
			onApplicationEnvironmentPreparedEvent((ApplicationEnvironmentPreparedEvent) event);
		}
		// 在 Spring Boot 容器的准备工作已经完成（并未启动）的时候
		else if (event instanceof ApplicationPreparedEvent) {
			onApplicationPreparedEvent((ApplicationPreparedEvent) event);
		}
		// 在 Spring Boot 容器关闭的时候
		else if (event instanceof ContextClosedEvent
				&& ((ContextClosedEvent) event).getApplicationContext().getParent() == null) {
			onContextClosedEvent();
		}
		// 在 Spring Boot 容器启动失败的时候
		else if (event instanceof ApplicationFailedEvent) {
			onApplicationFailedEvent();
		}
	}

	private void onApplicationStartingEvent(ApplicationStartingEvent event) {
		// 创建 loggingSystem 对象
		this.loggingSystem = LoggingSystem.get(event.getSpringApplication().getClassLoader());
		// LoggingSystem 的初始化的前置处理
		this.loggingSystem.beforeInitialize();
	}

	private void onApplicationEnvironmentPreparedEvent(ApplicationEnvironmentPreparedEvent event) {
		// 初始化 loggingSystem
		if (this.loggingSystem == null) {
			this.loggingSystem = LoggingSystem.get(event.getSpringApplication().getClassLoader());
		}
		// 初始化
		initialize(event.getEnvironment(), event.getSpringApplication().getClassLoader());
	}

	private void onApplicationPreparedEvent(ApplicationPreparedEvent event) {
		// 获取 BeanFactory
		ConfigurableListableBeanFactory beanFactory = event.getApplicationContext().getBeanFactory();
		// 注册各种 Bean
		if (!beanFactory.containsBean(LOGGING_SYSTEM_BEAN_NAME)) {
			beanFactory.registerSingleton(LOGGING_SYSTEM_BEAN_NAME, this.loggingSystem);
		}
		if (this.logFile != null && !beanFactory.containsBean(LOG_FILE_BEAN_NAME)) {
			beanFactory.registerSingleton(LOG_FILE_BEAN_NAME, this.logFile);
		}
		if (this.loggerGroups != null && !beanFactory.containsBean(LOGGER_GROUPS_BEAN_NAME)) {
			beanFactory.registerSingleton(LOGGER_GROUPS_BEAN_NAME, this.loggerGroups);
		}
	}

	private void onContextClosedEvent() {
		if (this.loggingSystem != null) {
			this.loggingSystem.cleanUp();
		}
	}

	private void onApplicationFailedEvent() {
		if (this.loggingSystem != null) {
			this.loggingSystem.cleanUp();
		}
	}

	/**
	 * Initialize the logging system according to preferences expressed through the
	 * {@link Environment} and the classpath.
	 * @param environment the environment
	 * @param classLoader the classloader
	 */
	protected void initialize(ConfigurableEnvironment environment, ClassLoader classLoader) {
		// 初始化 LoggingSystemProperties 配置
		new LoggingSystemProperties(environment).apply();
		// 初始化 logFile
		this.logFile = LogFile.get(environment);
		if (this.logFile != null) {
			this.logFile.applyToSystemProperties();
		}
		// 初始化 LoggerGroups 的配置
		this.loggerGroups = new LoggerGroups(DEFAULT_GROUP_LOGGERS);
		// 初始化早期的 Spring Boot Logging 级别
		initializeEarlyLoggingLevel(environment);
		// 初始化 LoggingSystem 日志系统
		initializeSystem(environment, this.loggingSystem, this.logFile);
		// 初始化最终的 Spring Boot Logging 级别
		initializeFinalLoggingLevels(environment, this.loggingSystem);
		// 注册关闭，如果有必要的
		registerShutdownHookIfNecessary(environment, this.loggingSystem);
	}

	private void initializeEarlyLoggingLevel(ConfigurableEnvironment environment) {
		if (this.parseArgs && this.springBootLogging == null) {
			if (isSet(environment, "debug")) {
				this.springBootLogging = LogLevel.DEBUG;
			}
			if (isSet(environment, "trace")) {
				this.springBootLogging = LogLevel.TRACE;
			}
		}
	}

	private boolean isSet(ConfigurableEnvironment environment, String property) {
		String value = environment.getProperty(property);
		return (value != null && !value.equals("false"));
	}

	private void initializeSystem(ConfigurableEnvironment environment, LoggingSystem system, LogFile logFile) {
		// 创建 LoggingInitializationContext 对象
		LoggingInitializationContext initializationContext = new LoggingInitializationContext(environment);
		// 获得日志组件的配置文件
		String logConfig = environment.getProperty(CONFIG_PROPERTY);
		// 如果没有配置，则直接初始化 LoggingSystem
		if (ignoreLogConfig(logConfig)) {
			system.initialize(initializationContext, null, logFile);
		}
		// 如果有配置，先尝试加载指定配置文件，然后初始化 LoggingSystem
		else {
			try {
				system.initialize(initializationContext, logConfig, logFile);
			}
			catch (Exception ex) {
				Throwable exceptionToReport = ex;
				while (exceptionToReport != null && !(exceptionToReport instanceof FileNotFoundException)) {
					exceptionToReport = exceptionToReport.getCause();
				}
				exceptionToReport = (exceptionToReport != null) ? exceptionToReport : ex;
				// NOTE: We can't use the logger here to report the problem
				System.err.println("Logging system failed to initialize using configuration from '" + logConfig + "'");
				exceptionToReport.printStackTrace(System.err);
				throw new IllegalStateException(ex);
			}
		}
	}

	private boolean ignoreLogConfig(String logConfig) {
		return !StringUtils.hasLength(logConfig) || logConfig.startsWith("-D");
	}

	private void initializeFinalLoggingLevels(ConfigurableEnvironment environment, LoggingSystem system) {
		// 绑定 LoggerGroup 组
		bindLoggerGroups(environment);
		// 如果 springBootLogging 不为空，则初始化日志级别
		if (this.springBootLogging != null) {
			initializeLogLevel(system, this.springBootLogging);
		}
		// 设置日志级别
		setLogLevels(system, environment);
	}

	private void bindLoggerGroups(ConfigurableEnvironment environment) {
		// 日志组
		if (this.loggerGroups != null) {
			// 获取环境中的 Binder
			Binder binder = Binder.get(environment);
			// 绑定日志级别
			binder.bind(LOGGING_GROUP, STRING_STRINGS_MAP).ifBound(this.loggerGroups::putAll);
		}
	}

	/**
	 * Initialize loggers based on the {@link #setSpringBootLogging(LogLevel)
	 * springBootLogging} setting.
	 * @param system the logging system
	 * @param springBootLogging the spring boot logging level requested
	 * @deprecated since 2.2.0 in favor of
	 * {@link #initializeSpringBootLogging(LoggingSystem, LogLevel)}
	 */
	@Deprecated
	protected void initializeLogLevel(LoggingSystem system, LogLevel springBootLogging) {
		initializeSpringBootLogging(system, springBootLogging);
	}

	/**
	 * Initialize loggers based on the {@link #setSpringBootLogging(LogLevel)
	 * springBootLogging} setting. By default this implementation will pick an appropriate
	 * set of loggers to configure based on the level.
	 * @param system the logging system
	 * @param springBootLogging the spring boot logging level requested
	 * @since 2.2.0
	 */
	protected void initializeSpringBootLogging(LoggingSystem system, LogLevel springBootLogging) {
		// 绑定 BiConsumer 的配置
		BiConsumer<String, LogLevel> configurer = getLogLevelConfigurer(system);
		// 获取日志
		SPRING_BOOT_LOGGING_LOGGERS.getOrDefault(springBootLogging, Collections.emptyList())
				.forEach((name) -> configureLogLevel(name, springBootLogging, configurer));
	}

	/**
	 * Set logging levels based on relevant {@link Environment} properties.
	 * @param system the logging system
	 * @param environment the environment
	 * @deprecated since 2.2.0 in favor of
	 * {@link #setLogLevels(LoggingSystem, ConfigurableEnvironment)}
	 */
	@Deprecated
	protected void setLogLevels(LoggingSystem system, Environment environment) {
		if (environment instanceof ConfigurableEnvironment) {
			setLogLevels(system, (ConfigurableEnvironment) environment);
		}
	}

	/**
	 * Set logging levels based on relevant {@link Environment} properties.
	 * @param system the logging system
	 * @param environment the environment
	 * @since 2.2.0
	 */
	protected void setLogLevels(LoggingSystem system, ConfigurableEnvironment environment) {
		// 获取日志配置
		BiConsumer<String, LogLevel> customizer = getLogLevelConfigurer(system);
		// 获取绑定环境
		Binder binder = Binder.get(environment);
		// 获取日志级别
		Map<String, LogLevel> levels = binder.bind(LOGGING_LEVEL, STRING_LOGLEVEL_MAP).orElseGet(Collections::emptyMap);
		// 配置日志级别
		levels.forEach((name, level) -> configureLogLevel(name, level, customizer));
	}

	private void configureLogLevel(String name, LogLevel level, BiConsumer<String, LogLevel> configurer) {
		if (this.loggerGroups != null) {
			LoggerGroup group = this.loggerGroups.get(name);
			if (group != null && group.hasMembers()) {
				group.configureLogLevel(level, configurer);
				return;
			}
		}
		configurer.accept(name, level);
	}

	private BiConsumer<String, LogLevel> getLogLevelConfigurer(LoggingSystem system) {
		return (name, level) -> {
			try {
				name = name.equalsIgnoreCase(LoggingSystem.ROOT_LOGGER_NAME) ? null : name;
				system.setLogLevel(name, level);
			}
			catch (RuntimeException ex) {
				this.logger.error(LogMessage.format("Cannot set level '%s' for '%s'", level, name));
			}
		};
	}

	private void registerShutdownHookIfNecessary(Environment environment, LoggingSystem loggingSystem) {
		// 获得 loggin.register-shutdown-hook 对应的配置值
		boolean registerShutdownHook = environment.getProperty(REGISTER_SHUTDOWN_HOOK_PROPERTY, Boolean.class, false);
		// 如果开启
		if (registerShutdownHook) {
			// 获得 shutdownHandler 钩子
			Runnable shutdownHandler = loggingSystem.getShutdownHandler();
			// 注册 ShutdownHook
			if (shutdownHandler != null && shutdownHookRegistered.compareAndSet(false, true)) {
				registerShutdownHook(new Thread(shutdownHandler));
			}
		}
	}

	void registerShutdownHook(Thread shutdownHook) {
		Runtime.getRuntime().addShutdownHook(shutdownHook);
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * Sets a custom logging level to be used for Spring Boot and related libraries.
	 * @param springBootLogging the logging level
	 */
	public void setSpringBootLogging(LogLevel springBootLogging) {
		this.springBootLogging = springBootLogging;
	}

	/**
	 * Sets if initialization arguments should be parsed for {@literal debug} and
	 * {@literal trace} properties (usually defined from {@literal --debug} or
	 * {@literal --trace} command line args). Defaults to {@code true}.
	 * @param parseArgs if arguments should be parsed
	 */
	public void setParseArgs(boolean parseArgs) {
		this.parseArgs = parseArgs;
	}

}
