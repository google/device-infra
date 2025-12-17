import {Injectable} from '@angular/core';
import {NativeDateAdapter} from '@angular/material/core';

/**
 * A custom DateAdapter that uses Pacific Daylight Time (PDT) as the default time zone.
 *
 * This adapter overrides the `today()` method to return the current date in PDT/PST
 * (America/Los_Angeles), ensuring that date pickers default to the correct "today"
 * regardless of the user's local system time zone.
 */
@Injectable()
export class PdtDateAdapter extends NativeDateAdapter {
  override today(): Date {
    const now = new Date();
    // Convert to PDT string
    const pdtString = now.toLocaleString('en-US', {
      timeZone: 'America/Los_Angeles',
    });
    // Parse back to Date object. This creates a date where the local time components
    // match the PDT time components.
    const pdtDate = new Date(pdtString);
    pdtDate.setHours(0, 0, 0, 0);
    return pdtDate;
  }
}
