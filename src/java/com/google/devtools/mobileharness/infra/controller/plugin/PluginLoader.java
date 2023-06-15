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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.infra.controller.plugin.provider.AnnotatedPluginClassProvider;
import com.google.devtools.mobileharness.infra.controller.plugin.provider.AnnotatedPluginModuleClassProvider;
import com.google.devtools.mobileharness.infra.controller.plugin.provider.NamedPluginClassProvider;
import com.google.devtools.mobileharness.infra.controller.plugin.provider.NamedPluginModuleClassProvider;
import com.google.devtools.mobileharness.infra.controller.plugin.provider.PluginClassProvider;
import com.google.devtools.mobileharness.infra.controller.plugin.provider.PluginModuleClassProvider;
import com.google.devtools.mobileharness.infra.controller.plugin.provider.RetryPluginClassProvider;
import com.google.devtools.mobileharness.infra.controller.plugin.provider.RetryPluginModuleClassProvider;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.ProvisionException;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.PluginModule;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

/**
 * Plugin loader for loading classes from the jar files.
 *
 * <p>If {@code className} is specified, the loader tries to load class with the given name.
 * Otherwise, the loader tries to load all classes with {@link Plugin} annotation and the given
 * {@code pluginType}. If no class matches the given {@code pluginType}, then the loader tries to
 * load all classes with {@link Plugin} annotation and the default plugin type {@link
 * PluginType#UNSPECIFIED} instead.
 *
 * <p>If forceLoadFromJarClassRegex is specified, we force loading of classes with names matching
 * the supplied regex from our plugin {@link classLoader}, rather than its parent {@link
 * ClassLoader}. This can be used to resolve problems arising from a plugin's unintended use of
 * classes from Mobile Harness, rather than its own classes (since parent classes are always used,
 * if they are present).
 *
 * <p>{@code moduleClassNames} follows the same pattern, however uses {@link PluginModule} instead.
 */
@NotThreadSafe
public class PluginLoader implements AutoCloseable {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** File paths of the plugin jars. */
  private final ImmutableList<URI> jarUris;

  /** If specified, only loads this class from the plugins. */
  @Nullable private final String className;

  /** If specified, only loads the given classes as plugin modules. */
  @Nullable private final ImmutableList<String> moduleClassNames;

  /**
   * If specified, force loading of classes with names matching the supplied regex from our plugin
   * {@link classLoader}, rather than its parent {@link ClassLoader}. This can be used to resolve
   * problems arising from a plugin's unintended use of classes from Mobile Harness, rather than its
   * own classes (since parent classes are always used, if they are present).
   */
  @Nullable private final String forceLoadFromJarClassRegex;

  @Nullable private final LogCollector<?> log;

  private final PluginType pluginType;
  private final ImmutableList<Module> systemModules;

  private final Object lock = new Object();

  @GuardedBy("lock")
  private boolean isLoaded;

  @GuardedBy("lock")
  private boolean isClosed;

  /** Instances of the plugin classes. */
  @GuardedBy("lock")
  @Nullable
  private List<Object> plugins;

  /** Dedicate class loader for the given plugin jars. */
  @GuardedBy("lock")
  @Nullable
  private URLClassLoader classLoader;

  /**
   * Creates a plugin loader.
   *
   * @param jarUris uris of the jar files which defines the plugin classes
   * @param className canonical name of the target plugin class, only load it if specified;
   *     otherwise, all classes marked with @{@link Plugin} are loaded
   * @param moduleClassNames canonical names of the target plugin module classes, only load them if
   *     specified; otherwise all classes marked with @{@link PluginModule} are loaded.
   * @param pluginType plugin type which should not be {@link PluginType#UNSPECIFIED}
   * @param systemModules extra modules to load first. These are modules that the system will use to
   *     interact with the plugins.
   */
  public PluginLoader(
      Iterable<URI> jarUris,
      @Nullable String className,
      @Nullable Collection<String> moduleClassNames,
      @Nullable String forceLoadFromJarClassRegex,
      PluginType pluginType,
      @Nullable LogCollector<?> log,
      Module... systemModules) {
    checkArgument(
        !PluginType.UNSPECIFIED.equals(pluginType),
        "Plugin type should not be %s",
        PluginType.UNSPECIFIED);
    this.jarUris = ImmutableList.copyOf(jarUris);
    this.className = className;
    if (moduleClassNames != null) {
      this.moduleClassNames = ImmutableList.copyOf(moduleClassNames);
    } else {
      this.moduleClassNames = null;
    }
    this.forceLoadFromJarClassRegex = forceLoadFromJarClassRegex;
    this.pluginType = pluginType;
    this.log = log;
    this.systemModules = ImmutableList.copyOf(systemModules);
  }

