import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';
import {DeviceSummary} from '../../../../../core/models/host_overview';

/**
 * Component to display decommission confirmation content.
 */
@Component({
  selector: 'app-decommission-content',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './decommission_content.ng.html',
  styleUrls: ['./decommission_content.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DecommissionContent {
  @Input() devices: DeviceSummary[] = [];
}
