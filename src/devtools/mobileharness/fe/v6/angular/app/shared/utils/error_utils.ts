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
export function getErrorMessage(
  err: unknown,
  fallbackMessage = 'An unknown error occurred.',
): string {
  if (err == null) {
    return fallbackMessage;
  }

  const rawErr = err as {
    error?: {error?: {message?: unknown}; message?: unknown} | null;
    cause?: unknown;
    message?: unknown;
  };
  // Angular 20's rxResource/resource wraps non-Error objects (such as
  // HttpErrorResponse) in an Error instance with the original error in `.cause`.
  const httpErr =
    rawErr.error == null &&
    rawErr.cause != null &&
    typeof rawErr.cause === 'object'
      ? (rawErr.cause as typeof rawErr)
      : rawErr;

  const candidates = [
    httpErr.error?.error?.message,
    httpErr.error?.message,
    httpErr.message,
  ];
  for (const msg of candidates) {
    if (typeof msg === 'string' && msg) return msg;
  }

  return fallbackMessage;
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
