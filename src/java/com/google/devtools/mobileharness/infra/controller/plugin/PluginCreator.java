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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.groupingBy;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.plugin.loader.PluginInstantiator;
import com.google.devtools.mobileharness.infra.controller.plugin.provider.AnnotatedPluginClassProvider;
import com.google.devtools.mobileharness.infra.controller.plugin.provider.AnnotatedPluginModuleClassProvider;
import com.google.devtools.mobileharness.infra.controller.plugin.provider.NamedPluginClassProvider;
import com.google.devtools.mobileharness.infra.controller.plugin.provider.NamedPluginModuleClassProvider;
import com.google.devtools.mobileharness.infra.controller.plugin.provider.PluginClassProvider;
import com.google.devtools.mobileharness.infra.controller.plugin.provider.PluginModuleClassProvider;
import com.google.devtools.mobileharness.infra.controller.plugin.provider.RetryPluginClassProvider;
import com.google.devtools.mobileharness.infra.controller.plugin.provider.RetryPluginModuleClassProvider;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.FormatMethod;
import com.google.inject.Module;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.Plugin.PluginType;
import com.google.wireless.qa.mobileharness.shared.controller.plugin.PluginModule;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;

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
 * the supplied regex from our plugin {@link #classLoader}, rather than its parent {@link
 * ClassLoader}. This can be used to resolve problems arising from a plugin's unintended use of
 * classes from Mobile Harness, rather than its own classes (since parent classes are always used,
 * if they are present).
 *
 * <p>{@code moduleClassNames} follows the same pattern, however uses {@link PluginModule} instead.
 */
@NotThreadSafe
public class PluginCreator implements AutoCloseable {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The factory to create instance. */
  public interface Factory {

    PluginCreator create(
        Collection<String> jarPaths,
        @Nullable Collection<String> classNames,
        @Nullable Collection<String> moduleClassNames,
        @Nullable String forceLoadFromJarClassRegex,
        PluginType pluginType,
        @Nullable LogCollector<?> log,
        Module... systemModules);
  }

  /** File paths of the plugin jars. */
  private final ImmutableList<URI> jarUris;

  /** If specified, only loads these classes from the plugins. */
  @Nullable private final ImmutableList<String> classNames;

  /** If specified, only loads the given classes as plugin modules. */
  @Nullable private final ImmutableList<String> moduleClassNames;

  /**
   * If specified, force loading of classes with names matching the supplied regex from our plugin
   * {@link #classLoader}, rather than its parent {@link ClassLoader}. This can be used to resolve
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
  private ClassLoader classLoader;

  /** The scan result of the plugin jars. */
  @GuardedBy("lock")
  @Nullable
  private ScanResult scanResult;

  /**
   * Creates a plugin loader.
   *
   * @param jarPaths path of the jar files which defines the plugin classes
   * @param classNames canonical names of the target plugin classes, only load them if specified;
   *     otherwise, all classes marked with @{@link Plugin} are loaded
   * @param moduleClassNames canonical names of the target plugin module classes, only load them if
   *     specified; otherwise all classes marked with @{@link PluginModule} are loaded
   * @param pluginType plugin type which should not be {@link PluginType#UNSPECIFIED}
   * @param systemModules extra modules to load first. These are modules that the system will use to
   *     interact with the plugins.
   */
  public PluginCreator(
      Collection<String> jarPaths,
      @Nullable Collection<String> classNames,
      @Nullable Collection<String> moduleClassNames,
      @Nullable String forceLoadFromJarClassRegex,
      PluginType pluginType,
      @Nullable LogCollector<?> log,
      Module... systemModules) {
    checkArgument(
        !PluginType.UNSPECIFIED.equals(pluginType),
        "Plugin type should not be %s",
        PluginType.UNSPECIFIED);
    this.jarUris = jarPaths.stream().map(File::new).map(File::toURI).collect(toImmutableList());
    this.classNames = classNames == null ? null : ImmutableList.copyOf(classNames);
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
   * Creates plugin instances from the plugin jars. If call this method, the plugin class should
   * provide a zero-argument constructor. And if there is any plugin instance created, don't forget
   * to call {@link #close()} after you no longer needs the plugin instances.
   *
   * @return if there is any new plugin instance created
   */
  @CanIgnoreReturnValue
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
          throw new MobileHarnessException(
              BasicErrorId.PLUGIN_LOADER_FAILED_TO_GET_JAR_URL,
              String.format("Failed to get URL of JAR [%s]", uri),
              e);
        }
      }

      logInfo("Loading plugins from jars %s", jarUrls);
      classLoader =
          PluginLoader.createClassLoader(
              jarUrls, getClass().getClassLoader(), forceLoadFromJarClassRegex);
      Set<Class<? extends Module>> moduleClasses = getPluginModuleClasses(jarUrls);
      Set<Class<?>> classes = getPluginClasses(jarUrls);
      if (classes.isEmpty()) {
        logInfo("No plugin is loaded");
        return false;
      }

