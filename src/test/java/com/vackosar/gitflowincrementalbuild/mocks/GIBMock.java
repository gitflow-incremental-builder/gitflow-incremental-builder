package com.vackosar.gitflowincrementalbuild.mocks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class GIBMock {

    public Path dirName = null;

    private void delete(File f) {
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                delete(c);
            }
        }
        if (!f.delete()) {
            throw new RuntimeException("Failed to delete file: " + f);
        }
    }

    public GIBMock() throws IOException {
        final File zip =
                Arrays.asList(Paths.get(LocalRepoMock.TEST_WORK_DIR + "target")
                        .toFile()
                        .listFiles())
                        .stream()
                        .filter(file -> file.getName().endsWith(".zip"))
                        .findFirst()
                        .get();
        System.out.println(zip);
        new UnZiper().act(zip, new File(LocalRepoMock.TEST_WORK_DIR + "tmp"));
        dirName =  Paths.get(LocalRepoMock.TEST_WORK_DIR + "tmp/" + zip.getName().replaceAll("-bin.zip$", "")).normalize().toAbsolutePath().toRealPath();
    }

    public Process execute(Path pom) throws IOException, InterruptedException {
        final Process process =
                new ProcessBuilder()
                        .command(dirName.toString() + "/gib.bat", pom.toString())
                        .directory(new File("tmp/repo"))
                        .start();
        process.waitFor();
        return process;
    }

    public void close() {
        delete(dirName.toFile());
    }

}
