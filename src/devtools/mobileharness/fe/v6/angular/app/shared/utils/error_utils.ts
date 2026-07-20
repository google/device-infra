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
