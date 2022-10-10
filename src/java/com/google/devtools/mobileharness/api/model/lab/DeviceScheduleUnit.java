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

package com.google.devtools.mobileharness.api.model.lab;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.devtools.mobileharness.api.model.lab.in.CompositeDimensions;
import com.google.devtools.mobileharness.api.model.lab.in.Decorators;
import com.google.devtools.mobileharness.api.model.lab.in.Drivers;
import com.google.devtools.mobileharness.api.model.lab.in.LiteDimensionsFactory;
import com.google.devtools.mobileharness.api.model.lab.in.Owners;
import com.google.devtools.mobileharness.api.model.lab.in.Types;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Schedule unit of a Mobile Harness device. It contains the device information needed for the
 * scheduler.
 */
@Beta
public class DeviceScheduleUnit implements Cloneable {
  /** Device locator. */
  private final DeviceLocator locator;

  /** Supported device types. */
  private final Types types;

  /** Supported drivers. */
  private final Drivers drivers;

  /** Supported decorators. */
  private final Decorators decorators;

  /** Device owners. */
  private final Owners owners;

  /** Device dimension. */
  private final CompositeDimensions dimensions;

  /**
   * Creates a schedule unit of a Mobile Harness device.
   *
   * @param locator device location
   */
  public DeviceScheduleUnit(DeviceLocator locator) {
    this.locator = Preconditions.checkNotNull(locator);
    this.types = new Types();
    this.drivers = new Drivers();
    this.decorators = new Decorators();
    this.dimensions = LiteDimensionsFactory.createCompositeDimensions();
    this.owners = new Owners();
  }

  protected DeviceScheduleUnit(DeviceScheduleUnit other) {
    this(other.locator);
    this.types.setAll(other.types.getAll());
    this.drivers.setAll(other.drivers.getAll());
    this.decorators.setAll(other.decorators.getAll());
    this.owners.setAll(other.owners.getAll());
    this.dimensions.supported().addAll(other.dimensions.supported().getAll());
    this.dimensions.required().addAll(other.dimensions.required().getAll());
  }

  @Override
  public Object clone() {
    return new DeviceScheduleUnit(this);
  }

  /** Returns the device locator. */
  public DeviceLocator locator() {
    return locator;
  }

  /** Gets the supported device types. */
  public Types types() {
    return types;
  }

  /** Gets the supported drivers. */
  public Drivers drivers() {
    return drivers;
  }

  /** Gets the supported decorators. */
  public Decorators decorators() {
    return decorators;
  }

  /** Gets the device owners. */
  public Owners owners() {
    return owners;
  }

  /** Gets the device dimensions. */
  public CompositeDimensions dimensions() {
    return dimensions;
  }

  /** Converts to {@link DeviceFeature} proto. */
  public DeviceFeature toFeature() {
    return DeviceFeature.newBuilder()
        .addAllOwner(owners.getAll())
        .addAllType(types.getAll())
        .addAllDriver(drivers.getAll())
        .addAllDecorator(decorators.getAll())
        .setCompositeDimension(dimensions.toProto())
        .build();
  }

  /** Copies all owners/types/drivers/decorators/dimensions from {@link DeviceFeature} proto. */
  @CanIgnoreReturnValue
  public DeviceScheduleUnit addFeature(DeviceFeature proto) {
    owners.addAll(proto.getOwnerList());
    types.addAll(proto.getTypeList());
    drivers.addAll(proto.getDriverList());
    decorators.addAll(proto.getDecoratorList());
    dimensions.addAll(proto.getCompositeDimension());
    return this;
  }
}
