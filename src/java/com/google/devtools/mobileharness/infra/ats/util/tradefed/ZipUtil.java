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

package com.google.devtools.mobileharness.infra.ats.util.tradefed;

import static com.google.common.base.StandardSystemProperty.OS_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** A helper class for compression-related operations */
public class ZipUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int COMPRESSION_METHOD_STORED = 0;
  private static final int COMPRESSION_METHOD_DEFLATE = 8;
  private static final String DEFAULT_DIRNAME = "dir";
  private static final String DEFAULT_FILENAME = "files";
  private static final String ZIP_EXTENSION = ".zip";
  private static final String PARTIAL_ZIP_DATA = "compressed_data";

  private static final boolean IS_UNIX;

  static {
    String os = OS_NAME.value().toLowerCase(Locale.ROOT);
    IS_UNIX = (os.contains("nix") || os.contains("nux") || os.contains("aix"));
  }

  private ZipUtil() {}

  /**
   * Utility method to verify that a zip file is not corrupt.
   *
   * @param zipFile the {@link File} to check
   * @param thorough Whether to attempt to fully extract the archive. If {@code false}, this method
   *     will fail to detect CRC errors in a well-formed archive.
   * @throws MobileHarnessException if the file could not be opened or read
   * @return {@code false} if the file appears to be corrupt; {@code true} otherwise
   */
  public static boolean isZipFileValid(File zipFile, boolean thorough)
      throws MobileHarnessException {
    if (zipFile == null) {
      logger.atFine().log("isZipFileValid received a null file reference.");
      return false;
    }
    if (!zipFile.exists()) {
      logger.atFine().log("Zip file does not exist: %s", zipFile.getAbsolutePath());
      return false;
    }

    try (ZipFile z = new ZipFile(zipFile)) {
      if (thorough) {
        // Reading the entire file is the only way to detect CRC errors within the archive
        final File extractDir = FileUtil.createTempDir("extract-" + zipFile.getName());
        try {
          extractZip(z, extractDir);
        } finally {
          FileUtil.recursiveDelete(extractDir);
        }
      }
    } catch (ZipException e) {
      // File is likely corrupted
      try {
        logger.atWarning().log(
            "Detected corrupt zip file %s: %s",
            zipFile.getCanonicalPath(), MoreThrowables.shortDebugString(e));
      } catch (IOException e2) {
        logger.atWarning().log(
            "Failed to get canonical path of corrupt zip file %s: %s",
            zipFile.getAbsolutePath(), MoreThrowables.shortDebugString(e2));
      }
      return false;
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.ZIP_UTIL_CHECK_ZIP_FILE_ERROR,
          String.format("Failed to validate zip file %s", zipFile.getAbsolutePath()),
          e);
    }

    return true;
  }

  /**
   * Utility method to extract entire contents of zip file into given directory
   *
   * @param zipFile the {@link ZipFile} to extract
   * @param destDir the local dir to extract file to
   * @throws MobileHarnessException if failed to extract file
   */
  public static void extractZip(ZipFile zipFile, File destDir) throws MobileHarnessException {
    Enumeration<? extends ZipEntry> entries = zipFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      File childFile = new File(destDir, entry.getName());
      validateDestinationDir(destDir, entry.getName());
      childFile.getParentFile().mkdirs();
      if (entry.isDirectory()) {
        childFile.mkdirs();
      } else {
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
          FileUtil.writeToFile(inputStream, childFile);
        } catch (IOException e) {
          throw new MobileHarnessException(
              BasicErrorId.ZIP_UTIL_GET_INPUT_STREAM_ERROR, e.getMessage(), e);
        }
      }
    }
  }

  /**
   * Utility method to extract contents of zip file into given directory
   *
   * @param zipFile the {@link ZipFile} to extract
   * @param destDir the local dir to extract file to
   * @param shouldExtract the predicate to determine if an ZipEntry should be extracted
   * @throws MobileHarnessException if failed to extract file
   */
  public static void extractZip(ZipFile zipFile, File destDir, Predicate<ZipEntry> shouldExtract)
      throws MobileHarnessException {
    Enumeration<? extends ZipEntry> entries = zipFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      File childFile = new File(destDir, entry.getName());
      validateDestinationDir(destDir, entry.getName());
      childFile.getParentFile().mkdirs();
      if (!entry.isDirectory() && shouldExtract.test(entry)) {
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
          FileUtil.writeToFile(inputStream, childFile);
        } catch (IOException e) {
          throw new MobileHarnessException(
              BasicErrorId.ZIP_UTIL_GET_INPUT_STREAM_ERROR, e.getMessage(), e);
        }
      }
    }
  }

  /**
   * Utility method to extract one specific file from zip file into a tmp file
   *
   * @param zipFile the {@link ZipFile} to extract
   * @param filePath the filePath of to extract
   * @throws MobileHarnessException if failed to extract file
   * @return the {@link File} or {@code Optional.empty()} if not found
   */
  public static Optional<File> extractFileFromZip(ZipFile zipFile, String filePath)
      throws MobileHarnessException {
    ZipEntry entry = zipFile.getEntry(filePath);
    if (entry == null) {
      return Optional.empty();
    }
    File createdFile = FileUtil.createTempFile("extracted", FileUtil.getExtension(filePath));
    try (InputStream inputStream = zipFile.getInputStream(entry)) {
      FileUtil.writeToFile(inputStream, createdFile);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.ZIP_UTIL_GET_INPUT_STREAM_ERROR, e.getMessage(), e);
    }
    return Optional.of(createdFile);
  }

  /**
   * Utility method to create a temporary zip file containing the given directory and all its
   * contents.
   *
   * @param dir the directory to zip
   * @return a temporary zip {@link File} containing directory contents
   * @throws MobileHarnessException if failed to create zip file
   */
  public static File createZip(File dir) throws MobileHarnessException {
    return createZip(dir, DEFAULT_DIRNAME);
  }

  /**
   * Utility method to create a temporary zip file containing the given directory and all its
   * contents.
   *
   * @param dir the directory to zip
   * @param name the base name of the zip file created without the extension.
   * @return a temporary zip {@link File} containing directory contents
   * @throws MobileHarnessException if failed to create zip file
   */
  public static File createZip(File dir, String name) throws MobileHarnessException {
    File zipFile = FileUtil.createTempFile(name, ZIP_EXTENSION);
    createZip(dir, zipFile);
    return zipFile;
  }

  /**
   * Utility method to create a zip file containing the given directory and all its contents.
   *
   * @param dir the directory to zip
   * @param zipFile the zip file to create - it should not already exist
   * @throws MobileHarnessException if failed to create zip file
   */
  public static void createZip(File dir, File zipFile) throws MobileHarnessException {
    ZipOutputStream out = null;
    try {
      FileOutputStream fileStream = new FileOutputStream(zipFile);
      out = new ZipOutputStream(new BufferedOutputStream(fileStream));
      addToZip(out, dir, new ArrayList<String>());
    } catch (IOException e) {
      zipFile.delete();
      throw new MobileHarnessException(BasicErrorId.ZIP_UTIL_CREATE_ZIP_ERROR, e.getMessage(), e);
    } catch (RuntimeException e) {
      zipFile.delete();
      throw e;
    } finally {
      StreamUtil.close(out);
    }
  }

  /**
   * Utility method to create a temporary zip file containing the given files
   *
   * @param files list of files to zip
   * @return a temporary zip {@link File} containing directory contents
   * @throws MobileHarnessException if failed to create zip file
   */
  public static File createZip(List<File> files) throws MobileHarnessException {
    return createZip(files, DEFAULT_FILENAME);
  }

  /**
   * Utility method to create a temporary zip file containing the given files.
   *
   * @param files list of files to zip
   * @param name the base name of the zip file created without the extension.
   * @return a temporary zip {@link File} containing directory contents
   * @throws MobileHarnessException if failed to create zip file
   */
  public static File createZip(List<File> files, String name) throws MobileHarnessException {
    File zipFile = FileUtil.createTempFile(name, ZIP_EXTENSION);
    createZip(files, zipFile);
    return zipFile;
  }

  /**
   * Utility method to create a zip file containing the given files
   *
   * @param files list of files to zip
   * @param zipFile the zip file to create - it should not already exist
   * @throws MobileHarnessException if failed to create zip file
   */
  public static void createZip(List<File> files, File zipFile) throws MobileHarnessException {
    ZipOutputStream out = null;
    try {
      FileOutputStream fileStream = new FileOutputStream(zipFile);
      out = new ZipOutputStream(new BufferedOutputStream(fileStream));
      for (File file : files) {
        addToZip(out, file, new ArrayList<>());
      }
    } catch (IOException | RuntimeException e) {
      zipFile.delete();
      throw new MobileHarnessException(BasicErrorId.ZIP_UTIL_CREATE_ZIP_ERROR, e.getMessage(), e);
    } finally {
      StreamUtil.close(out);
    }
  }

  /**
   * Recursively adds given file and its contents to ZipOutputStream
   *
   * @param out the {@link ZipOutputStream}
   * @param file the {@link File} to add to the stream
   * @param relativePathSegs the relative path of file, including separators
   * @throws MobileHarnessException if failed to add file to zip
   */
  public static void addToZip(ZipOutputStream out, File file, List<String> relativePathSegs)
      throws MobileHarnessException {
    relativePathSegs.add(file.getName());
    if (file.isDirectory()) {
      // note: it appears even on windows, ZipEntry expects '/' as a path separator
      relativePathSegs.add("/");
    }
    ZipEntry zipEntry = new ZipEntry(buildPath(relativePathSegs));
    try {
      out.putNextEntry(zipEntry);
      if (file.isFile()) {
        writeToStream(file, out);
      }
      out.closeEntry();
    } catch (IOException e) {
      throw new MobileHarnessException(BasicErrorId.ZIP_UTIL_ADD_TO_ZIP_ERROR, e.getMessage(), e);
    }
    if (file.isDirectory()) {
      // recursively add contents
      File[] subFiles = file.listFiles();
      if (subFiles == null) {
        throw new MobileHarnessException(
            BasicErrorId.ZIP_UTIL_LIST_FILES_ERROR,
            String.format("Could not read directory %s", file.getAbsolutePath()));
      }
      for (File subFile : subFiles) {
        addToZip(out, subFile, relativePathSegs);
      }
      // remove the path separator
      relativePathSegs.remove(relativePathSegs.size() - 1);
    }
    // remove the last segment, added at beginning of method
    relativePathSegs.remove(relativePathSegs.size() - 1);
  }

  /**
   * Close an open {@link ZipFile}, ignoring any exceptions.
   *
   * @param zipFile the file to close
   */
  public static void closeZip(ZipFile zipFile) {
    if (zipFile != null) {
      try {
        zipFile.close();
      } catch (IOException e) {
        // ignore
      }
    }
  }

  /**
   * Helper method to create a gzipped version of a single file.
   *
   * @param file the original file
   * @param gzipFile the file to place compressed contents in
   * @throws MobileHarnessException if failed to create the gzip file
   */
  public static void gzipFile(File file, File gzipFile) throws MobileHarnessException {
    GZIPOutputStream out = null;
    try {
      FileOutputStream fileStream = new FileOutputStream(gzipFile);
      out = new GZIPOutputStream(new BufferedOutputStream(fileStream, 64 * 1024));
      writeToStream(file, out);
    } catch (IOException e) {
      gzipFile.delete();
      throw new MobileHarnessException(BasicErrorId.ZIP_UTIL_GZIP_FILE_ERROR, e.getMessage(), e);
    } catch (RuntimeException e) {
      gzipFile.delete();
      throw e;
    } finally {
      StreamUtil.close(out);
    }
  }

  /**
   * Helper method to write input file contents to output stream.
   *
   * @param file the input {@link File}
   * @param out the {@link OutputStream}
   * @throws MobileHarnessException if failed to write input file contents to the output stream
   */
  private static void writeToStream(File file, OutputStream out) throws MobileHarnessException {
    InputStream inputStream = null;
    try {
      inputStream = new BufferedInputStream(new FileInputStream(file));
      StreamUtil.copyStreams(inputStream, out);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.ZIP_UTIL_WRITE_TO_STREAM_ERROR,
          String.format("Failed to write file %s to stream", file.getAbsolutePath()),
          e);
    } finally {
      StreamUtil.close(inputStream);
    }
  }

  /**
   * Builds a file system path from a stack of relative path segments
   *
   * @param relativePathSegs the list of relative paths
   * @return a {@link String} containing all relativePathSegs
   */
  private static String buildPath(List<String> relativePathSegs) {
    StringBuilder pathBuilder = new StringBuilder();
    for (String segment : relativePathSegs) {
      pathBuilder.append(segment);
    }
    return pathBuilder.toString();
  }

  /**
   * Extract a zip file to a temp directory prepended with a string
   *
   * @param zipFile the zip file to extract
   * @param nameHint a prefix for the temp directory
   * @return a {@link File} pointing to the temp directory
   */
  public static File extractZipToTemp(File zipFile, String nameHint) throws MobileHarnessException {
    File localRootDir = FileUtil.createTempDir(nameHint);
    try (ZipFile zip = new ZipFile(zipFile)) {
      extractZip(zip, localRootDir);
      return localRootDir;
    } catch (MobileHarnessException | IOException e) {
      // clean tmp file since we couldn't extract.
      FileUtil.recursiveDelete(localRootDir);
      throw new MobileHarnessException(BasicErrorId.ZIP_UTIL_EXTRACT_ZIP_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Apply the file permission configured in the central directory entry.
   *
   * @param targetFile the {@link File} to set permission to.
   * @param zipEntry a {@link CentralDirectoryInfo} object that contains the file permissions.
   * @throws MobileHarnessException if fail to access the file.
   */
  public static void applyPermission(File targetFile, CentralDirectoryInfo zipEntry)
      throws MobileHarnessException {
    if (!IS_UNIX) {
      logger.atWarning().log("Permission setting is only supported in Unix/Linux system.");
      return;
    }

    if (zipEntry.getFilePermission() != 0) {
      try {
        Files.setPosixFilePermissions(
            targetFile.toPath(), FileUtil.unixModeToPosix(zipEntry.getFilePermission()));
      } catch (IOException e) {
        throw new MobileHarnessException(
            BasicErrorId.ZIP_UTIL_SET_POSIX_FILE_PERMISSION_ERROR,
            String.format(
                "Failed to set posix file permission to file %s", targetFile.getAbsolutePath()),
            e);
      }
    }
  }

  /**
   * Extract the requested folder from a partial zip file and apply proper permission.
   *
   * @param targetFile the {@link File} to save the extracted file to.
   * @param zipEntry a {@link CentralDirectoryInfo} object of the file to extract from the partial
   *     zip file.
   * @throws MobileHarnessException if failed to unzip requested folder from the partial zip file
   */
  public static void unzipPartialZipFolder(File targetFile, CentralDirectoryInfo zipEntry)
      throws MobileHarnessException {
    unzipPartialZipFile(null, targetFile, zipEntry, null, -1);
  }

  /**
   * Extract a single requested file from a partial zip file.
   *
   * <p>This method assumes all files are on the same disk when compressed.
   *
   * <p>If {@code targetFile} is a directory, an empty directory will be created without its
   * contents.
   *
   * <p>If {@code targetFile} is a symlink, a symlink will be created but not resolved.
   *
   * <p>It doesn't support following features yet:
   *
   * <p>Zip file larger than 4GB
   *
   * <p>ZIP64(require ZipLocalFileHeader update on compressed size)
   *
   * <p>Encrypted zip file
   *
   * @param partialZip a {@link File} that's a partial of the zip file.
   * @param targetFile the {@link File} to save the extracted file to.
   * @param zipEntry a {@link CentralDirectoryInfo} object of the file to extract from the partial
   *     zip file.
   * @param localFileHeader a {@link LocalFileHeader} object of the file to extract from the partial
   *     zip file.
   * @param startOffset start offset of the file to extract.
   * @throws MobileHarnessException if failed to unzip requested file from the partial zip file
   */
  public static void unzipPartialZipFile(
      File partialZip,
      File targetFile,
      CentralDirectoryInfo zipEntry,
      LocalFileHeader localFileHeader,
      long startOffset)
      throws MobileHarnessException {
    try {
      if (zipEntry.getFileName().endsWith("/")) {
        // Create a folder.
        targetFile.mkdir();
        return;
      }

      if (zipEntry.getCompressedSize() == 0) {
        // The file is empty, just create an empty file.
        targetFile.getParentFile().mkdirs();
        try {
          targetFile.createNewFile();
        } catch (IOException e) {
          throw new MobileHarnessException(
              BasicErrorId.ZIP_UTIL_CREATE_NEW_FILE_ERROR,
              String.format("Failed to create new file %s", targetFile.getAbsolutePath()),
              e);
        }
        return;
      }

      File zipFile = targetFile;
      if (zipEntry.getCompressionMethod() != COMPRESSION_METHOD_STORED || zipEntry.isSymLink()) {
        // Create a temp file to store the compressed data, then unzip it.
        zipFile = FileUtil.createTempFile(PARTIAL_ZIP_DATA, ZIP_EXTENSION);
      } else {
        // The file is not compressed, stream it directly to the target.
        zipFile.getParentFile().mkdirs();
        try {
          zipFile.createNewFile();
        } catch (IOException e) {
          throw new MobileHarnessException(
              BasicErrorId.ZIP_UTIL_CREATE_NEW_FILE_ERROR,
              String.format("Failed to create new file %s", targetFile.getAbsolutePath()),
              e);
        }
      }

      // Save compressed data to zipFile
      try (FileInputStream stream = new FileInputStream(partialZip)) {
        FileUtil.writeToFile(
            stream,
            zipFile,
            false,
            startOffset + localFileHeader.getHeaderSize(),
            zipEntry.getCompressedSize());
      } catch (IOException e) {
        throw new MobileHarnessException(
            BasicErrorId.ZIP_UTIL_GET_INPUT_STREAM_ERROR,
            String.format("Failed to get input stream to file %s", partialZip.getAbsolutePath()),
            e);
      }

      if (zipEntry.isSymLink()) {
        try {
          unzipSymlink(zipFile, targetFile, zipEntry);
          return;
        } finally {
          zipFile.delete();
        }
      }

      if (zipEntry.getCompressionMethod() == COMPRESSION_METHOD_STORED) {
        return;
      } else if (zipEntry.getCompressionMethod() == COMPRESSION_METHOD_DEFLATE) {
        boolean success = false;
        try {
          unzipRawZip(zipFile, targetFile, zipEntry);
          success = true;
        } finally {
          zipFile.delete();
          if (!success) {
            logger.atWarning().log("Failed to unzip %s", zipEntry.getFileName());
            targetFile.delete();
          }
        }
      } else {
        throw new IllegalStateException(
            String.format(
                "Compression method %d is not supported.", localFileHeader.getCompressionMethod()));
      }
    } finally {
      if (targetFile.exists()) {
        applyPermission(targetFile, zipEntry);
      }
    }
  }

  /**
   * Unzip the raw compressed content without wrapper (local file header).
   *
   * @param zipFile the {@link File} that contains the compressed data of the target file.
   * @param targetFile {@link File} to same the decompressed data to.
   * @throws MobileHarnessException if decompression failed due to zip format issue or if failed to
   *     access the compressed data or the decompressed file has mismatched CRC.
   */
  private static void unzipRawZip(File zipFile, File targetFile, CentralDirectoryInfo zipEntry)
      throws MobileHarnessException {
    targetFile.getParentFile().mkdirs();
    try {
      targetFile.createNewFile();
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.ZIP_UTIL_CREATE_NEW_FILE_ERROR,
          String.format("Failed to create new file %s", targetFile.getAbsolutePath()),
          e);
    }

    try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
      unzipToStream(zipFile, outputStream);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.ZIP_UTIL_GET_OUTPUT_STREAM_ERROR, e.getMessage(), e);
    }

    // Validate CRC
    long targetFileCrc = FileUtil.calculateCrc32(targetFile);
    if (targetFileCrc != zipEntry.getCrc()) {
      throw new MobileHarnessException(
          BasicErrorId.ZIP_UTIL_CRC_NOT_MATCH_ERROR,
          String.format(
              "Failed to match CRC for file %s [expected=%s, actual=%s]",
              targetFile, zipEntry.getCrc(), targetFileCrc));
    }
  }

  private static void unzipToStream(File zipFile, OutputStream outputStream)
      throws MobileHarnessException {
    Inflater decompresser = new Inflater(true);
    try (FileInputStream inputStream = new FileInputStream(zipFile)) {
      byte[] data = new byte[32768];
      byte[] buffer = new byte[65536];
      while (inputStream.read(data) > 0) {
        decompresser.setInput(data);
        while (!decompresser.finished() && !decompresser.needsInput()) {
          int size = decompresser.inflate(buffer);
          outputStream.write(buffer, 0, size);
        }
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.ZIP_UTIL_UNZIP_TO_STREAM_ERROR, e.getMessage(), e);
    } catch (DataFormatException e) {
      throw new MobileHarnessException(
          BasicErrorId.ZIP_UTIL_UNZIP_TO_STREAM_DATA_FORMAT_ERROR, e.getMessage(), e);
    } finally {
      decompresser.end();
    }
  }

  private static void unzipSymlink(File zipFile, File targetFile, CentralDirectoryInfo zipEntry)
      throws MobileHarnessException {
    String target = null;
    if (zipEntry.getCompressionMethod() == COMPRESSION_METHOD_STORED) {
      target = FileUtil.readStringFromFile(zipFile);
    } else if (zipEntry.getCompressionMethod() == COMPRESSION_METHOD_DEFLATE) {
      try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        unzipToStream(zipFile, baos);
        target = baos.toString(UTF_8);
      } finally {
        if (target == null) {
          logger.atWarning().log("Failed to unzip %s", zipEntry.getFileName());
          targetFile.delete();
        }
      }
    } else {
      throw new MobileHarnessException(
          BasicErrorId.ZIP_UTIL_UNZIP_SYMLINK_COMPRESSION_METHOD_NOT_SUPPORTED,
          String.format(
              "Compression method %d is not supported.", zipEntry.getCompressionMethod()));
    }

    targetFile.getParentFile().mkdirs();
    try {
      Files.createSymbolicLink(Path.of(targetFile.getPath()), Path.of(target));
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.ZIP_UTIL_CREATE_SYMBOLIC_LINK_ERROR,
          String.format("Failed to create symbolic link %s", targetFile.getAbsolutePath()),
          e);
    }
  }

  protected static void validateDestinationDir(File destDir, String filename)
      throws MobileHarnessException {
    try {
      String canonicalDestinationDirPath = destDir.getCanonicalPath();
      File destinationfile = new File(destDir, filename);
      String canonicalDestinationFile = destinationfile.getCanonicalPath();
      if (!canonicalDestinationFile.startsWith(canonicalDestinationDirPath + File.separator)) {
        throw new IllegalStateException("Entry is outside of the target dir: " + filename);
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.ZIP_UTIL_VALIDATE_DESTINATION_DIR_ERROR,
          String.format("Failed to validate destination dir %s", destDir),
          e);
    }
  }

  /**
   * Closes the given {@link Closeable}.
   *
   * @param closeable the {@link Closeable}. No action taken if <code>null</code>.
   */
  public static void close(Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (IOException e) {
        // ignore
      }
    }
  }
}
