import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ViewEncapsulation,
  inject,
} from '@angular/core';
import {
  APP_DATA,
  type AppData,
} from '@deviceinfra/app/core/models/app_data';
import {setSafeHref} from '@deviceinfra/app/shared/utils/safe_dom';

/** Item displayed in the Google apps 9-dots launcher menu. */
export interface GoogleAppItem {
  readonly id: string;
  readonly label: string;
  readonly url: string;
  readonly spriteOffset: string;
}

const GOOGLE_APPS_ITEMS: readonly GoogleAppItem[] = [
  {
    id: 'account',
    label: 'Account',
    url: 'https://myaccount.google.com/?utm_source=OGB&utm_medium=app',
    spriteOffset: '0 -232px',
  },
  {
    id: 'gmail',
    label: 'Gmail',
    url: 'https://mail.google.com/mail/',
    spriteOffset: '0 -1624px',
  },
  {
    id: 'drive',
    label: 'Drive',
    url: 'https://drive.google.com/',
    spriteOffset: '0 -348px',
  },
  {
    id: 'docs',
    label: 'Docs',
    url: 'https://docs.google.com/document/?usp=docs_alc',
    spriteOffset: '0 -870px',
  },
  {
    id: 'sheets',
    label: 'Sheets',
    url: 'https://docs.google.com/spreadsheets/?usp=sheets_alc',
    spriteOffset: '0 -754px',
  },
  {
    id: 'slides',
    label: 'Slides',
    url: 'https://docs.google.com/presentation/?usp=slides_alc',
    spriteOffset: '0 -812px',
  },
  {
    id: 'calendar',
    label: 'Calendar',
    url: 'https://calendar.google.com/calendar',
    spriteOffset: '0 -1566px',
  },
  {
    id: 'chat',
    label: 'Chat',
    url: 'https://chat.google.com/',
    spriteOffset: '0 -2842px',
  },
  {
    id: 'meet',
    label: 'Meet',
    url: 'https://meet.google.com/?hs=197',
    spriteOffset: '0 -2552px',
  },
  {
    id: 'gemini',
    label: 'Gemini',
    url: 'https://gemini.google.com/?utm_source=app_launcher&utm_medium=owned&utm_campaign=base_all',
    spriteOffset: '0 -2320px',
  },
  {
    id: 'groups',
    label: 'Groups',
    url: 'https://groups.google.com/',
    spriteOffset: '0 -2262px',
  },
  {
    id: 'contacts',
    label: 'Contacts',
    url: 'https://contacts.google.com/',
    spriteOffset: '0 -58px',
  },
  {
    id: 'keep',
    label: 'Keep',
    url: 'https://keep.google.com/',
    spriteOffset: '0 -2668px',
  },
  {
    id: 'sites',
    label: 'Sites',
    url: 'https://sites.google.com/',
    spriteOffset: '0 -1740px',
  },
  {
    id: 'cloud_search',
    label: 'Cloud Search',
    url: 'https://cloudsearch.google.com/cloudsearch',
    spriteOffset: '0 -2900px',
  },
];

/**
 * OneGoogleBar component (`<app-one-google-bar>`) displaying the Google apps
 * 9-dots waffle launcher and the user account switcher menu in the top toolbar.
 */
@Component({
  selector: 'app-one-google-bar',
  standalone: true,
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './one_google_bar.ng.html',
  styleUrl: './one_google_bar.scss',
  host: {'class': 'account-menu'},
  imports: [
    CommonModule,
  ],
})
export class OneGoogleBar {
  readonly appData: AppData = inject(APP_DATA);
  readonly googleApps = GOOGLE_APPS_ITEMS;
  avatarLoadFailed = false;

  onAvatarError() {
    this.avatarLoadFailed = true;
  }

  getUserInitial(): string {
    const source = (
      this.appData.userDisplayName ||
      this.appData.email ||
      ''
    ).trim();
    return source ? source.charAt(0).toUpperCase() : '';
  }

  getAccountButtonLabel(): string {
    const email = this.appData.email ?? '';
    const displayName = (this.appData.userDisplayName ?? '').trim();
    return displayName
      ? `Google Account: ${displayName} (${email})`
      : `Google Account: ${email}`;
  }

  switchAccount() {
    const continueUrl = encodeURIComponent(window.location.href);
    this.redirectToUrl(
      `https://accounts.google.com/AccountChooser?continue=${continueUrl}&faa=1`,
    );
  }

  logout() {
    const continueUrl = encodeURIComponent(window.location.href);
    this.redirectToUrl(
      `https://accounts.google.com/Logout?continue=${continueUrl}`,
    );
  }

  redirectToUrl(url: string) {
    const anchor = document.createElement('a');
    setSafeHref(anchor, url);
    anchor.click();
  }
}
