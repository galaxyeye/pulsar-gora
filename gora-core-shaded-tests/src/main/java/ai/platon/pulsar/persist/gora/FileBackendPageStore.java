package ai.platon.pulsar.persist.gora;

import ai.platon.gora.memory.store.MemStore;
import ai.platon.pulsar.common.AppPaths;
import ai.platon.pulsar.common.config.VolatileConfig;
import ai.platon.pulsar.common.urls.URLUtils;
import ai.platon.pulsar.persist.gora.generated.GWebPage;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * A very simple file backend storage for webpages
 */
public class FileBackendPageStore extends MemStore<String, GWebPage> {

    private static final Logger logger = LoggerFactory.getLogger(FileBackendPageStore.class);
    private final Path persistDirectory;

    public FileBackendPageStore(Path persistDirectory) {
        this.persistDirectory = persistDirectory;
    }

    private boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    /**
     * Get a page from the store.
     */
    @Override
    public synchronized GWebPage get(String reversedUrl, String[] fields) {
        GWebPage page = (GWebPage) map.get(reversedUrl);
        if (page == null) {
            page = readAvro(reversedUrl);
            if (page == null) {
                page = readHtml(reversedUrl);
            }
        }
        return page;
    }

    /**
     * Put a page into the store.
     */
    @Override
    public synchronized void put(String reversedUrl, GWebPage page) {
        super.put(reversedUrl, page);

        String url = URLUtils.unreverseUrlOrNull(reversedUrl);
        if (url != null) {
            writeAvro(page);
            writeHtml(page);
        }
    }

    /**
     * Delete a page from the store.
     *
     * This function deletes a page identified by its reversed URL from the store. It first attempts to delete the page
     * using the superclass's delete method. If the URL can be successfully unreversed, it also deletes the associated
     * `.avro` and `.html` files from the file system.
     *
     * @param reversedUrl The reversed URL of the page to be deleted.
     * @return `true` if the page and its associated files were successfully deleted, `false` otherwise.
     */
    @Override
    public synchronized boolean delete(String reversedUrl) {
        // Attempt to delete the page using the superclass's delete method.
        boolean success = super.delete(reversedUrl);

        // Unreverse the URL to get the original URL.
        String url = URLUtils.unreverseUrlOrNull(reversedUrl);
        if (url != null) {
            // Get the paths for the associated `.avro` and `.html` files.
            Path path1 = getPersistPath(url, ".avro");
            Path path2 = getPersistPath(url, ".html");

            // Delete the `.avro` and `.html` files if they exist.
            try {
                boolean filesDeleted = Files.deleteIfExists(path1) && Files.deleteIfExists(path2);
                success = success && filesDeleted;
            } catch (IOException e) {
                // Log the exception or handle it appropriately
                logger.warn("Failed to delete files for " + reversedUrl, e);
                success = false;
            }
        }

        // Return the overall success status of the deletion operation.
        return success;
    }

    @Override
    public String getSchemaName() {
        return "FileBackendPageStore";
    }

    @Override
    public String[] getFields() {
        return GWebPage._ALL_FIELDS;
    }

    public synchronized GWebPage readHtml(String reversedUrl) {
        String url = URLUtils.unreverseUrlOrNull(reversedUrl);
        if (url == null) {
            return null;
        }
        Path path = getPersistPath(url, ".html");

        if (isTraceEnabled()) {
            logger.trace("Getting {} {} | {}", reversedUrl, Files.exists(path), path);
        }

        if (Files.exists(path)) {
            byte[] content;
            try {
                content = Files.readAllBytes(path);
            } catch (IOException e) {
                logger.warn("Failed to read HTML file: " + path, e);
                return null;
            }
            // never expire, so it serves as a mock site
            Instant lastModified = Instant.now();
            return newSuccessPage(url, lastModified, content);
        }

        return null;
    }

