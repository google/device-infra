# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Description:
#   Basic output data models of Mobile Harness jobs and tests.

package(
    default_visibility = [
        "//:deviceinfra_all_pkg",
        "//devtools/mobileharness/project:automagic",
        "//devtools/mobileharness/project:mobileiq",
        "//devtools/mobileharness/project:project_eco",
        "//devtools/mobileharness/project:stadia_engprod_leti",
        "//devtools/mobileharness/project:vinson",
        "//devtools/mobileharness/project:walkman",
        "//devtools/mobileharness/project:walled_gardener",
        "//java/com/google/devtools/mobileharness/api:__subpackages__",
        "//java/com/google/devtools/mobileharness/contrib:__subpackages__",
        "//java/com/google/devtools/mobileharness/infra:__subpackages__",
        "//java/com/google/devtools/mobileharness/platform/android:__subpackages__",
        "//java/com/google/devtools/mobileharness/platform/fuchsia:__subpackages__",
        "//java/com/google/devtools/mobileharness/platform/ios:__subpackages__",
        "//java/com/google/devtools/mobileharness/service/moss/util/slg:__subpackages__",
        "//java/com/google/wireless/qa/mobileharness/client:__subpackages__",
        "//java/com/google/wireless/qa/mobileharness/shared/acts:__subpackages__",
        "//java/com/google/wireless/qa/mobileharness/shared/api:__subpackages__",
        "//java/com/google/wireless/qa/mobileharness/shared/controller/event/util:__subpackages__",
        "//java/com/google/wireless/qa/mobileharness/shared/model:__subpackages__",
        "//java/com/google/wireless/qa/mobileharness/shared/sponge:__subpackages__",
        "//javatests/com/google/android/apps/camera/syshealth/cashv2:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/api:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/contrib:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/infra:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/integration/plugins:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/platform/android:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/platform/fuchsia:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/platform/ios:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/platform/maneki:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/platform/testbed/adhoc:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/service/moss/util/slg:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness/client:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness/shared/acts:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness/shared/api/decorator:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness/shared/api/device:__pkg__",
        "//javatests/com/google/wireless/qa/mobileharness/shared/api/driver:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness/shared/api/step/android:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness/shared/controller:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness/shared/model:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness/shared/sponge:__subpackages__",
    ],
)

java_library(
    name = "timing",
    srcs = ["Timing.java"],
    tags = [
        "alt_dep=//java/com/google/devtools/mobileharness/api/model/job/out:timing",
        "avoid_dep",
    ],
    deps = ["@maven//:com_google_guava_guava"],
)

java_library(
    name = "touchable_timing",
    srcs = ["TouchableTiming.java"],
    tags = [
        "alt_dep=//java/com/google/devtools/mobileharness/api/model/job/out:touchable_timing",
        "avoid_dep",
    ],
    deps = [
        ":timing",
        "//devtools/mobileharness/service/moss/proto:result_java_proto",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "out_factory",
    srcs = ["JobOutInternalFactory.java"],
    tags = [
        "alt_dep=//java/com/google/devtools/mobileharness/api/model/job/out:out_factory",
        "avoid_dep",
    ],
    visibility = [
        # Note: this tool is only for for MH infra, please don't add more visibilities.
        "//java/com/google/devtools/mobileharness/api/model/job/out:__pkg__",
        "//java/com/google/devtools/mobileharness/service/moss/util/slg:__pkg__",
        "//javatests/com/google/devtools/mobileharness/service/moss/util/slg:__pkg__",
        "//javatests/com/google/devtools/mobileharness/api/model/job:__subpackages__",
    ],
    deps = [
        ":out",
        ":touchable_timing",
        "//devtools/mobileharness/service/moss/proto:result_java_proto",
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/log",
    ],
)

java_library(
    name = "out",
    srcs = glob(
        ["*.java"],
        exclude = [
            "JobOutInternalFactory.java",
            "Timing.java",
            "TouchableTiming.java",
        ],
    ),
    tags = [
        "alt_dep=//java/com/google/devtools/mobileharness/api/model/job/out",
        "avoid_dep",
    ],
    exports = [
        ":timing",
        ":touchable_timing",
    ],
    deps = [
        ":touchable_timing",
        "//devtools/mobileharness/service/moss/proto:slg_java_proto",
        "//java/com/google/common/time",
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/mobileharness/api/model/proto:error_java_proto",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/converter",
        "//src/java/com/google/devtools/common/metrics/stability/model:error_id",
        "//src/java/com/google/devtools/common/metrics/stability/util:error_id_comparator",
        "//src/java/com/google/devtools/deviceinfra/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job/in",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:error_model_converter",
        "//src/java/com/google/wireless/qa/mobileharness/shared:exception",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/log",
        "//src/java/com/google/wireless/qa/mobileharness/shared/log:log_collector_backend",
        "//src/java/com/google/wireless/qa/mobileharness/shared/log:log_context",
        "//src/java/com/google/wireless/qa/mobileharness/shared/log:log_data",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)
