import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, inject, Input, OnInit, signal} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';

import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';
import {Dialog} from '../../../../../shared/components/config_common/dialog/dialog';

/**
 * Component for displaying the empty configuration of a host.
 *
 * It is used to configure the host's dimensions, owners, and other properties.
 */
@Component({
  selector: 'app-host-empty',
  standalone: true,
  templateUrl: './host_empty.ng.html',
  styleUrl: './host_empty.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatIconModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    Dialog,
  ],
})
export class HostEmpty implements OnInit {
  readonly data = inject(MAT_DIALOG_DATA);
  private readonly configService = inject(CONFIG_SERVICE);
  private readonly dialogRef = inject(MatDialogRef<HostEmpty>);

  @Input() hostName = this.data.hostName;
  @Input() title = this.data.title;

  copyFromAnother = signal(false);
  anotherHostName = signal('');
  copyFromAnotherErrorMessage = signal('');

  ngOnInit() {}

  startNewConfig() {
    this.dialogRef.close({action: 'new', hostName: this.data.hostName});
  }

  copyFromAnotherHost() {
    this.copyFromAnother.set(true);
  }

  loadAndReviewConfig() {
    if (!this.anotherHostName().trim()) {
      this.copyFromAnotherErrorMessage.set('Please enter a host name.');
      return;
    }

    this.configService.getHostConfig(this.anotherHostName().trim()).subscribe({
      next: (config) => {
        if (!config) {
          this.copyFromAnotherErrorMessage.set(
              `Host not found or has no configuration.`,
          );
          return;
        }

        this.dialogRef.close({
          action: 'copy',
          hostName: this.data.hostName,
          config: config.hostConfig,
        });
      },
      error: (error) => {
        this.copyFromAnotherErrorMessage.set(
            error.message || 'Failed to load host configuration.',
        );
      },
    });
  }
}
