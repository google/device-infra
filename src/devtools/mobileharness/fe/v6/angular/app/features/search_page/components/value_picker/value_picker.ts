import {CommonModule} from '@angular/common';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  linkedSignal,
  output,
  signal,
  viewChild,
} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';

/** Represents a selectable item in the value picker. */
export interface PickerValueItem {
  value: string;
  displayLabel: string;
  filtered?: number;
  total?: number;
  isNoValue?: boolean;
  disabled?: boolean;
}

/** Payload emitted when applying value picker changes. */
export interface ValuePickerApplyEvent {
  selected: string[];
  negate: boolean;
  isAdvanced: boolean;
  advMode?: string;
  advText?: string;
  advValues?: string[];
  tjsRangeFrom?: string;
  tjsRangeTo?: string;
  tjsPropName?: string;
  tjsTextVal?: string;
}

/** List of configurations for advanced matching options. */
export const ADV_MODES_LIST = [
  {id: 'prefix', label: 'Starts with', placeholder: 'Enter a prefix…'},
  {id: 'substring', label: 'Contains', placeholder: 'Enter text…'},
  {id: 'not_substring', label: 'Does not contain', placeholder: 'Enter text…'},
  {id: 'regex', label: 'Matches regex', placeholder: 'Enter a pattern…'},
  {
    id: 'not_regex',
    label: 'Does not match regex',
    placeholder: 'Enter a pattern…',
  },
  {id: 'exactly', label: 'Is exactly', placeholder: 'Add a value…'},
  {id: 'at_least', label: 'Is at least', placeholder: 'Add a value…'},
];

/** Value picker overlay component providing selection and input forms for filters. */
@Component({
  selector: 'app-value-picker',
  standalone: true,
  templateUrl: './value_picker.ng.html',
  styleUrl: './value_picker.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, MatButtonModule, MatIconModule],
  host: {
    '(document:mousedown)': 'onDocumentMousedown($event)',
    '(document:keydown.escape)': 'onEscape()',
  },
})
export class ValuePicker implements AfterViewInit {
  readonly key = input.required<string>();
  readonly title = input<string>('');
  readonly pos = input<{top: number; left: number}>({top: 0, left: 0});
  readonly canUseAdvanced = input<boolean>(false);
  readonly isPlural = input<boolean>(false);
  readonly loading = input<boolean>(false);
  readonly values = input<PickerValueItem[]>([]);
  readonly selectedValues = input<Set<string>>(new Set());
  readonly negated = input<boolean>(false);
  readonly valuesType = input<'counted' | 'plain'>('counted');

  readonly initialIsAdvanced = input<boolean>(false);
  readonly initialAdvMode = input<string>('prefix');
  readonly initialAdvText = input<string>('');
  readonly initialAdvValues = input<string[]>([]);

  readonly tjsType = input<'simple' | 'enum' | 'range' | 'namedPair' | 'text'>(
    'simple',
  );
  readonly needsName = input<boolean>(false);
  readonly namePlaceholder = input<string>('Name');
  readonly valPlaceholder = input<string>('Value');
  readonly isTjs = input<boolean>(false);

  readonly apply = output<ValuePickerApplyEvent>();
  readonly cancel = output<void>();

  readonly searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  readonly selectedSet = linkedSignal(() => new Set(this.selectedValues()));
  readonly stagedCustomInputs = linkedSignal(() => {
    this.selectedValues();
    return new Set<string>();
  });
  readonly pickerNegate = linkedSignal(() => this.negated());
  readonly showPolarityMenu = linkedSignal(() => {
    this.key();
    return false;
  });
  readonly showOverflowMenu = linkedSignal(() => {
    this.key();
    return false;
  });
  readonly pickerSearchQuery = linkedSignal(() => {
    this.key();
    return '';
  });
  readonly pickerSortBy = signal<'value' | 'filtered' | 'total'>('filtered');
  readonly pickerSortAsc = signal<boolean>(false);

  readonly pickerIsAdvanced = linkedSignal(() => this.initialIsAdvanced());
  readonly advMode = linkedSignal(() => this.initialAdvMode() || 'prefix');
  readonly advText = linkedSignal(() => this.initialAdvText() || '');
  readonly advValues = linkedSignal(() => this.initialAdvValues() || []);
  readonly advInputVal = linkedSignal(() => {
    this.key();
    return '';
  });

  readonly rangeFrom = linkedSignal(() => {
    const selected = Array.from(this.selectedValues());
    const t = selected[0] || '';
    const m = t.match(/From:\s*([^\s]+)\s*To:\s*([^\s]+)/);
    if (m) return m[1];
    const now = Date.now();
    return this.toDateTimeLocal(now - 86400000);
  });

  readonly rangeTo = linkedSignal(() => {
    const selected = Array.from(this.selectedValues());
    const t = selected[0] || '';
    const m = t.match(/From:\s*([^\s]+)\s*To:\s*([^\s]+)/);
    if (m) return m[2];
    const now = Date.now();
    return this.toDateTimeLocal(now);
  });

