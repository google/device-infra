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

java_library(
    name = "test_execution_unit",
    srcs = [
        "JobExecutionUnit.java",
        "TestExecutionUnit.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/container/controller:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/container/sandbox:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job:__pkg__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job:locator",
        "//src/java/com/google/devtools/mobileharness/api/model/job/in",
        "//src/java/com/google/devtools/mobileharness/api/model/job/out:timing",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler/model/job",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/util:job_timer",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:count_down_timer",
    ],
)

java_library(
    name = "test_execution_result",
    srcs = [
        "TestExecutionResult.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/container:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
    ],
)
