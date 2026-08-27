/**
 * Object utility functions.
 */
export const objectUtils = {
  objectToArray(obj: object): Array<{key: string; value: string}> {
    return Object.entries(obj).map(([key, value]) => ({key, value}));
  },
  objectKeys(obj: object): string[] {
    return Object.keys(obj);
  },
  sortedObjectKeys(obj: object): string[] {
    return Object.keys(obj).sort();
  },
  deepCopy(obj: object): object {
    return JSON.parse(JSON.stringify(obj)) as object;
  },
};
