package com.vackosar.gitflowincrementalbuild.jgit;

import com.vackosar.gitflowincrementalbuild.boundary.Configuration;
import com.vackosar.gitflowincrementalbuild.control.Property;
import com.vackosar.gitflowincrementalbuild.control.jgit.GitProvider;
import com.vackosar.gitflowincrementalbuild.entity.SkipExecutionException;
import com.vackosar.gitflowincrementalbuild.mocks.EmptyLocalRepoMock;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GitProviderTest {

    @TempDir
    Path tempDir;

    @Mock
    private MavenSession mavenSessionMock;

    @Mock
    private MavenProject currentProjectMock;

    private final GitProvider underTest = new GitProvider();

    @BeforeEach
    void setup() {
        when(currentProjectMock.getProperties()).thenReturn(new Properties());
        currentProjectMock.getProperties().put(Property.disable.prefixedName(), "true"); // otherwise unrelated stuff in MavenSession would need mocking
        when(mavenSessionMock.getCurrentProject()).thenReturn(currentProjectMock);
    }

    @AfterEach
    void tearDown() {
        underTest.close();
    }

    @Test
    public void get() throws IOException, URISyntaxException, GitAPIException {
        EmptyLocalRepoMock.withBasicPom(tempDir, emptyLocalRepoMock -> {
            when(currentProjectMock.getBasedir()).thenReturn(emptyLocalRepoMock.getRepoDir().toFile());

            assertNotNull(underTest.get(new Configuration(mavenSessionMock)));
        });
    }

    @Test
    public void get_noGitDir() {

        when(currentProjectMock.getBasedir()).thenReturn(tempDir.toFile());

        assertThrows(SkipExecutionException.class, () -> underTest.get(new Configuration(mavenSessionMock)));
    }
}