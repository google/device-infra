import {SecurityContext} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {DomSanitizer} from '@angular/platform-browser';

import {SafeHtmlPipe} from './safe_html_pipe';

describe('SafeHtmlPipe Pipe', () => {
  let pipe: SafeHtmlPipe;
  let sanitizer: DomSanitizer;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [SafeHtmlPipe],
    });
    pipe = TestBed.inject(SafeHtmlPipe);
    sanitizer = TestBed.inject(DomSanitizer);
  });

  it('should create an instance', () => {
    expect(pipe).toBeTruthy();
  });

  it('should sanitize HTML content', () => {
    const unsafeHtml = '<script>alert("XSS")</script><b>Safe</b>';

    // Spy on the real sanitizer to check how it's called
    spyOn(sanitizer, 'sanitize').and.callThrough();

    const result = pipe.transform(unsafeHtml);

    expect(sanitizer.sanitize)
        .toHaveBeenCalledWith(SecurityContext.HTML, unsafeHtml);
    // Angular's sanitizer will strip the script tag
    expect(result).toEqual('<b>Safe</b>');
  });

  it('should return null for null input', () => {
    expect(pipe.transform(null)).toBeNull();
  });

  it('should return empty string for empty string input', () => {
    const result = pipe.transform('');
    expect(result).toEqual('');
  });
});
