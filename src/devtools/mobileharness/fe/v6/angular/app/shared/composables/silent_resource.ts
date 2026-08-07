import {inject, ResourceRef, signal} from '@angular/core';
import {rxResource} from '@angular/core/rxjs-interop';
import {Observable} from 'rxjs';
import {finalize, tap} from 'rxjs/operators';
import {LoadingService} from '../services/loading_service';

/** Options for useSilentResource composable. */
export interface SilentResourceOptions<T, P> {
  params: () => P;
  stream: (params: P) => Observable<T>;
  onInitialLoad?: (data: T) => void;
}

/**
 * Composable that wraps `rxResource` with automatic loading overlay management and
 * silent background reloading support.
 *
 * - Shows `LoadingService` overlay ONLY during the initial page load.
 * - Suppresses `LoadingService` overlay during background silent reloads.
 * - Executes `onInitialLoad` callback once upon initial successful data fetch.
 */
export function useSilentResource<T, P>(
  options: SilentResourceOptions<T, P>,
): {
  resource: ResourceRef<T | undefined>;
  reloadSilent: () => void;
} {
  const isInitial = signal<boolean>(true);
  const loadingService = inject(LoadingService);

  const resource = rxResource<T, P>({
    params: options.params,
    stream: ({params}) => {
      if (isInitial()) {
        loadingService.show();
      }
      return options.stream(params).pipe(
        tap((data) => {
          if (isInitial() && data) {
            options.onInitialLoad?.(data);
            isInitial.set(false);
          }
        }),
        finalize(() => {
          loadingService.hide();
        }),
      );
    },
  });

  return {
    resource,
    reloadSilent: () => resource.reload(),
  };
}
