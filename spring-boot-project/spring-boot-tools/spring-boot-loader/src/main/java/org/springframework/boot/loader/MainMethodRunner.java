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

package org.springframework.boot.loader;

import java.lang.reflect.Method;

/**
 * Utility class that is used by {@link Launcher}s to call a main method. The class
 * containing the main method is loaded using the thread context class loader.
 *
 * Launcher 用来调用的主方法
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @since 1.0.0
 */
public class MainMethodRunner {
	/**
	 * 主类名
	 */
	private final String mainClassName;
	/**
	 * 参数数组
	 */
	private final String[] args;

	/**
	 * Create a new {@link MainMethodRunner} instance.
	 *
	 * 创建一个新的实例
	 * @param mainClass the main class  主类
	 * @param args incoming arguments 进来的参数
	 */
	public MainMethodRunner(String mainClass, String[] args) {
		this.mainClassName = mainClass;
		this.args = (args != null) ? args.clone() : null;
	}

	public void run() throws Exception {
		// 获取主类
		Class<?> mainClass = Thread.currentThread().getContextClassLoader().loadClass(this.mainClassName);
		// 获取 main 方法
		Method mainMethod = mainClass.getDeclaredMethod("main", String[].class);
		// 执行调用
		mainMethod.invoke(null, new Object[] { this.args });
	}

}
