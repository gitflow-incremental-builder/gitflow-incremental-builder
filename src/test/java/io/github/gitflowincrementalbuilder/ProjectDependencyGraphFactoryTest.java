package io.github.gitflowincrementalbuilder;

import static io.github.gitflowincrementalbuilder.ProjectDependencyGraphFactory.createGraph;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.graph.DefaultProjectDependencyGraph;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.gitflowincrementalbuilder.config.Configuration;
import io.github.gitflowincrementalbuilder.config.Property;
import io.github.gitflowincrementalbuilder.util.LoggerSpyUtil;

@ExtendWith(MockitoExtension.class)
public class ProjectDependencyGraphFactoryTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private MavenSession mavenSessionMock;

    @Mock
    private ProjectDependencyGraph sessionGraphMock;

    private final Properties projectProperties = new Properties();

    @BeforeEach
    void setUp() {
        var currentProject = mock(MavenProject.class);
        when(currentProject.getProperties()).thenReturn(projectProperties);
        when(mavenSessionMock.getCurrentProject()).thenReturn(currentProject);
        when(mavenSessionMock.getProjectDependencyGraph()).thenReturn(sessionGraphMock);
    }

    @Test
    void createGraph_modeOff() {
        projectProperties.setProperty(Property.rebuildProjectDependencyGraphMode.prefixedName(), "off");

        var result = createGraph(List.of(), new Configuration(mavenSessionMock), false);
        
        assertThat(result).isSameAs(sessionGraphMock);
    }

    @Test
    void createGraph_modeOff_force() {
        projectProperties.setProperty(Property.rebuildProjectDependencyGraphMode.prefixedName(), "off");

        var result = createGraph(List.of(), new Configuration(mavenSessionMock), true);
        
        assertThat(result)
                .isExactlyInstanceOf(DefaultProjectDependencyGraph.class)
                .isNotSameAs(sessionGraphMock);
    }

    // note: Maven38DefaultDependencyGraph case not testable due to https://github.com/mockito/mockito/issues/3629,
    //       same for CycleDetectedException and DuplicateProjectException cases, as they are thrown from the constructor
    
    @Test
    void createGraph_slow() throws Exception {
        // logger spy has to be installed via reflection because the field in the target class is static
        Logger originalLogger = LoggerFactory.getLogger(ProjectDependencyGraphFactory.class);
        Field loggerField = FieldUtils.getField(ProjectDependencyGraphFactory.class, "logger", true);
        Logger loggerSpy = LoggerSpyUtil.buildSpiedLoggerFor(ProjectDependencyGraphFactory.class);
        FieldUtils.writeStaticField(loggerField, loggerSpy, true);

        try (var mockedConstruction = mockConstruction(DefaultProjectDependencyGraph.class, (mock, context) -> {
                Thread.sleep(ProjectDependencyGraphFactory.REBUILD_DURATION_THRESHOLD_MS + 1);
            })) {

            var result = createGraph(List.of(), new Configuration(mavenSessionMock), false);

            assertThat(result)
                    .isExactlyInstanceOf(DefaultProjectDependencyGraph.class)
                    .isNotSameAs(sessionGraphMock);

            verify(loggerSpy).info(contains("this rebuild can be switched off via property"), any(Object[].class));
        } finally {
            FieldUtils.writeStaticField(loggerField, originalLogger, true);
        }
    }

}
