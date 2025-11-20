/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.tradefed.contentprovider;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Content Provider implementation to hide sd card details away from host/device interactions, and
 * that allows to abstract the host/device interactions more by allowing device and host to
 * communicate files through the provider.
 *
 * <p>This implementation aims to be standard and work in all situations.
 */
public class ManagedFileContentProvider extends ContentProvider {
  public static final String COLUMN_NAME = "name";
  public static final String COLUMN_ABSOLUTE_PATH = "absolute_path";
  public static final String COLUMN_DIRECTORY = "is_directory";
  public static final String COLUMN_MIME_TYPE = "mime_type";
  public static final String COLUMN_METADATA = "metadata";

  // TODO: Complete the list of columns
  private static final String[] columns =
      new String[] {
        COLUMN_NAME, COLUMN_ABSOLUTE_PATH, COLUMN_DIRECTORY, COLUMN_MIME_TYPE, COLUMN_METADATA
      };

  private static final String TAG = "TradefedContentProvider";
  private static final MimeTypeMap mimeMap = MimeTypeMap.getSingleton();

  private Map<Uri, ContentValues> fileTracker = new HashMap<>();

  @Override
  public boolean onCreate() {
    fileTracker = new HashMap<>();
    return true;
  }

  /**
   * Use a content URI with absolute device path embedded to get information about a file or a
   * directory on the device.
   *
   * @param uri A content uri that contains the path to the desired file/directory.
   * @param projection - not supported.
   * @param selection - not supported.
   * @param selectionArgs - not supported.
   * @param sortOrder - not supported.
   * @return A {@link Cursor} containing the results of the query. Cursor contains a single row for
   *     files and for directories it returns one row for each {@link File} returned by {@link
   *     File#listFiles()}.
   */
  @Override
  public Cursor query(
      Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    File file = getFileForUri(uri);
    if (file.getAbsolutePath().equals("/")) {
      // Querying the root will list all the known file (inserted)
      final MatrixCursor cursor = new MatrixCursor(columns, fileTracker.size());
      for (Map.Entry<Uri, ContentValues> path : fileTracker.entrySet()) {
        String metadata = path.getValue().getAsString(COLUMN_METADATA);
        cursor.addRow(getRow(columns, getFileForUri(path.getKey()), metadata));
      }
      return cursor;
    }

    if (!file.exists()) {
      Log.e(TAG, String.format("Query - File from uri: '%s' does not exists.", uri));
      return null;
    }

    if (!file.isDirectory()) {
      // Just return the information about the file itself.
      final MatrixCursor cursor = new MatrixCursor(columns, 1);
      cursor.addRow(getRow(columns, file, /* metadata= */ null));
      return cursor;
    }

    // Otherwise return the content of the directory - similar to doing ls command.
    File[] files = file.listFiles();
    sortFilesByAbsolutePath(files);
    final MatrixCursor cursor = new MatrixCursor(columns, files.length + 1);
    for (File child : files) {
      cursor.addRow(getRow(columns, child, /* metadata= */ null));
    }
    return cursor;
  }

  @Override
  public String getType(Uri uri) {
    return getType(getFileForUri(uri));
  }

  private String getType(File file) {
    final int lastDot = file.getName().lastIndexOf('.');
    if (lastDot >= 0) {
      final String extension = file.getName().substring(lastDot + 1);
      final String mime = mimeMap.getMimeTypeFromExtension(extension);
      if (mime != null) {
        return mime;
      }
    }

    return "application/octet-stream";
  }

