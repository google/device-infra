import {Directive, ElementRef, inject} from '@angular/core';
import {MatTooltip} from '@angular/material/tooltip';

/**
 * Conditionally disables and enables mat tooltip if the element is truncated.
 */
@Directive({
  selector: '[matTooltip][tooltipIfTruncated]',
  standalone: true,
  host: {
    '(mouseenter)': 'checkTruncation()',
    '(focusin)': 'checkTruncation()',
  },
})
export class TooltipIfTruncatedDirective {
  private readonly matTooltip = inject(MatTooltip);
  private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);

  checkTruncation() {
    const element = this.elementRef.nativeElement;
    const isDisabled = Boolean(
      element.clientWidth >= element.scrollWidth &&
        element.clientHeight >= element.scrollHeight,
    );
    this.matTooltip.disabled = isDisabled;
  }
}
