import {ChangeDetectionStrategy, Component} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';

/**
 * Component to display kill job confirmation dialog content.
 */
@Component({
  selector: 'app-kill-job-confirm-content',
  standalone: true,
  imports: [MatIconModule],
  templateUrl: './kill_job_confirm_content.ng.html',
  styleUrls: ['./kill_job_confirm_content.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KillJobConfirmContent {}