    public synchronized GWebPage readAvro(String reversedUrl) {
        String url = URLUtils.unreverseUrlOrNull(reversedUrl);
        if (url == null) {
            return null;
        }
        Path path = getPersistPath(url, ".avro");

        if (!Files.exists(path)) {
            return null;
        }

        if (isTraceEnabled()) {
            logger.trace("Getting {} {} | {}", reversedUrl, Files.exists(path), path);
        }

        try {
            return readAvro(path);
        } catch (AvroRuntimeException e) {
            logger.warn("Failed to read avro file from " + path + ", the file might be corrupted, delete it", e);
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
            return null;
        } catch (IOException e) {
            logger.warn(e.toString());
            return null;
        }
    }

    public synchronized GWebPage readAvro(Path path) throws IOException {
        if (!Files.exists(path)) {
            return null;
        }

        DatumReader<GWebPage> datumReader = new SpecificDatumReader<>(GWebPage.class);
        GWebPage page = null;
        try (DataFileReader<GWebPage> dataFileReader = new DataFileReader<>(path.toFile(), datumReader)) {
            while (dataFileReader.hasNext()) {
                page = dataFileReader.next(page);
            }
        }
        return page;
    }

    public synchronized void writeHtml(GWebPage page) {
        ByteBuffer contentBuffer = page.getContent();
        if (contentBuffer == null) {
            return;
        }
        Path path = getPersistPath(page.getBaseUrl().toString(), ".htm");

        if (isTraceEnabled()) {
            logger.trace("Putting {} | {}", contentBuffer.array().length, path);
        }

        try {
            Files.write(path, contentBuffer.array());
        } catch (IOException e) {
            logger.warn("Failed to write HTML file: " + path, e);
        }
    }

    public synchronized void writeAvro(GWebPage page) {
        Path path = getPersistPath(page.getBaseUrl().toString(), ".avro");

        if (isTraceEnabled()) {
            ByteBuffer contentBuffer = page.getContent();
            logger.trace("Putting {} | {}", contentBuffer != null ? contentBuffer.array().length : 0, path);
        }

        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            logger.warn("Failed to delete existing avro file: " + path, e);
        }

        try {
            writeAvro0(page, path);
        } catch (AvroRuntimeException e) {
            logger.warn("Failed to write avro file to " + path, e);
        } catch (IOException e) {
            logger.warn(e.toString());
        }
    }

    public Path getPersistPath(String url, String suffix) {
        Path directory = getPersistDirectory(url);
        String filename = AppPaths.INSTANCE.fromUri(url, "", suffix);
        return directory.resolve(filename);
    }

    private Path getPersistDirectory(String url) {
        String dirForDomain = AppPaths.INSTANCE.fromHost(url);
        Path path = persistDirectory.resolve(dirForDomain);
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            logger.warn("Failed to create directories: " + path, e);
        }
        return path;
    }

    private void writeAvro0(GWebPage page, Path path) throws IOException {
        DatumWriter<GWebPage> datumWriter = new SpecificDatumWriter<>(GWebPage.class);
        try (DataFileWriter<GWebPage> dataFileWriter = new DataFileWriter<>(datumWriter)) {
            dataFileWriter.create(page.getSchema(), path.toFile());
            dataFileWriter.append(page);
        }
    }

    private GWebPage newSuccessPage(String url, Instant lastModified, byte[] content) {
        GWebPage page = GWebPage.newBuilder().build();
        // page.setLocation(url);
        // page.setFetchCount(1);
        // page.setPrevFetchTime(lastModified);
        // page.setFetchInterval(ChronoUnit.DECADES.getDuration());
        // page.setFetchTime(lastModified.plus(page.getFetchInterval()));
        // page.setProtocolStatus(ProtocolStatus.STATUS_SUCCESS);

        page.setContent(ByteBuffer.wrap(content));
        if (page.getContentLength() != content.length) {
            throw new IllegalArgumentException(
                    "Content length mismatch: " + page.getContentLength() + " != " + content.length);
        }
        if (page.getPersistedContentLength() != content.length) {
            throw new IllegalArgumentException(
                    "Persisted content length mismatch: " + page.getPersistedContentLength() + " != " + content.length);
        }

        return page;
    }
}
