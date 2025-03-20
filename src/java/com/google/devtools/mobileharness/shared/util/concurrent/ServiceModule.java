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

/*
 * Copyright (C) 2011 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.devtools.mobileharness.shared.util.concurrent;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.inject.multibindings.MapBinder.newMapBinder;
import static com.google.inject.multibindings.Multibinder.newSetBinder;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.J2ktIncompatible;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.common.util.concurrent.ServiceManager.Listener;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.DoNotMock;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import javax.inject.Qualifier;

/**
 * A helper module binding {@linkplain Service services} and {@linkplain Listener ServiceManager
 * listeners} to the application {@link ServiceManager}.
 *
 * @author Chris Nokleberg
 */
@J2ktIncompatible
@GwtIncompatible
// For preventing service configuration outside of configureServices().
@SuppressWarnings("MutableGuiceModule")
public class ServiceModule extends AbstractModule {
  @Retention(RetentionPolicy.RUNTIME)
  @Qualifier
  private @interface Internal {}

  private static final Module INTERNAL =
      new AbstractModule() {
        @Override
        protected void configure() {}

        @Provides
        @Singleton
        @ExternalServiceManager
        ServiceManager provideServiceManager(
            Set<Service> services,
            @Internal Map<Integer, Listener> listeners,
            @Internal Map<Integer, Executor> executors) {
          ServiceManager manager = new ServiceManager(services);
          Iterator<Entry<Integer, Listener>> listenerIterator = listeners.entrySet().iterator();
          Iterator<Entry<Integer, Executor>> executorIterator = executors.entrySet().iterator();
          while (listenerIterator.hasNext() && executorIterator.hasNext()) {
            Entry<Integer, Listener> listenerEntry = listenerIterator.next();
            Entry<Integer, Executor> executorEntry = executorIterator.next();
            if (!listenerEntry.getKey().equals(executorEntry.getKey())) {
              throw new AssertionError();
            }
            manager.addListener(listenerEntry.getValue(), executorEntry.getValue());
          }
          return manager;
        }
      };

  private @Nullable Multibinder<Service> serviceMultibinder;
  private @Nullable MapBinder<Integer, Listener> serviceManagerListeners;
  private @Nullable MapBinder<Integer, Executor> serviceManagerExecutors;
  private static final AtomicInteger index = new AtomicInteger();

  @Override
  protected final void configure() {
    install(INTERNAL);
    Binder userBinder = binder().skipSources(ServiceModule.class, SingleServiceModule.class);
    serviceManagerExecutors =
        newMapBinder(userBinder, Integer.class, Executor.class, Internal.class);
    serviceManagerListeners =
        newMapBinder(userBinder, Integer.class, Listener.class, Internal.class);
    serviceMultibinder = newSetBinder(userBinder, Service.class);
    configureServices();
    serviceMultibinder = null;
    serviceManagerListeners = null;
    serviceManagerExecutors = null;
  }

  /** Returns a new module which will install the given service class. */
  public static ServiceModule forService(Class<? extends Service> service) {
    return forService(Key.get(service));
  }

  /** Returns a new module which will install the given service key. */
  public static ServiceModule forService(Key<? extends Service> key) {
    return new SingleServiceModule(key);
  }

  private static final class SingleServiceModule extends ServiceModule {
    final Key<? extends Service> key;

    SingleServiceModule(Key<? extends Service> key) {
      this.key = checkNotNull(key);
    }

    @Override
    protected void configureServices() {
      bindService().to(key);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      return obj instanceof SingleServiceModule && ((SingleServiceModule) obj).key.equals(key);
    }

    @Override
    public int hashCode() {
      return key.hashCode();
    }

    @Override
    public String toString() {
      return "ServiceModule[" + key + "]";
    }
  }

