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

package(default_applicable_licenses = ["//:license"])

java_library(
    name = "parser",
    srcs = [
        "MoblyYamlParser.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/result:__subpackages__",
    ],
    deps = [
        ":mobly_doc_entry",
        "//src/devtools/mobileharness/infra/ats/console/result/proto:result_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
        "@maven//:org_yaml_snakeyaml",
    ],
)

java_library(
    name = "mobly_doc_entry",
    srcs = [
        "MoblySummaryEntry.java",
        "MoblyTestEntry.java",
        "MoblyYamlDocEntry.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/result:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/ats/console/result/proto:result_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)
