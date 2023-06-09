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

package com.google.wireless.qa.mobileharness.shared.model.job.in.spec;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.reflect.TypeToken;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Extension;
import com.google.protobuf.ExtensionLite;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import com.google.protobuf.UnknownFieldSet;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.JobSpecWalker.Visitor;
import com.google.wireless.qa.mobileharness.shared.proto.spec.BaseSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.DecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.DriverSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.FieldDetail;
import com.google.wireless.qa.mobileharness.shared.proto.spec.FileDetail;
import com.google.wireless.qa.mobileharness.shared.proto.spec.JobSpec;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ConfigurationBuilder;

/** Helper for {@link JobSpec}. */
public class JobSpecHelper {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Prefix of file tags in returns of method {@link #getFiles}. */
  public static final String FILE_TAG_PREFIX = "--++**<<$$JOB_SPEC$$>>**++--";

  /** Valid extensible specs in {@code JobSpec}. */
  private static final ImmutableSet<Descriptor> EXTENSIBLE_SPEC =
      ImmutableSet.of(DriverSpec.getDescriptor(), DecoratorSpec.getDescriptor());

  /** Prefixes for packages to register the job spec extensions. */
  private static final ImmutableList<String> DEFAULT_PACKAGE_PREFIXES =
      ImmutableList.of(
          "com.google.wireless.qa.mobileharness.shared.proto.spec",
          "com.google.devtools.deviceaction.framework.proto.action");

  /**
   * Holder of lazy initialized {@link #defaultHelper}, which holds all specs defined in Mobile
   * Harness.
   */
  private static class DefaultHelperHolder {

    private static final JobSpecHelper defaultHelper = new JobSpecHelper();

    static {
      defaultHelper.registerSpecUnderPackage(DEFAULT_PACKAGE_PREFIXES);
    }
  }

  /** Registry for keeping all {@code JobSpec} extensions. */
  private final ExtensionRegistry registry = ExtensionRegistry.newInstance();

  /**
   * Classes of registered {@code JobSpec} extensions. Keep it consistent with {@link #registry}.
   */
  private final Set<Class<? extends Message>> registeredExtensionsClasses = new HashSet<>();

  /** Gets default helper that loads all specs under Mobile Harness default spec packages. */
  public static JobSpecHelper getDefaultHelper() {
    return DefaultHelperHolder.defaultHelper;
  }

  @SuppressWarnings({"unused", "unchecked"})
  public void registerSpecUnderPackage(List<String> packageNames) {
    // Params for reflection configuration.
    List<Object> params = new ArrayList<>(packageNames);
    params.add(new SubTypesScanner(false));
    params.add(
        new ClassLoader(JobSpecHelper.class.getClassLoader()) {
          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            Enumeration<URL> original = getParent().getResources(name);
            List<URL> finalized = new ArrayList<>();
            while (original.hasMoreElements()) {
              URL url = original.nextElement();
              if (Ascii.equalsIgnoreCase("file", url.getProtocol())) {
                logger.atWarning().log("Skip loading specs from src path: %s", url);
              } else {
                finalized.add(url);
              }
            }
            return new Enumeration<URL>() {
              Iterator<URL> iter = finalized.iterator();

              @Override
              public boolean hasMoreElements() {
                return iter.hasNext();
              }

              @Override
              public URL nextElement() {
                return iter.next();
              }
            };
          }
        });
    Reflections reflections =
        new Reflections(ConfigurationBuilder.build(params.toArray(new Object[0])));
    for (Class<?> clazz : reflections.getSubTypesOf(Object.class)) {
      if (Message.class.isAssignableFrom(clazz)) {
        registerSpecExtension((Class<? extends Message>) clazz);
      }
    }
  }

  /**
   * Registers job spec extension if {@code clazz} is a valid extension of {@code
   * mobileharness.shared.spec.DriverSpec} or {@code mobileharness.shared.spec.DecoratorSpec}
   *
   * @return whether {@code clazz} is successfully registered.
   */
  @VisibleForTesting
  public boolean registerSpecExtension(Class<? extends Message> clazz) {
    Extension<?, ?> extension = getSpecExtension(clazz);
    if (extension == null) {
      return false;
    }
    registry.add(extension);
    registeredExtensionsClasses.add(clazz);
    logger.atFine().log("Registered job spec: %s", clazz.getName());
    return true;
  }

