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
#   JobConfig related utilities.

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//java/com/google/devtools/mobileharness:__subpackages__",
        "//java/com/google/wireless/qa/mobileharness:__subpackages__",
        "//javatests/com/google/devtools/mobileharness:__subpackages__",
        "//javatests/com/google/wireless/qa/mobileharness:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/sessionplugin:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/ats/server/sessionplugin:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/client/rbe:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness:__subpackages__",
    ],
)

java_library(
    name = "job_info_creator",
    srcs = [
        "JobConfigHelper.java",
        "JobInfoCreator.java",
        "JobSettingsCreator.java",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/devtools/mobileharness/infra/client/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/base",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "//src/java/com/google/devtools/mobileharness/shared/util/sharedpool:shared_pool_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/system",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:exit_code",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:property",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/in/spec",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:common_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_config_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto/spec:job_spec_java_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/util:flag_parser",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_code_gson_gson",
        "@maven//:com_google_guava_guava",
    ],
)
