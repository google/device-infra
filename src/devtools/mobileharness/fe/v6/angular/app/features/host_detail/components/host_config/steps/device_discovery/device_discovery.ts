import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  EventEmitter,
  input,
  Output,
} from '@angular/core';

import {
  MANEKI_DEVICE_TYPE_OPTIONS,
  SSH_DEVICE_TYPE_OPTIONS,
} from '@deviceinfra/app/core/constants/host_config_constants';
import type {
  DeviceDiscoverySettings,
  ManekiSpec,
} from '@deviceinfra/app/core/models/host_config_models';
import {EntryChip} from '../../../../../../shared/components/config_common/entry_chip/entry_chip';
import {
  MetadataColumn,
  MetadataList,
  type MetadataUiStatus,
} from '../../../../../../shared/components/config_common/metadata_list/metadata_list';

type ChipKey =
  | 'monitoredDeviceUuids'
  | 'testbedUuids'
  | 'miscDeviceUuids'
  | 'overTcpIps';
type MetadataKey = 'overSshDevices' | 'manekiSpecs';

/**
 * Component for displaying the device discovery of a host in the host
 * configuration workflow.
 */
@Component({
  selector: 'app-device-discovery',
  standalone: true,
  templateUrl: './device_discovery.ng.html',
  styleUrl: './device_discovery.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, EntryChip, MetadataList],
})
export class DeviceDiscovery {
  readonly workflow = input<'wizard' | 'settings'>('settings');
  readonly uiStatus = input<MetadataUiStatus>({
    sectionStatus: {visible: true, editability: {editable: true}},
  });

  readonly deviceDiscovery = input<DeviceDiscoverySettings>({
    monitoredDeviceUuids: [],
    testbedUuids: [],
    miscDeviceUuids: [],
    overTcpIps: [],
    overSshDevices: [],
    manekiSpecs: [],
  });
  @Output()
  readonly deviceDiscoveryChange = new EventEmitter<DeviceDiscoverySettings>();

  @Output() readonly hasError = new EventEmitter<boolean>();

  errorList: number[] = [];

  readonly chips = computed(() => {
    const settings = this.deviceDiscovery();
    return [
      {
        key: 'monitoredDeviceUuids' as ChipKey,
        title: 'Monitored Device UUIDs',
        description: 'The UUIDs of devices which will be monitored by the lab.',
        entries: settings.monitoredDeviceUuids || [],
      },
      {
        key: 'testbedUuids' as ChipKey,
        title: 'Testbed UUIDs',
        description: 'The UUIDs of testbed configs for this lab.',
        entries: settings.testbedUuids || [],
      },
      {
        key: 'miscDeviceUuids' as ChipKey,
        title: 'Misc Device UUIDs',
        description:
          'The UUIDs of devices which should be treated as MiscDevice.',
        entries: settings.miscDeviceUuids || [],
      },
      {
        key: 'overTcpIps' as ChipKey,
        title: 'TCP Discovery IPs',
        description:
          'IP addresses the lab will attempt to connect to via TCP by default.',
        entries: settings.overTcpIps || [],
      },
    ];
  });

  readonly metadataList = computed(() => {
    const settings = this.deviceDiscovery();
    const list: Array<{
      key: MetadataKey;
      title: string;
      description: string;
      emptyMessage: string;
      addButtonLabel: string;
      columns: MetadataColumn[];
      list: Array<Record<string, string> | ManekiSpec>;
    }> = [
      {
        key: 'overSshDevices' as MetadataKey,
        title: 'Over SSH Devices',
        description: 'Define devices that the lab will connect to via SSH.',
        emptyMessage: 'No SSH devices configured.',
        addButtonLabel: 'Add SSH Device',
        columns: [
          {
            columnDef: 'ipAddress',
            header: 'IP Address',
            cell: 'ipAddress',
            type: 'input',
            inputType: 'text',
            required: true,
          },
          {
            columnDef: 'username',
            header: 'Username',
            cell: 'username',
            type: 'input',
            inputType: 'text',
            required: true,
          },
          {
            columnDef: 'password',
            header: 'Password',
            cell: 'password',
            type: 'input',
            inputType: 'password',
          },
          {
            columnDef: 'sshDeviceType',
            header: 'Device Type',
            cell: 'sshDeviceType',
            type: 'select',
            options: SSH_DEVICE_TYPE_OPTIONS,
            required: true,
          },
        ] as MetadataColumn[],
        list: settings.overSshDevices || [],
      },
      {
        key: 'manekiSpecs' as MetadataKey,
        title: 'Maneki Device Detector Specs',
        description:
          'Configure specs for Maneki, a network-based device detector.',
        emptyMessage: 'No Maneki specs configured.',
        addButtonLabel: 'Add Maneki Spec',
        columns: [
          {
            columnDef: 'type',
            header: 'Device Type',
            cell: 'type',
            type: 'select',
            options: MANEKI_DEVICE_TYPE_OPTIONS,
            defaultValue: 'android',
            required: true,
          },
          {
            columnDef: 'macAddress',
            header: 'MAC Address',
            cell: 'macAddress',
            type: 'input',
            inputType: 'text',
            required: true,
            pattern: '^([0-9A-Fa-f]{2}:){5}([0-9A-Fa-f]{2})$',
            patternError:
              'Invalid MAC address format. Expected: 00:11:22:33:44:55',
          },
        ] as MetadataColumn[],
        list: settings.manekiSpecs || [],
      },
    ];
    return list;
  });

  onChipChange(key: ChipKey, newEntries: string[]) {
    const newDeviceDiscovery = {
      ...this.deviceDiscovery(),
      [key]: newEntries,
    };
    this.deviceDiscoveryChange.emit(newDeviceDiscovery);
  }

  onMetadataChange(
    key: MetadataKey,
    newList: Array<Record<string, string> | ManekiSpec>,
  ) {
    const newDeviceDiscovery = {
      ...this.deviceDiscovery(),
      [key]: newList,
    };
    this.deviceDiscoveryChange.emit(newDeviceDiscovery);
  }

  onHasError(hasError: boolean, index: number) {
    const error = this.errorList.includes(index);
    if (hasError && !error) this.errorList.push(index);
    if (!hasError && error) {
      this.errorList = this.errorList.filter((item) => item !== index);
    }

    this.hasError.emit(this.errorList.length > 0);
  }
}