  /**
   * Creates a plugin loader.
   *
   * @param jarPaths path of the jar files which defines the plugin classes
   * @param className canonical name of the target plugin class, only load it if specified;
   *     otherwise, all classes marked with @{@link Plugin} are loaded
   * @param moduleClassNames canonical names of the target plugin module classes, only load them if
   *     specified; otherwise all classes marked with @{@link PluginModule} are loaded
   * @param pluginType plugin type which should not be {@link PluginType#UNSPECIFIED}
   * @param systemModules extra modules to load first. These are modules that the system will use to
   *     interact with the plugins.
   */
  public PluginLoader(
      Collection<String> jarPaths,
      @Nullable String className,
      @Nullable Collection<String> moduleClassNames,
      @Nullable String forceLoadFromJarClassRegex,
      PluginType pluginType,
      @Nullable LogCollector<?> log,
      Module... systemModules) {
    this(
        jarPaths.stream().map(File::new).map(File::toURI).collect(Collectors.toList()),
        className,
        moduleClassNames,
        forceLoadFromJarClassRegex,
        pluginType,
        log,
        systemModules);
  }

  /**
   * Creates plugin instances from the plugin jars. If call this method, the plugin class should
   * provide a zero-argument constructor. And if there is any plugin instance created, don't forget
   * to call {@link #close()} after you no longer needs the plugin instances.
   *
   * @return if there is any new plugin instance created
   */
  public boolean load() throws MobileHarnessException {
    synchronized (lock) {
      if (isLoaded || isClosed) {
        return false;
      }
      isLoaded = true;
      List<URL> jarUrls = new ArrayList<>();
      for (URI uri : jarUris) {
        try {
          jarUrls.add(uri.toURL());
        } catch (MalformedURLException e) {
          throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
              BasicErrorId.PLUGIN_LOADER_FAILED_TO_GET_JAR_URL,
              String.format("Failed to get URL of JAR [%s]", uri),
              e);
        }
      }
      logger.atInfo().log("Loading plugins from jars %s", jarUrls);
      classLoader = new PluginClassLoader(jarUrls);

      // Finds plugin module classes.
      Set<Class<? extends Module>> moduleClasses;
      if (moduleClassNames == null) {
        logger.atInfo().log(
            "No plugin module class name given, searching plugin module classes"
                + " by plugin module annotation");
        moduleClasses = new HashSet<>();
        for (URL jarUrl : jarUrls) {
          logger.atInfo().log("Searching plugin module classes in jar [%s]", jarUrl);

          ConfigurationBuilder configBuilder =
              new ConfigurationBuilder()
                  .setUrls(ImmutableList.of(jarUrl))
                  .addClassLoader(classLoader);
          Reflections reflections = new Reflections(configBuilder);
          PluginModuleClassProvider moduleClassProvider =
              new RetryPluginModuleClassProvider(
                  new AnnotatedPluginModuleClassProvider(
                      reflections, log, true /* warnUnmatchedTypes */, pluginType),
                  new AnnotatedPluginModuleClassProvider(
                      reflections, log, false /* warnUnmatchedTypes */, PluginType.UNSPECIFIED));

          Set<Class<? extends Module>> newModuleClasses;
          try {
            newModuleClasses = moduleClassProvider.getPluginModuleClasses();
          } catch (MobileHarnessException e) {
            close();
            throw e;
          }
          logger.atInfo().log(
              "Get plugin module classes %s from jar [%s]", newModuleClasses, jarUrl);
          moduleClasses.addAll(newModuleClasses);
        }
      } else {
        logger.atInfo().log(
            "Searching plugin module classes in all jars by names %s", moduleClassNames);
        PluginModuleClassProvider moduleClassProvider =
            new NamedPluginModuleClassProvider(moduleClassNames, classLoader);
        try {
          moduleClasses = moduleClassProvider.getPluginModuleClasses();
        } catch (MobileHarnessException e) {
          close();
          throw e;
        }
        logger.atInfo().log("Get plugin module classes %s", moduleClasses);
      }

      // Finds plugin classes.
      Set<Class<?>> classes;
      if (Strings.isNullOrEmpty(className)) {
        logger.atInfo().log(
            "No plugin class name given, searching plugin class by plugin annotation");
        classes = new HashSet<>();
        for (URL jarUrl : jarUrls) {
          logger.atInfo().log("Searching plugin classes in jar [%s]", jarUrl);

          // Class name not specified, finds all classes marked with @Plugin.
          ConfigurationBuilder configBuilder =
              new ConfigurationBuilder()
                  .setUrls(ImmutableList.of(jarUrl))
                  .addClassLoader(classLoader);
          Reflections reflections = new Reflections(configBuilder);
          PluginClassProvider classProvider =
              new RetryPluginClassProvider(
                  new AnnotatedPluginClassProvider(
                      reflections, log, true /* warnUnmatchedTypes */, pluginType),
                  new AnnotatedPluginClassProvider(
                      reflections, log, false /* warnUnmatchedTypes */, PluginType.UNSPECIFIED));

          Set<Class<?>> newClasses;
          try {
            newClasses = classProvider.getPluginClasses();
          } catch (MobileHarnessException e) {
            close();
            throw e;
          }
          logger.atInfo().log("Get plugin classes %s from jar [%s]", newClasses, jarUrl);
          if (newClasses.size() > 1) {
            if (log != null) {
              log.atWarning().log(
                  "Get more than one plugin class from jar [%s]: %s", jarUrl, newClasses);
            }
            logger.atWarning().log(
                "Get more than one plugin class from jar [%s]: %s", jarUrl, newClasses);
          }
          classes.addAll(newClasses);
        }
      } else {
        logger.atInfo().log("Searching plugin class in all jars by name [%s]", className);
        PluginClassProvider classProvider =
            new NamedPluginClassProvider(Collections.singleton(className), classLoader);
        try {
          classes = classProvider.getPluginClasses();
        } catch (MobileHarnessException e) {
          close();
          throw e;
        }
        logger.atInfo().log("Get plugin class %s", classes);
      }

      if (classes.isEmpty()) {
        logger.atInfo().log("No plugin is loaded");
        return false;
      }

      logger.atInfo().log("Create injector with plugin module instances");
      Set<Module> moduleInstances = new HashSet<>(systemModules);
      for (Class<? extends Module> moduleClass : moduleClasses) {
        try {
          moduleInstances.add(moduleClass.getConstructor().newInstance());
        } catch (ReflectiveOperationException e) {
          close();
          throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
              BasicErrorId.PLUGIN_LOADER_FAILED_TO_CREATE_PLUGIN_MODULE_INSTANCE,
              String.format("Failed to create plugin module instance [%s]", moduleClass.getName()),
              e);
        }
      }
      Injector injector = Guice.createInjector(moduleInstances);

