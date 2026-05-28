import {inject, signal, computed, Signal, DestroyRef} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Observable} from 'rxjs';
import {filter, finalize, tap} from 'rxjs/operators';

import {DeviceActionService} from '../services/device_action_service';
import {RemoteControlService} from '../services/remote_control_service';
import {RemoteControlDeviceInfo} from '../components/remote_control/remote_control.types';
import {DeviceOverview, SubDeviceInfo} from '../../core/models/device_overview';
import {DeviceSummary} from '../../core/models/host_overview';

/**
 * Composable function encapsulating reactive state and boilerplate for executing
 * device actions (screenshot, logcat, flash, quarantine).
 * Automatically manages component-level subscriptions, loading states, and cancellation.
 */
export function useDeviceActions() {
  const actionService = inject(DeviceActionService);
  const remoteControlService = inject(RemoteControlService);
  const destroyRef = inject(DestroyRef);

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

  const runAction = <T>(
    action: string,
    stream$: Observable<T>,
  ): Observable<T> => {
    activeActions.update((state) => ({ ...state, [action]: true }));
    return stream$.pipe(
      finalize(() => {
        activeActions.update((state) => ({ ...state, [action]: false }));
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

    takeScreenshot: (deviceId: string) => {
      runAction(
        'screenshot',
        actionService
          .takeScreenshot(deviceId)
          .pipe(takeUntilDestroyed(destroyRef)),
      ).subscribe({
        error: () => {},
      });
    },

    getLogcat: (deviceId: string) => {
      runAction(
        'logcat',
        actionService.getLogcat(deviceId).pipe(takeUntilDestroyed(destroyRef)),
      ).subscribe({
        error: () => {},
      });
    },

    flashDevice: (
      deviceId: string,
      hostName: string,
      params?: {deviceType?: string; requiredDimensions?: string},
    ) => {
      actionService.flashDevice(deviceId, hostName, params);
    },

    quarantineDevice: (
      deviceId: string,
      options?: {
        quarantineInfo?: {isQuarantined: boolean; expiry?: string};
        onSuccess?: () => void;
      },
    ) => {
      runAction(
        'quarantine',
        actionService
          .quarantineDevice(deviceId, {
            quarantineInfo: options?.quarantineInfo,
          })
          .pipe(
            takeUntilDestroyed(destroyRef),
            filter((result) => !!result),
            tap(() => { options?.onSuccess?.(); }),
          ),
      ).subscribe({
        error: () => {},
      });
    },

    changeQuarantine: (deviceId: string, expiry?: string) => {
      actionService
        .changeQuarantine(deviceId, expiry)
        .pipe(takeUntilDestroyed(destroyRef))
        .subscribe({
          error: () => {},
        });
    },
  };
}
