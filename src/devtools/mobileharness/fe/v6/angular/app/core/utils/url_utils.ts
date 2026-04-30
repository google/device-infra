import {ActivatedRoute, Router} from '@angular/router';

/**
 * Performs client-side navigation with merged query parameters.
 *
 * @param url The target URL.
 * @param router The Angular Router.
 * @param route The active route to get current query params from.
 */
export function navigateWithPreservedParams(
  url: string,
  router: Router,
  route: ActivatedRoute,
) {
  // Parse the target URL.
  const newUrl = new URL(url, window.location.origin);
  // Get current query parameters from the active route.
  const currentParams = route.snapshot.queryParamMap;
  // Merge the current query parameters into the new URL if they are not already present.
  // Note: The new URL parameters have high priority and will NOT be overridden by current parameters.
  // This ensures parameters like `is_embedded_mode` are preserved across navigations if not specified in new URL.
  for (const key of currentParams.keys) {
    if (!newUrl.searchParams.has(key)) {
      const values = currentParams.getAll(key);
      for (const value of values) {
        newUrl.searchParams.append(key, value);
      }
    }
  }
  // Perform client-side navigation with the merged URL.
  router.navigateByUrl(newUrl.pathname + newUrl.search);
}
