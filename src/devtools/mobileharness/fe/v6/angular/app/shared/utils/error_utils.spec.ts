import {getErrorMessage, isNotFoundError} from './error_utils';

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
});

describe('isNotFoundError', () => {
  it('identifies internal (ESF) 404 error', () => {
    const err = {
      error: {
        error: {code: 404, message: 'Host X not found.', status: 'NOT_FOUND'},
      },
    };
    expect(isNotFoundError(err)).toBeTrue();
  });

  it('identifies OSS (Envoy) code 5 error', () => {
    const err = {
      error: {code: 5, message: 'Host X not found.'},
    };
    expect(isNotFoundError(err)).toBeTrue();
  });

  it('identifies root level status 404', () => {
    const err = {
      status: 404,
      message: 'Not Found',
    };
    expect(isNotFoundError(err)).toBeTrue();
  });

  it('returns false for other errors', () => {
    const err = {
      error: {code: 13, message: 'Internal error'},
    };
    expect(isNotFoundError(err)).toBeFalse();
  });

  it('returns false for other status codes', () => {
    const err = {
      status: 500,
    };
    expect(isNotFoundError(err)).toBeFalse();
  });

  it('returns false for null/undefined/string', () => {
    expect(isNotFoundError(null)).toBeFalse();
    expect(isNotFoundError(undefined)).toBeFalse();
    expect(isNotFoundError('not found')).toBeFalse();
  });
});
