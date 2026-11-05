import {DOCUMENT} from '@angular/common';
import {Injectable, OnDestroy, inject, signal} from '@angular/core';
import {Observable, Subject, throwError} from 'rxjs';
import {filter, map, take, tap, timeout} from 'rxjs/operators';
import {EnvUniverseService} from './env_universe_service';

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
  // no need to notify for other types of pages as of now, as NO
  // parent frame supports them.
  page: 'host_details' | 'device_details';
  params: Record<string, string>;
}

/**
 * Interface for the message sent to the parent window when search state changes.
 */
export interface SearchStateChangedMessage {
  type: 'SEARCH_STATE_CHANGED';
  params: Record<string, string | string[]>;
}

/**
 * Interface for the message received from the parent window to trigger navigation.
 */
export interface NavigateMessage {
  type: 'NAVIGATE';
  url: string;
}

/**
 * Geometry of the host page's header slot, relative to the iframe, in which
 * the embedded search input is rendered so that it appears inside the host
 * application's own header.
 */
export interface EmbeddedHeaderSlot {
  /** Left offset of the slot from the iframe's left edge, in px. */
  left: number;
  /** Width of the slot, in px. */
  width: number;
  /** Height of the host header band, in px. */
  height: number;
  /** CSS color of the host header's bottom border, if any. */
  borderColor?: string;
}

/**
 * Interface for the message received from the parent window describing the
 * header slot geometry.
 */
export interface EmbeddedHeaderSlotMessage extends EmbeddedHeaderSlot {
  type: 'EMBEDDED_HEADER_SLOT';
}

/**
 * Interface for the message sent to the parent window to request the header
 * slot geometry (e.g. right after bootstrap, before the parent had a chance to
 * send it).
 */
export interface EmbeddedHeaderSlotRequest {
  type: 'EMBEDDED_HEADER_SLOT_REQUEST';
}

/** Messages this app posts to the host window. */
type HostMessage =
  | ExternalUrlRequest
  | NavigatedMessage
  | SearchStateChangedMessage
  | EmbeddedHeaderSlotRequest;

/**
 * Service to handle URL generation and cross-window communication for navigation.
 */
@Injectable({providedIn: 'root'})
export class UrlService implements OnDestroy {
  private readonly isEmbeddedMode: boolean;
  private readonly envUniverseService = inject(EnvUniverseService);
  private readonly responses$ = new Subject<ExternalUrlResponse>();
  readonly navigate$ = new Subject<string>();
  /**
   * Latest header slot geometry received from the parent window, or null when
   * not embedded or not received yet.
   */
  readonly embeddedHeaderSlot = signal<EmbeddedHeaderSlot | null>(null);
  private readonly win: Window | null;
  private readonly messageListener = (event: MessageEvent) => {
    const data = event.data;
    if (data) {
      if (data.type === 'GET_EXTERNAL_URL_RESPONSE') {
        this.responses$.next(data);
      } else if (data.type === 'NAVIGATE') {
        this.navigate$.next(data.url);
      } else if (data.type === 'EMBEDDED_HEADER_SLOT') {
        this.onEmbeddedHeaderSlotMessage(data);
      } else {
        console.error('Unknown message type:', data.type, data);
      }
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
    this.navigate$.complete();
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
    this.postMessageToHost(request);

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
   * Returns whether the application is running in standalone mode (not embedded in an iframe).
   *
   * Input: None.
   * Output: boolean (true if standalone, false if embedded).
   * Explanation: Checks the internal `isEmbeddedMode` flag to determine if the
   * app is running independently or inside a parent frame.
   */
  isStandalone(): boolean {
    return !this.isEmbeddedMode;
  }

  /**
   * Notifies the parent window that a navigation has occurred in the iframe.
   *
   * @param page The identifier of the page (e.g., 'host_details', 'device_details').
   * @param params Parameters for the current page.
   *
   * Input:
   *   - page: 'host_details' | 'device_details' identifying the target page.
   *   - params: Record<string, string> containing route parameters.
   * Output: None.
   * Explanation: Sends a `NAVIGATED` message to the parent window via `postMessage`
   * to keep the parent application's URL or state synchronized with the iframe's
   * internal navigation. Only takes effect in embedded mode.
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

    this.postMessageToHost(message);
  }

  /**
   * Notifies the parent window that search state has changed.
   *
   * @param params The new search parameters.
   */
  notifySearchStateChanged(params: Record<string, string | string[]>) {
    if (!this.isEmbeddedMode || !this.win) {
      return;
    }

    const message: SearchStateChangedMessage = {
      type: 'SEARCH_STATE_CHANGED',
      params,
    };

    this.postMessageToHost(message);
  }

  /**
   * Returns whether the application is embedded in the ATS (OSS) host, i.e.
   * running in embedded mode in the OSS build. The Google-internal build also
   * has an embedded mode (e.g. Arsenal), which must not get the ATS-specific
   * host integration.
   */
  isInAtsEmbeddedMode(): boolean {
    return this.isEmbeddedMode && this.envUniverseService.isAts();
  }

  /**
   * Asks the host window for the geometry of its header slot.
   *
   * Explanation: When embedded in the ATS host (MTT UI2) the host lets the
   * iframe paint into a slot of its own header, where the docked search input
   * is rendered. The host answers with an `EMBEDDED_HEADER_SLOT` message (and
   * re-sends it whenever the slot moves or resizes); the latest geometry is
   * exposed through `embeddedHeaderSlot`. No-op outside ATS embedded mode.
   */
  requestEmbeddedHeaderSlot() {
    if (!this.isInAtsEmbeddedMode() || !this.win) {
      return;
    }

    const message: EmbeddedHeaderSlotRequest = {
      type: 'EMBEDDED_HEADER_SLOT_REQUEST',
    };

    this.postMessageToHost(message);
  }

  /**
   * Posts a message to the host (parent) window.
   *
   * Explanation: The 'origin' query parameter, set by the host when it embeds
   * the app, ensures the message is only received by the intended host
   * application; without it the message is posted to any origin.
   */
  private postMessageToHost(message: HostMessage) {
    if (!this.win) {
      return;
    }
    const urlParams = new URLSearchParams(this.win.location.search);
    const origin = urlParams.get('origin') || '*';
    this.win.parent.postMessage(message, origin);
  }

  /**
   * Stores the header slot geometry sent by the parent window, ignoring
   * malformed messages.
   */
  private onEmbeddedHeaderSlotMessage(data: EmbeddedHeaderSlotMessage) {
    const {left, width, height, borderColor} = data;
    if (
      !Number.isFinite(left) ||
      !Number.isFinite(width) ||
      !Number.isFinite(height) ||
      width < 0 ||
      height < 0
    ) {
      console.error('Ignoring invalid EMBEDDED_HEADER_SLOT message:', data);
      return;
    }
    this.embeddedHeaderSlot.set({
      left,
      width,
      height,
      borderColor: typeof borderColor === 'string' ? borderColor : undefined,
    });
  }
}