  /**
   * Returns a binding builder that will bind a service.
   *
   * <p>See {@link #configureServices}.
   */
  protected final LinkedBindingBuilder<Service> bindService() {
    if (serviceMultibinder == null) {
      throw new IllegalStateException("bindings may be configured only during configureServices()");
    }
    return serviceMultibinder.addBinding();
  }

  /**
   * Returns a binding builder that will bind a listener.
   *
   * <p>See {@link #configureServices}.
   */
  protected final ListenerExecutorBindingBuilder bindServiceManagerListener() {
    if (serviceManagerExecutors == null || serviceManagerListeners == null) {
      throw new IllegalStateException("bindings may be configured only during configureServices()");
    }
    int id = index.getAndIncrement();
    return new ListenerExecutorBindingBuilder(
        serviceManagerListeners.addBinding(id), serviceManagerExecutors.addBinding(id));
  }

  /**
   * To register a service, install an instance of this module like so:
   *
   * <pre>
   *   Guice.createInjector(..., new ServiceModule() {
   *     {@literal @}Override protected void configureServices() {
   *       <b>bindService().to(MyService.class);</b>
   *       <b>bindServiceManagerListener().to(MyListener.class);</b>
   *       <b>bindServiceManagerListener().withExecutor(executor).to(MyListener.class);</b>
   *     }
   *   });
   * </pre>
   *
   * <p>The services will be available as a {@code Set}.
   */
  protected void configureServices() {}

  /** A binder for configuring a {@link Listener}. */
  @DoNotMock("Use ServiceModule.bindServiceManagerListener")
  public interface ListenerBindingBuilder {
    /** See the EDSL examples at {@link com.google.inject.Binder}. */
    void to(TypeLiteral<? extends Listener> implementation);

    /** See the EDSL examples at {@link com.google.inject.Binder}. */
    void to(Class<? extends Listener> implementation);

    /** See the EDSL examples at {@link com.google.inject.Binder}. */
    void to(Key<? extends Listener> targetKey);

    /** See the EDSL examples at {@link com.google.inject.Binder}. */
    void toInstance(Listener instance);

    /** See the EDSL examples at {@link com.google.inject.Binder}. */
    void toProvider(Provider<? extends Listener> provider);

    /** See the EDSL examples at {@link com.google.inject.Binder}. */
    void toProvider(Class<? extends javax.inject.Provider<? extends Listener>> providerType);

    /** See the EDSL examples at {@link com.google.inject.Binder}. */
    void toProvider(TypeLiteral<? extends javax.inject.Provider<? extends Listener>> providerType);

    /** See the EDSL examples at {@link com.google.inject.Binder}. */
    void toProvider(Key<? extends javax.inject.Provider<? extends Listener>> providerKey);

    /** See the EDSL examples at {@link com.google.inject.Binder}. */
    <S extends Listener> void toConstructor(Constructor<S> constructor);

    /** See the EDSL examples at {@link com.google.inject.Binder}. */
    <S extends Listener> void toConstructor(
        Constructor<S> constructor, TypeLiteral<? extends S> type);
  }

  /**
   * A binding builder that enabled binding a {@link Listener} with a custom {@link Executor}.
   *
   * <p>N.B. All listeners are added to the {@link ServiceManager} at construction time, and as such
   * are bound as {@link Singleton singletons}.
   */
  public static class ListenerExecutorBindingBuilder implements ListenerBindingBuilder {
    private interface ExecutorAdder {
      void addTo(LinkedBindingBuilder<Executor> executorDelegate);
    }

    private static final ExecutorAdder DEFAULT =
        new ExecutorAdder() {
          @Override
          public void addTo(LinkedBindingBuilder<Executor> executorDelegate) {
            executorDelegate.toInstance(directExecutor());
          }
        };

    private final LinkedBindingBuilder<Listener> listenerDelegate;
    private final LinkedBindingBuilder<Executor> executorDelegate;
    private ExecutorAdder executor = DEFAULT;

