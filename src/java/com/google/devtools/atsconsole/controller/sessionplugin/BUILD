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
    name = "ats_session_plugin",
    srcs = ["AtsSessionPlugin.java"],
    visibility = [
        "//src/java/com/google/devtools/atsconsole/controller:__subpackages__",
    ],
    deps = [
        "//src/devtools/atsconsole/controller/proto:session_plugin_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/java/com/google/devtools/deviceinfra/shared/util/logging:google_logger",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_event",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/model:session_info",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf_protobuf_java",
        "@maven//:com_google_guava_guava",
        "@maven//:javax_inject_jsr330_api",
    ],
)
