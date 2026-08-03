import {openInNewTab} from './safe_dom';

/**
 * Extracts a human-readable error message from an HTTP error response.
 *
 * The backend can return error messages in different JSON structures depending
 * on the deployment:
 *
 * - Internal (ESF/One Platform): `{"error": {"code": 404, "message": "..."}}`
 *   → Angular parses the body into `err.error`, so the message is at
 *   `err.error.error.message`.
 *
 * - OSS (Envoy grpc_json_transcoder with convert_grpc_status):
 *   `{"code": 5, "message": "..."}` → the message is at `err.error.message`.
 *
 * Falls back to Angular's auto-generated `err.message` (e.g.
 * "Http failure response for URL: 404 Not Found") if neither structured field
 * is available (e.g. when the server returns no body).
 */
export function getErrorMessage(err: unknown): string {
  if (err != null && typeof err === 'object') {
    const httpErr = err as Record<string, unknown>;

    // Try the structured backend message first.
    const body = httpErr['error'];
    if (body != null && typeof body === 'object') {
      const bodyObj = body as Record<string, unknown>;

      // Internal (ESF): err.error = {"error": {"message": "..."}}
      const inner = bodyObj['error'];
      if (inner != null && typeof inner === 'object') {
        const msg = (inner as Record<string, unknown>)['message'];
        if (typeof msg === 'string' && msg) return msg;
      }

      // OSS (Envoy): err.error = {"message": "..."}
      const directMsg = bodyObj['message'];
      if (typeof directMsg === 'string' && directMsg) return directMsg;
    }

    // Fallback: Angular's auto-generated message.
    const fallback = httpErr['message'];
    if (typeof fallback === 'string' && fallback) return fallback;
  }

  return 'An unknown error occurred.';
}

/**
 * Checks if the error represents a "Not Found" error (HTTP 404 or gRPC Code 5).
 */
export function isNotFoundError(err: unknown): boolean {
  if (err != null && typeof err === 'object') {
    const httpErr = err as Record<string, unknown>;

    // Check standard Angular HttpErrorResponse status
    if (httpErr['status'] === 404) {
      return true;
    }

    // Try the structured backend message body
    const body = httpErr['error'];
    if (body != null && typeof body === 'object') {
      const bodyObj = body as Record<string, unknown>;

      // Internal (ESF): err.error = {"error": {"code": 404}}
      const inner = bodyObj['error'];
      if (inner != null && typeof inner === 'object') {
        const code = (inner as Record<string, unknown>)['code'];
        if (code === 404) return true;
      }

      // OSS (Envoy): err.error = {"code": 5}
      const directCode = bodyObj['code'];
      if (directCode === 5) return true;
    }
  }

  return false;
}

/**
 * Formats error details into a string, handling different types of error objects.
 */
export function formatErrorDetails(err: unknown): string {
  if (!err) {
    return '';
  }
  if (err instanceof Error && err.stack) {
    return err.stack;
  }
  if (typeof err === 'object') {
    const errObj = err as Record<string, unknown>;
    if ('error' in errObj && errObj['error']) {
      return typeof errObj['error'] === 'object'
        ? safeJsonStringify(errObj['error'])
        : String(errObj['error']);
    }
    return safeJsonStringify(err);
  }
  return String(err);
}

/**
 * Safely stringifies an object to JSON, falling back to String() on error.
 */
export function safeJsonStringify(obj: unknown): string {
  try {
    return JSON.stringify(obj, null, 2);
  } catch (e) {
    return String(obj);
  }
}

/**
 * Opens a new tab to report a bug to Buganizer with the given error details.
 */
export function reportBug(
  errorTitle: string,
  errorMessage: string,
  errorDetails: string,
  copyHint = 'please use "Copy Error" button to get full details',
) {
  const maxDetailsLength = 1000;
  let detailsForBug = errorDetails;

  if (detailsForBug.length > maxDetailsLength) {
    detailsForBug =
      detailsForBug.substring(0, maxDetailsLength) +
      `\n\n... (details truncated, ${copyHint})`;
  }

  const title = encodeURIComponent(`[MHFE] ${errorTitle}: ${errorMessage}`);
  const body = encodeURIComponent(
    `Action failed.\n\nError: ${errorMessage}\n\nDetails:\n${detailsForBug}`,
  );

  const url = `https://issuetracker.google.com/issues/new?component=94628&title=${title}&description=${body}`;
  openInNewTab(url);
}
