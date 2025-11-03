import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, EventEmitter, Input, OnInit, Output} from '@angular/core';

import type {HostProperty} from '../../../../../../core/models/host_config_models';
import {MetadataColumn, MetadataList, type MetadataUiStatus} from '../../../../../../shared/components/config_common/metadata_list/metadata_list';

/**
 * Component for displaying the host properties of a host in the host
 * configuration workflow.
 */
@Component({
  selector: 'app-host-properties',
  standalone: true,
  templateUrl: './host_properties.ng.html',
  styleUrl: './host_properties.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MetadataList],
})
export class HostProperties implements OnInit {
  @Input() workflow: 'wizard'|'settings' = 'settings';
  @Input()
  uiStatus: MetadataUiStatus = {
    sectionStatus: {visible: true, editability: {editable: true}},
  };

  @Input() properties: HostProperty[] = [];
  @Output() readonly propertiesChange = new EventEmitter<HostProperty[]>();

  readonly columns: MetadataColumn[] = [
    {
      columnDef: 'key',
      header: 'NAME',
      cell: 'key',
      type: 'input',
      inputType: 'text',
      defaultValue: () => `new_property_${Date.now()}`,
      placeholder: 'e.g., host_group',
    },
    {
      columnDef: 'value',
      header: 'VALUE',
      cell: 'value',
      type: 'input',
      inputType: 'text',
      placeholder: 'e.g., staging',
    },
  ];

  ngOnInit() {}
}
