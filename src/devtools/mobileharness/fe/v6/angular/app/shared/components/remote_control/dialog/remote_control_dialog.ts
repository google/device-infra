import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnDestroy,
  OnInit,
  signal,
} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatButtonToggleModule} from '@angular/material/button-toggle';
import {MatChipOption, MatChipsModule} from '@angular/material/chips';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatSliderModule} from '@angular/material/slider';
import {MatTooltipModule} from '@angular/material/tooltip';
import {
  DeviceEligibilityResult,
  DeviceProxyType,
  DeviceSummary,
  RemoteControlDevicesRequest,
  SessionOptions,
} from 'app/core/models/host_overview';
import {ConfirmDialog} from 'app/shared/components/confirm_dialog/confirm_dialog';
import {ToggleSwitch} from 'app/shared/components/toggle_switch/toggle_switch';
import {SnackBarService} from 'app/shared/services/snackbar_service';
import {ConfirmConnectionContent} from '../feedback/confirm_connection_content';

/** Data required to initialize the RemoteControlDialog. */
export interface RemoteControlDialogData {
  devices: DeviceSummary[];
  eligibilityResults: DeviceEligibilityResult[];
  sessionOptions: SessionOptions;
}

interface DeviceListItem {
  summary: DeviceSummary;
  eligibility: DeviceEligibilityResult;
  validIdentities: string[];
  hasAccess: boolean;
}

/**
 * Dialog for configuring and starting a remote control session.
 * Handles device selection, identity (Run As), duration, and other settings.
 */
