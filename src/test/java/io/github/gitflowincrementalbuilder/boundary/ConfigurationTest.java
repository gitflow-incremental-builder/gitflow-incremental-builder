package io.github.gitflowincrementalbuilder.boundary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.gitflowincrementalbuilder.SystemPropertiesResetExtension;
import io.github.gitflowincrementalbuilder.boundary.Configuration.BuildUpstreamMode;
import io.github.gitflowincrementalbuilder.control.Property;

/**
 * Tests the system properties parsing logic in {@link Configuration}.
 *
 * @author famod
 *
 */
@ExtendWith({SystemPropertiesResetExtension.class, MockitoExtension.class})
public class ConfigurationTest {

    @TempDir
    Path tempDir;

    @Mock
    private MavenExecutionRequest mavenExecutionRequestMock;

    @Mock(lenient = true)
    private MavenSession mavenSessionMock;

    @Mock(lenient = true)
    private MavenProject currentProjectMock;

    private final Properties projectProperties = new Properties();

    @BeforeEach
    void before() {
        when(mavenSessionMock.getRequest()).thenReturn(mavenExecutionRequestMock);

        when(currentProjectMock.getProperties()).thenReturn(projectProperties);
        when(mavenSessionMock.getCurrentProject()).thenReturn(currentProjectMock);
    }

    @Test
    public void invalidProperty() {
        String invalidProperty = Property.PREFIX + "invalid";
        System.setProperty(invalidProperty, "invalid");

        assertThatIllegalArgumentException().isThrownBy(() -> new Configuration(mavenSessionMock))
                .withMessageContaining(invalidProperty)
                .withMessageContaining(Property.disableBranchComparison.prefixedName());
    }

