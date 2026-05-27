/**
 * State of an action button.
 */
export declare interface ActionButtonState {
  enabled: boolean;
  visible: boolean;
  tooltip: string;
  isReady: boolean;
}

/**
 * Represents the result of checking write permission.
 */
export declare interface WritePermissionResult {
  hasPermission: boolean;
  userName?: string;
}