  public JobSpec parseText(String jobSpecText) throws MobileHarnessException {
    JobSpec.Builder builder = JobSpec.newBuilder();
    try {
      TextFormat.merge(jobSpecText, registry, builder);
      return builder.build();
    } catch (ParseException e) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SPEC_PARSE_PROTOBUF_ERROR,
          "Failed to parse JobSpec text: " + jobSpecText,
          e);
    }
  }

  /** Merges specs together. For conflict settings, the latter one overides the former one. */
  public JobSpec mergeSpec(List<JobSpec> specs) {
    JobSpec.Builder builder = JobSpec.newBuilder();
    for (JobSpec other : specs) {
      builder.mergeFrom(other);
    }
    return builder.build();
  }

  /**
   * Gets spec with class {@code specClass} from {@code jobSpec}.
   *
   * @throws MobileHarnessException if {@code specClass} is not a valid extension of {@link JobSpec}
   */
  @SuppressWarnings("unchecked")
  public <T extends Message> T getSpec(JobSpec jobSpec, Class<T> specClass)
      throws MobileHarnessException {
    Extension<?, ?> extension = getSpecExtension(specClass);
    if (extension == null) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SPEC_PARSE_PROTOBUF_ERROR,
          String.format("class %s doesn't have valid extension", specClass));
    }
    Descriptor containerType = extension.getDescriptor().getContainingType();
    if (containerType == DecoratorSpec.getDescriptor()) {
      return (T)
          jobSpec.getDecoratorSpec().getExtension((ExtensionLite<DecoratorSpec, ?>) extension);
    } else if (containerType == DriverSpec.getDescriptor()) {
      return (T) jobSpec.getDriverSpec().getExtension((ExtensionLite<DriverSpec, ?>) extension);
    }
    throw new MobileHarnessException(
        BasicErrorId.JOB_SPEC_PARSE_PROTOBUF_ERROR,
        String.format(
            "Extension of %s has an invalid container type: %s",
            specClass, containerType.getFullName()));
  }

  /**
   * Sets {@code spec} to {@link JobSpec} if {@code spec} is a registered extension of {@link
   * JobSpec}.
   */
  @SuppressWarnings("unchecked")
  public <T extends Message> void setSpec(JobSpec.Builder jobSpec, T spec)
      throws MobileHarnessException {
    Class<?> specClass = spec.getClass();
    Extension<?, ?> extension = getSpecExtension(spec.getClass());
    if (extension == null) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SPEC_PARSE_PROTOBUF_ERROR,
          String.format("class %s doesn't have valid extension: ", specClass));
    }
    Descriptor containerType = extension.getDescriptor().getContainingType();
    if (containerType == DecoratorSpec.getDescriptor()) {
      jobSpec
          .getDecoratorSpecBuilder()
          .setExtension((ExtensionLite<DecoratorSpec, T>) extension, spec);
    } else if (containerType == DriverSpec.getDescriptor()) {
      jobSpec.getDriverSpecBuilder().setExtension((ExtensionLite<DriverSpec, T>) extension, spec);
    } else {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SPEC_PARSE_PROTOBUF_ERROR,
          String.format(
              "Extension of %s has an invalid container type: %s",
              specClass, containerType.getFullName()));
    }
  }

  /**
   * Gets valid extension from {@code specClass}. Returns null if failed to find one.
   *
   * @param specClass class of spec.
   */
  @VisibleForTesting
  @Nullable
  static Extension<?, ?> getSpecExtension(Class<? extends Message> specClass) {
    for (Field field : specClass.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      TypeToken<?> fieldType = TypeToken.of(field.getType());
      if (!fieldType.isSubtypeOf(Extension.class)) {
        continue;
      }
      try {
        Extension<?, ?> extension = (Extension<?, ?>) field.get(null);
        Descriptor containerType = extension.getDescriptor().getContainingType();
        if (!EXTENSIBLE_SPEC.contains(containerType)) {
          continue;
        }
        return extension;
      } catch (IllegalArgumentException | IllegalAccessException e) {
        // Ignore the exception.
      }
    }
    return null;
  }

  /** Gets extension registry in helper. */
  public ExtensionRegistry getExtensionRegistry() {
    return registry;
  }

  /** Gets registered extension classes in helper. */
  public Set<Class<? extends Message>> getRegisteredExtensionClasses() {
    return ImmutableSet.copyOf(registeredExtensionsClasses);
  }

  private class UnknownExtensionResolver extends Visitor {
    @Override
    public void visitUnknownFields(Message.Builder message, UnknownFieldSet unknownFields)
        throws MobileHarnessException {

      UnknownFieldSet.Builder unresolvedFields = UnknownFieldSet.newBuilder();
      UnknownFieldSet.Builder resolvedFields = UnknownFieldSet.newBuilder();
      Descriptor container = message.getDescriptorForType();
      boolean hasResolvedField = false;
      for (Map.Entry<Integer, UnknownFieldSet.Field> field : unknownFields.asMap().entrySet()) {
        if (registry.findImmutableExtensionByNumber(container, field.getKey()) == null) {
          unresolvedFields.addField(field.getKey(), field.getValue());
          continue;
        }
        resolvedFields.addField(field.getKey(), field.getValue());
        hasResolvedField = true;
      }
      if (!hasResolvedField) {
        return;
      }
      try {
        message
            .mergeFrom(resolvedFields.build().toByteArray(), registry)
            .setUnknownFields(unresolvedFields.build());
      } catch (InvalidProtocolBufferException e) {
        throw new MobileHarnessException(
            BasicErrorId.JOB_SPEC_PARSE_PROTOBUF_ERROR,
            "Can't parse unknown exception of job spec:" + e.getMessage());
      }
    }
  }

  /**
   * Resolves unknown spec extension in {@code jobSpec} by using {@link #registry}, and return the
   * resolved JobSpec.
   *
   * @throws MobileHarnessException if {@code jobSpec} is invalid
   */
  public JobSpec resolveUnknownSpecExtension(JobSpec jobSpec)
      throws MobileHarnessException, InterruptedException {
    return JobSpecWalker.resolve(jobSpec, new UnknownExtensionResolver());
  }

  /** A JobSpec visitor to collect all file paths. */
  public abstract static class FilePathVisitor extends Visitor {

    /**
     * handle value of each file. If the returned value is not null, use it to replace the original
     * value.
     */
    public abstract String handleFile(String file)
        throws MobileHarnessException, InterruptedException;

    @Override
    public final void visitPrimitiveFileField(Message.Builder builder, FieldDescriptor field)
        throws MobileHarnessException, InterruptedException {
      if (field.isRepeated()) {
        int size = builder.getRepeatedFieldCount(field);
        for (int i = 0; i < size; i++) {
          String newValue = handleFile((String) builder.getRepeatedField(field, i));
          if (newValue != null) {
            builder.setRepeatedField(field, i, newValue);
          }
        }
      } else {
        String newValue = handleFile((String) builder.getField(field));
        if (newValue != null) {
          builder.setField(field, newValue);
        }
      }
    }
  }

  /** Gets all file paths in {@code jobSpec}. */
  public static Map<String, String> getFiles(JobSpec jobSpec)
      throws MobileHarnessException, InterruptedException {
    Map<String, String> files = new HashMap<>();
    forEachFiles(
        jobSpec,
        new FilePathVisitor() {
          @Nullable
          @Override
          public String handleFile(String file) {
            files.put(FILE_TAG_PREFIX + file, file);
            return null;
          }
        });
    return files;
  }

  /**
   * Calls {@code visitor} on each file field in {@code jobSpec}. If returned value of {@code
   * handler} is not null, the original value will be replaced with it.
   *
   * @param jobSpec JobSpec to visit
   * @param visitor visitor of {@code jobSpec}
   */
  public static JobSpec forEachFiles(JobSpec jobSpec, FilePathVisitor visitor)
      throws MobileHarnessException, InterruptedException {
    return JobSpecWalker.resolve(jobSpec, visitor);
  }

  /**
   * Checks whether {@code clazz} is spec configable, which means we can get spec class of it.
   *
   * @return true if {@code clazz} is spec configable
   */
  public static boolean isSpecConfigable(Class<?> clazz) {
    try {
      getSpecClass(clazz);
      return true;
    } catch (MobileHarnessException e) {
      return false;
    }
  }

  /**
   * Gets spec proto class of {@code clazz} if it is a valid SpecConfigable class.
   *
   * @param clazz The class to check. You could use {link #asSpecConfigable} to check if it is a
   *     SpecConfigable class
   * @return the Spec proto class
   * @throws MobileHarnessException if {@code clazz} is invalid, or it doesn't have a valid spec
   *     proto class
   */
  @SuppressWarnings("unchecked")
  public static Class<? extends Message> getSpecClass(Class<?> clazz)
      throws MobileHarnessException {
    if (!TypeToken.of(clazz).isSubtypeOf(SpecConfigable.class)) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SPEC_PARSE_PROTOBUF_ERROR,
          String.format("Class %s is not a subclass of SpecConfigable.", clazz));
    }
    TypeToken<?> specTypeToken =
        TypeToken.of(clazz).resolveType(SpecConfigable.class.getTypeParameters()[0]);
    Type specType = specTypeToken.getType();
    if (!(specType instanceof Class) || !specTypeToken.isSubtypeOf(Message.class)) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SPEC_PARSE_PROTOBUF_ERROR,
          String.format(
              "Class %s doesn't have a valid Spec class. Expect a subclass of %s, but is %s",
              clazz, Message.class, specType));
    }
    return (Class<? extends Message>) specType;
  }

  private static Descriptor getSpecDescriptor(Class<? extends Message> spec)
      throws MobileHarnessException {
    try {
      return getDefaultInstance(spec).getDescriptorForType();
    } catch (IllegalArgumentException e) {
      throw new MobileHarnessException(
          BasicErrorId.JOB_SPEC_PARSE_PROTOBUF_ERROR,
          "Failed to find default instance of spec: " + spec,
          e);
    }
  }

  public static Map<String, FieldDetail> getFieldDetails(Class<? extends Message> spec)
      throws MobileHarnessException {
    Map<String, FieldDetail> details = new HashMap<>();
    for (FieldDescriptor field : getSpecDescriptor(spec).getFields()) {
      // TODO: Make moscar support setting Message.
      if (field.getJavaType() == JavaType.MESSAGE) {
        continue;
      }
      if (field.getOptions().hasExtension(BaseSpec.fileDetail)) {
        continue;
      }
      // If a field doesn't have any details, it is treated as a field with empty fileDetail.
      details.put(field.getName(), field.getOptions().getExtension(BaseSpec.fieldDetail));
    }
    return details;
  }

  public static Map<String, FileDetail> getFileDetails(Class<? extends Message> spec)
      throws MobileHarnessException {
    Map<String, FileDetail> details = new HashMap<>();
    for (FieldDescriptor field : getSpecDescriptor(spec).getFields()) {
      // TODO: Make moscar support setting Message.
      if (field.getJavaType() == JavaType.MESSAGE) {
        continue;
      }
      if (field.getOptions().hasExtension(BaseSpec.fileDetail)) {
        details.put(field.getName(), field.getOptions().getExtension(BaseSpec.fileDetail));
      }
    }
    return details;
  }

  public static <T extends MessageLite> T getDefaultInstance(Class<T> type) {
    try {
      return type.cast(type.getMethod("getDefaultInstance").invoke(null));
    } catch (ReflectiveOperationException | ClassCastException e) {
      throw new IllegalArgumentException(
          "Message class must implement the #getDefaultInstance() static method: " + type, e);
    }
  }
}
