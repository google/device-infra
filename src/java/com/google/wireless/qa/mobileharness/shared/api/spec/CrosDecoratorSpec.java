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
import java.time.Duration;

/** Constants used by ChromeOS (Cros) decorators. */
@SuppressWarnings("InterfaceWithOnlyStatics") // This interface is implemented by some decorators
public interface CrosDecoratorSpec {

  /** Base directory for CIPD packages used by Cros decorators. */
  String CIPD_DIR = "/usr/local/google/mobileharness/ate/cipd_packages/";

  /** The path to the dt-converter CIPD package within {@link #CIPD_DIR}. */
  String DT_CONVERTER_CIPD_PATH = CIPD_DIR + "dt-converter";

  /** The path to the lsnexus CIPD package within {@link #CIPD_DIR}. */
  String LSNEXUS_CIPD_PATH = CIPD_DIR + "lsnexus";

  /** The path to the foil-provision CIPD package within {@link #CIPD_DIR}. */
  String FOIL_PROVISION_CIPD_PATH = CIPD_DIR + "foil-provision";

  /**
   * CIPD package name for dt-converter.
   *
   * <p>Currently built and published for linux-amd64 in ChromeOS test infrastructure.
   */
  String DT_CONVERTER_PACKAGE = "chromiumos/infra/dt-converter/linux-amd64";

  /**
   * CIPD package name for lsnexus.
   *
   * <p>Currently built and published for linux-amd64 in ChromeOS test infrastructure.
   */
  String LSNEXUS_PACKAGE = "chromiumos/infra/cft/lsnexus/linux-amd64";

  /**
   * CIPD package name for foil-provision.
   *
   * <p>Currently built and published for linux-amd64 in ChromeOS test infrastructure.
   */
  String FOIL_PROVISION_PACKAGE = "chromiumos/infra/cft/provision/foil-provision/linux-amd64";

  /**
   * The default CIPD tag used to pull production packages at runtime when no custom tag is
   * specified.
   */
  String DEFAULT_CIPD_TAG = "prod";

  /** Parameter name for dt-converter CIPD package tag, version, or instance hash. */
  @ParamAnnotation(
      help =
          "CIPD tag, version, or instance hash to pull dt-converter dynamically at runtime."
              + " Defaults to 'prod' to pull the production package. If empty (\"\"), falls back"
              + " to the pre-installed container binary.",
      required = false)
  String DT_CONVERTER_CIPD_TAG = "dt_converter_cipd_tag";

  /** Parameter name for lsnexus CIPD package tag, version, or instance hash. */
  @ParamAnnotation(
      help =
          "CIPD tag, version, or instance hash to pull lsnexus dynamically at runtime."
              + " Defaults to 'prod' to pull the production package. If empty (\"\"), falls back"
              + " to the pre-installed container binary.",
      required = false)
  String LSNEXUS_CIPD_TAG = "lsnexus_cipd_tag";

  /** Parameter name for foil-provision CIPD package tag, version, or instance hash. */
  @ParamAnnotation(
      help =
          "CIPD tag, version, or instance hash to pull foil-provision dynamically at runtime."
              + " Defaults to 'prod' to pull the production package. If empty (\"\"), falls back"
              + " to the pre-installed container binary.",
      required = false)
  String FOIL_PROVISION_CIPD_TAG = "foil_provision_cipd_tag";

  /** Parameter name for target build ID to provision onto the device. */
  @ParamAnnotation(
      help =
          "Target build ID to provision onto the device. If not specified, resolved via"
              + " dt-converter stable-version.",
      required = false)
  String BUILD_ID = "build_id";

  /** Parameter name for target build target (e.g. brya-trunk_staging-userdebug). */
  @ParamAnnotation(
      help =
          "Target build target (e.g. brya-trunk_staging-userdebug) to provision. If not specified,"
              + " resolved via dt-converter stable-version.",
      required = false)
  String BUILD_TARGET = "build_target";

  /** Parameter name for indicating whether to use signed image for provisioning. */
  @ParamAnnotation(
      help = "Whether to use signed image for provisioning. Defaults to false.",
      required = false)
  String USE_SIGNED_IMAGE = "use_signed_image";

  /** Parameter name for indicating whether to use test ramdisk for provisioning. */
  @ParamAnnotation(
      help = "Whether to use test ramdisk for provisioning. Defaults to false.",
      required = false)
  String USE_TEST_RAMDISK = "use_test_ramdisk";

  /** Parameter name to skip resolving stable version from labservice. */
  @ParamAnnotation(
      help =
          "Whether to skip resolving stable version from labservice via dt-converter."
              + " Defaults to false.",
      required = false)
  String SKIP_STABLE_VERSION = "skip_stable_version";

  /** Default timeout for foil-provision command execution. */
  Duration DEFAULT_FOIL_PROVISION_TIMEOUT = Duration.ofMinutes(60);

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
