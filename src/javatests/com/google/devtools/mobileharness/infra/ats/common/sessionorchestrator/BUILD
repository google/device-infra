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

load("@rules_java//java:defs.bzl", "java_test")

package(default_applicable_licenses = ["//:license"])

java_test(
    name = "ats_session_orchestrator_test",
    size = "small",
    srcs = ["AtsSessionOrchestratorTest.java"],
    test_class = "com.google.devtools.mobileharness.infra.ats.common.sessionorchestrator.AtsSessionOrchestratorTest",
    deps = [
        "//src/devtools/mobileharness/infra/ats/common/proto:xts_common_java_proto",
        "//src/devtools/mobileharness/platform/android/xts/proto:test_message_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job/in",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/sessionorchestrator:session_orchestrator",
        "//src/java/com/google/devtools/mobileharness/infra/ats/common/sessionorchestrator:session_orchestrator_delegate",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_info",
        "//src/java/com/google/devtools/mobileharness/infra/controller/scheduler/model/job/in:device_requirements",
        "//src/java/com/google/devtools/mobileharness/platform/android/xts/constant:xts_constants",
        "//src/java/com/google/wireless/qa/mobileharness/client/api/event:job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/comm/message",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/javatests/com/google/devtools/mobileharness/builddefs:truth",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)
