import {Injectable, inject} from '@angular/core';
import {Environment} from './environment';

const UNIVERSE_GOOGLE_1P = 'google_1p';

/**
 * Service to mux Environment and Universe query parameter.
 * Provides helper methods to identify the environment and universe.
 */
@Injectable({providedIn: 'root'})
export class EnvUniverseService {
  private readonly environment = inject(Environment);

  private readonly IS_GOOGLE_INTERNAL = this.environment.isGoogleInternal();
  private readonly universe = this.resolveNormalizedUniverse();

  private resolveNormalizedUniverse(): string {
    const urlParams = new URLSearchParams(window.location.search);
    const universe = urlParams.get('universe');

    if (this.IS_GOOGLE_INTERNAL) {
      // Normalize: If internal and missing, treat as google_1p
      return universe || UNIVERSE_GOOGLE_1P;
    }

    if (universe) {
      throw new Error('universe is NOT allowed in ATS env');
    }

    return '';
  }

  /** Returns true if google internal env, and the universe is "google_1p". */
  isGoogle1P(): boolean {
    if (!this.IS_GOOGLE_INTERNAL) {
      return false;
    }
    return this.universe === UNIVERSE_GOOGLE_1P;
  }

  /** Returns true if google internal env, and the universe is NOT "google_1p". e.g. "oppo", "vivo". */
  isGoogleOEM(): boolean {
    if (!this.IS_GOOGLE_INTERNAL) {
      return false;
    }
    return this.universe !== UNIVERSE_GOOGLE_1P;
  }

  /** Returns true if google internal env. */
  isGoogleInternal(): boolean {
    return this.IS_GOOGLE_INTERNAL;
  }

  /** Returns true if OSS env. */
  isAts(): boolean {
    return !this.IS_GOOGLE_INTERNAL;
  }

  /**
   * Returns the normalized universe string.
   * For google 1p, it returns "google_1p".
   * For OEMs, it returns "oppo", "vivo", etc.
   * For ATS (OSS), it returns an empty string.
   */
  getUniverseString(): string {
    return this.universe;
  }

  toString(): string {
    return `env: ${this.IS_GOOGLE_INTERNAL ? 'google internal' : 'ats'}, universe: ${this.universe}`;
  }
}
