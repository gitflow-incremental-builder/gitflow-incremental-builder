package com.vackosar.gitflowincrementalbuild;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UnZiper {

    public void act(File zip, File outputFolder){
        try{
            createOutputFolder(outputFolder);
            ZipInputStream zis = createZipInputStream(zip);
            process(outputFolder, zis);
            zis.closeEntry();
            zis.close();
        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    private void process(File outputFolder, ZipInputStream zis) throws IOException {
        ZipEntry ze = zis.getNextEntry();
        while(ze!=null){
            String fileName = ze.getName();
            File newFile = new File(outputFolder.getPath() + File.separator + fileName);
            createParentDirectories(newFile);
            if (ze.isDirectory()) {
                newFile.mkdir();
            } else {
                writeToFile(zis, newFile);
            }
            newFile.setLastModified(ze.getLastModifiedTime().toMillis());
            setFileCreationDate(newFile, ze.getLastModifiedTime());
            ze = zis.getNextEntry();
        }
    }

    private void writeToFile(ZipInputStream zis, File newFile) throws IOException {
        FileOutputStream fos = new FileOutputStream(newFile);
        int len;
        byte[] buffer = new byte[1024];
        while ((len = zis.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
        }
        fos.close();
    }

    private void createParentDirectories(File newFile) {
        new File(newFile.getParent()).mkdirs();
    }

    private ZipInputStream createZipInputStream(File zip) throws FileNotFoundException {
        return new ZipInputStream(new FileInputStream(zip));
    }

    private void createOutputFolder(File outputFolder) {
        if(!outputFolder.exists()){
            outputFolder.mkdir();
        }
    }

    public void setFileCreationDate(File file, FileTime creationDate) throws IOException{
        BasicFileAttributeView attributes = Files.getFileAttributeView(Paths.get(file.getPath()), BasicFileAttributeView.class);
        attributes.setTimes(creationDate, creationDate, creationDate);
    }
}