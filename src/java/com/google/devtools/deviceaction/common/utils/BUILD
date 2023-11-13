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
        "//src/java/com/google/devtools/deviceaction:deviceaction_java_pkg",
        "//src/javatests/com/google/devtools/deviceaction:deviceaction_javatests_pkg",
    ],
)

java_library(
    name = "utils",
    srcs = [
        "AaptUtil.java",
        "BundletoolUtil.java",
        "CommandHistoryWriter.java",
        "CompositeResolver.java",
        "Conditions.java",
        "Constants.java",
        "FlagBasedResourceHelper.java",
        "FlagParser.java",
        "GCSResolver.java",
        "GCSUtil.java",
        "HelpUtil.java",
        "LazyCached.java",
        "LocalFileResolver.java",
        "ProtoHelper.java",
        "Resolver.java",
        "ResourceHelper.java",
        "ResourceUtil.java",
        "SimpleResolver.java",
        "StaticResourceHelper.java",
        "TimeoutMonitor.java",
        "Verify.java",
    ],
    exports = [
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
    ],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_java_proto",
        "//src/devtools/deviceaction/schemas/proto:base_spec_java_proto",
        "//src/devtools/deviceaction/schemas/proto/actions:install_mainline_spec_java_proto",
        "//src/devtools/deviceaction/schemas/proto/actions:reset_spec_java_proto",
        "//src/java/com/google/devtools/deviceaction/common/annotations",
        "//src/java/com/google/devtools/deviceaction/common/error",
        "//src/java/com/google/devtools/deviceaction/common/schemas",
        "//src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/platform/android/packagemanager:info",
        "//src/java/com/google/devtools/mobileharness/shared/util/command",
        "//src/java/com/google/devtools/mobileharness/shared/util/command/history",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "//src/java/com/google/wireless/qa/mobileharness/shared/android:aapt",
        "@com_google_protobuf//:protobuf_java",
        "@maven//:com_google_api_client_google_api_client",
        "@maven//:com_google_apis_google_api_services_storage",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_http_client_google_http_client",
        "@maven//:com_google_http_client_google_http_client_gson",
        "@maven//:javax_inject_jsr330_api",
    ],
)

java_library(
    name = "module",
    srcs = ["ResourceModule.java"],
    deps = [
        ":utils",
        "//src/java/com/google/devtools/deviceaction/common/annotations",
        "//src/java/com/google/devtools/deviceaction/common/error",
        "@maven//:com_google_inject_guice",
        "@maven//:javax_inject_jsr330_api",
    ],
)
