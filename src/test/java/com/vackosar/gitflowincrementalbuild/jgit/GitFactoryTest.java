package com.vackosar.gitflowincrementalbuild.jgit;

import com.google.common.io.Files;
import com.vackosar.gitflowincrementalbuild.boundary.Configuration;
import com.vackosar.gitflowincrementalbuild.control.jgit.GitFactory;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class GitFactoryTest {

    private MavenSession mavenSessionMock = mock(MavenSession.class);
    private Configuration configuration = mock(Configuration.class);
    private GitFactory underTest;

    public void initSimple() throws IOException {
        MavenProject mavenProject = new MavenProject();
        File pomXmlInCwd = new File("pom.xml").getCanonicalFile();
        File pomXmlInParent = new File("..", "pom.xml").getCanonicalFile();
        mavenProject.setFile(pomXmlInParent);
        if (pomXmlInCwd.exists()) {
            mavenProject.setFile(pomXmlInCwd);
        }
        doReturn(mavenProject).when(mavenSessionMock).getCurrentProject();

        underTest = GitFactory.newInstance(mavenSessionMock, configuration);

        MockitoAnnotations.initMocks(this);
    }

    @AfterEach
    public void tearDown() {
        if (underTest != null) {
            underTest.close();
        }
    }

    @Test
    public void test_simple() throws IOException {
        initSimple();
        assertNotNull(underTest.get());
        assertThat(underTest.getBranchName()).isNotEmpty();
    }

    @Test
    public void test_no_git_dir() {

        MavenProject mavenProject = new MavenProject();
        File tempDir = Files.createTempDir();
        File pom = new File(tempDir, "pom.xml");
        mavenProject.setFile(pom);
        doReturn(mavenProject).when(mavenSessionMock).getCurrentProject();

        assertThrows(SkipExecutionException.class, () -> GitFactory.newInstance(mavenSessionMock, configuration));
    }
}