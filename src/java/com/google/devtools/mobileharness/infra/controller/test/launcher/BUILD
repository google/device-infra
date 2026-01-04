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

load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "launcher_provider",
    srcs = ["LauncherProvider.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:proxy_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:abstract_test_runner",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
    ],
)

java_library(
    name = "test_runner_thread_pool",
    srcs = ["TestRunnerThreadPool.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
    ],
    deps = ["@maven//:javax_inject_jsr330_api"],
)

java_library(
    name = "local_device_test_runner_launcher_provider",
    srcs = ["LocalDeviceTestRunnerLauncherProvider.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
    ],
    deps = [
        ":launcher_provider",
        ":local_device_test_runner_launcher",
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:proxy_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:test_executor",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:test_executor_provider",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:abstract_test_runner",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:sleeper",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "thread_pool_test_runner_launcher_provider",
    srcs = ["ThreadPoolTestRunnerLauncherProvider.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
    ],
    deps = [
        ":launcher_provider",
        ":test_runner_thread_pool",
        ":thread_pool_test_runner_launcher",
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:proxy_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:abstract_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/lab:annotations",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/device",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "local_device_test_runner_launcher",
    srcs = ["LocalDeviceTestRunnerLauncher.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/local:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/service:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice/controller:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:local_device_test_executor",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:test_executor",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:abstract_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:test_execution_result",
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable",
        "//src/java/com/google/devtools/mobileharness/shared/context:invocation_context",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:more_futures",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "thread_pool_test_runner_launcher",
    srcs = ["ThreadPoolTestRunnerLauncher.java"],
    visibility = [
        "//java/com/google/devtools/mobileharness/infra/container/testengine/controller:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:abstract_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/event",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/model:test_execution_result",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:callables",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:tag",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "direct_test_runner_launcher_factory",
    srcs = ["DirectTestRunnerLauncherFactory.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:abstract_test_runner",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:direct_test_runner",
    ],
)

java_library(
    name = "thread_pool_launcher_provisioning_module",
    srcs = ["ThreadPoolLauncherProvisioningModule.java"],
    visibility = [
        "//:omnilab_fusion",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__pkg__",
    ],
    deps = [
        ":launcher_provider",
        ":test_runner_thread_pool",
        ":thread_pool_test_runner_launcher_provider",
        "//src/java/com/google/devtools/mobileharness/shared/util/concurrent:thread_pools",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "local_device_launcher_provisioning_module",
    srcs = ["LocalDeviceLauncherProvisioningModule.java"],
    visibility = [
        "//:omnilab_fusion",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__pkg__",
    ],
    deps = [
        ":launcher_provider",
        ":local_device_test_runner_launcher_provider",
        "@maven//:com_google_inject_guice",
    ],
)
