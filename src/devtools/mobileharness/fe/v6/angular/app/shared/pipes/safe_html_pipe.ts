import {inject, Pipe, PipeTransform, SecurityContext} from '@angular/core';
import {DomSanitizer} from '@angular/platform-browser';

/**
 * Pipe for sanitizing HTML.
 */
@Pipe({
  name: 'safeHtmlPipe',
  standalone: true,
})
export class SafeHtmlPipe implements PipeTransform {
  private readonly sanitizer = inject(DomSanitizer);

  transform(value: string|null|undefined): string|null {
    if (value === null || value === undefined) {
      return null;
    }
    const sanitizedHtml = this.sanitizer.sanitize(SecurityContext.HTML, value);
    return sanitizedHtml;
  }
}
