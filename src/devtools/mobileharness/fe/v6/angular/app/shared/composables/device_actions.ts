import {computed, DestroyRef, inject, signal, Signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatDialog} from '@angular/material/dialog';
import {Observable} from 'rxjs';
import {filter, finalize, tap} from 'rxjs/operators';

import {DeviceOverview, SubDeviceInfo} from '../../core/models/device_overview';
import {DeviceSummary} from '../../core/models/host_overview';
import {ConfirmDialog} from '../components/confirm_dialog/confirm_dialog';
import {FlashErrorContent} from '../components/flash_error_content/flash_error_content';
import {RemoteControlDeviceInfo} from '../components/remote_control/remote_control.types';
import {DeviceActionService} from '../services/device_action_service';
import {RemoteControlService} from '../services/remote_control_service';

/**
 * Composable function encapsulating reactive state and boilerplate for executing
 * device actions (screenshot, logcat, flash, quarantine).
 * Automatically manages component-level subscriptions, loading states, and cancellation.
 */
export function useDeviceActions() {
  const actionService = inject(DeviceActionService);
  const remoteControlService = inject(RemoteControlService);
  const destroyRef = inject(DestroyRef);
  const dialog = inject(MatDialog);

  const activeActions = signal<Record<string, boolean>>({});
  const computedCache = new Map<string, Signal<boolean>>();

  const isRunning = (action: string): Signal<boolean> => {
    let comp = computedCache.get(action);
    if (!comp) {
      comp = computed(() => !!activeActions()[action]);
      computedCache.set(action, comp);
    }
    return comp;
  };

  /**
   * Wraps an Observable stream with loading state management. i.e. sets the
   * loading state to true when the stream starts and sets it to false when the
   * stream ends (either successfully or with an error). This is useful for
   * tracking loading states in the UI, e.g. disable the button while the action
   * is running, like for the getLogcat/getScreenshot actions.
   */
  const runAction = <T>(
    action: string,
    stream$: Observable<T>,
  ): Observable<T> => {
    activeActions.update((state) => ({...state, [action]: true}));
    return stream$.pipe(
      finalize(() => {
        activeActions.update((state) => ({...state, [action]: false}));
      }),
    );
  };

  return {
    isRunning,

    startRemoteControl: (
      hostName: string,
      devices:
        | DeviceOverview
        | DeviceSummary
        | Array<DeviceOverview | DeviceSummary>,
      options?: {isSubDevice?: boolean; subDeviceOnly?: SubDeviceInfo},
    ) => {
      const deviceList: Array<DeviceOverview | DeviceSummary> = Array.isArray(
        devices,
      )
        ? devices
        : [devices];
      const selectedDevices: RemoteControlDeviceInfo[] = deviceList.map((d) => {
        if ('basicInfo' in d) {
          const isTestbed =
            d.healthAndActivity?.deviceTypes?.some(
              (t) => t.type === 'TestbedDevice',
            ) ?? false;
          return {
            id: d.id,
            model: d.basicInfo.model || '',
            isTestbed,
            subDevices: options?.subDeviceOnly
              ? [options.subDeviceOnly]
              : d.subDevices,
          } as RemoteControlDeviceInfo;
        } else {
          const isTestbed =
            (d.types?.some((t) => t.type === 'TestbedDevice') ?? false) &&
            !!d.subDevices &&
            d.subDevices.length > 0;
          return {
            id: d.id,
            model: d.model || '',
            isTestbed: options?.subDeviceOnly ? true : isTestbed,
            subDevices: options?.subDeviceOnly
              ? [options.subDeviceOnly]
              : d.subDevices,
          } as RemoteControlDeviceInfo;
        }
      });

      remoteControlService
        .startRemoteControl(hostName, selectedDevices, !!options?.isSubDevice)
        .pipe(takeUntilDestroyed(destroyRef))
        .subscribe();
    },

    /**
     * Takes a screenshot of the device.
     * @param deviceId The ID of the device.
     * @param hostName The name of the host.
     */
    takeScreenshot: (deviceId: string, hostName: string) => {
      runAction(
        'screenshot',
        actionService
          .takeScreenshot(deviceId, hostName)
          .pipe(takeUntilDestroyed(destroyRef)),
      ).subscribe({
        error: () => {},
      });
    },

    /**
     * Gets the logcat of the device.
     * @param deviceId The ID of the device.
     * @param hostName The name of the host.
     */
    getLogcat: (deviceId: string, hostName: string) => {
      runAction(
        'logcat',
        actionService
          .getLogcat(deviceId, hostName)
          .pipe(takeUntilDestroyed(destroyRef)),
      ).subscribe({
        error: () => {},
      });
    },

    flashDevice: (
      deviceId: string,
      hostName: string,
      params?: {deviceType?: string; requiredDimensions?: string},
    ) => {
      if (!params || !params?.deviceType) {
        console.log(
          `needed params for flash device is missing! params: ${JSON.stringify(params)}`,
        );
        dialog.open(ConfirmDialog, {
          data: {
            title: 'Cannot start flashing',
            contentComponent: FlashErrorContent,
            type: 'error',
            primaryButtonLabel: 'OK',
          },
        });
        return;
      }

      actionService.flashDevice(deviceId, hostName, params);
    },

    /**
     * Quarantines the device or opens the quarantine dialog.
     * @param deviceId The ID of the device.
     * @param options The options for quarantine, including quarantine info, success callback, and host name.
     */
    quarantineDevice: (
      deviceId: string,
      options: {
        quarantineInfo?: {isQuarantined: boolean; expiry?: string};
        onSuccess?: () => void;
        hostName: string;
      },
    ) => {
      runAction(
        'quarantine',
        actionService
          .quarantineDevice(deviceId, {
            quarantineInfo: options.quarantineInfo,
            hostName: options.hostName,
          })
          .pipe(
            takeUntilDestroyed(destroyRef),
            filter((result) => !!result),
            tap(() => {
              options?.onSuccess?.();
            }),
          ),
      ).subscribe({
        error: () => {},
      });
    },

    /**
     * Changes the quarantine duration of the device.
     * @param deviceId The ID of the device.
     * @param hostName The name of the host.
     * @param expiry The new expiry time for the quarantine (optional).
     */
    changeQuarantine: (deviceId: string, hostName: string, expiry?: string) => {
      actionService
        .changeQuarantine(deviceId, hostName, expiry)
        .pipe(takeUntilDestroyed(destroyRef))
        .subscribe({
          error: () => {},
        });
    },

    configureDevice: (
      deviceId: string,
      hostName: string,
      hostIp: string,
      universe?: string,
    ) => {
      actionService.configureDevice(deviceId, hostName, hostIp, universe);
    },

    /**
     * Prepares the device.
     *
     * @param deviceId The ID of the device to prepare.
     * @param hostName The name of the host.
     */
    prepareDevice: (deviceId: string, hostName: string) => {
      runAction(
        'prepare',
        actionService
          .prepareDevice(deviceId, hostName)
          .pipe(takeUntilDestroyed(destroyRef)),
      ).subscribe({
        error: () => {},
      });
    },
  };
}
