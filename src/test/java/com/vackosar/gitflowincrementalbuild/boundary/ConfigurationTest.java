package com.vackosar.gitflowincrementalbuild.boundary;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Arrays;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.common.collect.ImmutableMap;
import com.vackosar.gitflowincrementalbuild.SystemPropertiesResetExtension;
import com.vackosar.gitflowincrementalbuild.boundary.Configuration.BuildUpstreamMode;
import com.vackosar.gitflowincrementalbuild.control.Property;

/**
 * Tests the system properties parsing logic in {@link Configuration}.
 *
 * @author famod
 *
 */
@ExtendWith({SystemPropertiesResetExtension.class, MockitoExtension.class})
public class ConfigurationTest {

    @Mock
    private MavenExecutionRequest mavenExecutionRequestMock;

    @Mock(lenient = true)
    private MavenSession mavenSessionMock;

    @Mock(lenient = true)
    private MavenProject mockTLProject;

    private final Properties projectProperties = new Properties();

    @BeforeEach
    void before() {
        when(mavenSessionMock.getRequest()).thenReturn(mavenExecutionRequestMock);

        when(mockTLProject.getProperties()).thenReturn(projectProperties);
        when(mavenSessionMock.getTopLevelProject()).thenReturn(mockTLProject);
    }

    @Test
    public void invalidProperty() {
        String invalidProperty = Property.PREFIX + "invalid";
        System.setProperty(invalidProperty, "invalid");

        assertThatIllegalArgumentException().isThrownBy(() -> new Configuration.Provider(mavenSessionMock).get())
                .withMessageContaining(invalidProperty)
                .withMessageContaining(Property.disableBranchComparison.prefixedName());
    }

    @Test
    public void enabled() {
        System.setProperty(Property.enabled.prefixedName(), "false");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertFalse(configuration.enabled);
        assertNull(configuration.disableIfBranchRegex);
    }

    @Test
    public void enabled_projectProperties() {
        projectProperties.put(Property.enabled.prefixedName(), "false");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertFalse(configuration.enabled);
        assertNull(configuration.disableIfBranchRegex);
    }

    @Test
    public void enabled_projectProperties_overriddenBySystemProperty() {
        projectProperties.put(Property.enabled.prefixedName(), "true");
        System.setProperty(Property.enabled.prefixedName(), "false");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertFalse(configuration.enabled);
        assertNull(configuration.disableIfBranchRegex);
    }

    @Test
    public void argsForUpstreamModules() {
        System.setProperty(Property.argsForUpstreamModules.prefixedName(), "x=true a=false");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertEquals(ImmutableMap.of("x", "true", "a", "false"), configuration.argsForUpstreamModules);
    }

    @Test
    public void excludeDownstreamModulesPackagedAs() {
        System.setProperty(Property.excludeDownstreamModulesPackagedAs.prefixedName(), "ear,war");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertEquals(Arrays.asList("ear", "war"), configuration.excludeDownstreamModulesPackagedAs);
    }

    // deprecated old name of excludeDownstreamModulesPackagedAs
    @Test
    public void excludeTransitiveModulesPackagedAs() {
        System.setProperty(Property.excludeDownstreamModulesPackagedAs.deprecatedPrefixedName(), "ear,war");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertEquals(Arrays.asList("ear", "war"), configuration.excludeDownstreamModulesPackagedAs);
    }

    @Test
    public void forceBuildModules_pattern() {
        String expectedPatternString = ".*-some-artifact";
        System.setProperty(Property.forceBuildModules.prefixedName(), expectedPatternString);

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertNotNull(configuration.forceBuildModules,"Field forceBuildModules is null");
        assertEquals( 1, configuration.forceBuildModules.size(), "Unexpected number of Patterns in forceBuildModules");
        Pattern pattern = configuration.forceBuildModules.get(0);
        assertNotNull(pattern, "Pattern form forceBuildModules is null");
        assertEquals(expectedPatternString, pattern.pattern(), "Unexpected pattern string of Pattern from forceBuildModules");
    }

