import {Pipe, PipeTransform} from '@angular/core';
import {Cell, Column, Row} from '../../../core/models/search';
import {FilterChip} from '../models';
import {getCell} from './search_cell_utils';

/**
 * Pure pipe to extract a Cell from a Row given column key and columns catalog.
 */
@Pipe({
  name: 'appCell',
  standalone: true,
  pure: true,
})
export class SearchCellPipe implements PipeTransform {
  transform(
    row: Row | Record<string, unknown> | undefined,
    colKey: string,
    columns: Column[],
  ): Cell | null {
    return getCell(row, colKey, columns);
  }
}

/**
 * Pure pipe to calculate rounded utilization percentage.
 */
@Pipe({
  name: 'appUtilPct',
  standalone: true,
  pure: true,
})
export class UtilPctPipe implements PipeTransform {
  transform(val: number | undefined, total: number | undefined): number {
    if (!val || !total || total <= 0) return 0;
    return Math.round((100 * val) / total);
  }
}

/**
 * Pure pipe to calculate width percentage for utilization bar.
 */
@Pipe({
  name: 'appUtilWidth',
  standalone: true,
  pure: true,
})
export class UtilWidthPipe implements PipeTransform {
  transform(val: number | undefined, total: number | undefined): number {
    if (!val || !total || total <= 0) return 0;
    return (100 * val) / total;
  }
}

/**
 * Helper to check whether a FilterChip is negated.
 */
export function isChipNegated(chip: FilterChip): boolean {
  if (chip.negated) return true;
  if (chip.complex?.containsSubstring?.negated) return true;
  if (chip.complex?.matchesRegex?.negated) return true;
  const cond = (chip.pillCondition || '').toLowerCase().trim();
  return (
    cond.startsWith('does not') ||
    cond.startsWith('is not') ||
    cond.startsWith('not ') ||
    cond.startsWith('!')
  );
}

/**
 * Pure pipe to determine if a FilterChip is negated.
 */
@Pipe({
  name: 'appChipNegated',
  standalone: true,
  pure: true,
})
export class ChipNegatedPipe implements PipeTransform {
  transform(chip: FilterChip): boolean {
    return isChipNegated(chip);
  }
}
