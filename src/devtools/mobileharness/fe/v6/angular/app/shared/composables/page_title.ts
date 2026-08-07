import {DestroyRef, EffectRef, Signal, effect, inject} from '@angular/core';
import {Title} from '@angular/platform-browser';

/** Default browser page title used across the application. */
export const DEFAULT_PAGE_TITLE = 'OmniLab Console';

/**
 * Composable that updates the browser page title dynamically from a Signal.
 * Automatically restores the default page title when the component is destroyed.
 */
export function usePageTitle(
  titleSignal: Signal<string | null | undefined>,
  defaultTitle = DEFAULT_PAGE_TITLE,
): EffectRef {
  const titleService = inject(Title);
  const destroyRef = inject(DestroyRef);

  destroyRef.onDestroy(() => {
    titleService.setTitle(defaultTitle);
  });

  return effect(() => {
    const title = titleSignal();
    titleService.setTitle(title ? title : defaultTitle);
  });
}