    @Test
    public void forceBuildModules_patternInvalid() {
        System.setProperty(Property.forceBuildModules.prefixedName(), "*-some-artifact");   // pattern is missing the dot

        assertThatIllegalArgumentException().isThrownBy(() -> new Configuration.Provider(mavenSessionMock).get())
                .withMessageContaining(Property.forceBuildModules.prefixedName())
                .withCauseExactlyInstanceOf(PatternSyntaxException.class);
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // tests for configuration.buildUpstreamMode (which is calculated from two(!) properties: buildUpstream and buildUpstreamMode)

    @Test
    public void buildUpstreamMode_never() {
        System.setProperty(Property.buildUpstream.prefixedName(), "never");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertSame(BuildUpstreamMode.NONE, configuration.buildUpstreamMode);
        verify(mavenExecutionRequestMock, never()).getMakeBehavior();
    }

    @Test
    public void buildUpstreamMode_false() {
        System.setProperty(Property.buildUpstream.prefixedName(), "false");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertSame(BuildUpstreamMode.NONE, configuration.buildUpstreamMode);
        verify(mavenExecutionRequestMock, never()).getMakeBehavior();
    }

    @Test
    public void buildUpstreamMode_always() {
        System.setProperty(Property.buildUpstream.prefixedName(), "always");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertSame(BuildUpstreamMode.CHANGED, configuration.buildUpstreamMode);
        verify(mavenExecutionRequestMock, never()).getMakeBehavior();
    }

    @Test
    public void buildUpstreamMode_true() {
        System.setProperty(Property.buildUpstream.prefixedName(), "true");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertSame(BuildUpstreamMode.CHANGED, configuration.buildUpstreamMode);
        verify(mavenExecutionRequestMock, never()).getMakeBehavior();
    }

    @Test
    public void buildUpstreamMode_unknown() {
        System.setProperty(Property.buildUpstream.prefixedName(), "foo");

        assertThatIllegalArgumentException().isThrownBy(() -> new Configuration.Provider(mavenSessionMock).get())
                .withMessageContaining(Property.buildUpstream.prefixedName());
    }

    // tests for mode value 'derived' (default value)

    @Test
    public void buildUpstreamMode_derived_noMake() {
        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertSame(BuildUpstreamMode.NONE, configuration.buildUpstreamMode);
    }

    @Test
    public void buildUpstreamMode_derived_makeUpstream() {
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertSame(BuildUpstreamMode.CHANGED, configuration.buildUpstreamMode);
    }

    @Test
    public void buildUpstreamMode_derived_makeBoth() {
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_BOTH);

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertSame(BuildUpstreamMode.CHANGED, configuration.buildUpstreamMode);
    }

