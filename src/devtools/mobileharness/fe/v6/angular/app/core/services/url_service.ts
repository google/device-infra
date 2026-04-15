import {DOCUMENT} from '@angular/common';
import {Injectable, OnDestroy, inject} from '@angular/core';
import {Observable, Subject, throwError} from 'rxjs';
import {filter, map, take, tap, timeout} from 'rxjs/operators';

/**
 * Interface for the message sent to the parent window.
 */
export interface ExternalUrlRequest {
  type: 'GET_EXTERNAL_URL';
  requestId: string;
  page: string;
  params: Record<string, string>;
}

/**
 * Interface for the message received from the parent window.
 */
export interface ExternalUrlResponse {
  type: 'GET_EXTERNAL_URL_RESPONSE';
  requestId: string;
  url: string;
}

/**
 * Interface for the message sent to the parent window when a navigation
 * occurs in the iframe.
 */
export interface NavigatedMessage {
  type: 'NAVIGATED';
  page: 'host_details' | 'device_details';
  params: Record<string, string>;
}

/**
 * Service to handle URL generation and cross-window communication for navigation.
 */
@Injectable({providedIn: 'root'})
export class UrlService implements OnDestroy {
  private readonly isEmbeddedMode: boolean;
  private readonly responses$ = new Subject<ExternalUrlResponse>();
  private readonly win: Window | null;
  private readonly messageListener = (event: MessageEvent) => {
    const data = event.data;
    // Only handle responses that match our external URL response type.
    if (data && data.type === 'GET_EXTERNAL_URL_RESPONSE') {
      this.responses$.next(data);
    }
  };

  private readonly document = inject(DOCUMENT, {optional: true});

  constructor() {
    // Initialise the window object from the injected document (to support SSR/Testing).
    this.win =
      this.document?.defaultView ??
      (typeof window !== 'undefined' ? window : null);

    // Initialise the embedded mode flag by checking the URL query parameter
    // AND verifying that the app is actually running inside an iframe.
    // This prevents false positives if the param is set but the app is standalone.
    const search = this.win?.location?.search ?? '';
    const params = new URLSearchParams(search);
    const isEmbeddedParam = params.get('is_embedded_mode') === 'true';
    const isInsideIframe = this.win && this.win.self !== this.win.top;

    this.isEmbeddedMode = isEmbeddedParam && !!isInsideIframe;

    // Listen for messages from the parent window only if we are in embedded mode
    // and confirmed to be in an iframe.
    if (this.isEmbeddedMode && this.win) {
      this.win.addEventListener('message', this.messageListener);
    }
  }

  ngOnDestroy() {
    if (this.win) {
      this.win.removeEventListener('message', this.messageListener);
    }
    this.responses$.complete();
  }

  /**
   * Sends a request to the parent window to get an external URL for a page.
   *
   * @param page The identifier of the page (e.g., 'host_details', 'device_details').
   * @param params Parameters required for constructing the URL.
   * @return An observable emitting the URL string.
   */
  getExternalUrl(
    page: string,
    params: Record<string, string>,
  ): Observable<string> {
    if (!this.isEmbeddedMode || !this.win) {
      console.log(
        `Not in embedded mode, the host window is NOT available! isEmbeddedMode: ${this.isEmbeddedMode}, win: ${this.win}`,
      );
      return throwError(
        () =>
          new Error('Not in embedded mode, the host window is NOT available!'),
      );
    }

    // Generate a unique request ID to match the response with this specific request.
    const requestId = Math.random().toString(36).substring(2);
    const request: ExternalUrlRequest = {
      type: 'GET_EXTERNAL_URL',
      requestId,
      page,
      params,
    };

    // Sends a postMessage to the parent window to request the true external URL.
    // We use the 'origin' query parameter to ensure the message is only received
    // by the intended parent application.
    const urlParams = new URLSearchParams(this.win.location.search);
    const origin = urlParams.get('origin') || '*';
    this.win.parent.postMessage(request, origin);

    // Listens for a response from the parent window that matches our requestId.
    // We include a 5-second timeout to handle cases where the parent
    // doesn't respond (e.g., if the parent doesn't support the message protocol).
    return this.responses$.pipe(
      filter((res) => res.requestId === requestId),
      map((res) => res.url),
      tap((url) => {
        console.log('received external URL from parent window:', url);
      }),
      take(1),
      timeout(5000),
    );
  }

  /** Returns whether the application is running in embedded mode. */
  isInEmbeddedMode(): boolean {
    return this.isEmbeddedMode;
  }

  /**
   * Notifies the parent window that a navigation has occurred in the iframe.
   *
   * @param page The identifier of the page (e.g., 'host_details', 'device_details').
   * @param params Parameters for the current page.
   */
  notifyNavigated(
    page: 'host_details' | 'device_details',
    params: Record<string, string>,
  ) {
    if (!this.isEmbeddedMode || !this.win) {
      return;
    }

    const message: NavigatedMessage = {
      type: 'NAVIGATED',
      page,
      params,
    };

    const urlParams = new URLSearchParams(this.win.location.search);
    const origin = urlParams.get('origin') || '*';
    this.win.parent.postMessage(message, origin);
  }
}
