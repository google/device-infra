import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
  WritableSignal,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTableModule} from '@angular/material/table';

import {DeployableVersion} from 'app/core/models/host_action';
import {FlagsDialog} from 'app/features/host_detail/components/host_overview/flags_dialog/flags_dialog';
import {Dialog} from 'app/shared/components/config_common/dialog/dialog';
import {SnackBarService} from 'app/shared/services/snackbar_service';

/**
 * Data passed to the ReleaseDialog component.
 */
export interface ReleaseDialogData {
  hostName: string;
  releaseConfigs?: DeployableVersion[];
  passThroughFlags: WritableSignal<string>;
}

/**
 * Component for displaying and managing host releases.
 */
@Component({
  selector: 'app-release-dialog',
  standalone: true,
  templateUrl: './release_dialog.ng.html',
  styleUrl: './release_dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Dialog,
    MatTableModule,
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
})
export class ReleaseDialog implements OnInit {
  readonly data: ReleaseDialogData = inject(MAT_DIALOG_DATA);
  private readonly snackBar = inject(SnackBarService);
  private readonly dialog = inject(MatDialog);

  displayedColumns: string[] = ['version', 'buildTime', 'status', 'actions'];

  readonly currentStep = signal<number>(1);
  readonly selectedVersion = signal<DeployableVersion | null>(null);

  readonly hasSyncCommands = computed(() => {
    return !!this.selectedVersion()?.releaseDetails?.syncCommands?.length;
  });

  readonly hasAsyncCommands = computed(() => {
    return !!this.selectedVersion()?.releaseDetails?.asyncCommands?.length;
  });

  readonly hasCommands = computed(() => {
    return this.hasSyncCommands() || this.hasAsyncCommands();
  });
  readonly currentTab = signal<string>('metadata');
  readonly tempFlags = signal<string>('');
  readonly showDetails = signal<boolean>(false);
  readonly isDeploying = signal<boolean>(false);

  availableVersions: DeployableVersion[] = [];

  ngOnInit() {
    this.availableVersions = this.data.releaseConfigs || [];
    this.tempFlags.set(this.data.passThroughFlags());
  }

  selectVersion(version: DeployableVersion) {
    this.selectedVersion.set(version);
  }

  viewDetails(version: DeployableVersion) {
    const current = this.selectedVersion();
    if (current?.version === version.version && this.showDetails()) {
      this.showDetails.set(false);
    } else {
      this.selectedVersion.set(version);
      this.showDetails.set(true);
    }
  }

  switchTab(tab: string) {
    this.currentTab.set(tab);
  }

  proceed() {
    if (this.selectedVersion()) {
      this.currentStep.set(2);
    }
  }

  back() {
    this.currentStep.set(1);
  }

  deploy() {
    this.isDeploying.set(true);
    // Simulate deployment delay
    setTimeout(() => {
      this.isDeploying.set(false);
      this.snackBar.showSuccess(
        `Deployed ${this.selectedVersion()?.version} successfully`,
      );
    }, 1500);
  }

  openFlagsDialog() {
    const dialogRef = this.dialog.open(FlagsDialog, {
      data: {
        hostName: this.data.hostName,
        currentFlags: this.tempFlags(),
      },
      width: '72rem',
      maxHeight: '90vh',
      autoFocus: false,
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result === 'close') {
        return;
      }
      if (typeof result === 'string') {
        this.data.passThroughFlags.set(result);
        this.tempFlags.set(result);
      }
    });
  }
}
