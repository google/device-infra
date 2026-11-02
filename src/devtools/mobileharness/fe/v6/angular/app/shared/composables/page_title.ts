import {
  DestroyRef,
  EffectRef,
  Injectable,
  Signal,
  effect,
  inject,
} from '@angular/core';
import {Title} from '@angular/platform-browser';
import {RouterStateSnapshot, TitleStrategy} from '@angular/router';

/**
 * Browser page title of a page that has no page-specific title. It is also the
 * product name that ends every other page title.
 */
export const DEFAULT_PAGE_TITLE = 'OmniLab Console';

/**
 * Formats a browser page title. The page-specific part comes first so that it
 * stays readable when the browser shortens the tab, and the product name comes
 * last. Without a page-specific part the title is the product name alone.
 */
export function formatPageTitle(pageTitle?: string | null): string {
  return pageTitle
    ? `${pageTitle} - ${DEFAULT_PAGE_TITLE}`
    : DEFAULT_PAGE_TITLE;
}

/**
 * Sets the browser page title from the `title` of the deepest primary route,
 * formatted by {@link formatPageTitle}.
 *
 * A route without a `title` belongs to a page that sets its own title, such as
 * a detail page whose title names the entity it shows, so the title is left as
 * that page sets it.
 */
@Injectable({providedIn: 'root'})
export class PageTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const pageTitle = this.buildTitle(snapshot);
    if (pageTitle !== undefined) {
      this.title.setTitle(formatPageTitle(pageTitle));
    }
  }
}

/**
 * Composable that keeps the browser page title in sync with a Signal holding
 * the page-specific part of the title, formatted by {@link formatPageTitle}.
 * Restores the product name alone when the component is destroyed.
 */
export function usePageTitle(
  pageTitle: Signal<string | null | undefined>,
): EffectRef {
  const titleService = inject(Title);
  const destroyRef = inject(DestroyRef);

  destroyRef.onDestroy(() => {
    titleService.setTitle(formatPageTitle());
  });

  return effect(() => {
    titleService.setTitle(formatPageTitle(pageTitle()));
  });
}
