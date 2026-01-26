/**
 * Information about the host machine.
 */
export declare interface HostInfo {
  /** The hostname of the lab server. */
  name: string;
  /** The IP address of the lab server. */
  ip: string;
  // Future additions could include a link to the host's detail page.
}

/**
 * State of an action button in device header.
 */
export declare interface ActionButtonState {
  enabled: boolean;
  visible: boolean;
  tooltip: string;
}

/**
 * Parameters for flash action button.
 */
export declare interface FlashButtonParams {
  deviceType: string;
  requiredDimensions: string;
}

/**
 * State of flash action button.
 */
export declare interface FlashActionButtonState extends ActionButtonState {
  params: FlashButtonParams;
}

/**
 * Quarantine information of device.
 */
export declare interface QuarantineInfo {
  isQuarantined: boolean;
  // ISO 8601 date string(e.g. "1972-01-01T10:00:20.021Z"), or '' if not quarantined
  expiry: string;
}

/**
 * Actions for device header.
 */
export declare interface DeviceActions {
  screenshot: ActionButtonState;
  logcat: ActionButtonState;
  flash: FlashActionButtonState;
  remoteControl: ActionButtonState;
  quarantine: ActionButtonState;
  configuration: ActionButtonState;
}

/**
 * Information for device header.
 */
export declare interface DeviceHeaderInfo {
  id: string;
  host?: HostInfo;
  quarantine?: QuarantineInfo;
  actions?: DeviceActions;
}

/**
 * Response for TakeScreenshot API.
 */
export declare interface TakeScreenshotResponse {
  screenshotUrl: string;
  capturedAt: string;
}

/**
 * Response for GetLogcat API.
 */
export declare interface GetLogcatResponse {
  logUrl: string;
  capturedAt: string;
}

/**
 * Flash options for remote control.
 */
export declare interface FlashOptions {
  branch: string;
  buildId: string;
  target: string;
}

/**
 * Request for RemoteControl API.
 */
export declare interface RemoteControlRequest {
  runAs: string;
  timeoutHours: number;
  proxyType: 'DEFAULT' | 'TCP' | 'ADB' | 'VNC';
  videoResolution: 'DEFAULT' | 'HIGH' | 'LOW';
  maxVideoSize: 'DEFAULT' | '1024';
  flashOptions?: FlashOptions;
}

/**
 * Response for RemoteControl API.
 */
export declare interface RemoteControlResponse {
  sessionUrl: string;
}

/**
 * Request for QuarantineDevice API.
 */
export declare interface QuarantineDeviceRequest {
  /** The timestamp in ISO 8601 format with UTC timezone when the quarantine should expire. */
  endTime: string;
}

/**
 * Response for QuarantineDevice API.
 */
export declare interface QuarantineDeviceResponse {
  quarantineExpiry: string;
}

/** Data for screenshot dialog. */
export declare interface ScreenshotDialogData {
  deviceId: string;
  screenshotUrl: string;
  capturedAt: string;
}

/** Data for logcat dialog. */
export declare interface LogcatDialogData {
  deviceId: string;
  logContent: string;
  capturedAt: string;
  logUrl: string;
}

/** Data for flash dialog. */
export declare interface FlashDialogData {
  deviceId: string;
  hostName: string;
  deviceType: string;
  requiredDimensions: string;
}

/** Data for quarantine dialog. */
export declare interface QuarantineDialogData {
  deviceId: string;
  isUpdate: boolean;
  currentExpiry?: string;
  title: string;
  description: string;
  confirmText: string;
}
