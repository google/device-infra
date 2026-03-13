# Package Installer

## High-level requirements

-   Support installation of packages composed of various file types.
-   Provide high-level installation options.
-   Expose low-level installation options available in underlying tools (ADB and
    Bundletool).
-   Implement robust error handling:
    -   Retry failed installations after attempting to uninstall and clean the
        package to restore a good device state.
    -   Provide detailed error codes and messages.

## File types

The following file types are supported for making up a package:

-   APK
    -   One or multiple files.
    -   File extension: `.apk`
    -   Install command:
        -   `adb install path/to/app.apk`
        -   `adb install-multiple path/to/split1.apk path/to/split2.apk ...`
-   APK Set
    -   File extension: `.apks`
    -   Install command: `bundletool install-apks --apks=path/to/app.apks`
    -   Explicit choice to use `install-apks` over `extract-apks` and `adb
        install-multiple`, because Bundletool pushes all additional modules
        needed for local testing mode.

The following file types are only supported as additional files for the APK file
type case:

-   Dex Metadata
    -   One or multiple, additional files that will be added to the ADB install
        commands.
    -   File extensions: `.dm`, `.dma`.
    -   See https://source.android.com/docs/core/runtime/configure.

The following file types are currently NOT supported but should be considered
regarding future extension:

-   Android App Bundle
    -   File extension: `.aab`
    -   Install command: `bundletool install-apks --apks=path/to/apk_set.apks`
    -   Requires an APK Set as input, which should be generated using
        `bundletool get-device-spec`, for retrieving device-specific settings,
        and`bundletool build-apks` for generating the APK Set.
-   Android Pony EXpress
    -   File extension: `.apex`
-   OTA Update Packages
    -   Install command: `adb sideload path/to/update.zip`
    -   Requires the device to be in recovery mode.

The following file types are out of scope:

