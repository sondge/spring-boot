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

package org.springframework.boot.loader.archive;

import org.springframework.boot.loader.Launcher;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.jar.Manifest;

/**
 * An archive that can be launched by the {@link Launcher}.
 *
 * 一个可以被 {@link Launcher} 运行的镜像
 *
 * @author Phillip Webb
 * @since 1.0.0
 * @see JarFileArchive
 */
public interface Archive extends Iterable<Archive.Entry>, AutoCloseable {

	/**
	 * Returns a URL that can be used to load the archive.
	 *
	 * 可以被加载 archive 使用的 URL
	 * @return the archive URL
	 * @throws MalformedURLException if the URL is malformed
	 */
	URL getUrl() throws MalformedURLException;

	/**
	 * Returns the manifest of the archive.
	 *
	 * 返回镜像的 Mainfest
	 * @return the manifest
	 * @throws IOException if the manifest cannot be read
	 */
	Manifest getManifest() throws IOException;

	/**
	 * Returns nested {@link Archive}s for entries that match the specified filter.
	 *
	 * 根据指定的匹配返回最近的 镜像
	 * @param filter the filter used to limit entries
	 * @return nested archives
	 * @throws IOException if nested archives cannot be read
	 */
	List<Archive> getNestedArchives(EntryFilter filter) throws IOException;

	/**
	 * Closes the {@code Archive}, releasing any open resources.
	 * @throws Exception if an error occurs during close processing
	 * @since 2.2.0
	 */
	@Override
	default void close() throws Exception {

	}

	/**
	 * Represents a single entry in the archive.
	 */
	interface Entry {

		/**
		 * Returns {@code true} if the entry represents a directory.
		 *
		 * 返回指定的实体是否是文件
		 * @return if the entry is a directory
		 */
		boolean isDirectory();

		/**
		 * Returns the name of the entry.
		 *
		 * 返回这个实体的名称
		 * @return the name of the entry
		 */
		String getName();

	}

	/**
	 * Strategy interface to filter {@link Entry Entries}.
	 */
	interface EntryFilter {

		/**
		 * Apply the jar entry filter.
		 *
		 * 应用这个 jar 实体过滤器
		 * @param entry the entry to filter
		 * @return {@code true} if the filter matches
		 */
		boolean matches(Entry entry);

	}

}
