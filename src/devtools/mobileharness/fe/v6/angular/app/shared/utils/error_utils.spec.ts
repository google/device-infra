import {getErrorMessage} from './error_utils';

describe('getErrorMessage', () => {
  it('extracts message from internal (ESF) error shape', () => {
    const err = {
      error: {
        error: {code: 404, message: 'Host X not found.', status: 'NOT_FOUND'},
      },
      message: 'Http failure response for URL: 404 Not Found',
    };
    expect(getErrorMessage(err)).toBe('Host X not found.');
  });

  it('extracts message from OSS (Envoy) error shape', () => {
    const err = {
      error: {code: 5, message: 'Host X not found.'},
      message: 'Http failure response for URL: 404 Not Found',
    };
    expect(getErrorMessage(err)).toBe('Host X not found.');
  });

  it('falls back to Angular message when no structured body', () => {
    const err = {
      error: null,
      message: 'Http failure response for URL: 404 Not Found',
    };
    expect(getErrorMessage(err)).toBe(
      'Http failure response for URL: 404 Not Found',
    );
  });

  it('falls back to Angular message when body has no message field', () => {
    const err = {
      error: {code: 500},
      message: 'Http failure response for URL: 500 Internal Server Error',
    };
    expect(getErrorMessage(err)).toBe(
      'Http failure response for URL: 500 Internal Server Error',
    );
  });

  it('returns default for null input', () => {
    expect(getErrorMessage(null)).toBe('An unknown error occurred.');
  });

  it('returns default for undefined input', () => {
    expect(getErrorMessage(undefined)).toBe('An unknown error occurred.');
  });

  it('returns default for string input', () => {
    expect(getErrorMessage('some error')).toBe('An unknown error occurred.');
  });

  it('unwraps Angular rxResource Error.cause containing an HttpErrorResponse payload', () => {
    const wrapped = new Error(
      "Resource returned an error that's not an Error instance: [object Object]. Check this error's .cause for the actual error.",
    );
    (wrapped as Error & {cause?: unknown}).cause = {
      error: {error: {code: 503, message: 'Service down'}},
    };
    expect(getErrorMessage(wrapped)).toBe('Service down');
  });
});
