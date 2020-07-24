package com.vackosar.gitflowincrementalbuild.jgit;

import com.google.common.io.Files;
import com.vackosar.gitflowincrementalbuild.boundary.Configuration;
import com.vackosar.gitflowincrementalbuild.control.Property;
import com.vackosar.gitflowincrementalbuild.control.jgit.GitProvider;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GitProviderTest {

    @Mock
    private MavenSession mavenSessionMock;

    private GitProvider underTest = new GitProvider();

    @BeforeEach
    public void setup() {
        MavenProject projectMock = mock(MavenProject.class);
        when(projectMock.getProperties()).thenReturn(new Properties());
        projectMock.getProperties().put(Property.enabled.prefixedName(), "false"); // otherwise unrelated stuff in MavenSession would need mocking
        when(mavenSessionMock.getTopLevelProject()).thenReturn(projectMock);
    }

    @AfterEach
    public void tearDown() {
        underTest.close();
    }

    @Test
    public void test_simple() throws IOException {
        initSimple();
        assertNotNull(underTest.get(new Configuration(mavenSessionMock)));

        verify(mavenSessionMock).getCurrentProject();
    }

    private void initSimple() throws IOException {
        MavenProject mavenProject = new MavenProject();
        File pomXmlInCwd = new File("pom.xml").getCanonicalFile();
        File pomXmlInParent = new File("..", "pom.xml").getCanonicalFile();
        mavenProject.setFile(pomXmlInParent);
        if (pomXmlInCwd.exists()) {
            mavenProject.setFile(pomXmlInCwd);
        }
        doReturn(mavenProject).when(mavenSessionMock).getCurrentProject();
    }

    @Test
    public void test_no_git_dir() {

        MavenProject mavenProject = new MavenProject();
        File tempDir = Files.createTempDir();
        File pom = new File(tempDir, "pom.xml");
        mavenProject.setFile(pom);
        doReturn(mavenProject).when(mavenSessionMock).getCurrentProject();

        assertThrows(SkipExecutionException.class, () -> underTest.get(new Configuration(mavenSessionMock)));
    }
}