import {CdkOverlayOrigin, OverlayModule} from '@angular/cdk/overlay';
import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  EventEmitter,
  Input,
  Output,
  TemplateRef,
} from '@angular/core';

/**
 * Component to display a list of items with a limit and a "more" button for overflow.
 */
@Component({
  selector: 'app-overflow-list',
  standalone: true,
  imports: [CommonModule, OverlayModule],
  templateUrl: './overflow_list.ng.html',
  styleUrl: './overflow_list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OverflowList<T> {
  @Input({required: true}) items: T[] = [];
  @Input() limit = 1;
  @Input({required: true}) itemTemplate!: TemplateRef<unknown>;
  @Input() moreButtonClass = '';

  @Output() readonly showOverlay = new EventEmitter<CdkOverlayOrigin>();
  @Output() readonly hideOverlay = new EventEmitter<MouseEvent>();

  readonly itemsSignal = computed(() => this.items || []);

  readonly displayedItems = computed(() => {
    return this.itemsSignal().slice(0, this.limit);
  });

  readonly remainingCount = computed(() => {
    return Math.max(0, this.itemsSignal().length - this.limit);
  });

  onMoreHover(trigger: CdkOverlayOrigin) {
    this.showOverlay.emit(trigger);
  }

  onMoreLeave(event: MouseEvent) {
    this.hideOverlay.emit(event);
  }
}
