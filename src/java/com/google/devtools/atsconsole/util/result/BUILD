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
    name = "result_lister",
    srcs = ["ResultLister.java"],
    visibility = ["//src/java/com/google/devtools/atsconsole/command:__pkg__"],
    deps = [
        "//src/devtools/atsconsole/result/proto:report_java_proto",
        "//src/java/com/google/devtools/atsconsole:console_info",
        "//src/java/com/google/devtools/atsconsole/result/report:compatibility_report_parser",
        "//src/java/com/google/devtools/atsconsole/result/xml:xml_constants",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/deviceinfra/shared/util/path",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/base:table_formatter",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)
