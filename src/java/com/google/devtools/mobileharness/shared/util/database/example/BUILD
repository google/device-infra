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
    default_testonly = 1,
)

# A binary to print the row count of a table in a MySQL database.
#
# Manual: bazel run src/java/com/google/devtools/mobileharness/shared/util/database/example:table_printer -- '<jdbc_url>' '<table_name>'
#
# Example: bazel run src/java/com/google/devtools/mobileharness/shared/util/database/example:table_printer -- 'jdbc:mysql:///testing_database?user=testing-user&password=testing-user' 'testing_table'
java_binary(
    name = "table_printer",
    srcs = ["TablePrinter.java"],
    main_class = "com.google.devtools.mobileharness.shared.util.database.example.TablePrinter",
    runtime_deps = [
        "@maven//:com_mysql_mysql_connector_j",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/database:database_connections",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
    ],
)
