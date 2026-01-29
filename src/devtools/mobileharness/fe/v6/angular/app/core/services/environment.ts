import {Injectable} from '@angular/core';

function checkIsGoogleInternal(): boolean {
  let isGoogleInternal = false;
  return isGoogleInternal;
}

const GOOGLE_INTERNAL_BUILD = checkIsGoogleInternal();

/** Utility class to determine the runtime environment. */
@Injectable({providedIn: 'root'})
export class Environment {
  /** Returns true if running in the internal Google environment. */
  isGoogleInternal(): boolean {
    return GOOGLE_INTERNAL_BUILD;
  }
}