@Component({
  selector: 'app-remote-control-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatChipsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSliderModule,
    MatTooltipModule,
    ToggleSwitch,
  ],
  templateUrl: './remote_control_dialog.ng.html',
  styleUrls: ['./remote_control_dialog.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RemoteControlDialog implements OnInit, OnDestroy {
  private readonly dialogRef = inject(MatDialogRef<RemoteControlDialog>);
  private readonly dialog = inject(MatDialog);
  readonly data = inject<RemoteControlDialogData>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(SnackBarService);

  /** Form group for the dialog configuration. */
  readonly form = this.fb.group({
    deviceConfigs: this.fb.array([]), // FormArray of {deviceId, runAs}
    globalRunAs: [''], // Control for the global selector
    durationMinutes: [180, [Validators.required, Validators.min(10)]],
    durationH: [3, [Validators.min(0)]],
    durationM: [0, [Validators.min(0), Validators.max(59)]],

    // Flash Settings
    enableFlash: [false],
    flashBranch: ['', this.requiredIfFlashEnabled()],
    flashBuildId: ['', this.requiredIfFlashEnabled()],
    flashTarget: ['', this.requiredIfFlashEnabled()],

    // Advanced Settings
    proxyType: [0], // 0 = Auto
    videoResolution: ['default'],
    limitVideoSize: [false],
  });

  // State for UI toggles
  showAdvancedSettings = signal(false);
  showSkippedInConfirm = signal(true);

  // Processed data for the view
  deviceList = signal<DeviceListItem[]>([]);

  commonIdentities = signal<string[]>([]);
  globalRunAsOptions = computed(() => {
    return this.commonIdentities();
  });

  startingSession = signal(false);

  // Constants
  readonly PROXY_TYPE_LABELS: Record<number, string> = {
    0: 'Auto (Default)',
    1: 'ADB & Video',
    2: 'ADB Console',
    3: 'USB-over-IP',
    4: 'SSH',
    5: 'Video Only',
  };

  // Duration presets in minutes
  readonly DURATION_CHIPS = [60, 120, 240, 480, 720];
  readonly DURATION_CHIPS_SHORT = [15, 30, 45, 60, 120, 180];

  get deviceConfigs(): FormArray {
    return this.form.get('deviceConfigs') as FormArray;
  }

  get maxDurationHours(): number {
    return this.data.sessionOptions.maxDurationHours;
  }

  get durationChips(): Array<{label: string; value: number}> {
    const maxMinutes = this.maxDurationHours * 60;
    let chips: number[];

    if (this.maxDurationHours <= 3) {
      chips = this.DURATION_CHIPS_SHORT.filter((m) => m <= maxMinutes);
    } else {
      chips = this.DURATION_CHIPS.filter((m) => m <= maxMinutes);
    }

    return chips.map((m) => {
      const h = Math.floor(m / 60);
      const min = m % 60;
      return {
        label: m < 60 ? `${m}m` : min === 0 ? `${h}h` : `${h}h${min}m`,
        value: m,
      };
    });
  }

  // Proxies common to all selected devices
  commonProxyModes = signal<DeviceProxyType[]>([]);

  // Current time signal, updated periodically
  currentTime = signal(Date.now());
  private intervalId: ReturnType<typeof setInterval> | undefined;
  private timerId: ReturnType<typeof setTimeout> | undefined;

  // Reactively track duration minutes from the form
  readonly durationMinutes = toSignal(
    this.form.controls['durationMinutes'].valueChanges,
    {initialValue: this.form.controls['durationMinutes'].value},
  );

  // Computed signal for End Time
  readonly endTime = computed(() => {
    const minutes = this.durationMinutes() || 0;
    const now = this.currentTime();
    const d = new Date(now + minutes * 60000);
    const timeStr = d.toLocaleTimeString([], {
      hour: 'numeric',
      minute: '2-digit',
    });
    if (d.getDate() !== new Date(now).getDate()) {
      return `Tomorrow, ${timeStr}`;
    }
    return `Ends at ${timeStr}`;
  });

  get deviceGridClass(): string {
    return `cols-${Math.min(3, this.deviceList().length)}`;
  }

  ngOnInit() {
    this.initializeDeviceData();
    this.setupDurationSync();
    this.setupGlobalRunAsSync();
    this.setupFlashValidation();

    // Update time aligned to the next minute for better performance and accuracy
    const now = new Date();
    const msToNextMinute =
      (60 - now.getSeconds()) * 1000 - now.getMilliseconds();
    this.timerId = setTimeout(() => {
      this.currentTime.set(Date.now());
      this.intervalId = setInterval(() => {
        this.currentTime.set(Date.now());
      }, 60000);
    }, msToNextMinute);
  }

  ngOnDestroy() {
    if (this.intervalId) {
      clearInterval(this.intervalId);
    }
    if (this.timerId) {
      clearTimeout(this.timerId);
    }
  }

  private initializeDeviceData() {
    const devices = this.data.devices;
    const results = this.data.eligibilityResults;

    const processedList: DeviceListItem[] = devices.map((device) => {
      const result = results.find((r) => r.deviceId === device.id);
      const validIdentities: string[] = result?.runAsCandidates || [];
      // Device has access if it is eligible OR (not eligible AND reason is NOT PERMISSION_DENIED)
      // i.e. access is denied only if not eligible AND reason IS PERMISSION_DENIED
      const hasAccess =
        result?.isEligible ||
        result?.ineligibilityReason?.code !== 'PERMISSION_DENIED';

      // Add control to form array
      const deviceGroup = this.fb.group({
        deviceId: [device.id],
        runAs: [
          hasAccess ? validIdentities[0] : '',
          hasAccess ? Validators.required : null,
        ],
      });
      this.deviceConfigs.push(deviceGroup);

      return {
        summary: device,
        eligibility: result!,
        validIdentities,
        hasAccess,
      };
    });

    this.deviceList.set(processedList);

    // Initialize Common Identities from Data
    const commonIds = (
      this.data.sessionOptions.commonRunAsCandidates || []
    ).sort();
    this.commonIdentities.set(commonIds);

    // Default select the first common identity if available
    if (commonIds.length > 0) {
      const defaultId = commonIds[0];
      this.form.get('globalRunAs')?.setValue(defaultId, {emitEvent: false});

      // Propagate to all devices
      this.deviceConfigs.controls.forEach(
        (control: AbstractControl, index: number) => {
          const deviceData = this.deviceList()[index];
          if (deviceData.validIdentities.includes(defaultId)) {
            control.get('runAs')?.setValue(defaultId);
          }
        },
      );
    }

    // Initialize Common Proxies from Data
    this.commonProxyModes.set(
      (this.data.sessionOptions.commonProxyTypes || []).sort((a, b) => a - b),
    );
  }

  // --- Form Helpers ---

  getDeviceControl(index: number): FormGroup {
    return this.deviceConfigs.at(index) as FormGroup;
  }

  // --- Logic ---

  requiredIfFlashEnabled(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const form = control.parent;
      if (!form) return null;
      const enableFlash = form.get('enableFlash')?.value;
      if (enableFlash && !control.value) {
        return {'required': true};
      }
      return null;
    };
  }

  private setupFlashValidation() {
    this.form.get('enableFlash')?.valueChanges.subscribe((enabled) => {
      const fields = ['flashBranch', 'flashBuildId', 'flashTarget'];
      fields.forEach((field) => {
        const control = this.form.get(field);
        if (control) {
          control.updateValueAndValidity();
          if (enabled) {
            control.markAsTouched();
          } else {
            control.markAsUntouched();
          }
        }
      });
    });
  }

  private setupDurationSync() {
    // Slider -> Inputs
    this.form.get('durationMinutes')?.valueChanges.subscribe((val) => {
      if (val === null || val === undefined) return;
      const h = Math.floor(val / 60);
      const m = val % 60;
      this.form.get('durationH')?.setValue(h, {emitEvent: false});
      this.form.get('durationM')?.setValue(m, {emitEvent: false});
    });

    // Inputs -> Slider
    const syncToSlider = () => {
      const h = this.form.get('durationH')?.value || 0;
      const m = this.form.get('durationM')?.value || 0;
      let total = h * 60 + m;
      if (total > this.maxDurationHours * 60) {
        total = this.maxDurationHours * 60;
      }
      this.form.get('durationMinutes')?.setValue(total);
    };

    this.form.get('durationH')?.valueChanges.subscribe(syncToSlider);
    this.form.get('durationM')?.valueChanges.subscribe(syncToSlider);
  }

  private setupGlobalRunAsSync() {
    // Global -> Devices
    this.form.get('globalRunAs')?.valueChanges.subscribe((val) => {
      if (!val || val === 'Mixed') {
        return;
      }

      this.deviceConfigs.controls.forEach(
        (control: AbstractControl, index: number) => {
          const deviceData = this.deviceList()[index];
          if (deviceData.validIdentities.includes(val)) {
            control.get('runAs')?.setValue(val);
          }
        },
      );
    });

    // Devices -> Global
    this.deviceConfigs.valueChanges.subscribe(
      (vals: Array<{deviceId: string; runAs: string}>) => {
        const runAsValues = vals.map((v) => v.runAs);
        const unique = new Set(runAsValues);

        // If all devices share the same valid runAs value
        if (unique.size === 1) {
          const val = runAsValues[0];
          // And it is one of the common identities
          if (val && this.commonIdentities().includes(val)) {
            // If current global is different, update it
            if (this.form.get('globalRunAs')?.value !== val) {
              this.form.get('globalRunAs')?.setValue(val, {emitEvent: false});
            }
            return;
          }
        }

        // Otherwise, set to Mixed if not already
        if (this.form.get('globalRunAs')?.value !== '') {
          this.form.get('globalRunAs')?.setValue('', {emitEvent: false});
        }
      },
    );
  }

  // --- Actions ---

  removeDevice(index: number) {
    this.deviceConfigs.removeAt(index);

    // Also update our local list
    const currentList = this.deviceList();
    currentList.splice(index, 1);
    this.deviceList.set([...currentList]);

    if (currentList.length === 0) {
      this.dialogRef.close();
    }
  }

  setDuration(minutes: number | undefined | null) {
    // If user unchecks the chip (value is null/undefined), default to 10 minutes
    this.form.patchValue({durationMinutes: minutes || 10});
  }

  toggleChip(chip: MatChipOption, event: MouseEvent) {
    const target = event.target as HTMLElement;
    // If the click target is the button (which handles selection itself), do nothing.
    if (target.closest('.mdc-evolution-chip__action')) {
      return;
    }
    // Otherwise (e.g. click on padding), manually toggle.
    chip.toggleSelected(true);
  }

  getSettingsSummary(): string {
    const proxy = this.form.get('proxyType')?.value ?? 0;
    const res = this.form.get('videoResolution')?.value ?? 'default';
    const limit = this.form.get('limitVideoSize')?.value;

    const parts = [];
    parts.push(
      proxy === 0 ? 'Auto Proxy' : this.PROXY_TYPE_LABELS[proxy] || 'Proxy',
    );

    let qual = res.charAt(0).toUpperCase() + res.slice(1) + ' Quality';
    if (limit) qual += ' (1024px)';
    parts.push(qual);

    return parts.join(' • ');
  }

  // Helpers for Confirm Dialog
  getDurationLabel(req: RemoteControlDevicesRequest): string {
    const minutes = Math.floor(req.durationSeconds / 60);
    return `${minutes}m`;
  }

  getResolutionLabel(req: RemoteControlDevicesRequest): string {
    const res = req.videoResolution;
    const limit = req.maxVideoSize === '1024' ? ' (1024px)' : '';

    if (!res) return 'Default Quality' + limit;
    return res === 'HIGH' ? 'High Quality' + limit : 'Low Quality' + limit;
  }

  getProxyLabel(req: RemoteControlDevicesRequest): string {
    return this.PROXY_TYPE_LABELS[req.proxyType] || 'Unknown';
  }

  getFlashLabel(req: RemoteControlDevicesRequest): string {
    return req.flashOptions ? 'Flash On' : 'Flash Off';
  }

  isFormValid(): boolean {
    // Check if the form is valid, including all controls.
    // The validation logic is mostly in the control validators, but we can add
    // specific checks if needed.
    return this.form.valid;
  }

  /** Validates the form and initiates the remote control session start process. */
  startSession() {
    if (!this.isFormValid()) {
      this.snackBar.showError('Please correct the errors in the form.');
      return;
    }
    this.startingSession.set(true);

    const val = this.form.value;

    // Construct request
    const rawConfigs = val.deviceConfigs as
      | Array<{deviceId: string; runAs: string}>
      | undefined;
    const deviceConfigs = (rawConfigs ?? [])
      .map((c) => ({
        deviceId: c.deviceId,
        runAs: c.runAs,
      }))
      .filter((c) => c.runAs);

    const req: RemoteControlDevicesRequest = {
      deviceConfigs,
      durationSeconds: (val.durationMinutes ?? 180) * 60,
      proxyType: val.proxyType ?? 0,
      videoResolution:
        val.videoResolution === 'default'
          ? undefined
          : val.videoResolution === 'high'
            ? 'HIGH'
            : 'LOW',
      maxVideoSize: val.limitVideoSize ? '1024' : 'DEFAULT',
      flashOptions: val.enableFlash
        ? {
            branch: val.flashBranch ?? '',
            buildId: val.flashBuildId ?? '',
            target: val.flashTarget ?? '',
          }
        : undefined,
    };

    // Correction for maxVideoSize based on logic in original CL (checked = 1024)
    if (val.limitVideoSize) {
      req.maxVideoSize = '1024';
    } else {
      req.maxVideoSize = 'DEFAULT';
    }

    this.openConfirmDialog(req);
  }

  private openConfirmDialog(req: RemoteControlDevicesRequest) {
    const targetDeviceIds = new Set(req.deviceConfigs.map((c) => c.deviceId));
    const skippedDevices = this.data.devices
      .filter((d) => !targetDeviceIds.has(d.id))
      .map((d) => {
        const result = this.data.eligibilityResults.find(
          (r) => r.deviceId === d.id,
        );
        let reason = 'Skipped';
        if (result && !result.isEligible) {
          if (result.ineligibilityReason?.code === 'PERMISSION_DENIED') {
            reason = 'Denied';
          } else {
            reason = 'Ineligible';
          }
        }
        return {id: d.id, reason};
      });

    this.dialog
      .open(ConfirmDialog, {
        panelClass: 'confirm-dialog-panel',
        data: {
          title: 'Confirm Connection',
          contentComponent: ConfirmConnectionContent,
          contentComponentInputs: {
            request: req,
            skippedDevices,
          },
          type: 'info',
          primaryButtonLabel: `Launch Session (${req.deviceConfigs.length})`,
          primaryButtonIcon: 'open_in_new',
          secondaryButtonLabel: 'Cancel',
        },
        disableClose: true,
      })
      .afterClosed()
      .subscribe((result) => {
        if (result === 'primary') {
          this.dialogRef.close(req);
        }
        this.startingSession.set(false);
      });
  }
}
