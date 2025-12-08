
/**
 * Creates a safe object URL from a Blob, using the appropriate API for the environment.
 */
export function createSafeObjectURL(blob: Blob): string {
  return URL.createObjectURL(blob);
}

/**
 * Opens a URL in a new tab safely, using the appropriate API for the environment.
 */
export function openInNewTab(url: string | URL) {
  const urlString = url.toString();
  window.open(urlString, '_blank');
}

/**
 * Sets the href of an anchor element safely, using the appropriate API for the environment.
 */
export function setSafeHref(anchor: HTMLAnchorElement, url: string | URL) {
  anchor.href = url.toString();
}

/**
 * Revokes an object URL.
 */
export function revokeObjectURL(url: string) {
  URL.revokeObjectURL(url);
}
