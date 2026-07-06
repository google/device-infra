import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  signal,
  untracked,
} from '@angular/core';
import {MatIconModule} from '@angular/material/icon';

/**
 * A common, reusable accordion item component that displays an expandable/collapsible panel with a title and projected content.
 */
@Component({
  selector: 'app-accordion-item',
  standalone: true,
  templateUrl: './accordion_item.ng.html',
  styleUrl: './accordion_item.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatIconModule],
  host: {
    'class': 'accordion-item',
    '[class.expanded]': 'expanded()',
  },
})
export class AccordionItem {
  /** The title or header text displayed on the trigger button. */
  readonly title = input.required<string>();

  /** Whether the accordion item should be expanded by default on initialization or input change. */
  readonly defaultExpanded = input(false);

  /** An optional unique identifier prefix for ARIA controls linkage. */
  readonly customId = input<string>('');

  /** Internal signal managing the current expanded/collapsed state of this specific item. */
  readonly expanded = signal(false);

  /** Generated unique ID for the panel if customId is not provided. */
  private readonly generatedId = `accordion-panel-${Math.random().toString(36).substring(2, 9)}`;

  readonly effectivePanelId = computed(
    () => this.customId() || this.generatedId,
  );
  readonly effectiveTriggerId = computed(() =>
    this.customId()
      ? `${this.customId()}-trigger`
      : `trigger-${this.generatedId}`,
  );

  constructor() {
    effect(() => {
      const initial = this.defaultExpanded();
      untracked(() => {
        this.expanded.set(initial);
      });
    });
  }

  /** Toggles the expansion state of the accordion item. */
  toggle(): void {
    this.expanded.update((current) => !current);
  }
}
