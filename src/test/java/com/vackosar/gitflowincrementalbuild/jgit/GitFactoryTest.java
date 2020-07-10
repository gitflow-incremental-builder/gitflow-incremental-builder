package com.vackosar.gitflowincrementalbuild.jgit;

import com.google.common.io.Files;
import com.vackosar.gitflowincrementalbuild.boundary.Configuration;
import com.vackosar.gitflowincrementalbuild.control.jgit.GitFactory;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class GitFactoryTest {

    private MavenSession mavenSessionMock = mock(MavenSession.class);
    private Configuration configuration = mock(Configuration.class);

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

    @BeforeEach
    @AfterEach
    public void tearDown() {
        GitFactory.destroy();
    }

    @Test
    public void test_simple() throws IOException {
        initSimple();
        assertNotNull(GitFactory.getOrCreateThreadLocalGit(mavenSessionMock, configuration));
        
        verify(mavenSessionMock).getCurrentProject();
    }
    
    @Test
    public void test_bind() throws IOException {
        Git git = mock(Git.class);
        Repository repository = mock(Repository.class);
        doReturn(repository).when(git).getRepository();
        
        GitFactory.bind(git);
        assertNotNull(GitFactory.getOrCreateThreadLocalGit(mavenSessionMock, configuration));
        
        verifyNoInteractions(mavenSessionMock, configuration);
    }

    @Test
    public void test_no_git_dir() {

        MavenProject mavenProject = new MavenProject();
        File tempDir = Files.createTempDir();
        File pom = new File(tempDir, "pom.xml");
        mavenProject.setFile(pom);
        doReturn(mavenProject).when(mavenSessionMock).getCurrentProject();

        assertThrows(SkipExecutionException.class, () -> GitFactory.getOrCreateThreadLocalGit(mavenSessionMock, configuration));
    }
}