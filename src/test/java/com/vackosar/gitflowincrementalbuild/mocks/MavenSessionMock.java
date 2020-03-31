package com.vackosar.gitflowincrementalbuild.mocks;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import static org.mockito.Mockito.withSettings;

public class MavenSessionMock {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenSessionMock.class);

    public static MavenSession get(Path workDir, Properties topLevelProjectProperties) throws Exception {
        PomFinder finder = new PomFinder();
        Files.walkFileTree(workDir, finder);
        List<MavenProject> projects = finder.projects.stream()
                .sorted()
                .map(MavenSessionMock::createProject)
                .collect(Collectors.toList());

        MavenProject topLevelProject = projects.get(0);
        topLevelProject.getModel().setProperties(topLevelProjectProperties);
        MavenSession mavenSession = mock(MavenSession.class, withSettings().lenient());
        when(mavenSession.getCurrentProject()).thenReturn(topLevelProject);
        MavenExecutionRequest request = mock(MavenExecutionRequest.class);
        when(mavenSession.getRequest()).thenReturn(request);
        when(mavenSession.getAllProjects()).thenReturn(projects);
        when(mavenSession.getProjects()).thenReturn(projects);
        when(mavenSession.getTopLevelProject()).thenReturn(topLevelProject);
        ProjectDependencyGraph dependencyGraphMock = mock(ProjectDependencyGraph.class);
        when(mavenSession.getProjectDependencyGraph()).thenReturn(dependencyGraphMock);
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
            if (file.getFileName().toString().contains("gc.log.lock")) {
                LOGGER.debug("Failed to visit {}", file);
            } else {
                LOGGER.warn("Failed to visit " + file, exc);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }
    }

}
