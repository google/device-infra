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

import {DeployableVersion} from '@deviceinfra/app/core/models/host_action';
import {FlagsDialog} from '@deviceinfra/app/features/host_detail/components/host_overview/flags_dialog/flags_dialog';
import {Dialog} from '@deviceinfra/app/shared/components/config_common/dialog/dialog';
import {SnackBarService} from '@deviceinfra/app/shared/services/snackbar_service';

interface VersionDelta {
  type: 'deploy' | 'upgrade' | 'downgrade' | 'redeploy';
  label: 'Deploy' | 'Upgrade' | 'Downgrade' | 'Redeploy';
  icon: 'rocket_launch' | 'arrow_upward' | 'arrow_downward' | 'refresh';
}

/**
 * Data passed to the ReleaseDialog component.
 */
export interface ReleaseDialogData {
  hostName: string;
  releaseConfigs?: DeployableVersion[];
  passThroughFlags: WritableSignal<string>;
  preSelectLatest?: boolean;
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

  readonly currentStep = signal<number>(1);
  readonly selectedVersion = signal<DeployableVersion | null>(null);
  readonly availableVersions = signal<DeployableVersion[]>([]);

  readonly formattedAvailableVersions = computed(() => {
    return this.availableVersions().map((v) => ({
      ...v,
      version: `v${v.version.replace(/^v/, '')}`,
    }));
  });

  readonly statusDisplayMap: Record<string, string> = {
    'LATEST': 'Latest',
    'CURRENT': 'Current',
    'LATEST_AND_CURRENT': 'Latest and Current',
  };

  readonly hasSyncCommands = computed(() => {
    return !!this.selectedVersion()?.releaseDetails?.syncCommands?.length;
  });

  readonly hasAsyncCommands = computed(() => {
    return !!this.selectedVersion()?.releaseDetails?.asyncCommands?.length;
  });

  readonly hasCommands = computed(() => {
    return this.hasSyncCommands() || this.hasAsyncCommands();
  });

  readonly versionDeltaInfo = computed<VersionDelta>(() => {
    const target = this.selectedVersion()?.version || '';
    const current =
      this.formattedAvailableVersions().find(
        (v) => v.status === 'CURRENT' || v.status === 'LATEST_AND_CURRENT',
      )?.version || '';

    const defaultResult: VersionDelta = {
      type: 'deploy',
      label: 'Deploy',
      icon: 'rocket_launch',
    };

    if (!target || !current || current === 'Unknown' || current === 'N/A') {
      return defaultResult;
    }

    const s1 = target.replace(/^v/i, '').split('.');
    const s2 = current.replace(/^v/i, '').split('.');

    let type: 'upgrade' | 'downgrade' | 'redeploy' = 'redeploy';

    for (let i = 0; i < Math.max(s1.length, s2.length); i++) {
      const seg1 = s1[i] ?? '0';
      const seg2 = s2[i] ?? '0';

      if (seg1 === seg2) continue;

      const n1 = Number(seg1);
      const n2 = Number(seg2);

      if (!isNaN(n1) && !isNaN(n2)) {
        // Both are numbers, compare numerically
        type = n1 > n2 ? 'upgrade' : 'downgrade';
        break;
      } else {
        // One or both are non-numeric. Compare lexicographically.
        type = seg1 > seg2 ? 'upgrade' : 'downgrade';
        break;
      }
    }

    const deltaConfig: Record<
      'upgrade' | 'downgrade' | 'redeploy',
      Omit<VersionDelta, 'type'>
    > = {
      'upgrade': {label: 'Upgrade', icon: 'arrow_upward'},
      'downgrade': {label: 'Downgrade', icon: 'arrow_downward'},
      'redeploy': {label: 'Redeploy', icon: 'refresh'},
    };

    return {
      type,
      ...deltaConfig[type],
    };
  });

  readonly currentTab = signal<string>('metadata');
  readonly tempFlags = signal<string>('');
  readonly flagsModifiedThisSession = signal<boolean>(false);
  readonly showDetails = signal<boolean>(false);
  readonly isDeploying = signal<boolean>(false);

  ngOnInit() {
    this.tempFlags.set(this.data.passThroughFlags());
    this.processVersions(this.data.releaseConfigs || []);
    if (this.data.preSelectLatest) {
      const latest = this.availableVersions().find(
        (v) => v.status === 'LATEST' || v.status === 'LATEST_AND_CURRENT',
      );
      if (latest) {
        this.selectVersion(latest);
        this.currentStep.set(2);
      }
    }
  }

  processVersions(versions: DeployableVersion[]) {
    this.availableVersions.set(versions);
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
      // Only update if the result is a string and not the explicit 'close' signal.
      if (typeof result === 'string' && result !== 'close') {
        if (result !== this.tempFlags()) {
          this.flagsModifiedThisSession.set(true);
          this.snackBar.showSuccess('Pass-through flags updated successfully');
        }
        this.data.passThroughFlags.set(result);
        this.tempFlags.set(result);
      }
    });
  }
}
