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
    name = "test_flow_converter",
    srcs = ["TestFlowConverter.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:__pkg__",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/controller/test/local/utp/proto:basic_flow_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "no_op_test_flow_converter",
    srcs = ["NoOpTestFlowConverter.java"],
    visibility = [
        "//:deviceinfra_all_pkg",
        "//java/com/google/devtools/mobileharness/infra/rbeexecutor:__subpackages__",
    ],
    deps = [
        ":test_flow_converter",
        "//src/devtools/mobileharness/infra/controller/test/local/utp/proto:incompatible_reason_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/allocation",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local/utp/common:incompatible_reasons",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/local/utp/common:test_flows",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)