    @Test
    public void mavenSession() {
        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.mavenSession).isSameAs(mavenSessionMock);
    }

    @Test
    public void currentProject_execRoot() {
        MavenProject execRootProject = mock(MavenProject.class);
        when(execRootProject.isExecutionRoot()).thenReturn(true);
        when(execRootProject.getProperties()).thenReturn(new Properties());
        when(mavenSessionMock.getProjects()).thenReturn(Arrays.asList(currentProjectMock, execRootProject));

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.currentProject).isSameAs(execRootProject);
    }

    @Test
    public void currentProject_execRootNoMatch() {
        MavenProject execRootProject = mock(MavenProject.class);
        when(mavenSessionMock.getProjects()).thenReturn(Arrays.asList(currentProjectMock, execRootProject));

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.currentProject).isSameAs(currentProjectMock);
    }

    @Test
    public void currentProject_noExecRoot() {
        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.currentProject).isSameAs(currentProjectMock);
    }

    @Test
    public void disable() {
        System.setProperty(Property.disable.prefixedName(), "true");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.disable).isTrue();
        assertThat(configuration.disableIfBranchMatches).isNull();
    }

    @Test
    public void disable_projectProperties() {
        projectProperties.put(Property.disable.prefixedName(), "true");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.disable).isTrue();
        assertThat(configuration.disableIfBranchMatches).isNull();
    }

    @Test
    public void disable_projectProperties_overriddenBySystemProperty() {
        projectProperties.put(Property.disable.prefixedName(), "false");
        System.setProperty(Property.disable.prefixedName(), "true");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.disable).isTrue();
        assertThat(configuration.disableIfBranchMatches).isNull();
    }

    @Test
    public void argsForUpstreamModules() {
        System.setProperty(Property.argsForUpstreamModules.prefixedName(), "x=true a=false");

        Configuration configuration = new Configuration(mavenSessionMock);

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("x", "true");
        expected.put("a", "false");
        assertThat(configuration.argsForUpstreamModules).isEqualTo(expected);
    }

    @Test
    public void excludeDownstreamModulesPackagedAs() {
        System.setProperty(Property.excludeDownstreamModulesPackagedAs.prefixedName(), "ear,war");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.excludeDownstreamModulesPackagedAs).isEqualTo(Arrays.asList("ear", "war"));
    }

    @Test
    public void forceBuildModules_pattern() {
        String expectedPatternString = ".*-some-artifact";
        System.setProperty(Property.forceBuildModules.prefixedName(), expectedPatternString);

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.forceBuildModules).as("Field forceBuildModules").isNotNull();
        assertThat(configuration.forceBuildModules).as("Number of Patterns in forceBuildModules").hasSize(1);
        Pattern pattern = configuration.forceBuildModules.get(0);
        assertThat(pattern).as("Pattern from forceBuildModules").isNotNull();
        assertThat(pattern.pattern()).as("String of Pattern from forceBuildModules").isEqualTo(expectedPatternString);

        assertThat(configuration.forceBuildModulesConditionally).isEmpty();
    }

    @Test
    public void forceBuildModules_patternInvalid() {
        System.setProperty(Property.forceBuildModules.prefixedName(), "*-some-artifact");   // pattern is missing the dot

        assertThatIllegalArgumentException().isThrownBy(() -> new Configuration(mavenSessionMock))
                .withMessageContaining(Property.forceBuildModules.prefixedName())
                .withCauseExactlyInstanceOf(PatternSyntaxException.class);
    }

    @Test
    public void forceBuildModules_withConditionals() {
        System.setProperty(Property.forceBuildModules.prefixedName(), "A, X=B|C, D, .*M, E.*=F|G.*");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.forceBuildModules).as("Field forceBuildModules").isNotNull();
        assertThat(configuration.forceBuildModules)
                .as("Pattern strings of forceBuildModules")
                .extracting(Pattern::pattern)
                .containsExactly("A", "D", ".*M");

        assertThat(configuration.forceBuildModulesConditionally).as("Field forceBuildModulesConditionally").isNotNull();
        assertThat(configuration.forceBuildModulesConditionally)
                .as("LHS pattern strings of forceBuildModulesConditionally")
                .extractingFromEntries(e -> Optional.ofNullable(e.getKey()).map(Pattern::pattern).orElse(null))
                .containsExactly("X", "E.*");
        assertThat(configuration.forceBuildModulesConditionally)
                .as("RHS pattern strings of forceBuildModulesConditionally")
                .extractingFromEntries(e -> Optional.ofNullable(e.getValue()).map(Pattern::pattern).orElse(null))
                .containsExactly("B|C", "F|G.*");
    }

    @Test
    public void forceBuildModules_onlyConditionals() {
        System.setProperty(Property.forceBuildModules.prefixedName(), "X=B|C");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.forceBuildModules).isEmpty();

        assertThat(configuration.forceBuildModulesConditionally).as("Field forceBuildModulesConditionally").isNotNull();
        assertThat(configuration.forceBuildModulesConditionally)
                .as("LHS pattern strings of forceBuildModulesConditionally")
                .extractingFromEntries(e -> Optional.ofNullable(e.getKey()).map(Pattern::pattern).orElse(null))
                .containsExactly("X");
        assertThat(configuration.forceBuildModulesConditionally)
                .as("RHS pattern strings of forceBuildModulesConditionally")
                .extractingFromEntries(e -> Optional.ofNullable(e.getValue()).map(Pattern::pattern).orElse(null))
                .containsExactly("B|C");
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // tests for configuration.buildUpstreamMode (which is calculated from two(!) properties: buildUpstream and buildUpstreamMode)

    @Test
    public void buildUpstreamMode_never() {
        System.setProperty(Property.buildUpstream.prefixedName(), "never");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildUpstreamMode).isSameAs(BuildUpstreamMode.NONE);
        verify(mavenExecutionRequestMock, never()).getMakeBehavior();
    }

    @Test
    public void buildUpstreamMode_false() {
        System.setProperty(Property.buildUpstream.prefixedName(), "false");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildUpstreamMode).isSameAs(BuildUpstreamMode.NONE);
        verify(mavenExecutionRequestMock, never()).getMakeBehavior();
    }

    @Test
    public void buildUpstreamMode_always() {
        System.setProperty(Property.buildUpstream.prefixedName(), "always");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildUpstreamMode).isSameAs(BuildUpstreamMode.CHANGED);
        verify(mavenExecutionRequestMock, never()).getMakeBehavior();
    }

    @Test
    public void buildUpstreamMode_true() {
        System.setProperty(Property.buildUpstream.prefixedName(), "true");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildUpstreamMode).isSameAs(BuildUpstreamMode.CHANGED);
        verify(mavenExecutionRequestMock, never()).getMakeBehavior();
    }

    @Test
    public void buildUpstreamMode_unknown() {
        System.setProperty(Property.buildUpstream.prefixedName(), "foo");

        assertThatIllegalArgumentException().isThrownBy(() -> new Configuration(mavenSessionMock))
                .withMessageContaining(Property.buildUpstream.prefixedName());
    }

    // tests for mode value 'derived' (default value)

    @Test
    public void buildUpstreamMode_derived_noMake() {
        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildUpstreamMode).isSameAs(BuildUpstreamMode.NONE);
    }

    @Test
    public void buildUpstreamMode_derived_makeUpstream() {
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildUpstreamMode).isSameAs(BuildUpstreamMode.CHANGED);
    }

    @Test
    public void buildUpstreamMode_derived_makeBoth() {
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_BOTH);

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildUpstreamMode).isSameAs(BuildUpstreamMode.CHANGED);
    }

    @Test
    public void buildUpstreamMode_derived_makeDownstream() {
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_DOWNSTREAM);

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildUpstreamMode).isSameAs(BuildUpstreamMode.NONE);
    }

    @Test
    public void buildUpstreamMode_derived_makeUpstream_impacted() {
        System.setProperty(Property.buildUpstreamMode.prefixedName(), "impacted");
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildUpstreamMode).isSameAs(BuildUpstreamMode.IMPACTED);
    }

    @Test
    public void buildUpstreamMode_derived_makeUpstream_unknown() {
        System.setProperty(Property.buildUpstreamMode.prefixedName(), "foo");
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        assertThatIllegalArgumentException().isThrownBy(() -> new Configuration(mavenSessionMock))
                .withMessageContaining(Property.buildUpstreamMode.prefixedName())
                .withCauseExactlyInstanceOf(IllegalArgumentException.class);
    }

    // just an example to show 'derived' can also be set explicitely
    @Test
    public void buildUpstreamMode_derivedExplicit_makeUpstream() {
        System.setProperty(Property.buildUpstream.prefixedName(), "derived");
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildUpstreamMode).isSameAs(BuildUpstreamMode.CHANGED);
    }

    // ///////////////////////////////////////
    // tests for configuration.buildDownstream

    // 'always' is default
    @Test
    public void buildDownstream() {
        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildDownstream).isTrue();
        verify(mavenExecutionRequestMock, times(1)).getMakeBehavior();  // called once for buildDownstreamMode
    }

    @Test
    public void buildDownstream_never() {
        System.setProperty(Property.buildDownstream.prefixedName(), "never");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildDownstream).isFalse();
        verify(mavenExecutionRequestMock, times(1)).getMakeBehavior();  // called once for buildDownstreamMode
    }

    @Test
    public void buildDownstream_false() {
        System.setProperty(Property.buildDownstream.prefixedName(), "false");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildDownstream).isFalse();
        verify(mavenExecutionRequestMock, times(1)).getMakeBehavior();  // called once for buildDownstreamMode
    }

    @Test
    public void buildDownstream_always() {
        System.setProperty(Property.buildDownstream.prefixedName(), "always");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildDownstream).isTrue();
        verify(mavenExecutionRequestMock, times(1)).getMakeBehavior();  // called once for buildDownstreamMode
    }

    @Test
    public void buildDownstream_true() {
        System.setProperty(Property.buildDownstream.prefixedName(), "true");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildDownstream).isTrue();
        verify(mavenExecutionRequestMock, times(1)).getMakeBehavior();  // called once for buildDownstreamMode
    }

    @Test
    public void buildDownstream_unknown() {
        System.setProperty(Property.buildDownstream.prefixedName(), "foo");

        assertThatIllegalArgumentException().isThrownBy(() -> new Configuration(mavenSessionMock))
                .withMessageContaining(Property.buildDownstream.prefixedName());
    }

    @Test
    public void buildDownstream_derived_noMake() {
        System.setProperty(Property.buildDownstream.prefixedName(), "derived");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildDownstream).isFalse();
    }

    @Test
    public void buildDownstream_derived_makeDownstream() {
        System.setProperty(Property.buildDownstream.prefixedName(), "derived");
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_DOWNSTREAM);

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildDownstream).isTrue();
    }

    @Test
    public void buildDownstream_derived_makeBoth() {
        System.setProperty(Property.buildDownstream.prefixedName(), "derived");
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_BOTH);

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildDownstream).isTrue();
    }

    @Test
    public void buildDownstream_derived_makeUpstream() {
        System.setProperty(Property.buildDownstream.prefixedName(), "derived");
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.buildDownstream).isFalse();
    }

    @Test
    public void plugin_baseBranch() {
        mockPluginConfig(Property.baseBranch.name(), "foo");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.baseBranch).isEqualTo("foo");
    }

    @Test
    public void plugin_baseBranch_projectProperties() {
        mockPluginConfig(Property.baseBranch.name(), "foo");
        projectProperties.put(Property.baseBranch.prefixedName(), "bar");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.baseBranch).isEqualTo("foo");
    }

    @Test
    public void plugin_noConfig_projectProperties() {
        mockPlugin(null);
        projectProperties.put(Property.baseBranch.prefixedName(), "foo");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.baseBranch).isEqualTo("foo");
    }

    @Test
    public void plugin_emptyConfigChildrenNull_projectProperties() {
        mockPlugin(mock(Xpp3Dom.class));
        projectProperties.put(Property.baseBranch.prefixedName(), "foo");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.baseBranch).isEqualTo("foo");
    }

    @Test
    public void plugin_emptyConfigChildrenEmpty_projectProperties() {
        Xpp3Dom configMock = mock(Xpp3Dom.class);
        when(configMock.getChildren()).thenReturn(new Xpp3Dom[] {});
        mockPlugin(configMock);
        projectProperties.put(Property.baseBranch.prefixedName(), "foo");

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.baseBranch).isEqualTo("foo");
    }

    @Test
    public void noCurrentProject() {
        when(mavenSessionMock.getCurrentProject()).thenReturn(null);

        Configuration configuration = new Configuration(mavenSessionMock);

        assertThat(configuration.disable).isTrue();
    }

    private void mockPluginConfig(String propertyName, String value) {
        Xpp3Dom childConfigMock = mock(Xpp3Dom.class);
        when(childConfigMock.getName()).thenReturn(propertyName);
        when(childConfigMock.getValue()).thenReturn(value);
        Xpp3Dom configMock = mock(Xpp3Dom.class);
        when(configMock.getChildren()).thenReturn(new Xpp3Dom[]{ childConfigMock });
        mockPlugin(configMock);
    }

    private void mockPlugin(Xpp3Dom config) {
        Plugin pluginMock = mock(Plugin.class);
        when(pluginMock.getConfiguration()).thenReturn(config);
        when(currentProjectMock.getPlugin(Configuration.PLUGIN_KEY)).thenReturn(pluginMock);
    }
}
