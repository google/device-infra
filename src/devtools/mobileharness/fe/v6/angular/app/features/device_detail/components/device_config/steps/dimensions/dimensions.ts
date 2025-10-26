import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
} from '@angular/core';
import type {DeviceDimension} from '../../../../../../core/models/device_config_models';
import {DimensionList} from '../../../../../../shared/components/common_config/dimension_list/dimension_list';

/**
 * Component for displaying the dimensions of a device in the device configuration workflow.
 */
@Component({
  selector: 'app-dimensions',
  standalone: true,
  templateUrl: './dimensions.ng.html',
  styleUrl: './dimensions.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, DimensionList],
})
export class Dimensions implements OnInit {
  @Input() workflow: 'wizard' | 'settings' = 'wizard';

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
