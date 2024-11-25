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

load("//third_party/bazel_rules/rules_java/java:java_library.bzl", "java_library")

package(default_applicable_licenses = ["//:license"])

java_binary(
    name = "device_admin_cli",
    srcs = ["DeviceAdminCli.java"],
    main_class = "com.google.devtools.mobileharness.platform.android.deviceadmin.cli.DeviceAdminCli",
    deps = [
        ":device_admin_cli_module",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)

java_library(
    name = "device_admin_cli_module",
    srcs = ["DeviceAdminCliModule.java"],
    deps = [
        "//third_party/java/cloud_kms",
        "//third_party/java/gax",
        "//third_party/java/tink:integration/gcpkms",
        "//third_party/java/tink:signature",
        "@maven//:com_google_auth_google_auth_library_oauth2_http",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)
