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

package com.google.devtools.mobileharness.shared.util.filter;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerFeature;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabServerSetting;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.LabInfoMask;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.FieldMask;
import com.google.protobuf.util.FieldMaskUtil;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * A {@link LabInfoMask} compiled into cheap boolean gates, so that a data source can build only the
 * requested parts of each {@link LabInfo} instead of building everything and trimming afterwards.
 *
 * <p>This is the {@link LabInfo} counterpart of {@link CompiledDeviceInfoMask}; see that class for
 * the motivation.
 *
 * <h2>How to use it</h2>
 *
 * <ol>
 *   <li>Compile the mask once per query with {@link #of(LabInfoMask)}, or use {@link #retainAll()}
 *       when there is no mask. Instances are immutable and may be shared.
 *   <li>For each lab, obtain a {@link #newMaskedLabInfoBuilder()} and feed every field through its
 *       {@code setMasked*} method. Each takes a source object and a getter rather than a value and
 *       calls the getter only when the mask keeps that field, so expensive work belongs inside the
 *       getter.
 *   <li>{@link MaskedLabInfoBuilder#build()} yields the projected {@link LabInfo}, or empty when
 *       the mask drops {@link LabInfo} entirely. Dropped labs still count towards {@code
 *       lab_total_count}.
 * </ol>
 *
 * <h2>Mask semantics</h2>
 *
 * <ul>
 *   <li><b>No {@code field_mask}</b>: every field is kept. {@link #of} returns the shared {@link
 *       #retainAll()} instance for a default mask.
 *   <li><b>{@code field_mask} present but empty</b>: the {@link LabInfo} is dropped. Every gate is
 *       false and {@link MaskedLabInfoBuilder#build()} returns empty.
 *   <li><b>{@code field_mask} with paths</b>: a field is kept when the mask lists it, an ancestor
 *       of it, or a descendant of it (see {@link MaskUtils#isFieldRequested}). Every field of
 *       {@link LabInfo} and every field of {@link LabLocator} has its own gate, so a mask made of
 *       those paths is honoured by construction alone. A mask that reaches deeper, for example
 *       {@code lab_server_setting.port.num}, is honoured by {@link FieldMaskUtil#trim} on {@code
 *       build()}; such masks are rare and the trim costs a reflective copy per lab.
 * </ul>
 *
 * <p>This class compiles exactly the mask it is given. Anything a query needs beyond what the
 * client asked for, such as sort keys, is the caller's concern; see {@code
 * com.google.devtools.mobileharness.shared.labinfo.Projection}.
 */
@Immutable
public final class CompiledLabInfoMask {

  private static final String LAB_LOCATOR_PATH = "lab_locator";
  private static final String LAB_SERVER_SETTING_PATH = "lab_server_setting";
  private static final String LAB_SERVER_FEATURE_PATH = "lab_server_feature";
  private static final String LAB_STATUS_PATH = "lab_status";

  /** The {@link LabLocator} fields, each with its mask path and how to copy it. */
  private enum LocatorField {
    IP("lab_locator.ip") {
      @Override
      void copy(LabLocator full, LabLocator.Builder out) {
        out.setIp(full.getIp());
      }
    },
    HOST_NAME("lab_locator.host_name") {
      @Override
      void copy(LabLocator full, LabLocator.Builder out) {
        out.setHostName(full.getHostName());
      }
    },
    PORT("lab_locator.port") {
      @Override
      void copy(LabLocator full, LabLocator.Builder out) {
        out.addAllPort(full.getPortList());
      }
    },
    MASTER_DETECTED_IP("lab_locator.master_detected_ip") {
      @Override
      void copy(LabLocator full, LabLocator.Builder out) {
        if (full.hasMasterDetectedIp()) {
          out.setMasterDetectedIp(full.getMasterDetectedIp());
        }
      }
    },
    UNIVERSE("lab_locator.universe") {
      @Override
      void copy(LabLocator full, LabLocator.Builder out) {
        out.setUniverse(full.getUniverse());
      }
    };

    private static final ImmutableSet<LocatorField> ALL = ImmutableSet.copyOf(values());

    private final String path;

    LocatorField(String path) {
      this.path = path;
    }

    abstract void copy(LabLocator full, LabLocator.Builder out);
  }

  /**
   * Every path the builder projects by construction. A field mask made only of these paths needs no
   * {@link FieldMaskUtil#trim} on {@code build()}.
   */
  private static final ImmutableSet<String> NATIVE_PATHS =
      Stream.concat(
              Stream.of(
                  LAB_LOCATOR_PATH,
                  LAB_SERVER_SETTING_PATH,
                  LAB_SERVER_FEATURE_PATH,
                  LAB_STATUS_PATH),
              LocatorField.ALL.stream().map(field -> field.path))
          .collect(toImmutableSet());

  private static final CompiledLabInfoMask RETAIN_ALL =
      new CompiledLabInfoMask(LabInfoMask.getDefaultInstance());

  /**
   * Field mask to trim with on {@code build()}; null when every field is kept or every mask path is
   * projected by construction.
   */
  @Nullable private final FieldMask trimMask;

  private final boolean keepsLabInfo;
  private final boolean keepsLabServerSetting;
  private final boolean keepsLabServerFeature;
  private final boolean keepsLabStatus;

  /** The {@code lab_locator} fields that are kept; empty drops {@code lab_locator}. */
  private final ImmutableSet<LocatorField> keptLocatorFields;

  /** True when {@code lab_locator.host_name} is the only kept locator field. */
  private final boolean keepsOnlyHostName;

  /** Returns the shared mask that keeps every field. */
  public static CompiledLabInfoMask retainAll() {
    return RETAIN_ALL;
  }

  /** Compiles {@code mask}; a default mask yields {@link #retainAll()}. */
  public static CompiledLabInfoMask of(LabInfoMask mask) {
    requireNonNull(mask);
    return mask.equals(LabInfoMask.getDefaultInstance())
        ? RETAIN_ALL
        : new CompiledLabInfoMask(mask);
  }

  private CompiledLabInfoMask(LabInfoMask mask) {
    boolean retainAllFields = !mask.hasFieldMask();
    FieldMask fieldMask = mask.getFieldMask();
    Predicate<String> keeps =
        path -> retainAllFields || MaskUtils.isFieldRequested(fieldMask, path);

    this.keepsLabInfo = retainAllFields || fieldMask.getPathsCount() > 0;
    this.trimMask =
        keepsLabInfo && !retainAllFields && !NATIVE_PATHS.containsAll(fieldMask.getPathsList())
            ? fieldMask
            : null;
    this.keepsLabServerSetting = keeps.test(LAB_SERVER_SETTING_PATH);
    this.keepsLabServerFeature = keeps.test(LAB_SERVER_FEATURE_PATH);
    this.keepsLabStatus = keeps.test(LAB_STATUS_PATH);
    this.keptLocatorFields =
        LocatorField.ALL.stream().filter(field -> keeps.test(field.path)).collect(toImmutableSet());
    this.keepsOnlyHostName = keptLocatorFields.equals(ImmutableSet.of(LocatorField.HOST_NAME));
  }

  /**
   * Whether any {@link LabInfo} is produced at all. False only for a present-but-empty {@code
   * field_mask}; data sources should still count such labs in {@code lab_total_count}.
   */
  public boolean keepsLabInfo() {
    return keepsLabInfo;
  }

  /** Whether any part of {@code lab_locator} is kept and its getter will be evaluated. */
  public boolean keepsLabLocator() {
    return !keptLocatorFields.isEmpty();
  }

  /** Whether {@code lab_server_setting} is kept and its getter will be evaluated. */
  public boolean keepsLabServerSetting() {
    return keepsLabServerSetting;
  }

  /** Whether {@code lab_server_feature} is kept and its getter will be evaluated. */
  public boolean keepsLabServerFeature() {
    return keepsLabServerFeature;
  }

  /** Whether {@code lab_status} is kept and its getter will be evaluated. */
  public boolean keepsLabStatus() {
    return keepsLabStatus;
  }

  /** Projects {@code fullLocator} down to the kept fields; by reference when all are kept. */
  private LabLocator projectLabLocator(LabLocator fullLocator) {
    if (keptLocatorFields.equals(LocatorField.ALL)) {
      return fullLocator;
    }
    LabLocator.Builder projected = LabLocator.newBuilder();
    for (LocatorField field : keptLocatorFields) {
      field.copy(fullLocator, projected);
    }
    return projected.build();
  }

  /** Creates a single-use builder for one {@link LabInfo} guided by this mask. */
  public MaskedLabInfoBuilder newMaskedLabInfoBuilder() {
    return new MaskedLabInfoBuilder(this);
  }

  /**
   * Applies this mask to an already built {@link LabInfo}. Data sources that can avoid building the
   * unrequested parts should use {@link #newMaskedLabInfoBuilder()} instead; this is the fallback
   * for sources that only produce full messages.
   */
  public Optional<LabInfo> project(LabInfo labInfo) {
    if (!keepsLabInfo) {
      return Optional.empty();
    }
    if (this == RETAIN_ALL) {
      return Optional.of(labInfo);
    }
    MaskedLabInfoBuilder builder = newMaskedLabInfoBuilder();
    if (labInfo.hasLabLocator()) {
      builder.setMaskedLabLocator(
          labInfo, full -> full.getLabLocator().getHostName(), LabInfo::getLabLocator);
    }
    if (labInfo.hasLabServerSetting()) {
      builder.setMaskedLabServerSetting(labInfo, full -> Optional.of(full.getLabServerSetting()));
    }
    if (labInfo.hasLabServerFeature()) {
      builder.setMaskedLabServerFeature(labInfo, full -> Optional.of(full.getLabServerFeature()));
    }
    builder.setMaskedLabStatus(labInfo, LabInfo::getLabStatus);
    return builder.build();
  }

  /**
   * Builds one {@link LabInfo} under a {@link CompiledLabInfoMask}.
   *
   * <p>Each {@code setMasked*} method takes a source object and a getter rather than a value, and
   * calls the getter only when the mask keeps that field. Put the expensive work inside the getter.
   * Nothing the mask does not keep is ever set, so {@link #build()} copies nothing unless the mask
   * reaches below the fields this class gates.
   *
   * <p>A builder is single-use and not thread-safe.
   */
  public static final class MaskedLabInfoBuilder {

    private final CompiledLabInfoMask mask;
    private final LabInfo.Builder protoBuilder = LabInfo.newBuilder();

    private MaskedLabInfoBuilder(CompiledLabInfoMask mask) {
      this.mask = mask;
    }

    /**
     * Sets {@code lab_locator} when the mask keeps any part of it. When only {@code
     * lab_locator.host_name} is kept, which is what ordering labs needs, only {@code
     * hostNameGetter} is called and the locator is built from the host name alone; otherwise {@code
     * locatorGetter} is called and its result projected to the kept fields.
     */
    @CanIgnoreReturnValue
    public <S> MaskedLabInfoBuilder setMaskedLabLocator(
        S source, Function<S, String> hostNameGetter, Function<S, LabLocator> locatorGetter) {
      if (mask.keepsOnlyHostName) {
        protoBuilder.setLabLocator(
            LabLocator.newBuilder().setHostName(hostNameGetter.apply(source)));
      } else if (mask.keepsLabLocator()) {
        protoBuilder.setLabLocator(mask.projectLabLocator(locatorGetter.apply(source)));
      }
      return this;
    }

    /** Sets {@code lab_server_setting} from {@code getter} when the mask keeps it. */
    @CanIgnoreReturnValue
    public <S> MaskedLabInfoBuilder setMaskedLabServerSetting(
        S source, Function<S, Optional<LabServerSetting>> getter) {
      if (mask.keepsLabServerSetting) {
        getter.apply(source).ifPresent(protoBuilder::setLabServerSetting);
      }
      return this;
    }

    /** Sets {@code lab_server_feature} from {@code getter} when the mask keeps it. */
    @CanIgnoreReturnValue
    public <S> MaskedLabInfoBuilder setMaskedLabServerFeature(
        S source, Function<S, Optional<LabServerFeature>> getter) {
      if (mask.keepsLabServerFeature) {
        getter.apply(source).ifPresent(protoBuilder::setLabServerFeature);
      }
      return this;
    }

    /** Sets {@code lab_status} from {@code getter} when the mask keeps it. */
    @CanIgnoreReturnValue
    public <S> MaskedLabInfoBuilder setMaskedLabStatus(S source, Function<S, LabStatus> getter) {
      if (mask.keepsLabStatus) {
        protoBuilder.setLabStatus(getter.apply(source));
      }
      return this;
    }

    /**
     * Returns the projected {@link LabInfo}, or empty when the mask drops {@link LabInfo} entirely.
     */
    public Optional<LabInfo> build() {
      if (!mask.keepsLabInfo) {
        return Optional.empty();
      }
      LabInfo result = protoBuilder.build();
      return Optional.of(
          mask.trimMask == null ? result : FieldMaskUtil.trim(mask.trimMask, result));
    }
  }
}
