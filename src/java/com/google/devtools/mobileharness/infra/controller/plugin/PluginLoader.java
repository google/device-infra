/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.infra.controller.plugin;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import javax.annotation.Nullable;

/** Plugin loader for loading plugins. */
public class PluginLoader {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static URLClassLoader createClassLoader(
      Collection<String> jarPaths, ClassLoader parentClassLoader) throws MobileHarnessException {
    ImmutableList.Builder<URL> jarUrls = ImmutableList.builder();
    for (String jarPath : jarPaths) {
      try {
        jarUrls.add(new File(jarPath).toURI().toURL());
      } catch (MalformedURLException e) {
        throw new MobileHarnessException(
            BasicErrorId.PLUGIN_LOADER_MALFORMED_URL, "Malformed URL, jar_path=%s" + jarPath, e);
      }
    }
    return createClassLoader(
        jarUrls.build(), parentClassLoader, /* forceLoadFromJarClassRegex= */ null);
  }

  public static URLClassLoader createClassLoader(
      Collection<URL> jarUrls,
      ClassLoader parentClassLoader,
      @Nullable String forceLoadFromJarClassRegex) {
    return new PluginClassLoader(jarUrls, parentClassLoader, forceLoadFromJarClassRegex);
  }

  private PluginLoader() {}

  /**
   * Operates exactly as a {@link URLClassLoader}, with the exception that any classes with names
   * matching the optional {@link #forceLoadFromJarClassRegex} will not be loaded from the parent
   * {@link ClassLoader}.
   *
   * <p>This gives plugin authors a mechanism to force the system to load certain classes from their
   * plugin library, rather than from Mobile Harness, in situations where an unintended mixing of
   * classes from the parent (Mobile Harness) classloader results in problems.
   */
  private static class PluginClassLoader extends URLClassLoader {

    /**
     * If specified, force loading of classes with names matching the supplied regex from this class
     * loader, rather than its parent class loader. This can be used to resolve problems arising
     * from a plugin's unintended use of classes from Mobile Harness, rather than its own classes
     * (since parent classes are always used, if they are present).
     */
    @Nullable private final String forceLoadFromJarClassRegex;

    private PluginClassLoader(
        Collection<URL> jarUrls,
        ClassLoader parentClassLoader,
        @Nullable String forceLoadFromJarClassRegex) {
      super(jarUrls.toArray(new URL[0]), parentClassLoader);
      this.forceLoadFromJarClassRegex = forceLoadFromJarClassRegex;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (getClassLoadingLock(name)) {
        // First, check if the class has already been loaded
        Class<?> c = findLoadedClass(name);
        if (c == null) {
          if (forceLoadFromJarClassRegex != null && name.matches(forceLoadFromJarClassRegex)) {
            logger.atInfo().log(
                "Class %s forced to load from plugin library only, by"
                    + " *_force_load_from_jar_class_regex = %s",
                name, forceLoadFromJarClassRegex);
            c = findClass(name);
            if (resolve) {
              resolveClass(c);
            }
          } else {
            c = super.loadClass(name, resolve);
          }
        }
        return c;
      }
    }
  }
}