    private ListenerExecutorBindingBuilder(
        LinkedBindingBuilder<Listener> listenerDelegate,
        LinkedBindingBuilder<Executor> executorDelegate) {
      this.listenerDelegate = listenerDelegate;
      this.executorDelegate = executorDelegate;
    }

    /**
     * Specify an {@link Executor} to use to execute the {@link Listener} callbacks. If none is
     * specified the {@link MoreExecutors#directExecutor()} will be used as a default.
     */
    @CanIgnoreReturnValue
    public ListenerBindingBuilder withExecutor(Executor executor) {
      checkNotNull(executor);
      checkState(this.executor == DEFAULT, "An executor was already specified.");
      this.executor =
          new ExecutorAdder() {
            @Override
            public void addTo(LinkedBindingBuilder<Executor> executorDelegate) {
              executorDelegate.toInstance(executor);
            }
          };
      return this;
    }

    /**
     * Specify an {@link Executor} to use to execute the {@link Listener} callbacks. If none is
     * specified the {@link MoreExecutors#directExecutor()} will be used as a default.
     */
    @CanIgnoreReturnValue
    public ListenerBindingBuilder withExecutor(Key<? extends Executor> executorKey) {
      checkNotNull(executorKey);
      checkState(this.executor == DEFAULT, "An executor was already specified.");
      this.executor =
          new ExecutorAdder() {
            @Override
            public void addTo(LinkedBindingBuilder<Executor> executorDelegate) {
              executorDelegate.to(executorKey);
            }
          };
      return this;
    }

    /**
     * Specify an {@link Executor} to use to execute the {@link Listener} callbacks. If none is
     * specified the {@link MoreExecutors#directExecutor()} will be used as a default.
     */
    @CanIgnoreReturnValue
    public ListenerBindingBuilder withExecutor(Provider<? extends Executor> executorProvider) {
      checkNotNull(executorProvider);
      checkState(this.executor == DEFAULT, "An executor was already specified.");
      this.executor =
          new ExecutorAdder() {
            @Override
            public void addTo(LinkedBindingBuilder<Executor> executorDelegate) {
              executorDelegate.toProvider(executorProvider);
            }
          };
      return this;
    }

    @Override
    public void to(TypeLiteral<? extends Listener> implementation) {
      listenerDelegate.to(implementation);
      executor.addTo(executorDelegate);
    }

    @Override
    public void to(Class<? extends Listener> implementation) {
      listenerDelegate.to(implementation);
      executor.addTo(executorDelegate);
    }

    @Override
    public void to(Key<? extends Listener> targetKey) {
      listenerDelegate.to(targetKey);
      executor.addTo(executorDelegate);
    }

    @Override
    public void toInstance(Listener instance) {
      listenerDelegate.toInstance(instance);
      executor.addTo(executorDelegate);
    }

    @Override
    public void toProvider(Provider<? extends Listener> provider) {
      listenerDelegate.toProvider(provider);
      executor.addTo(executorDelegate);
    }

    @Override
    public void toProvider(
        Class<? extends javax.inject.Provider<? extends Listener>> providerType) {
      listenerDelegate.toProvider(providerType);
      executor.addTo(executorDelegate);
    }

    @Override
    public void toProvider(
        TypeLiteral<? extends javax.inject.Provider<? extends Listener>> providerType) {
      listenerDelegate.toProvider(providerType);
      executor.addTo(executorDelegate);
    }

    @Override
    public void toProvider(Key<? extends javax.inject.Provider<? extends Listener>> providerKey) {
      listenerDelegate.toProvider(providerKey);
      executor.addTo(executorDelegate);
    }

    @Override
    public <S extends Listener> void toConstructor(Constructor<S> constructor) {
      listenerDelegate.toConstructor(constructor);
      executor.addTo(executorDelegate);
    }

    @Override
    public <S extends Listener> void toConstructor(
        Constructor<S> constructor, TypeLiteral<? extends S> type) {
      listenerDelegate.toConstructor(constructor, type);
      executor.addTo(executorDelegate);
    }
  }
}