    @Test
    public void buildUpstreamMode_derived_makeDownstream() {
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_DOWNSTREAM);

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertSame(BuildUpstreamMode.NONE, configuration.buildUpstreamMode);
    }

    @Test
    public void buildUpstreamMode_derived_makeUpstream_impacted() {
        System.setProperty(Property.buildUpstreamMode.prefixedName(), "impacted");
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertSame(BuildUpstreamMode.IMPACTED, configuration.buildUpstreamMode);
    }

    @Test
    public void buildUpstreamMode_derived_makeUpstream_unknown() {
        System.setProperty(Property.buildUpstreamMode.prefixedName(), "foo");
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        assertThatIllegalArgumentException().isThrownBy(() -> new Configuration.Provider(mavenSessionMock).get())
                .withMessageContaining(Property.buildUpstreamMode.prefixedName())
                .withCauseExactlyInstanceOf(IllegalArgumentException.class);
    }

    // just an example to show 'derived' can also be set explicitely
    @Test
    public void buildUpstreamMode_derivedExplicit_makeUpstream() {
        System.setProperty(Property.buildUpstream.prefixedName(), "derived");
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertSame(BuildUpstreamMode.CHANGED, configuration.buildUpstreamMode);
    }

    // ///////////////////////////////////////
    // tests for configuration.buildDownstream

    // 'always' is default
    @Test
    public void buildDownstream() {
        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertTrue(configuration.buildDownstream);
        verify(mavenExecutionRequestMock, times(1)).getMakeBehavior();  // called once for buildDownstreamMode
    }

    @Test
    public void buildDownstream_never() {
        System.setProperty(Property.buildDownstream.prefixedName(), "never");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertFalse(configuration.buildDownstream);
        verify(mavenExecutionRequestMock, times(1)).getMakeBehavior();  // called once for buildDownstreamMode
    }

    @Test
    public void buildDownstream_false() {
        System.setProperty(Property.buildDownstream.prefixedName(), "false");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertFalse(configuration.buildDownstream);
        verify(mavenExecutionRequestMock, times(1)).getMakeBehavior();  // called once for buildDownstreamMode
    }

    @Test
    public void buildDownstream_always() {
        System.setProperty(Property.buildDownstream.prefixedName(), "always");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertTrue(configuration.buildDownstream);
        verify(mavenExecutionRequestMock, times(1)).getMakeBehavior();  // called once for buildDownstreamMode
    }

    @Test
    public void buildDownstream_true() {
        System.setProperty(Property.buildDownstream.prefixedName(), "true");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertTrue(configuration.buildDownstream);
        verify(mavenExecutionRequestMock, times(1)).getMakeBehavior();  // called once for buildDownstreamMode
    }

    @Test
    public void buildDownstream_unknown() {
        System.setProperty(Property.buildDownstream.prefixedName(), "foo");

        assertThatIllegalArgumentException().isThrownBy(() -> new Configuration.Provider(mavenSessionMock).get())
                .withMessageContaining(Property.buildDownstream.prefixedName());
    }

    @Test
    public void buildDownstream_derived_noMake() {
        System.setProperty(Property.buildDownstream.prefixedName(), "derived");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertFalse(configuration.buildDownstream);
    }

    @Test
    public void buildDownstream_derived_makeDownstream() {
        System.setProperty(Property.buildDownstream.prefixedName(), "derived");
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_DOWNSTREAM);

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertTrue(configuration.buildDownstream);
    }

    @Test
    public void buildDownstream_derived_makeBoth() {
        System.setProperty(Property.buildDownstream.prefixedName(), "derived");
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_BOTH);

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertTrue(configuration.buildDownstream);
    }

    @Test
    public void buildDownstream_derived_makeUpstream() {
        System.setProperty(Property.buildDownstream.prefixedName(), "derived");
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertFalse(configuration.buildDownstream);
    }

    @Test
    public void plugin_baseBranch() {
        mockPluginConfig(Property.baseBranch.name(), "foo");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertEquals("foo", configuration.baseBranch);
    }

    @Test
    public void plugin_baseBranch_projectProperties() {
        mockPluginConfig(Property.baseBranch.name(), "foo");
        projectProperties.put(Property.baseBranch.prefixedName(), "bar");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertEquals("foo", configuration.baseBranch);
    }

    @Test
    public void plugin_noConfig_projectProperties() {
        mockPlugin(null);
        projectProperties.put(Property.baseBranch.prefixedName(), "foo");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertEquals("foo", configuration.baseBranch);
    }

    @Test
    public void plugin_emptyConfigChildrenNull_projectProperties() {
        mockPlugin(mock(Xpp3Dom.class));
        projectProperties.put(Property.baseBranch.prefixedName(), "foo");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertEquals("foo", configuration.baseBranch);
    }

    @Test
    public void plugin_emptyConfigChildrenEmpty_projectProperties() {
        Xpp3Dom configMock = mock(Xpp3Dom.class);
        when(configMock.getChildren()).thenReturn(new Xpp3Dom[] {});
        mockPlugin(configMock);
        projectProperties.put(Property.baseBranch.prefixedName(), "foo");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertEquals("foo", configuration.baseBranch);
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
        when(mockTLProject.getPlugin(Configuration.PLUGIN_KEY)).thenReturn(pluginMock);
    }
}
