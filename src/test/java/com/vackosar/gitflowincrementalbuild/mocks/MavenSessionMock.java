package com.vackosar.gitflowincrementalbuild.mocks;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MavenSessionMock {

    public static MavenSession get() throws Exception {
        return get(LocalRepoMock.WORK_DIR);
    }

    public static MavenSession get(Path workDir) throws Exception {
        PomFinder finder = new PomFinder();
        Files.walkFileTree(workDir, finder);
        List<MavenProject> projects = finder.projects.stream().map(MavenSessionMock::createProject).collect(Collectors.toList());
        MavenSession mavenSession = mock(MavenSession.class);
        when(mavenSession.getCurrentProject()).thenReturn(projects.get(0));
        MavenExecutionRequest request = mock(MavenExecutionRequest.class);
        when(mavenSession.getRequest()).thenReturn(request);
        when(mavenSession.getProjects()).thenReturn(projects);
        when(mavenSession.getTopLevelProject()).thenReturn(projects.get(0));
        return mavenSession;
    }

    private static MavenProject createProject(Path path) {
        MavenProject project = new MavenProject();
        Model model = new Model();
        model.setProperties(new Properties());
        project.setModel(model);
        project.setArtifactId(path.getFileName().toString());
        project.setGroupId(path.getFileName().toString());
        project.setVersion("1");
        project.setFile(path.resolve("pom.xml").toFile());
        return project;
    }

    private static class PomFinder implements FileVisitor<Path> {

        public List<Path> projects = new ArrayList<>();

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if ("pom.xml".equals(file.getFileName().toString())) {
                projects.add(file.getParent());
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return null;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }
    }

}