      // Creates plugin instances.
      plugins = new ArrayList<>();
      for (Class<?> pluginClass : classes) {
        try {
          plugins.add(injector.getInstance(pluginClass));
          logger.atInfo().log("Loaded plugin: %s", pluginClass.getName());
        } catch (ProvisionException e) {
          close();
          throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
              BasicErrorId.PLUGIN_LOADER_FAILED_TO_CREATE_PLUGIN_INSTANCE,
              String.format("Failed to create plugin instance [%s]", pluginClass.getName()),
              e);
        }
      }
      return true;
    }
  }

  /** Gets the plugin instances if there is any plugin loaded. Never return null. */
  public List<Object> getPlugins() {
    synchronized (lock) {
      if (plugins == null) {
        return ImmutableList.of();
      } else {
        return plugins;
      }
    }
  }

  /**
   * Closes the class loader. Note if there is any plugin instances created, always call this method
   * after you no longer needs the plugin instances.
   */
  @Override
  public void close() {
    synchronized (lock) {
      isClosed = true;
      if (classLoader != null) {
        try {
          classLoader.close();
          classLoader = null;
        } catch (IOException e) {
          logger.atWarning().withCause(e).log(
              "Failed to close plugin class loader for %s", jarUris);
        }
      }
    }
  }

  /** The factory to create instance. */
  public static class Factory {
    public PluginLoader create(
        Collection<String> jarPaths,
        @Nullable String className,
        @Nullable Collection<String> moduleClassNames,
        @Nullable String forceLoadFromJarClassRegex,
        PluginType pluginType,
        @Nullable LogCollector<?> log,
        Module... systemModules) {
      return new PluginLoader(
          jarPaths,
          className,
          moduleClassNames,
          forceLoadFromJarClassRegex,
          pluginType,
          log,
          systemModules);
    }
  }

  /**
   * Operates exactly as a {@link URLClassLoader}, with the exception that any classes with names
   * matching the optional {@link #forceLoadFromJarClassRegex} will not be loaded from the parent
   * {@link ClassLoader}.
   *
   * <p>This gives plugin authors a mechanism to force the system to load certain classes from their
   * plugin library, rather than from Mobile Harness, in situations where an unintended mixing of
   * classes from the parent (Mobile Harness) classloader results in problems.
   */
  private final class PluginClassLoader extends URLClassLoader {

    private PluginClassLoader(List<URL> jarUrls) {
      super(jarUrls.toArray(new URL[0]));
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
