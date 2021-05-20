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

import org.springframework.boot.loader.archive.Archive;

/**
 * {@link Launcher} for WAR based archives. This launcher for standard WAR archives.
 * Supports dependencies in {@code WEB-INF/lib} as well as {@code WEB-INF/lib-provided},
 * classes are loaded from {@code WEB-INF/classes}.
 *
 * 基于 WAR 包的启动路径
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @since 1.0.0
 */
public class WarLauncher extends ExecutableArchiveLauncher {
	/**
	 *  WEB-INF 路径
	 */
	private static final String WEB_INF = "WEB-INF/";
	/**
	 * WEB-INF 类路径
	 */
	private static final String WEB_INF_CLASSES = WEB_INF + "classes/";
	/**
	 * WEB-INF jar 包路径
	 */
	private static final String WEB_INF_LIB = WEB_INF + "lib/";
	/**
	 * WEB-INFO 包提供路径
	 */
	private static final String WEB_INF_LIB_PROVIDED = WEB_INF + "lib-provided/";
	// 构造方法
	public WarLauncher() {
	}

	/**
	 * 构造方法
	 * @param archive
	 */
	protected WarLauncher(Archive archive) {
		super(archive);
	}

	@Override
	public boolean isNestedArchive(Archive.Entry entry) {
		// 是否是最近的加载镜像
		if (entry.isDirectory()) {
			return entry.getName().equals(WEB_INF_CLASSES);
		}
		else {
			// 是否是包路径
			return entry.getName().startsWith(WEB_INF_LIB) || entry.getName().startsWith(WEB_INF_LIB_PROVIDED);
		}
	}

	public static void main(String[] args) throws Exception {
		new WarLauncher().launch(args);
	}

}