      // Creates plugin instances.
      plugins = new ArrayList<>();
      for (Class<?> pluginClass : classes) {
        try {
          plugins.add(
              PluginInstantiator.instantiatePlugin(pluginClass, moduleClasses, systemModules));
          logInfo("Loaded plugin: %s", pluginClass.getName());
        } catch (MobileHarnessException e) {
          close();
          throw e;
        }
      }
      return true;
    }
  }

  /** Gets the plugin instances if there is any plugin loaded. Never return null. */
  public List<Object> getPlugins() {
    synchronized (lock) {
      return Objects.requireNonNullElseGet(plugins, ImmutableList::of);
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
      if (scanResult != null) {
        scanResult.close();
        scanResult = null;
      }
      if (classLoader != null) {
        clearEventBusSubscriberCache(classLoader);
        try {
          if (classLoader instanceof Closeable) {
            ((Closeable) classLoader).close();
          }
          classLoader = null;
        } catch (IOException e) {
          logWarning("Failed to close plugin class loader for %s", jarUris);
        }
      }
    }
  }

  /**
   * Evicts plugin classes loaded by the given {@code classLoader} from Guava's static {@link
   * com.google.common.eventbus.EventBus} subscriber cache ({@code
   * SubscriberRegistry.subscriberMethodsCache}).
   *
   * <p>The cache uses weak keys, but each cached {@link java.lang.reflect.Method} value strongly
   * references its declaring class (the key). This forms a reference cycle that prevents the plugin
   * {@link ClassLoader} from being garbage collected after the plugin is unloaded, causing a memory
   * leak on long-running lab servers (b/530756464). Explicitly invalidating the entries for classes
   * loaded by this class loader breaks the cycle so the class loader can be reclaimed.
   *
   * <p>This uses reflection because {@code SubscriberRegistry} and its cache field are
   * package-private. Any failure is logged and swallowed so it never disrupts plugin unloading.
   */
  private static void clearEventBusSubscriberCache(ClassLoader classLoader) {
    try {
      Class<?> registryClass = Class.forName("com.google.common.eventbus.SubscriberRegistry");
      Field field = registryClass.getDeclaredField("subscriberMethodsCache");
      field.setAccessible(true);
      // The field's runtime type is Cache<Class<?>, ImmutableList<Method>>; the value type is
      // irrelevant here so the unchecked cast to a wildcard value type is safe.
      @SuppressWarnings("unchecked")
      Cache<Class<?>, ?> cache = (Cache<Class<?>, ?>) field.get(null);
      // Class loaders are intentionally compared by identity: evict only the classes defined by
      // this plugin's own class loader, leaving other plugins' and system classes untouched.
      long sizeBefore = cache.size();
      @SuppressWarnings("ReferenceEquality")
      boolean evicted =
          cache
              .asMap()
              .keySet()
              .removeIf(key -> key != null && key.getClassLoader() == classLoader);
      if (evicted) {
        logger.atInfo().log(
            "Evicted entries from EventBus subscriber cache for class loader %s,"
                + " cache size: %d -> %d",
            classLoader, sizeBefore, cache.size());
      }
    } catch (ReflectiveOperationException | RuntimeException | Error e) {
      logger.atWarning().withCause(e).log(
          "Failed to clear EventBus subscriber cache for class loader %s", classLoader);
    }
  }

  /** Scans the target jars. Handles creation-specific exceptions explicitly. */
  private void scanJars(List<URL> jarUrls) throws MobileHarnessException {
    synchronized (lock) {
      if (isClosed) {
        logInfo("Plugin creator is closed, skip scanning all jars: [%s]", jarUrls);
        return;
      }
      if (scanResult != null) {
        logInfo("Plugin creator has already scanned all jars: [%s]", jarUrls);
        return;
      }
      try {
        scanResult =
            new ClassGraph()
                .overrideClassLoaders(classLoader)
                .enableAnnotationInfo() // module scanning is enabled by default
                .ignoreClassVisibility()
                .scan();
      } catch (Exception e) {
        close();
        throw new MobileHarnessException(
            BasicErrorId.PLUGIN_LOADER_FAILED_TO_SCAN_CLASS_GRAPH_IN_JAR,
            String.format("Error scanning class graph in plugin jar [%s]", jarUrls),
            e);
      }
    }
  }

  /**
   * Gets the plugin module classes. If {@code moduleClassNames} is specified, the loader tries to
   * load class with the given names. Otherwise, the loader tries to load all classes with {@link
   * PluginModule} annotation.
   */
  private Set<Class<? extends Module>> getPluginModuleClasses(List<URL> jarUrls)
      throws MobileHarnessException {
    synchronized (lock) {
      Set<Class<? extends Module>> moduleClasses = new HashSet<>();
      if (moduleClassNames == null) {
        logInfo(
            "No plugin module class name given, scanning the jars to search plugin module classes"
                + " by plugin module annotation");
        scanJars(jarUrls);
        if (scanResult == null) {
          logInfo("Failed to scan the jars: [%s]", jarUrls);
          return moduleClasses;
        }
        // Group class metadata from ScanResult by their originating JAR URL
        Map<URL, List<ClassInfo>> classesByJar =
            scanResult.getClassesWithAnnotation(PluginModule.class).stream()
                .collect(groupingBy(ClassInfo::getClasspathElementURL));
        // Evaluate the fallback rules independently for each JAR.
        for (var entry : classesByJar.entrySet()) {
          final URL jarUrl = entry.getKey();
          logInfo("Searching plugin module classes in jar [%s]", jarUrl);
          final List<ClassInfo> classInfos = entry.getValue();
          PluginModuleClassProvider moduleClassProvider =
              new RetryPluginModuleClassProvider(
                  new AnnotatedPluginModuleClassProvider(
                      classInfos, log, /* warnUnmatchedTypes= */ true, pluginType),
                  new AnnotatedPluginModuleClassProvider(
                      classInfos, log, /* warnUnmatchedTypes= */ false, PluginType.UNSPECIFIED));
          Set<Class<? extends Module>> newModuleClasses;
          try {
            newModuleClasses = moduleClassProvider.getPluginModuleClasses();
          } catch (MobileHarnessException e) {
            close();
            throw e;
          }
          logInfo("Get plugin module classes %s from jar [%s]", newModuleClasses, jarUrl);
          moduleClasses.addAll(newModuleClasses);
        }
      } else {
        logInfo("Searching plugin module classes in all jars by names %s", moduleClassNames);
        PluginModuleClassProvider moduleClassProvider =
            new NamedPluginModuleClassProvider(moduleClassNames, classLoader);
        try {
          moduleClasses = moduleClassProvider.getPluginModuleClasses();
        } catch (MobileHarnessException e) {
          close();
          throw e;
        }
        logInfo("Get plugin module classes %s", moduleClasses);
      }
      return moduleClasses;
    }
  }

  /**
   * Gets the plugin classes. If {@code classNames} is specified, the loader tries to load class
   * with the given names. Otherwise, the loader tries to load all classes with {@link Plugin}
   * annotation.
   */
  private Set<Class<?>> getPluginClasses(List<URL> jarUrls) throws MobileHarnessException {
    synchronized (lock) {
      Set<Class<?>> classes = new HashSet<>();
      if (classNames == null) {
        logInfo("No plugin class name given, searching plugin class by plugin annotation");
        scanJars(jarUrls);
        if (scanResult == null) {
          logInfo("Failed to scan the jars: [%s]", jarUrls);
          return classes;
        }
        // Group class metadata from ScanResult by their originating JAR URL
        Map<URL, List<ClassInfo>> classesByJar =
            scanResult.getClassesWithAnnotation(Plugin.class).stream()
                .collect(groupingBy(ClassInfo::getClasspathElementURL));
        // Evaluate the fallback rules independently for each JAR.
        for (var entry : classesByJar.entrySet()) {
          final URL jarUrl = entry.getKey();
          logInfo("Searching plugin classes in jar [%s]", jarUrl);
          final List<ClassInfo> classInfos = entry.getValue();
          PluginClassProvider classProvider =
              new RetryPluginClassProvider(
                  new AnnotatedPluginClassProvider(
                      classInfos, log, /* warnUnmatchedTypes= */ true, pluginType),
                  new AnnotatedPluginClassProvider(
                      classInfos, log, /* warnUnmatchedTypes= */ false, PluginType.UNSPECIFIED));
          Set<Class<?>> newClasses;
          try {
            newClasses = classProvider.getPluginClasses();
          } catch (MobileHarnessException e) {
            close();
            throw e;
          }
          logInfo("Get plugin classes %s from jar [%s]", newClasses, jarUrl);
          if (newClasses.size() > 1) {
            logWarning("Get more than one plugin class from jar [%s]: %s", jarUrl, newClasses);
          }
          classes.addAll(newClasses);
        }
      } else {
        logInfo("Searching plugin classes in all jars by names %s", classNames);
        PluginClassProvider classProvider = new NamedPluginClassProvider(classNames, classLoader);
        try {
          classes = classProvider.getPluginClasses();
        } catch (MobileHarnessException e) {
          close();
          throw e;
        }
        logInfo("Get plugin classes %s", classes);
      }
      return classes;
    }
  }

  /** Logs an info message to the log collector if present; otherwise logs to the logger. */
  @FormatMethod
  private void logInfo(String format, Object... args) {
    if (log != null) {
      log.atInfo().alsoTo(logger).log(format, args);
    } else {
      logger.atInfo().logVarargs(format, args);
    }
  }

  /** Logs a warning message to the log collector if present; otherwise logs to the logger. */
  @FormatMethod
  private void logWarning(String format, Object... args) {
    if (log != null) {
      log.atWarning().alsoTo(logger).log(format, args);
    } else {
      logger.atWarning().logVarargs(format, args);
    }
  }
}