-   Opaque Binary Blob
    -   File extension: `.obb`
    -   Install command: `adb push main.12345.com.example.app.obb
        /sdcard/Android/obb/com.example.app/`
    -   An older way of working around file size limitations. Nowadays Play
        Store supports
        [Play Asset Delivery](https://developer.android.com/guide/playcore/asset-delivery).
        Installation can be supported through file pusher feature, and does not
        need to be handled as part of installation.

## Low-Level options

Depending on the file type, different installation options are supported by the
underlying tooling. The installer must provide a unified interface for these
options. If a requested option is incompatible with the file type, it must be
ignored and a warning logged.

-   Replace existing application
    -   `adb install -r`
    -   `adb install-multiple -r`
-   Allow test packages
    -   `adb install -t`
    -   `adb install-multiple -t`
    -   `java -jar bundletool.jar install-apks --allow-test-only`
-   Grant runtime permissions
    -   `adb install -g`
    -   `adb install-multiple -g`
    -   `java -jar bundletool.jar install-apks --grant-runtime-permissions`
    -   Available since API 23
-   Allow version code downgrade (debuggable packages only)
    -   `adb install -d`
    -   `adb install-multiple -d`
    -   `java -jar bundletool.jar install-apks --allow-downgrade`
-   Install instant app
    -   `adb install --instant`
    -   `adb install-multiple --instant`
    -   `java -jar bundletool.jar install-apks --instant`
    -   Available since API 26
-   Install for specific user
    -   `adb install --user <user_id>`
    -   `adb install-multiple --user <user_id>`
-   Force queryable (for test APKs)
    -   `adb install --force-queryable`
    -   `adb install-multiple --force-queryable`
-   Bypass low target sdk block
    -   `adb install --bypass-low-target-sdk-block`
    -   `adb install-multiple --bypass-low-target-sdk-block`
-   Force no streaming
    -   `adb install --no-streaming`
    -   `adb install-multiple --no-streaming`
    -   Available since API 27

## High-level options

Options that the installer should provide, regardless of file type. All of these
options must be explicitly opt-in (defaulting to false or inactive) by the
caller. These options may need additional functionality in the installer to
happen before, during, or after the installation.

-   Skip on version downgrade: If the app is already installed and its version
    code is greater than or equal to the incoming package's version code, skip
    the installation.
-   Skip if version match: If the app is already installed and its version code
    exactly matches the incoming package's version code, skip the installation.
-   Skip if cached: If the app is already installed using the exact same binary
    file(s), skip the installation. The installer must proactively verify the
    app is actually present on the device before trusting the cached MD5 system
    property.
-   Uninstall if installed: If the app is already installed, uninstall it before
    installing the new package. This ensures a completely clean state, resetting
    all data, permissions, keystore entries, and app ops. If this option is
    selected, the installation must not be skipped.
-   Clear data of package after install: Clear the package's data using `pm
    clear` after a successful install. This must also execute even if the
    installation itself was skipped (e.g., due to version match or caching).
-   Additional shell commands: Attach optional shell commands to a package to be
    executed on the device via `adb shell` after a successful installation.
    Users must be able to configure whether a failure in these commands causes
    the overall installation to fail.
-   Multiple packages: Callers can provide multiple packages for installation.
    These must be installed sequentially. If any package fails to install,
    subsequent installations are aborted, and the error is returned.
-   Timeout: `adb` and `bundletool` commands must support a user-configurable
    timeout, defaulting to 5 minutes per package. A timeout should be handled
    like any other infrastructure error, meaning the standard retry loop logic
    may attempt recovery before failing.

For some options the package's name and version is required. They should be
retrieved from the package file prior to installation. If the version of the
installed package is required, you can retrieve this from the device.

## Lifecycle

For each package, the installer follows this order of operations:

1.  Metadata extraction: Parse package name and version code from the package.
2.  Device check: If necessary, retrieve current installed version code from the
    device.
3.  Evaluate skipping installation: If requested by the user, check version
    match, downgrade, or cache options to determine if the "Tool install" step
    can be skipped. If skipped, proceed directly to post-install steps.
4.  Tool install: Use `adb` or `bundletool` to install the package.
5.  Error recovery: If the installation fails on the first attempt, attempt
    recovery based on the sub-steps below. On the second attempt, immediately
    pass the error back to the caller.
    1.  Known user errors: Throw known errors that are related to input files
        immediately back to the user.
    2.  Uninstall the package.
    3.  Go back to the step "Tool install".
6.  Clear data: Clear the package data, if requested by the user.
7.  Post install commands - Execute package-specific shell commands.

## Error handling

ADB and Bundletool both provide error information via stdout/stderr and the exit
code calling the respective command. The installer needs to parse stdout/stderr
to detect specific error conditions.

The following error codes indicate non-recoverable user/input errors and should
be thrown back to the caller immediately. They can be detected in the output of
`adb` or `bundletool` via substring matching (evaluate from top to bottom). For
each error code provided by the tools, a separate error in `AndroidErrorId`
should be created.

-   INSTALL_FAILED_CPU_ABI_INCOMPATIBLE
-   INSTALL_FAILED_DEPRECATED_SDK_VERSION
-   INSTALL_FAILED_DEXOPT
-   INSTALL_FAILED_DUPLICATE_PERMISSION
-   INSTALL_FAILED_INVALID_APK: Split null was defined multiple times
-   INSTALL_FAILED_INVALID_APK
-   INSTALL_FAILED_MISSING_FEATURE
-   INSTALL_FAILED_MISSING_SHARED_LIBRARY
-   INSTALL_FAILED_MISSING_SPLIT
-   INSTALL_FAILED_NEWER_SDK
-   INSTALL_FAILED_NO_MATCHING_ABIS
-   INSTALL_FAILED_OLDER_SDK
-   INSTALL_FAILED_PERMISSION_MODEL_DOWNGRADE
-   INSTALL_FAILED_SHARED_USER_INCOMPATIBLE
-   INSTALL_FAILED_TEST_ONLY
-   INSTALL_FAILED_VERIFICATION_FAILURE
-   INSTALL_PARSE_FAILED_BAD_MANIFEST
-   INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME
-   INSTALL_PARSE_FAILED_BAD_SHARED_USER_ID
-   INSTALL_PARSE_FAILED_CERTIFICATE_ENCODING
-   INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES
-   INSTALL_PARSE_FAILED_MANIFEST_EMPTY
-   INSTALL_PARSE_FAILED_MANIFEST_MALFORMED
-   INSTALL_PARSE_FAILED_NO_CERTIFICATES
-   INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION

All other errors codes should be retried once after the app is uninstalled.

If the uninstall fails, throw the previous error back to the user, including the
details from the uninstall.

If the retry install fails, these additional errors should be thrown back to the
user:

-   INSTALL_FAILED_UPDATE_INCOMPATIBLE
-   INSTALL_FAILED_VERSION_DOWNGRADE

All other errors are infrastructure errors, a generic error with error type
INFRA_ISSUE should be used.

Data loss (e.g. uninstalled apps) is acceptable. The devices are specifically
for testing and can be left in any state.

## Implementation details

### Architecture & API Surface

-   All new classes created should be located in this folder. The existing
    `ApkInstaller` will be the main component supporting the new installation
    methods. Updating existing component in other folders is allowed.
-   The installer component must expose the following functions to the user.
    Note that `deviceId` is always the first parameter:
    -   `void install(String deviceId, Installable installable)` - Installs one
        package.
    -   `void install(String deviceId, ImmutableList<Installable>
        installables)` - Installs a list of packages in the given order.
-   Create a record `Installable` as the container for all files and options for
    an app that should be installed.
-   Create different records for the different file types. Use a sealed
    interface for abstraction.

### Dynamic Metadata Extraction

-   Package names and version codes must not be provided by the caller. Instead,
    they must be dynamically retrieved from the installation files or device:
    -   The version code of an installed package can be parsed from the output
        of `dumpsys package <package-name>`, looking for the pattern
        `versionCode=(\\d+)` (preferred) or `Version: (\\d+)` (fallback). The
        first match should be used.
    -   The package name and version code of an APK file can be retrieved using
        [com.google.wireless.qa.mobileharness.shared.android.Aapt](google3/third_party/deviceinfra/src/java/com/google/wireless/qa/mobileharness/shared/android/Aapt.java).
    -   The package name and version code of an APK Set can be retrieved by
        reading one of the included APK files, preferably one of the
        `splits/base-master*.apk` files.
    -   There is currently no known way of retrieving the package name and
        version code of an AAB. The respective APK Set has to be generated first
        and used for retrieving the necessary information.

### Tooling & Options

-   Consider creating one or more containers for options.
-   Use
    [com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb](google3/third_party/deviceinfra/src/java/com/google/devtools/deviceinfra/platform/android/lightning/internal/sdk/adb/Adb.java)
    for ADB operations.
-   Use
    [com.google.devtools.mobileharness.platform.android.lightning.bundletool.Bundletool](google3/third_party/deviceinfra/src/java/com/google/devtools/mobileharness/platform/android/lightning/bundletool/Bundletool.java)
    for Bundletool operations.
-   "Skip if version match" is maintained for backwards compatibility. It
    carries risk when installing iterated builds (like infra apps such as
    `WifiUtil` or `MockLocation`), where engineers push code updates without
    incrementing the `versionCode` in the manifest. If a backwards-incompatible
    change is made but the version code stays the same, the installer will skip
    the update, leading to test failures against stale binaries. "Skip if
    cached" is a safer alternative for these scenarios because it verifies the
    physical binary payload.
-   "Skip if cached" is based on the MD5 checksum on the input file. For an
    input file the checksum can be calculated using `String md5 =
    ChecksumUtil.fingerprint(String filePath)`. For an APK Set, the checksum is
    calculated on the entire `.apks` file. The checksum of the installed package
    should be stored as an Android system property (setprop/getprop) in the
    format `installed_apk:user_<user-id>:<package-name>`.
    -   Android 8.0 and below have a 31-character limit on system property
        names. Keys exceeding this prefix may truncate, but this limitation will
        be ignored for backwards-compatibility reasons.
    -   **Future Work (Cache Collision):** The current caching property only
        checks the MD5 of the binary. This is required to maintain system
        backwards compatibility. However, this creates an edge case where
        state-altering options (like `-g` for runtime permissions) change across
        sequential runs but the APK remains the same, leading to a
        false-positive cache hit. Future implementations should update the
        *value* of the property to be a composite string
        `<md5-hash>:<options-hash>` to ensure installations are only skipped if
        both the binary and the requested options are exactly identical.

### Error Handling & Observability

-   Expose errors to users by defining new error IDs in
    [com.google.devtools.mobileharness.api.model.error.AndroidErrorId](google3/third_party/deviceinfra/src/java/com/google/devtools/mobileharness/api/model/error/AndroidErrorId.java)
    and using `MobileHarnessException` as the exception class. The file is
    large. Add new errors at the bottom, before the 200_000 error placeholder.
-   Logging should occur at important steps in the installation logic.

## Out of scope

The following items are out of scope for this specification, but will happen as
a separate step:

-   Migrating usage away from `ApkInstallArgs`.
-   Renaming of `ApkInstaller` to the more matching `PackageInstaller` name.
-   Special case handling of GmsCore.
-   Clearer boundaries between `ApkInstaller` and `AndroidPackageManagerUtil`,
    which currently share/duplicate responsibilities around retries.
