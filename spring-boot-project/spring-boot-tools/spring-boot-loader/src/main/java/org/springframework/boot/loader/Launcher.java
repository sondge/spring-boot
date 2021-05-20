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
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.jar.JarFile;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for launchers that can start an application with a fully configured
 * classpath backed by one or more {@link Archive}s.
 * <p>
 * 启动应用程序的基类，可以启动一个或者多个 {@link Archive} 的应用程序
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @since 1.0.0
 */
public abstract class Launcher {

	/**
	 * Launch the application. This method is the initial entry point that should be
	 * called by a subclass {@code public static void main(String[] args)} method.
	 *
	 * 启动应用程序，这个方法是子类的入口 main 方法的调用入口
	 *
	 * @param args the incoming arguments
	 * @throws Exception if the application fails to launch
	 */
	protected void launch(String[] args) throws Exception {
		// 注册 jar 文件
		JarFile.registerUrlProtocolHandler();
		// 创建 ClassLoader
		ClassLoader classLoader = createClassLoader(getClassPathArchives());
		// 启动主类
		launch(args, getMainClass(), classLoader);
	}

	/**
	 * Create a classloader for the specified archives.
	 *
	 * 对于指定的 archives 创建一个指定的 classLoader
	 *
	 * @param archives the archives
	 * @return the classloader
	 * @throws Exception if the classloader cannot be created
	 */
	protected ClassLoader createClassLoader(List<Archive> archives) throws Exception {
		// 创建一个 ArrayList
		List<URL> urls = new ArrayList<>(archives.size());
		// 将镜像加入
		for (Archive archive : archives) {
			urls.add(archive.getUrl());
		}
		// 创建 ClassLoader
		return createClassLoader(urls.toArray(new URL[0]));
	}

	/**
	 * Create a classloader for the specified URLs.
	 *
	 * 对于指定的 URLS 创建一个 classLoader
	 *
	 * @param urls the URLs
	 * @return the classloader
	 * @throws Exception if the classloader cannot be created
	 */
	protected ClassLoader createClassLoader(URL[] urls) throws Exception {
		return new LaunchedURLClassLoader(urls, getClass().getClassLoader());
	}

	/**
	 * Launch the application given the archive file and a fully configured classloader.
	 *
	 * @param args        the incoming arguments
	 * @param mainClass   the main class to run
	 * @param classLoader the classloader
	 * @throws Exception if the launch fails
	 */
	protected void launch(String[] args, String mainClass, ClassLoader classLoader) throws Exception {
		// 设置当前线程的 classLoader
		Thread.currentThread().setContextClassLoader(classLoader);
		// 创建主方法并且运行
		createMainMethodRunner(mainClass, args, classLoader).run();
	}

	/**
	 * Create the {@code MainMethodRunner} used to launch the application.
	 *
	 * 创建这个 MainMethodRunner 使用运行这个应用程序
	 *
	 * @param mainClass   the main class
	 * @param args        the incoming arguments
	 * @param classLoader the classloader
	 * @return the main method runner
	 */
	protected MainMethodRunner createMainMethodRunner(String mainClass, String[] args, ClassLoader classLoader) {
		return new MainMethodRunner(mainClass, args);
	}

	/**
	 * Returns the main class that should be launched.
	 *
	 * 返回应该被运行的主类
	 *
	 * @return the name of the main class
	 * @throws Exception if the main class cannot be obtained
	 */
	protected abstract String getMainClass() throws Exception;

	/**
	 * Returns the archives that will be used to construct the class path.
	 *
	 * 返回这个 archives 目的是被构造方法运行的方法
	 *
	 * @return the class path archives
	 * @throws Exception if the class path archives cannot be obtained
	 */
	protected abstract List<Archive> getClassPathArchives() throws Exception;

	protected final Archive createArchive() throws Exception {
		// 获取 jar 所在的绝对路径
		ProtectionDomain protectionDomain = getClass().getProtectionDomain();
		// 获取 code 源码
		CodeSource codeSource = protectionDomain.getCodeSource();
		// 获取本地  URI
		URI location = (codeSource != null) ? codeSource.getLocation().toURI() : null;
		// 获取路径
		String path = (location != null) ? location.getSchemeSpecificPart() : null;
		if (path == null) {
			throw new IllegalStateException("Unable to determine code source archive");
		}
		// 获取文件
		File root = new File(path);
		if (!root.exists()) {
			throw new IllegalStateException("Unable to determine code source archive from " + root);
		}
		// 如果是目录则使用 ExplodedArchive 进行展开
		// 如果不是目录，则使用 JarFileArchive
		return (root.isDirectory() ? new ExplodedArchive(root) : new JarFileArchive(root));
	}

}
