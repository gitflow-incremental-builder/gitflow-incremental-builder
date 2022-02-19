package io.github.gitflowincrementalbuilder.mocks;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UnZipper {

    public static final Path TEMPLATE_PROJECT_ZIP;

    static {
        try {
            TEMPLATE_PROJECT_ZIP = Paths.get(
                    UnZipper.class.getClassLoader().getResource("template.zip").toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Failed to get template.zip", e);
        }
    }

    public void act(Path zip, Path outputFolder) {
        try{
            createOutputFolder(outputFolder);
            try (ZipInputStream zis = createZipInputStream(zip)) {
                process(outputFolder, zis);
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void process(Path outputFolder, ZipInputStream zis) throws IOException {
        ZipEntry ze = zis.getNextEntry();
        while(ze != null) {
            String fileName = ze.getName();
            Path newFile = outputFolder.resolve(fileName);
            createParentDirectories(newFile);
            if (ze.isDirectory()) {
                Files.createDirectory(newFile);
            } else {
                writeToFile(zis, newFile);
            }
            Files.setLastModifiedTime(newFile, FileTime.fromMillis(ze.getLastModifiedTime().toMillis()));
            ze = zis.getNextEntry();
        }
    }

    private void writeToFile(ZipInputStream zis, Path newFile) throws IOException {
        try (OutputStream fos = Files.newOutputStream(newFile)) {
            int len;
            byte[] buffer = new byte[1024];
            while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
    }

    private void createParentDirectories(Path newFile) throws IOException {
        Files.createDirectories(newFile.getParent());
    }

    private ZipInputStream createZipInputStream(Path zip) throws IOException {
        return new ZipInputStream(Files.newInputStream(zip));
    }

    private void createOutputFolder(Path outputFolder) throws IOException {
        Files.createDirectories(outputFolder);
    }

}