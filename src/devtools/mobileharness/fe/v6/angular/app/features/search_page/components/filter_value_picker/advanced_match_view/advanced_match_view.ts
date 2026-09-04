import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  model,
  output,
  signal,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatIconModule} from '@angular/material/icon';

import {ADV_MODES_LIST, AdvancedMatchMode} from '../../../models';

/** Standalone sub-view handling prefix, regex, substring, and multi-chip matching modes. */
@Component({
  selector: 'app-advanced-match-view',
  standalone: true,
  templateUrl: './advanced_match_view.ng.html',
  styleUrl: './advanced_match_view.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, MatIconModule],
})
export class AdvancedMatchView {
  readonly mode = model.required<AdvancedMatchMode>();
  readonly text = model.required<string>();
  readonly values = model.required<string[]>();

  readonly backToSimple = output<void>();
  readonly apply = output<void>();

  readonly inputVal = signal<string>('');
  readonly advModesList = ADV_MODES_LIST;

  readonly isMultiMode = computed(() => {
    const m = this.mode();
    return m === 'exactly' || m === 'at_least';
  });

  readonly placeholder = computed(() => {
    const currentMode = this.mode();
    const found = this.advModesList.find(
      (m: {id: AdvancedMatchMode; label: string; placeholder: string}) =>
        m.id === currentMode,
    );
    return found?.placeholder || 'Enter value…';
  });

  addChip() {
    const val = this.inputVal().trim();
    if (!val) return;
    this.values.update((vals) =>
      vals.some((v) => v.toLowerCase() === val.toLowerCase())
        ? vals
        : [...vals, val],
    );
    this.inputVal.set('');
  }

  removeChip(index: number) {
    this.values.update((vals) => vals.filter((_, i) => i !== index));
  }
}
