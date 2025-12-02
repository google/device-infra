import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MAT_SNACK_BAR_DATA, MatSnackBarRef} from '@angular/material/snack-bar';

/** Data for the SnackBar component. */
export interface SnackBarData {
  message: string;
  icon: string;
  type: 'success' | 'error' | 'info' | 'inprogress';
}

/** A component used to display snack bar notifications. */
@Component({
  selector: 'app-snackbar',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule],
  templateUrl: './snackbar.ng.html',
  styleUrls: ['./snackbar.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SnackBar {
  /** Injected data for the snackbar. */
  data: SnackBarData = inject(MAT_SNACK_BAR_DATA);
  snackBarRef = inject(MatSnackBarRef);

  shouldSpin(): boolean {
    return this.data.icon === 'sync';
  }

  dismiss() {
    this.snackBarRef.dismiss();
  }
}
