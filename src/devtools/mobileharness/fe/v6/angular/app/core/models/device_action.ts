/**
 * Information about the host machine.
 */
export interface HostInfo {
  /** The hostname of the lab server. */
  name: string;
  /** The IP address of the lab server. */
  ip: string;
  // Future additions could include a link to the host's detail page.
}

/**
 * State of an action button in device header.
 */
export interface ActionButtonState {
  enabled: boolean;
  visible: boolean;
  tooltip: string;
}

/**
 * Parameters for flash action button.
 */
export interface FlashButtonParams {
  deviceType: string;
  requiredDimensions: string;
}

/**
 * State of flash action button.
 */
export interface FlashActionButtonState extends ActionButtonState {
  params: FlashButtonParams;
}

/**
 * Option for runAs in remote control.
 */
export interface RunAsOption {
  value: string;
  label: string;
  isDefault: boolean;
}

/**
 * State of remote control action button.
 */
export interface RemoteControlButtonState extends ActionButtonState {
  runAsOptions: RunAsOption[];
  defaultRunAs: string;
}

/**
 * Quarantine information of device.
 */
export interface QuarantineInfo {
  isQuarantined: boolean;
  expiry: string; // ISO 8601 date string, or '' if not quarantined
}

/**
 * Actions for device header.
 */
export interface DeviceActions {
  screenshot: ActionButtonState;
  logcat: ActionButtonState;
  flash: FlashActionButtonState;
  remoteControl: RemoteControlButtonState;
  quarantine: ActionButtonState;
  configuration: ActionButtonState;
}

/**
 * Information for device header.
 */
export interface DeviceHeaderInfo {
  id: string;
  host?: HostInfo;
  quarantine?: QuarantineInfo;
  actions?: DeviceActions;
}

/**
 * Response for TakeScreenshot API.
 */
export interface TakeScreenshotResponse {
  screenshotUrl: string;
  capturedAt: string;
}

/**
 * Response for GetLogcat API.
 */
export interface GetLogcatResponse {
  logUrl: string;
  capturedAt: string;
}

/**
 * Flash options for remote control.
 */
export interface FlashOptions {
  branch: string;
  buildId: string;
  target: string;
}

/**
 * Request for RemoteControl API.
 */
export interface RemoteControlRequest {
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
export interface RemoteControlResponse {
  sessionUrl: string;
}

/**
 * Request for QuarantineDevice API.
 */
export interface QuarantineDeviceRequest {
  durationHours: number;
}

/**
 * Response for QuarantineDevice API.
 */
export interface QuarantineDeviceResponse {
  quarantineExpiry: string;
}

/** Data for screenshot dialog. */
export interface ScreenshotDialogData {
  deviceId: string;
  screenshotUrl: string;
  capturedAt: string;
}

/** Data for logcat dialog. */
export interface LogcatDialogData {
  deviceId: string;
  logContent: string;
  capturedAt: string;
  logUrl: string;
}

/** Data for flash dialog. */
export interface FlashDialogData {
  deviceId: string;
  hostName: string;
  deviceType: string;
  requiredDimensions: string;
}

/** Data for quarantine dialog. */
export interface QuarantineDialogData {
  deviceId: string;
  isUpdate: boolean;
  currentExpiry?: string;
  title: string;
  description: string;
  confirmText: string;
}

/** Data for remote control dialog. */
export interface RemoteControlDialogData {
  deviceId: string;
  runAsOptions: RunAsOption[];
  defaultRunAs: string;
}
