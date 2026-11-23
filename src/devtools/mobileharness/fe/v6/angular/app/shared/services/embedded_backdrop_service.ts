import {DestroyRef, Injectable, inject} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatDialog} from '@angular/material/dialog';
import {UrlService} from '@deviceinfra/app/core/services/url_service';
import {concat, of} from 'rxjs';
import {distinctUntilChanged, map, mergeMap, scan} from 'rxjs/operators';

/**
 * Mirrors the backdrop of the app's dialogs to the ATS host page.
 *
 * When embedded in the ATS host (MTT UI2), a dialog's backdrop only covers the
 * iframe: the host's side navigation, header and footer stay bright and
 * clickable. This service tells the host whenever the backdrop is shown or
 * hidden (see `UrlService.notifyEmbeddedBackdropChanged`), so that the host
 * dims the rest of its page in sync with it.
 */
@Injectable({providedIn: 'root'})
export class EmbeddedBackdropService {
  private readonly dialog = inject(MatDialog);
  private readonly urlService = inject(UrlService);
  private readonly destroyRef = inject(DestroyRef);
  private started = false;

  /**
   * Starts mirroring the dialogs' backdrop to the host page.
   *
   * Explanation: All dialogs of this app are MatDialogs with the default
   * backdrop. It is shown from the moment a dialog is opened
   * (`MatDialog.afterOpened`, which also reports the dialogs opened through
   * child MatDialog instances) until the dialog starts closing
   * (`MatDialogRef.beforeClosed`, emitted when the dialog detaches, i.e. fades
   * out, its backdrop). Only the transitions between "no dialog" and "at least
   * one dialog" are posted, so stacked dialogs result in a single pair of
   * messages. Only meant for the ATS embedded mode, the only mode in which the
   * app starts it (see App). No-op when already started.
   */
  start(): void {
    if (this.started) {
      return;
    }
    this.started = true;
    this.dialog.afterOpened
      .pipe(
        mergeMap((dialogRef) =>
          concat(of(1), dialogRef.beforeClosed().pipe(map(() => -1))),
        ),
        scan((openDialogs, delta) => openDialogs + delta, 0),
        map((openDialogs) => openDialogs > 0),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((visible) => {
        this.urlService.notifyEmbeddedBackdropChanged(visible);
      });
  }
}