  @Override
  public Uri insert(Uri uri, ContentValues contentValues) {
    File file = getFileForUri(uri);
    if (!file.exists()) {
      Log.e(TAG, String.format("Insert - File from uri: '%s' does not exists.", uri));
      return null;
    }
    if (fileTracker.get(uri) != null) {
      Log.e(TAG, String.format("Insert - File from uri: '%s' already exists, ignoring.", uri));
      return null;
    }
    fileTracker.put(uri, contentValues);
    return uri;
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    // Stop Tracking the File of directory if it was tracked and delete it from the disk
    fileTracker.remove(uri);
    File file = getFileForUri(uri);
    return recursiveDelete(file);
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    File file = getFileForUri(uri);
    if (!file.exists()) {
      Log.e(TAG, String.format("Update - File from uri: '%s' does not exists.", uri));
      return 0;
    }
    if (fileTracker.get(uri) == null) {
      Log.e(
          TAG, String.format("Update - File from uri: '%s' is not tracked yet, use insert.", uri));
      return 0;
    }
    fileTracker.put(uri, values);
    return 1;
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    final File file = getFileForUri(uri);
    final int fileMode = modeToMode(mode);

    if ((fileMode & ParcelFileDescriptor.MODE_CREATE) == ParcelFileDescriptor.MODE_CREATE) {
      // If the file is being created, create all its parent directories that don't already
      // exist.
      file.getParentFile().mkdirs();
      if (!fileTracker.containsKey(uri)) {
        // Track the file, if not already tracked.
        fileTracker.put(uri, new ContentValues());
      }
    }
    return ParcelFileDescriptor.open(file, fileMode);
  }

  private Object[] getRow(String[] columns, File file, String metadata) {
    Object[] values = new Object[columns.length];
    for (int i = 0; i < columns.length; i++) {
      values[i] = getColumnValue(columns[i], file, metadata);
    }
    return values;
  }

  private Object getColumnValue(String columnName, File file, String metadata) {
    Object value = null;
    switch (columnName) {
      case COLUMN_NAME -> value = file.getName();
      case COLUMN_ABSOLUTE_PATH -> value = file.getAbsolutePath();
      case COLUMN_DIRECTORY -> value = file.isDirectory();
      case COLUMN_METADATA -> value = metadata;
      case COLUMN_MIME_TYPE -> value = file.isDirectory() ? null : getType(file);
      default -> {}
    }
    return value;
  }

  @SuppressLint("SdCardPath")
  private File getFileForUri(Uri uri) {
    // TODO: apply the /sdcard resolution to query() too.
    String uriPath = uri.getPath();
    uriPath = URLDecoder.decode(uriPath, UTF_8);
    if (uriPath.startsWith("/sdcard/")) {
      uriPath =
          uriPath.replace("/sdcard", Environment.getExternalStorageDirectory().getAbsolutePath());
    }
    return new File(uriPath);
  }

  /** Copied from FileProvider.java. */
  private static int modeToMode(String mode) {
    return switch (mode) {
      case "r" -> ParcelFileDescriptor.MODE_READ_ONLY;
      case "w", "wt" ->
          ParcelFileDescriptor.MODE_WRITE_ONLY
              | ParcelFileDescriptor.MODE_CREATE
              | ParcelFileDescriptor.MODE_TRUNCATE;
      case "wa" ->
          ParcelFileDescriptor.MODE_WRITE_ONLY
              | ParcelFileDescriptor.MODE_CREATE
              | ParcelFileDescriptor.MODE_APPEND;
      case "rw" -> ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE;
      case "rwt" ->
          ParcelFileDescriptor.MODE_READ_WRITE
              | ParcelFileDescriptor.MODE_CREATE
              | ParcelFileDescriptor.MODE_TRUNCATE;
      default -> throw new IllegalArgumentException("Invalid mode: " + mode);
    };
  }

  /**
   * Recursively delete given file or directory and all its contents.
   *
   * @param rootDir the directory or file to be deleted; can be null
   * @return The number of deleted files.
   */
  private int recursiveDelete(File rootDir) {
    int count = 0;
    if (rootDir != null) {
      if (rootDir.isDirectory()) {
        File[] childFiles = rootDir.listFiles();
        if (childFiles != null) {
          for (File child : childFiles) {
            count += recursiveDelete(child);
          }
        }
      }
      rootDir.delete();
      count++;
    }
    return count;
  }

  private void sortFilesByAbsolutePath(File[] files) {
    Arrays.sort(
        files,
        new Comparator<File>() {
          @Override
          public int compare(File f1, File f2) {
            return f1.getAbsolutePath().compareTo(f2.getAbsolutePath());
          }
        });
  }
}
