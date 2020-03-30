package com.vackosar.gitflowincrementalbuild.boundary;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
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
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.common.collect.ImmutableMap;
import com.vackosar.gitflowincrementalbuild.SystemPropertiesResetRule;
import com.vackosar.gitflowincrementalbuild.boundary.Configuration.BuildUpstreamMode;
import com.vackosar.gitflowincrementalbuild.control.Property;

/**
 * Tests the system properties parsing logic in {@link Configuration}.
 *
 * @author famod
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class ConfigurationTest {

    @Rule
    public final SystemPropertiesResetRule sysPropResetRule = new SystemPropertiesResetRule();

    @Mock
    private MavenExecutionRequest mavenExecutionRequestMock;

    @Mock
    private MavenSession mavenSessionMock;

    private final Properties projectProperties = new Properties();

    @Before
    public void setup() {
        when(mavenSessionMock.getRequest()).thenReturn(mavenExecutionRequestMock);

        MavenProject mockTLProject = mock(MavenProject.class);
        when(mockTLProject.getProperties()).thenReturn(projectProperties);
        when(mavenSessionMock.getTopLevelProject()).thenReturn(mockTLProject);
    }

    @Test
    public void invalidProperty() {
        String invalidProperty = Property.PREFIX + "invalid";
        System.setProperty(invalidProperty, "invalid");

        IllegalArgumentException expectedException = assertThrows(IllegalArgumentException.class,
                () -> new Configuration.Provider(mavenSessionMock).get());

        assertThat(expectedException.getMessage(), containsString(invalidProperty));
        assertThat(expectedException.getMessage(), containsString(Property.disableBranchComparison.fullName())); // just one of those valid ones
    }

    @Test
    public void enabled() {
        System.setProperty(Property.enabled.fullName(), "false");

        assertFalse(Configuration.isEnabled(mavenSessionMock));
    }

    @Test
    public void enabled_projectProperties() {
        projectProperties.put(Property.enabled.fullName(), "false");

        assertFalse(Configuration.isEnabled(mavenSessionMock));
    }

    @Test
    public void enabled_projectProperties_overriddenBySystemProperty() {
        projectProperties.put(Property.enabled.fullName(), "true");
        System.setProperty(Property.enabled.fullName(), "false");

        assertFalse(Configuration.isEnabled(mavenSessionMock));
    }

    @Test
    public void argsForUpstreamModules() {
        System.setProperty(Property.argsForUpstreamModules.fullName(), "x=true a=false");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertEquals(ImmutableMap.of("x", "true", "a", "false"), configuration.argsForUpstreamModules);
    }

    // deprecated old name of argsForUpstreamModules
    @Test
    public void argsForNotImpactedModules() {
        System.setProperty(Property.argsForUpstreamModules.deprecatedFullName(), "x=true a=false");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertEquals(ImmutableMap.of("x", "true", "a", "false"), configuration.argsForUpstreamModules);
    }

    @Test
    public void excludeDownstreamModulesPackagedAs() {
        System.setProperty(Property.excludeDownstreamModulesPackagedAs.fullName(), "ear,war");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertEquals(Arrays.asList("ear", "war"), configuration.excludeDownstreamModulesPackagedAs);
    }

    // deprecated old name of excludeDownstreamModulesPackagedAs
    @Test
    public void excludeTransitiveModulesPackagedAs() {
        System.setProperty(Property.excludeDownstreamModulesPackagedAs.deprecatedFullName(), "ear,war");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertEquals(Arrays.asList("ear", "war"), configuration.excludeDownstreamModulesPackagedAs);
    }

    @Test
    public void forceBuildModules_pattern() {
        String expectedPatternString = ".*-some-artifact";
        System.setProperty(Property.forceBuildModules.fullName(), expectedPatternString);

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertNotNull("Field forceBuildModules is null", configuration.forceBuildModules);
        assertEquals("Unexpected number of Patterns in forceBuildModules", 1, configuration.forceBuildModules.size());
        Pattern pattern = configuration.forceBuildModules.get(0);
        assertNotNull("Pattern form forceBuildModules is null", pattern);
        assertEquals("Unexpected pattern string of Pattern from forceBuildModules", expectedPatternString, pattern.pattern());
    }

    @Test
    public void forceBuildModules_patternInvalid() {
        System.setProperty(Property.forceBuildModules.fullName(), "*-some-artifact");   // pattern is missing the dot

        IllegalArgumentException expectedException = assertThrows(IllegalArgumentException.class,
                () -> new Configuration.Provider(mavenSessionMock).get());

        assertThat(expectedException.getMessage(), containsString(Property.forceBuildModules.fullName()));
        assertThat(expectedException.getCause(), instanceOf(PatternSyntaxException.class));
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // tests for configuration.buildUpstreamMode (which is calculated from two(!) properties: buildUpstream and buildUpstreamMode)

    @Test
    public void buildUpstreamMode_never() {
        System.setProperty(Property.buildUpstream.fullName(), "never");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertSame(BuildUpstreamMode.NONE, configuration.buildUpstreamMode);
        verify(mavenExecutionRequestMock, never()).getMakeBehavior();
    }

    @Test
    public void buildUpstreamMode_false() {
        System.setProperty(Property.buildUpstream.fullName(), "false");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertSame(BuildUpstreamMode.NONE, configuration.buildUpstreamMode);
        verify(mavenExecutionRequestMock, never()).getMakeBehavior();
    }

    @Test
    public void buildUpstreamMode_always() {
        System.setProperty(Property.buildUpstream.fullName(), "always");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertSame(BuildUpstreamMode.CHANGED, configuration.buildUpstreamMode);
        verify(mavenExecutionRequestMock, never()).getMakeBehavior();
    }

    @Test
    public void buildUpstreamMode_true() {
        System.setProperty(Property.buildUpstream.fullName(), "true");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertSame(BuildUpstreamMode.CHANGED, configuration.buildUpstreamMode);
        verify(mavenExecutionRequestMock, never()).getMakeBehavior();
    }

    @Test
    public void buildUpstreamMode_unknown() {
        System.setProperty(Property.buildUpstream.fullName(), "foo");

        IllegalArgumentException expectedException = assertThrows(IllegalArgumentException.class,
                () -> new Configuration.Provider(mavenSessionMock).get());

        assertThat(expectedException.getMessage(), containsString(Property.buildUpstream.fullName()));
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
        System.setProperty(Property.buildUpstreamMode.fullName(), "impacted");
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertSame(BuildUpstreamMode.IMPACTED, configuration.buildUpstreamMode);
    }

    @Test
    public void buildUpstreamMode_derived_makeUpstream_unknown() {
        System.setProperty(Property.buildUpstreamMode.fullName(), "foo");
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        IllegalArgumentException expectedException = assertThrows(IllegalArgumentException.class,
                () -> new Configuration.Provider(mavenSessionMock).get());

        assertThat(expectedException.getMessage(), containsString(Property.buildUpstreamMode.fullName()));
        assertThat(expectedException.getCause(), instanceOf(IllegalArgumentException.class));
    }

    // just an example to show 'derived' can also be set explicitely
    @Test
    public void buildUpstreamMode_derivedExplicit_makeUpstream() {
        System.setProperty(Property.buildUpstream.fullName(), "derived");
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
        System.setProperty(Property.buildDownstream.fullName(), "never");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertFalse(configuration.buildDownstream);
        verify(mavenExecutionRequestMock, times(1)).getMakeBehavior();  // called once for buildDownstreamMode
    }

    @Test
    public void buildDownstream_false() {
        System.setProperty(Property.buildDownstream.fullName(), "false");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertFalse(configuration.buildDownstream);
        verify(mavenExecutionRequestMock, times(1)).getMakeBehavior();  // called once for buildDownstreamMode
    }

    @Test
    public void buildDownstream_always() {
        System.setProperty(Property.buildDownstream.fullName(), "always");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertTrue(configuration.buildDownstream);
        verify(mavenExecutionRequestMock, times(1)).getMakeBehavior();  // called once for buildDownstreamMode
    }

    @Test
    public void buildDownstream_true() {
        System.setProperty(Property.buildDownstream.fullName(), "true");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertTrue(configuration.buildDownstream);
        verify(mavenExecutionRequestMock, times(1)).getMakeBehavior();  // called once for buildDownstreamMode
    }

    @Test
    public void buildDownstream_unknown() {
        System.setProperty(Property.buildDownstream.fullName(), "foo");

        IllegalArgumentException expectedException = assertThrows(IllegalArgumentException.class,
                () -> new Configuration.Provider(mavenSessionMock).get());

        assertThat(expectedException.getMessage(), containsString(Property.buildDownstream.fullName()));
    }

    @Test
    public void buildDownstream_derived_noMake() {
        System.setProperty(Property.buildDownstream.fullName(), "derived");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertFalse(configuration.buildDownstream);
    }

    @Test
    public void buildDownstream_derived_makeDownstream() {
        System.setProperty(Property.buildDownstream.fullName(), "derived");
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_DOWNSTREAM);

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertTrue(configuration.buildDownstream);
    }

    @Test
    public void buildDownstream_derived_makeBoth() {
        System.setProperty(Property.buildDownstream.fullName(), "derived");
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_BOTH);

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertTrue(configuration.buildDownstream);
    }

    @Test
    public void buildDownstream_derived_makeUpstream() {
        System.setProperty(Property.buildDownstream.fullName(), "derived");
        when(mavenExecutionRequestMock.getMakeBehavior()).thenReturn(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertFalse(configuration.buildDownstream);
    }
}
