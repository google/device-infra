import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';
import type {DeviceDimension} from '../../../../../../core/models/device_config_models';
import {
  MetadataColumn,
  MetadataList,
  type MetadataUiStatus,
} from '../../../../../../shared/components/config_common/metadata_list/metadata_list';

/**
 * Component for displaying the dimensions of a device in the device configuration workflow.
 */
@Component({
  selector: 'app-dimensions',
  standalone: true,
  templateUrl: './dimensions.ng.html',
  styleUrl: './dimensions.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MetadataList, MatIconModule, MatTooltipModule],
})
export class Dimensions implements OnInit {
  @Input() type: 'device' | 'host' = 'device';
  @Input() workflow: 'wizard' | 'settings' = 'wizard';
  @Input() uiStatus: MetadataUiStatus = {
    sectionStatus: {visible: true, editability: {editable: true}},
  };

  @Input() title = 'Dimensions';
  @Input() tooltipText = '';

  @Input() dimensions: {
    supported: DeviceDimension[];
    required: DeviceDimension[];
  } = {
    supported: [],
    required: [],
  };
  @Output() readonly dimensionsChange = new EventEmitter<{
    supported: DeviceDimension[];
    required: DeviceDimension[];
  }>();
  @Output() readonly hasError = new EventEmitter<boolean>();

  supportError = false;
  requiredError = false;

  readonly supportedTooltipText =
    "These are additional dimensions that describe devices on this host (e.g., 'label: some_label'). An OmniLab job can request these dimensions to find and use these devices.";
  readonly requiredTooltipText =
    "These are additional dimensions that a job MUST provide to use devices on this host (e.g., 'pool: some_pool'). This protects specialized devices from being used by general jobs.";

  readonly dimensionColumns: MetadataColumn[] = [
    {
      columnDef: 'name',
      header: 'NAME',
      cell: 'name',
      type: 'input',
      inputType: 'text',
      defaultValue: () => `new_dimension_${Date.now()}`,
      placeholder: 'e.g., pool',
    },
    {
      columnDef: 'value',
      header: 'VALUE',
      cell: 'value',
      type: 'input',
      inputType: 'text',
      placeholder: 'e.g., presubmit',
    },
  ];

  ngOnInit() {}

  onHasErrorChanged(type: 'supported' | 'required', hasError: boolean) {
    if (type === 'supported') {
      this.supportError = hasError;
    } else {
      this.requiredError = hasError;
    }

    this.hasError.emit(this.supportError || this.requiredError);
  }
}
