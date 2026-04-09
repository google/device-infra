import {Injectable, inject} from '@angular/core';
import {MatDialog} from '@angular/material/dialog';
import {
  ACTION_BAR_CONFIG_TOKEN,
  ActionBarAction,
  ActionContext,
} from 'app/core/constants/action_bar_config';
import {ComingSoonDialog} from '../components/coming_soon_dialog/coming_soon_dialog';

/**
 * Service to manage the display of "Coming Soon" alerts for unimplemented features.
 * Centralizes the logic and configuration to ensure consistency across the application.
 */
@Injectable({
  providedIn: 'root',
})
export class ComingSoonService {
  private readonly dialog = inject(MatDialog);
  private readonly config = inject(ACTION_BAR_CONFIG_TOKEN);

  /**
   * Shows the "Coming Soon" dialog for a specific feature.
   * @param feature The identifier of the feature that is not yet ready.
   * @param context The context in which the action is triggered.
   * @param legacyPageUrl The URL to the legacy page for this feature.
   */
  show(
    feature: ActionBarAction,
    context: ActionContext = 'default',
    legacyPageUrl?: string,
  ) {
    const metadata = this.config[feature];
    if (!metadata) {
      console.warn(
        `ComingSoonService: No metadata found for feature "${feature}".`,
      );
      return;
    }

    const {displayName, legacyScreenshotLinks} = metadata;
    const legacyScreenshotLink = legacyScreenshotLinks
      ? legacyScreenshotLinks[context] || legacyScreenshotLinks.default
      : undefined;
    const message = `The <b>${displayName}</b> feature is not yet available in the new console. Please switch to the legacy page to use this feature.`;

    this.dialog.open(ComingSoonDialog, {
      data: {
        title: 'Coming soon',
        message,
        icon: 'construction',
        confirmLabel: 'Go to Legacy Page',
        legacyPageUrl,
        legacyScreenshotLink,
      },
      panelClass: 'coming-soon-dialog-panel',
      autoFocus: false,
    });
  }
}
