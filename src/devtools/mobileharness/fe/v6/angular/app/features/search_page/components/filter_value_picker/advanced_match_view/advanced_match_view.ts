import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  model,
  output,
  signal,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatIconModule} from '@angular/material/icon';

import {
  ADV_MODES_LIST,
  AdvancedMatchMode,
  DEFAULT_INT_COMPARISON_OP,
  INT_COMPARISON_OPERATORS,
  IntComparisonOp,
} from '../../../models';
import {parseStrictInt32} from '../../../utils';

/** Standalone sub-view handling integer comparison, prefix, regex, substring, and multi-chip matching modes. */
@Component({
  selector: 'app-advanced-match-view',
  standalone: true,
  templateUrl: './advanced_match_view.ng.html',
  styleUrl: './advanced_match_view.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatIconModule],
})
export class AdvancedMatchView {
  readonly canCompareInt = input<boolean>(false);
  readonly mode = model.required<AdvancedMatchMode>();
  readonly text = model.required<string>();
  readonly values = model.required<string[]>();
  readonly intOp = model<IntComparisonOp>(DEFAULT_INT_COMPARISON_OP);

  readonly backToSimple = output<void>();
  readonly apply = output<void>();

  readonly inputVal = signal<string>('');
  readonly advModesList = ADV_MODES_LIST;
  readonly intOperators = INT_COMPARISON_OPERATORS;

  readonly visibleModes = computed(() =>
    this.advModesList.filter(
      (m) =>
        m.id !== 'compare_int' ||
        this.canCompareInt() ||
        this.mode() === 'compare_int',
    ),
  );

  readonly isCompareIntMode = computed(() => this.mode() === 'compare_int');

  readonly isInvalidInt = computed(
    () =>
      this.isCompareIntMode() &&
      this.text().trim().length > 0 &&
      parseStrictInt32(this.text()) === undefined,
  );

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

  selectIntOp(op: IntComparisonOp) {
    this.intOp.set(op);
  }

  selectMode(newMode: AdvancedMatchMode) {
    const wasMulti = this.isMultiMode();
    this.mode.set(newMode);
    if (wasMulti === this.isMultiMode()) return;

    if (wasMulti) {
      this.syncMultiToSingle();
    } else {
      this.syncSingleToMulti();
    }
  }

  private syncSingleToMulti() {
    const val = this.text().trim();
    if (val && this.values().length === 0) {
      this.values.set([val]);
    }
  }

  private syncMultiToSingle() {
    if (!this.text().trim() && this.values().length > 0) {
      this.text.set(this.values()[0]);
    }
  }

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
