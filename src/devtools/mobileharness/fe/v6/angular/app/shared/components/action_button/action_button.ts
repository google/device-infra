import {CommonModule} from '@angular/common';
import {Component, input, output} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatMenuModule} from '@angular/material/menu';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatTooltipModule} from '@angular/material/tooltip';
import type {ActionButtonState} from '../../../core/models/action_common';

/** A reusable button component that handles tooltips and loading states. */
@Component({
  selector: 'app-action-button',
  standalone: true,
  templateUrl: './action_button.ng.html',
  styleUrl: './action_button.scss',
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
})
export class ActionButton {
  readonly action = input<ActionButtonState>();
  readonly label = input.required<string>();
  readonly icon = input.required<string>();
  readonly mode = input<'button' | 'menu-item' | 'operation'>('button');
  readonly loading = input(false);
  readonly loadingLabel = input<string>();
  readonly disabled = input(false);
  readonly customClass = input('');
  readonly tooltipDelay = input(500);
  readonly testId = input('');

  readonly actionClick = output<void>();
  readonly comingSoonClick = output<void>();

  onButtonClick() {
    if (this.action()?.isReady) {
      this.actionClick.emit();
    } else {
      this.comingSoonClick.emit();
    }
  }
}
