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
  // CLIENT-SIDE ROUTER TRANSITION SANITIZATION:
  // Remove 'universe' when navigating to test or job details pages.
  // This prevents propagating key parameters like 'universe=google_1p' from monitoring pages
  // (like host/device details where universe is valid) to test/job details pages where they're invalid.
  // Note: This acts on internal application nav link clicks, while stripUniverseGuard sanitizes
  // external entrypoints, and universeInterceptor manages the HTTP request layer.
  const isJobOrTest = /\/(jobs|tests)\//.test(newUrl.pathname);
  if (isJobOrTest) {
    newUrl.searchParams.delete('universe');
  }

  // Merge the current query parameters into the new URL if they are not already present.
  // Note: The new URL parameters have high priority and will NOT be overridden by current parameters.
  // This ensures parameters like `is_embedded_mode` are preserved across navigations if not specified in new URL.
  for (const key of currentParams.keys) {
    if (key === 'universe' && isJobOrTest) {
      continue;
    }
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
