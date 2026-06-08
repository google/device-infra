import {CommonModule} from '@angular/common';
import {Component, EventEmitter, Input, Output} from '@angular/core';
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
  @Input() action?: ActionButtonState;
  @Input({required: true}) label!: string;
  @Input({required: true}) icon!: string;
  @Input() mode: 'button' | 'menu-item' | 'operation' = 'button';
  @Input() loading = false;
  @Input() loadingLabel?: string;
  @Input() disabled = false;
  @Input() customClass = '';
  @Input() tooltipDelay = 500;

  @Output() readonly actionClick = new EventEmitter<void>();
  @Output() readonly comingSoonClick = new EventEmitter<void>();

  onButtonClick() {
    if (this.action?.isReady) {
      this.actionClick.emit();
    } else {
      this.comingSoonClick.emit();
    }
  }
}
