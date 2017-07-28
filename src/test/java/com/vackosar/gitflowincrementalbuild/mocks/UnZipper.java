package com.vackosar.gitflowincrementalbuild.mocks;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UnZipper {

    public void act(File zip, File outputFolder){
        try{
            createOutputFolder(outputFolder);
            try (ZipInputStream zis = createZipInputStream(zip)) {
                process(outputFolder, zis);
                zis.closeEntry();
            }
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    private void process(File outputFolder, ZipInputStream zis) throws IOException {
        ZipEntry ze = zis.getNextEntry();
        while(ze != null){
            String fileName = ze.getName();
            File newFile = new File(outputFolder, fileName);
            createParentDirectories(newFile);
            if (ze.isDirectory()) {
                newFile.mkdir();
            } else {
                writeToFile(zis, newFile);
            }
            newFile.setLastModified(ze.getLastModifiedTime().toMillis());
            ze = zis.getNextEntry();
        }
    }

    private void writeToFile(ZipInputStream zis, File newFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(newFile)) {
            int len;
            byte[] buffer = new byte[1024];
            while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
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

}