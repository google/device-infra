import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges} from '@angular/core';

import type {DeviceDiscoverySettings, ManekiSpec} from '../../../../../../core/models/host_config_models';
import {EntryChip} from '../../../../../../shared/components/config_common/entry_chip/entry_chip';
import {MetadataColumn, MetadataList, type MetadataUiStatus} from '../../../../../../shared/components/config_common/metadata_list/metadata_list';

type ChipKey =
    |'monitoredDeviceUuids'|'testbedUuids'|'miscDeviceUuids'|'overTcpIps';
type MetadataKey = 'overSshDevices'|'manekiSpecs';

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
export class DeviceDiscovery implements OnInit, OnChanges {
  @Input() workflow: 'wizard'|'settings' = 'settings';
  @Input()
  uiStatus: MetadataUiStatus = {
    sectionStatus: {visible: true, editability: {editable: true}},
  };

  @Input()
  deviceDiscovery: DeviceDiscoverySettings = {
    monitoredDeviceUuids: [],
    testbedUuids: [],
    miscDeviceUuids: [],
    overTcpIps: [],
    overSshDevices: [],
    manekiSpecs: [],
  };
  @Output()
  readonly deviceDiscoveryChange = new EventEmitter<DeviceDiscoverySettings>();

  @Output() readonly hasError = new EventEmitter<boolean>();

  errorList: number[] = [];

  chips: Array<
      {key: ChipKey; title: string; description: string; entries: string[];}> =
      [
        {
          key: 'monitoredDeviceUuids',
          title: 'Monitored Device UUIDs',
          description:
              'The UUIDs of devices which will be monitored by the lab.',
          entries: [],
        },
        {
          key: 'testbedUuids',
          title: 'Testbed UUIDs',
          description: 'The UUIDs of testbed configs for this lab.',
          entries: [],
        },
        {
          key: 'miscDeviceUuids',
          title: 'Misc Device UUIDs',
          description:
              'The UUIDs of devices which should be treated as MiscDevice.',
          entries: [],
        },
        {
          key: 'overTcpIps',
          title: 'TCP Discovery IPs',
          description:
              'IP addresses the lab will attempt to connect to via TCP by default.',
          entries: [],
        },
      ];

  metadataList: Array<{
    key: MetadataKey; title: string; description: string; emptyMessage: string;
    addButtonLabel: string;
    columns: MetadataColumn[];
    list: Array<Record<string, string>|ManekiSpec>;
  }> =
      [
        {
          key: 'overSshDevices',
          title: 'Over SSH Devices',
          description: 'Define devices that the lab will connect to via SSH.',
          emptyMessage: 'No SSH devices configured.',
          addButtonLabel: 'Add SSH Device',
          columns:
                     [
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
                         type: 'input',
                         inputType: 'text',
                         required: true,
                       },
                     ] as MetadataColumn[],
          list: [],
        },
        {
          key: 'manekiSpecs',
          title: 'Maneki Device Detector Specs',
          description:
              'Configure specs for Maneki, a network-based device detector.',
          emptyMessage: 'No Maneki specs configured.',
          addButtonLabel: 'Add Maneki Spec',
          columns:
                     [
                       {
                         columnDef: 'type',
                         header: 'Device Type',
                         cell: 'type',
                         type: 'select',
                         options:
                             [
                               'Android',
                               'Roku',
                               'Rdk',
                               'Raspberry Pi',
                               'Ps4',
                               'Ps5',
                               'Generic',
                             ],
                         defaultValue: 'Android',
                         required: true,
                       },
                       {
                         columnDef: 'macAddress',
                         header: 'MAC Address',
                         cell: 'macAddress',
                         type: 'input',
                         inputType: 'text',
                         required: true,
                       },
                     ] as MetadataColumn[],
          list: [],
        },
      ];

  ngOnInit() {
    this.chips = this.chips.map((chip) => ({
                                  ...chip,
                                  entries: this.getDeviceDiscoveryEntries(
                                      this.deviceDiscovery, chip.key),
                                }));

    this.metadataList = this.metadataList.map(
        (item) => ({
          ...item,
          list: this.getDeviceDiscoveryMetadata(this.deviceDiscovery, item.key),
        }));
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['deviceDiscovery']) {
      this.updateInternalData(changes['deviceDiscovery'].currentValue);
    }
  }

  updateInternalData(settings: DeviceDiscoverySettings) {
    this.chips = this.chips.map(
        (chip) => ({
          ...chip,
          entries: this.getDeviceDiscoveryEntries(settings, chip.key),
        }));

    this.metadataList = this.metadataList.map(
        (item) => ({
          ...item,
          list: this.getDeviceDiscoveryMetadata(settings, item.key),
        }));
  }

  onChipChange(key: ChipKey, newEntries: string[]) {
    const newDeviceDiscovery = {
      ...this.deviceDiscovery,
      [key]: newEntries,
    };
    this.deviceDiscovery = newDeviceDiscovery;
    this.deviceDiscoveryChange.emit(this.deviceDiscovery);
  }

  onMetadataChange(
      key: MetadataKey,
      newList: Array<Record<string, string>|ManekiSpec>,
  ) {
    const newDeviceDiscovery = {
      ...this.deviceDiscovery,
      [key]: newList,
    };
    this.deviceDiscovery = newDeviceDiscovery;
    this.deviceDiscoveryChange.emit(this.deviceDiscovery);
  }

  onHasError(hasError: boolean, index: number) {
    const error = this.errorList.includes(index);
    if (hasError && !error) this.errorList.push(index);
    if (!hasError && error) {
      this.errorList = this.errorList.filter((item) => item !== index);
    }

    this.hasError.emit(this.errorList.length > 0);
  }

  getDeviceDiscoveryEntries(
      settings: DeviceDiscoverySettings,
      key: ChipKey,
      ): string[] {
    switch (key) {
      case 'monitoredDeviceUuids':
        return settings.monitoredDeviceUuids;
      case 'testbedUuids':
        return settings.testbedUuids;
      case 'miscDeviceUuids':
        return settings.miscDeviceUuids;
      case 'overTcpIps':
        return settings.overTcpIps;
      default:
        return [];
    }
  }

  getDeviceDiscoveryMetadata(
      settings: DeviceDiscoverySettings,
      key: MetadataKey,
      ): Array<Record<string, string>|ManekiSpec> {
    switch (key) {
      case 'overSshDevices':
        return settings.overSshDevices;
      case 'manekiSpecs':
        return settings.manekiSpecs;
      default:
        return [];
    }
  }
}
