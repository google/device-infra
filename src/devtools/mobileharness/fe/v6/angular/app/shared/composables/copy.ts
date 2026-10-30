import {inject, signal} from '@angular/core';
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
