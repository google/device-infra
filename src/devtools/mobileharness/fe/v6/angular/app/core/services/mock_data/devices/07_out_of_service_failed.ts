/**
 * @fileoverview Mock data for the "Out of Service, FAILED (Critical)" device
 * scenario. This represents a device that failed to prepare for a task and
 * could not be automatically recovered.
 */

import {DeviceError} from '../../../models/common_models';
import {DeviceConfig} from '../../../models/device_config_models';
import {DeviceOverview} from '../../../models/device_overview';
import {MockDeviceScenario} from '../models';

const DEVICE_ID = 'ANDROID-FAILED-DEVICE';

const MOCK_STACK_TRACE_1 = `DeviceException: Failed to get preparation config for device 0C251FDD4001C0, fingerprint: android_device_fingerprint   {
  ro_product_device: "redfin"
  ro_build_version_sdk: "31"
  ro_build_type: "user"
  ro_build_id: "SQ1A.220205.002"
  ro_product_name: "redfin"
}
 [MH|INFRA_ISSUE|DEVICE_INFRA_UNKNOWN_FAILURE|53901]
\tat com.google.devtools.omnilab.device.lab.server.extensions.android.AndroidRealDeviceLifecycle.fetchConfig(AndroidRealDeviceLifecycle.kt:409)
\tat com.google.devtools.omnilab.device.lab.server.extensions.android.AndroidRealDeviceLifecycle.bindConfig(AndroidRealDeviceLifecycle.kt:362)
Caused by: MobileHarnessException: Failed to get device configs for tenant: omnilab-android, pool: , fingerprint: android_device_fingerprint     {
  ro_product_device: "redfin"
  ro_build_version_sdk: "31"
  ro_build_type: "user"
  ro_build_id: "SQ1A.220205.002"
  ro_product_name: "redfin"
}
 [MH|INFRA_ISSUE|LAB_GET_CONFIG_FROM_CONFIG_SERVER_ERROR|40467]
\tat com.google.devtools.omnilab.device.lab.server.framework.device.config.TenantDeviceConfigManager.getRemoteResponse(TenantDeviceConfigManager.java:172)
Caused by: com.google.devtools.common.metrics.stability.rpc.stubby.StubbyExceptionWithErrorId: Failed to fetch tenant config for tenant omnilab-android [rpc_code=4]
\tat com.google.devtools.common.metrics.stability.rpc.stubby.StubbyExceptionUtil.toStubbyExceptionWithErrorId(StubbyExceptionUtil.java:104)
\tSuppressed: com.google.net.rpc3.client.RpcClientException: DEADLINE_EXCEEDED;mobileharness.api.deviceconfig/DeviceConfigService.GetTenantConfig;Server deadline (9.956377289956436s) expired in Apps Framework.`;

const MOCK_STACK_TRACE_2 = `com.google.io.file.DeadlineExceededException: /bigstore/omnilab-device-info/key-value-store/2A241FDH200AXF not opened; WRITE EncryptionOptions: null: generic::DEADLINE_EXCEEDED: estubs returned status: generic::deadline_exceeded: StubWithExponentialBackoff would delay this request until 2026-09-18T07:51:55.13815827925-07:00
\tat com.google.io.file.ErrorStatusIOException.throwFromStatus(ErrorStatusIOException.java:70)
\tat com.google.io.file.jni.impl.FileImpl.<init>(FileImpl.java:135)
\tat com.google.io.file.jni.GoogleOutputStream.<init>(GoogleOutputStream.java:58)
\tSuppressed: :  []
Caused by: :  []`;

const MOCK_STACK_TRACE_3 = `DeviceException: Cannot restore device in state OFFLINE [MH|INFRA_ISSUE|DEVICE_INFRA_UNKNOWN_FAILURE|53901]
\tat com.google.devtools.omnilab.device.lab.server.extensions.android.AndroidRealDeviceLifecycle.checkInRecoverableState(AndroidRealDeviceLifecycle.kt:496)
\tat com.google.devtools.omnilab.device.lab.server.extensions.android.AndroidRealDeviceLifecycle.beginPrepare(AndroidRealDeviceLifecycle.kt:247)
\tat com.google.devtools.omnilab.device.lab.server.extensions.android.AndroidRealDeviceLifecycle.prepareInternal$java_com_google_devtools_omnilab_device_lab_server_extensions_android_android(AndroidRealDeviceLifecycle.kt:208)`;

