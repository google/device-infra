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

package com.google.wireless.qa.mobileharness.shared.api.spec;

import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;

/** Constants used by ChromeOS (Cros) decorators. */
@SuppressWarnings("InterfaceWithOnlyStatics") // This interface is implemented by some decorators
public interface CrosDecoratorSpec {

  /** Base directory for CIPD packages used by Cros decorators. */
  String CIPD_DIR = "/usr/local/google/mobileharness/ate/cipd_packages/";

  /** The path to the dt-converter CIPD package within {@link #CIPD_DIR}. */
  String DT_CONVERTER_CIPD_PATH = CIPD_DIR + "dt-converter";

  /** The path to the lsnexus CIPD package within {@link #CIPD_DIR}. */
  String LSNEXUS_CIPD_PATH = CIPD_DIR + "lsnexus";

  // Inventory service related constants.
  /** Parameter name for the hostname or IP address of the inventory service. */
  @ParamAnnotation(help = "Hostname or IP address of the inventory service.", required = false)
  String INVENTORY_SERVICE_HOST = "inventory_service_host";

  /** Parameter name for the port of the inventory service. */
  @ParamAnnotation(help = "Port of the inventory service.", required = false)
  String INVENTORY_SERVICE_PORT = "inventory_service_port";

  /** The default hostname for the inventory service. */
  String DEFAULT_INVENTORY_SERVICE_HOST = "localhost";

  /** The default port for the inventory service. */
  int DEFAULT_INVENTORY_SERVICE_PORT = 1485;

  // Test info properties related constants.
  /**
   * The suffix used for test info properties that store the address of an lsnexus instance. The
   * full property name is typically formed by prepending a device identifier.
   */
  String LSNEXUS_PARAM_SUFFIX = "lsnexus_addr_";
}