  readonly propName = linkedSignal(() => {
    const vals = Array.from(this.selectedValues());
    if (this.needsName() && vals.length >= 2) {
      return vals[0] || '';
    }
    return this.title() || '';
  });
  readonly propVal = linkedSignal(() => {
    const vals = Array.from(this.selectedValues());
    if (this.needsName() && vals.length >= 2) {
      return vals[1] || '';
    }
    return vals.join(', ');
  });
  readonly textValue = linkedSignal(() =>
    Array.from(this.selectedValues()).join(', '),
  );

  private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);

  private toDateTimeLocal(ms: number): string {
    const d = new Date(ms);
    const pad = (n: number) => n.toString().padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  readonly advModesList = ADV_MODES_LIST;

  readonly posVerb = computed(() => (this.isPlural() ? 'are' : 'is'));
  readonly negVerb = computed(() => (this.isPlural() ? 'are not' : 'is not'));

  readonly advPlaceholder = computed(() => {
    const modeObj = this.advModesList.find((m) => m.id === this.advMode());
    return modeObj?.placeholder || 'Enter value…';
  });

  readonly isMultiAdvMode = computed(() => {
    const m = this.advMode();
    return m === 'exactly' || m === 'at_least';
  });

  readonly isPlain = computed(() => {
    if (this.valuesType() === 'plain') return true;
    const list = this.values();
    if (list.length === 0) return false;
    return list.every((v) => v.filtered === undefined && v.total === undefined);
  });

  readonly showAddRow = computed(() => {
    if (!this.loading()) return false;
    const q = this.pickerSearchQuery().trim();
    if (!q) return false;
    const qLower = q.toLowerCase();

    for (const s of this.selectedSet()) {
      if (s.toLowerCase() === qLower) return false;
    }
    for (const v of this.values()) {
      if (
        v.value.toLowerCase() === qLower ||
        v.displayLabel.toLowerCase() === qLower
      ) {
        return false;
      }
    }
    return true;
  });

  addCustomInput(rawQuery: string) {
    const q = rawQuery.trim();
    if (!q) return;

    const currentSelected = new Set(this.selectedSet());
    currentSelected.add(q);
    this.selectedSet.set(currentSelected);

    const currentStaged = new Set(this.stagedCustomInputs());
    currentStaged.add(q);
    this.stagedCustomInputs.set(currentStaged);

    this.pickerSearchQuery.set('');
  }

  readonly displayValues = computed(() => {
    const baseValues = this.values();
    const staged = this.stagedCustomInputs();
    const loading = this.loading();

    if (loading || staged.size === 0) {
      return baseValues;
    }

    const baseValuesLower = new Set(
      baseValues.flatMap((v) => [
        v.value.toLowerCase(),
        v.displayLabel.toLowerCase(),
      ]),
    );

    const missingStaged: PickerValueItem[] = [];
    for (const customVal of staged) {
      if (!baseValuesLower.has(customVal.toLowerCase())) {
        missingStaged.push({
          value: customVal,
          displayLabel: customVal,
          disabled: true,
        });
      }
    }

    if (missingStaged.length === 0) {
      return baseValues;
    }

    return [...baseValues, ...missingStaged];
  });

  readonly filteredValues = computed(() => {
    const query = this.pickerSearchQuery().toLowerCase().trim();
    let list = this.displayValues();
    if (query) {
      list = list.filter(
        (v) =>
          v.value.toLowerCase().includes(query) ||
          v.displayLabel.toLowerCase().includes(query),
      );
    }

    const sortCol = this.pickerSortBy();
    const asc = this.pickerSortAsc();

    const normalItems = list.filter((v) => !v.disabled);
    const disabledItems = list.filter((v) => v.disabled);

    const sortedNormal = [...normalItems].sort((a, b) => {
      if (sortCol === 'value') {
        const lblA = a.displayLabel.toLowerCase();
        const lblB = b.displayLabel.toLowerCase();
        const res = lblA < lblB ? -1 : lblA > lblB ? 1 : 0;
        return asc ? res : -res;
      } else if (sortCol === 'filtered') {
        const valA = a.filtered ?? 0;
        const valB = b.filtered ?? 0;
        return asc ? valA - valB : valB - valA;
      } else {
        const valA = a.total ?? 0;
        const valB = b.total ?? 0;
        return asc ? valA - valB : valB - valA;
      }
    });

    return [...sortedNormal, ...disabledItems].slice(0, 100);
  });

  readonly PINNED_THRESHOLD = 20;

  readonly showPinned = computed(() => {
    const hasSearch = this.pickerSearchQuery().trim().length > 0;
    const isLoading = this.loading();
    const totalCount = this.displayValues().length;
    const selectedCount = this.selectedSet().size;

    return (
      !hasSearch &&
      !isLoading &&
      totalCount > this.PINNED_THRESHOLD &&
      selectedCount > 0
    );
  });

  readonly pinnedValues = computed<PickerValueItem[]>(() => {
    if (!this.showPinned()) return [];
    const selected = this.selectedSet();
    const all = this.displayValues();

    const pinned = all.filter((v) => selected.has(v.value));
    return pinned.sort((a, b) => a.displayLabel.localeCompare(b.displayLabel));
  });

  readonly footerStatusText = computed(() => {
    if (this.pickerIsAdvanced()) {
      if (this.isMultiAdvMode()) {
        const n = this.advValues().length;
        return n === 0
          ? 'No values added'
          : `${n} value${n > 1 ? 's' : ''} added`;
      } else {
        return this.advText().trim() ? '1 value added' : 'No values added';
      }
    }
    const n = this.selectedSet().size;
    const verb = this.pickerNegate() ? 'excluded' : 'selected';
    return n === 0
      ? `No options ${verb}`
      : `${n} option${n > 1 ? 's' : ''} ${verb}`;
  });

  onDocumentMousedown(event: MouseEvent) {
    const target = event.target as HTMLElement;
    if (this.elementRef.nativeElement.contains(target)) {
      if (this.showPolarityMenu()) {
        const wrap =
          this.elementRef.nativeElement.querySelector('.vp-polarity-wrap');
        if (wrap && !wrap.contains(target)) {
          this.showPolarityMenu.set(false);
        }
      }
      if (this.showOverflowMenu()) {
        const wrap =
          this.elementRef.nativeElement.querySelector('.vp-overflow-wrap');
        if (wrap && !wrap.contains(target)) {
          this.showOverflowMenu.set(false);
        }
      }
      return;
    }

    if (
      target.closest('.search-chip') ||
      target.closest('.preset-link') ||
      target.closest('.chip-label')
    ) {
      return;
    }

    this.cancel.emit();
  }

  onEscape() {
    this.cancel.emit();
  }

  toggleSort(col: 'value' | 'filtered' | 'total') {
    if (this.pickerSortBy() === col) {
      this.pickerSortAsc.set(!this.pickerSortAsc());
    } else {
      this.pickerSortBy.set(col);
      this.pickerSortAsc.set(col === 'value');
    }
  }

  toggleValue(item: PickerValueItem) {
    if (item.disabled) return;
    const val = item.value;
    const current = new Set(this.selectedSet());
    if (current.has(val)) {
      current.delete(val);
    } else {
      current.add(val);
    }
    this.selectedSet.set(current);
  }

  selectOnlyValue(val: string) {
    this.selectedSet.set(new Set([val]));
  }

  copyValue(val: string) {
    if (val) {
      navigator.clipboard.writeText(val);
    }
  }

  clearAllValues() {
    this.selectedSet.set(new Set());
  }

  ngAfterViewInit() {
    this.focusSearchInput();
  }

  focusSearchInput() {
    setTimeout(() => {
      this.searchInput()?.nativeElement.focus();
    }, 30);
  }

  showAdvancedMatching() {
    this.pickerIsAdvanced.set(true);
    this.showOverflowMenu.set(false);
  }

  showSimpleMatching() {
    this.pickerIsAdvanced.set(false);
    this.focusSearchInput();
  }

  addAdvValue() {
    const val = this.advInputVal().trim();
    if (!val) return;
    const current = this.advValues();
    if (!current.some((v) => v.toLowerCase() === val.toLowerCase())) {
      this.advValues.set([...current, val]);
    }
    this.advInputVal.set('');
  }

  removeAdvValue(index: number) {
    const current = [...this.advValues()];
    current.splice(index, 1);
    this.advValues.set(current);
  }

  onSearchEnter() {
    const query = this.pickerSearchQuery().trim();
    if (query) {
      this.addCustomInput(query);
    } else {
      this.onApply();
    }
  }

  onApply() {
    const type = this.tjsType();
    if (type === 'range') {
      const fromVal = this.rangeFrom().trim();
      const toVal = this.rangeTo().trim();
      this.apply.emit({
        selected: [`From: ${fromVal} To: ${toVal}`],
        negate: false,
        isAdvanced: false,
        tjsRangeFrom: fromVal,
        tjsRangeTo: toVal,
      });
      return;
    }

    if (type === 'namedPair') {
      const name = this.propName().trim();
      const val = this.propVal().trim();
      this.apply.emit({
        selected: [val],
        negate: false,
        isAdvanced: false,
        tjsPropName: name,
        tjsTextVal: val,
      });
      return;
    }

    if (type === 'text' || (this.isTjs() && type === 'simple')) {
      const val = this.textValue().trim();
      this.apply.emit({
        selected: [val],
        negate: false,
        isAdvanced: false,
        tjsTextVal: val,
      });
      return;
    }

    const selectedList = Array.from(this.selectedSet());
    const pendingQuery = this.pickerSearchQuery().trim();
    if (
      pendingQuery &&
      !selectedList.includes(pendingQuery) &&
      !this.pickerIsAdvanced()
    ) {
      selectedList.push(pendingQuery);
    }
    this.apply.emit({
      selected: selectedList,
      negate: this.pickerNegate(),
      isAdvanced: this.pickerIsAdvanced(),
      advMode: this.advMode(),
      advText: this.advText().trim(),
      advValues: this.advValues(),
    });
  }

  onCancel() {
    this.cancel.emit();
  }
}
