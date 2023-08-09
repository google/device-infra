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

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:__subpackages__",
    ],
)

java_library(
    name = "base_module",
    srcs = [
        "TestRunBaseModule.java",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager:proxy_test_manager",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:net_util",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "test_run_module",
    srcs = ["TestRunModule.java"],
    deps = [
        ":annotations",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:local_device_runner_provider",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager:proxy_test_manager",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:job_manager",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:prepare_test",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:net_util",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "annotations",
    srcs = ["Annotations.java"],
    deps = [
        "@maven//:com_google_inject_guice",
    ],
)
