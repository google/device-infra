import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
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
export class Dimensions {
  readonly type = input<'device' | 'host'>('device');
  readonly workflow = input<'wizard' | 'settings'>('wizard');
  readonly uiStatus = input<MetadataUiStatus>({
    sectionStatus: {visible: true, editability: {editable: true}},
  });

  readonly title = input<string>('Dimensions');
  readonly tooltipText = input<string>('');

  readonly dimensionsInput = input.required<{
    supported?: DeviceDimension[];
    required?: DeviceDimension[];
  }>({alias: 'dimensions'});

  readonly dimensions = computed(() => ({
    supported: this.dimensionsInput().supported || [],
    required: this.dimensionsInput().required || [],
  }));

  readonly dimensionsChange = output<{
    supported?: DeviceDimension[];
    required?: DeviceDimension[];
  }>();
  readonly hasError = output<boolean>();

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

  onMetadataListChange(
    type: 'supported' | 'required',
    list: DeviceDimension[],
  ) {
    const current = this.dimensions();
    const updated = {
      ...current,
      [type]: list,
    };

    this.dimensionsChange.emit(updated);
  }

  onHasErrorChanged(type: 'supported' | 'required', hasError: boolean) {
    if (type === 'supported') {
      this.supportError = hasError;
    } else {
      this.requiredError = hasError;
    }

    this.hasError.emit(this.supportError || this.requiredError);
  }
}
