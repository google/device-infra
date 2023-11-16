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
    name = "byte_array_list",
    srcs = ["ByteArrayList.java"],
)

java_library(
    name = "test_record_helper",
    srcs = ["TestRecordHelper.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/util/tradefed:__pkg__",
    ],
    deps = [
        ":test_record_proto_util",
        "//src/devtools/mobileharness/infra/ats/console/tradefed/proto:record_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf_protobuf_java",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "test_record_writer",
    srcs = ["TestRecordWriter.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/result/report:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/ats/console/result/report:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/ats/console/tradefed/proto:record_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "test_record_proto_util",
    srcs = ["TestRecordProtoUtil.java"],
    deps = [
        ":byte_array_list",
        "//src/devtools/mobileharness/infra/ats/console/tradefed/proto:record_java_proto",
        "@com_google_protobuf//:protobuf_java",
    ],
)