/** Mock device errors for testing. */
export const MOCK_DEVICE_ERRORS: DeviceError[] = [
  {
    timestampMs: new Date('2026-09-10T20:11:00Z').getTime(),
    error: {
      summary: {
        errorId: {
          code: 53901,
          name: 'DEVICE_INFRA_UNKNOWN_FAILURE',
          type: 'INFRA_ISSUE',
          namespace: 'MH',
        },
        message: 'Failed to get preparation config for device 0C251FDD4001C0',
        classType: {className: 'DeviceException'},
        stackTrace: {element: []},
      },
      cause: [
        {
          errorId: {
            code: 40467,
            name: 'LAB_GET_CONFIG_FROM_CONFIG_SERVER_ERROR',
            type: 'INFRA_ISSUE',
            namespace: 'MH',
          },
          message: 'Failed to get device configs for tenant: omnilab-android',
          classType: {className: 'MobileHarnessException'},
          stackTrace: {element: []},
        },
        {
          errorId: {
            code: -1,
            name: 'UNKNOWN_ERROR_ID',
            type: 'UNCLASSIFIED',
            namespace: 'UNKNOWN',
          },
          message:
            'Failed to fetch tenant config for tenant omnilab-android [rpc_code=4]',
          classType: {className: 'StubbyExceptionWithErrorId'},
          stackTrace: {element: []},
        },
      ],
      criticalErrorId: {
        code: 53901,
        name: 'DEVICE_INFRA_UNKNOWN_FAILURE',
        type: 'INFRA_ISSUE',
        namespace: 'MH',
      },
      userFacingCriticalErrorId: {
        code: 53901,
        name: 'DEVICE_INFRA_UNKNOWN_FAILURE',
        type: 'INFRA_ISSUE',
        namespace: 'MH',
      },
      errorNameStackTrace:
        'DEVICE_INFRA_UNKNOWN_FAILURE|LAB_GET_CONFIG_FROM_CONFIG_SERVER_ERROR|UNKNOWN_ERROR_ID',
      errorCodeStackTrace: '53901|40467|-1',
      completeStackTrace: MOCK_STACK_TRACE_1,
    },
  },
  {
    timestampMs: new Date('2026-09-18T22:51:54Z').getTime(),
    error: {
      summary: {
        errorId: {
          code: 0,
          name: 'UNKNOWN',
          type: 'UNCLASSIFIED',
          namespace: '',
        },
        message: 'Bigstore file not opened; WRITE generic::DEADLINE_EXCEEDED',
        classType: {className: 'DeadlineExceededException'},
        stackTrace: {element: []},
      },
      completeStackTrace: MOCK_STACK_TRACE_2,
    },
  },
  {
    timestampMs: new Date('2026-09-20T06:07:53Z').getTime(),
    error: {
      summary: {
        errorId: {
          code: 40493,
          name: 'LAB_EXTERNAL_DEVICE_MANAGER_RESERVE_RPC_ERROR',
          type: 'DEPENDENCY_ISSUE',
          namespace: 'MH',
        },
        message: 'Cannot restore device in state OFFLINE',
        classType: {className: 'DeviceException'},
        stackTrace: {element: []},
      },
      criticalErrorId: {
        code: 40493,
        name: 'LAB_EXTERNAL_DEVICE_MANAGER_RESERVE_RPC_ERROR',
        type: 'DEPENDENCY_ISSUE',
        namespace: 'MH',
      },
      userFacingCriticalErrorId: {
        code: 53901,
        name: 'DEVICE_INFRA_UNKNOWN_FAILURE',
        type: 'INFRA_ISSUE',
        namespace: 'MH',
      },
      errorNameStackTrace:
        'LAB_EXTERNAL_DEVICE_MANAGER_RESERVE_RPC_ERROR|DEVICE_INFRA_UNKNOWN_FAILURE|UNKNOWN_ERROR_ID',
      errorCodeStackTrace: '40493|53901|-1',
      completeStackTrace: MOCK_STACK_TRACE_3,
    },
  },
];

const OVERVIEW: DeviceOverview = {
  id: DEVICE_ID,
  host: {name: 'host-e-5.prod.example.com', ip: '192.168.5.105'},
  healthAndActivity: {
    title: 'Out of Service (Needs Fixing)',
    subtitle: 'The device is in an error state and requires attention.',
    state: 'OUT_OF_SERVICE_NEEDS_FIXING',
    deviceStatus: {status: 'FAILED', isCritical: true},
    deviceTypes: [{type: 'AndroidDevice', isAbnormal: false}],
    lastInServiceTime: '2025-07-24T12:30:00.000Z',
    diagnostics: {
      diagnosis: 'The device status is <strong>FAILED</strong>.',
      explanation:
        'This means it failed to prepare for a task and could not be automatically recovered.',
      suggestedAction:
        'Check device logs for errors that occurred during the preparation phase. The device may need to be re-imaged or manually recovered.',
    },
    hasDeviceError: true,
  },
  basicInfo: {
    model: 'Pixel 6',
    version: '13',
    form: 'physical',
    os: 'Android',
    batteryLevel: 15,
    network: {},
    hardware: 'g/21fba',
    build: 'SP1A.210812.015',
  },
  permissions: {owners: [], executors: []},
  capabilities: {supportedDrivers: [], supportedDecorators: []},
  dimensions: {supported: {}, required: {}},
  properties: {},
};

const CONFIG: DeviceConfig = {
  permissions: {owners: [], executors: []},
  wifi: {type: 'none', ssid: '', psk: '', scanSsid: false},
  dimensions: {
    supported: [],
    required: [],
  },
  settings: {maxConsecutiveFail: 5, maxConsecutiveTest: 10000},
};

/**
 * Represents a mock device scenario where the device is out of service and has
 * failed. This scenario is used for testing and development purposes to
 * simulate a device in a specific state.
 */
const SCENARIO_OUT_OF_SERVICE_FAILED_DATA: MockDeviceScenario = {
  id: DEVICE_ID,
  scenarioName: 'Out of Service - Failed',
  overview: OVERVIEW,
  config: CONFIG,
  isQuarantined: false,
  actionVisibility: {
    screenshot: true,
    logcat: true,
    flash: true,
    remoteControl: true,
    quarantine: true,
  },
  deviceErrors: MOCK_DEVICE_ERRORS,
};

/**
 * Returns the mock scenario for out of service failed.
 */
export function SCENARIO_OUT_OF_SERVICE_FAILED(
  callCount?: number,
): MockDeviceScenario {
  return SCENARIO_OUT_OF_SERVICE_FAILED_DATA;
}
