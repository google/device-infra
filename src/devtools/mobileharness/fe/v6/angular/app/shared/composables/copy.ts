import {DestroyRef, inject, signal} from '@angular/core';
import {ClipboardService} from '../services/clipboard_service';
import {SnackBarService} from '../services/snackbar_service';

/**
 * Composable function that returns a copy-to-clipboard function
 * that automatically shows success/error toast notifications using SnackBarService.
 */
export function useCopyToClipboard() {
  const clipboard = inject(ClipboardService);
  const snackBar = inject(SnackBarService);

  return (text: string, successMessage = 'Copied to clipboard!'): boolean => {
    const success = clipboard.copyToClipboard(text);
    if (success) {
      snackBar.showSuccess(successMessage);
    } else {
      snackBar.showError('Failed to copy text.');
    }
    return success;
  };
}

/**
 * Composable that copies an ID to the clipboard and tracks its temporary
 * "copied" feedback state in a signal map that resets after `resetDelayMs`.
 */
export function useCopiedIds(
  toastMessage = 'Copied to clipboard!',
  resetDelayMs = 2000,
) {
  const copyToClipboard = useCopyToClipboard();
  const copiedIds = signal<Record<string, boolean>>({});
  const resetTimers = new Map<string, ReturnType<typeof setTimeout>>();
  const copyId = (id: string) => {
    if (!copyToClipboard(id, toastMessage)) {
      return;
    }
    const existingTimer = resetTimers.get(id);
    if (existingTimer !== undefined) {
      clearTimeout(existingTimer);
    }
    copiedIds.update((map: Record<string, boolean>) => ({...map, [id]: true}));
    resetTimers.set(
      id,
      setTimeout(() => {
        resetTimers.delete(id);
        copiedIds.update((map: Record<string, boolean>) => ({
          ...map,
          [id]: false,
        }));
      }, resetDelayMs),
    );
  };
  return {copiedIds, copyId};
}

/** How long a copy button shows its "copied" state, in milliseconds. */
const COPY_FEEDBACK_DURATION_MS = 3000;

/** Copy action and per-button state returned by {@link useCopyFeedback}. */
export interface CopyFeedback {
  /**
   * Copies `text` to the clipboard and reports the result in a snackbar. On
   * success, the button identified by `feedbackKey`, if given, reports as
   * copied for a few seconds.
   */
  readonly copy: (
    text: string,
    successMessage: string,
    feedbackKey?: string,
  ) => void;
  /** Whether the button identified by `feedbackKey` was just copied from. */
  readonly isCopied: (feedbackKey: string) => boolean;
}

/**
 * Composable for copy buttons that briefly switch to a "copied" state (e.g. a
 * check mark icon) after a successful copy, in addition to an info snackbar.
 */
export function useCopyFeedback(): CopyFeedback {
  const clipboard = inject(ClipboardService);
  const snackBar = inject(SnackBarService);
  const copiedKeys = signal<Record<string, boolean>>({});
  const resetTimers = new Map<string, ReturnType<typeof setTimeout>>();

  const setCopied = (feedbackKey: string, copied: boolean) => {
    copiedKeys.update((keys) => ({...keys, [feedbackKey]: copied}));
  };

  inject(DestroyRef).onDestroy(() => {
    for (const timer of resetTimers.values()) {
      clearTimeout(timer);
    }
  });

  return {
    copy: (text, successMessage, feedbackKey) => {
      if (!clipboard.copyToClipboard(text)) {
        snackBar.showError('Failed to copy to clipboard.');
        return;
      }
      snackBar.showInfo(successMessage);
      if (feedbackKey === undefined) {
        return;
      }
      clearTimeout(resetTimers.get(feedbackKey));
      setCopied(feedbackKey, true);
      resetTimers.set(
        feedbackKey,
        setTimeout(() => {
          resetTimers.delete(feedbackKey);
          setCopied(feedbackKey, false);
        }, COPY_FEEDBACK_DURATION_MS),
      );
    },
    isCopied: (feedbackKey) => copiedKeys()[feedbackKey] === true,
  };
}
