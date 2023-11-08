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
)

java_library(
    name = "lab_file_notifier",
    srcs = ["LabFileNotifier.java"],
    visibility = [
        "//java/com/google/devtools/mobileharness/infra/rbeexecutor:__subpackages__",
        "//javatests/com/google/devtools/mobileharness/infra/rbeexecutor:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/container:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/controller/test/manager:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/lab/proto:file_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/job/util:add_file_handler",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/allocation",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:job_spec_java_proto",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "lab_file_notifier_factory",
    srcs = ["LabFileNotifierFactory.java"],
    visibility = ["//src/java/com/google/devtools/mobileharness/infra/lab/controller:__pkg__"],
    deps = [
        ":lab_file_notifier",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
    ],
)
