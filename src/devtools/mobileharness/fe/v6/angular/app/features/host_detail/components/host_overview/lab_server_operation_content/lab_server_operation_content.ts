import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';

/**
 * The type of Lab Server operation.
 */
export type LabServerOperation =
  | 'START'
  | 'RESTART'
  | 'STOP'
  | 'DECOMMISSION'
  | 'DEPLOY';

/**
 * Component to display Lab Server Operation confirmation content.
 */
@Component({
  selector: 'app-lab-server-operation-content',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './lab_server_operation_content.ng.html',
  styleUrls: ['./lab_server_operation_content.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LabServerOperationContent {
  @Input({required: true}) hostName!: string;
  @Input({required: true}) status!: string;
  @Input({required: true}) operation!: LabServerOperation;

  @Input() targetVersion?: string;
  @Input() currentVersion?: string;

  isWarningState(): boolean {
    if (this.operation === 'STOP') {
      return ['DRAINING', 'DRAINED', 'STOPPING', 'STOPPED'].includes(
        this.status,
      );
    }
    return false;
  }
}
