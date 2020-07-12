package com.vackosar.gitflowincrementalbuild.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.vackosar.gitflowincrementalbuild.SystemPropertiesResetExtension;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

@ExtendWith(SystemPropertiesResetExtension.class)
public class PropertyTest implements WithAssertions {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyTest.class);

    private static final Properties NO_PROPS = new Properties();

    @Test
    public void exemplifyAll() {
        LOGGER.info("exemplifyAll():\n{}", Property.exemplifyAll());
    }

    @Test
    public void uniqueShortNames() {
        Map<String, List<Property>> byShortName = Arrays.stream(Property.values())
                .collect(Collectors.groupingBy(Property::prefixedShortName));
        String nonUniquesString = byShortName.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(Object::toString)
                .collect(Collectors.joining("\n\t"));
        if (!nonUniquesString.isEmpty()) {
            fail("Property.prefixedShortName clashes found:\n\t" + nonUniquesString);
        }
    }

    @Test
    public void getValue_unset() {
        assertEquals("true", Property.enabled.getValue(NO_PROPS, NO_PROPS));
    }

    // prefixedName

    @Test
    public void getValue_prefixedName_projectProperties() {
        assertEquals("false", Property.enabled.getValue(NO_PROPS, propsWith(Property.enabled.prefixedName(), "false")));
    }

    @Test
    public void getValue_prefixedName_systemProperties() {
        System.setProperty(Property.enabled.prefixedName(), "false");

        assertEquals("false", Property.enabled.getValue(NO_PROPS, NO_PROPS));
    }

    @Test
    public void getValue_prefixedName_systemProperties_emptyValueMapsToTrue() {
        // need to use a property that is false per default
        System.setProperty(Property.buildAll.prefixedName(), "");

        assertEquals("true", Property.buildAll.getValue(NO_PROPS, NO_PROPS));
    }

    @Test
    public void getValue_prefixedName_systemProperties_emptyValueNotMapped() {
        System.setProperty(Property.referenceBranch.prefixedName(), "");

        assertEquals("", Property.referenceBranch.getValue(NO_PROPS, NO_PROPS));
    }

    @Test
    public void getValue_prefixedName_systemProperties_override() {
        System.setProperty(Property.enabled.prefixedName(), "true");

        assertEquals("true", Property.enabled.getValue(NO_PROPS, propsWith(Property.enabled.prefixedName(), "false")));
    }

    @Test
    public void getValue_prefixedName_systemProperties_override_prefixedShortName() {
        System.setProperty(Property.enabled.prefixedShortName(), "true");

        assertEquals("true", Property.enabled.getValue(NO_PROPS, propsWith(Property.enabled.prefixedName(), "false")));
    }

    // prefixedShortName

    @Test
    public void getValue_prefixedShortName_projectProperties() {
        // short name will only be resolved from system properties!
        assertEquals("true", Property.enabled.getValue(NO_PROPS, propsWith(Property.enabled.prefixedShortName(), "false")));
    }

    @Test
    public void getValue_prefixedShortName_systemProperties() {
        System.setProperty(Property.enabled.prefixedShortName(), "false");

        assertEquals("false", Property.enabled.getValue(NO_PROPS, NO_PROPS));
    }

    @Test
    public void getValue_prefixedShortName_systemProperties_override() {
        System.setProperty(Property.enabled.prefixedShortName(), "true");

        assertEquals("true", Property.enabled.getValue(NO_PROPS, propsWith(Property.enabled.prefixedShortName(), "false")));
    }

    @Test
    public void getValue_prefixedShortName_systemProperties_override_prefixedNameWins() {
        System.setProperty(Property.enabled.prefixedName(), "false");
        System.setProperty(Property.enabled.prefixedShortName(), "true");

        assertEquals("false", Property.enabled.getValue(NO_PROPS, NO_PROPS));
    }

    // name (plugin mode)

    @Test
    public void getValue_pluginProperties() {
        assertEquals("false", Property.enabled.getValue(propsWith(Property.enabled.name(), "false"), NO_PROPS));
    }

    @Test
    public void getValue_pluginProperties_overridesProjectProperties() {
        assertEquals("false", Property.enabled.getValue(propsWith(Property.enabled.name(), "false"), propsWith(Property.enabled.name(), "true")));
    }

    @Test
    public void getValue_pluginProperties_systemProperties_override() {
        System.setProperty(Property.enabled.prefixedName(), "true");

        assertEquals("true", Property.enabled.getValue(propsWith(Property.enabled.name(), "false"), NO_PROPS));
    }

    @Test
    public void getValue_pluginProperties_systemProperties_overrideWithPrefixedShortName() {
        System.setProperty(Property.enabled.prefixedShortName(), "true");

        assertEquals("true", Property.enabled.getValue(propsWith(Property.enabled.name(), "false"), NO_PROPS));
    }

    // misc

    @Test
    public void getValueOpt_empty() {
        Optional<String> valueOpt = Property.disableIfBranchRegex.getValueOpt(NO_PROPS, NO_PROPS);

        assertEquals(Optional.empty(), valueOpt);
    }

    @Test
    public void getValueOpt_notEmpty() {
        Optional<String> valueOpt = Property.disableIfBranchRegex.getValueOpt(NO_PROPS, propsWith(Property.disableIfBranchRegex.prefixedName(), "master"));

        assertEquals(Optional.of("master"), valueOpt);
    }

    @Test
    public void checkProperties_defaults() {
        Property.checkProperties(NO_PROPS, NO_PROPS);
        // no exception
    }

    @Test
    public void checkProperties_ok() {
        System.setProperty(Property.enabled.prefixedName(), "");
        System.setProperty(Property.disableBranchComparison.prefixedShortName(), "true");

        Property.checkProperties(propsWith(Property.disableIfBranchRegex.name(), "master"),
                propsWith(Property.buildAllIfNoChanges.prefixedName(), "true"));
        // no exception
    }

    @Test
    public void checkProperties_fails() {
        System.setProperty(Property.PREFIX + "foo", "");
        System.setProperty(Property.PREFIX + "bar", "true");

        assertThatIllegalArgumentException().isThrownBy(() -> Property.checkProperties(
                propsWith("baz", "master"),
                propsWith(Property.PREFIX + "bing", "true")))
                .withMessageContaining("foo")
                .withMessageContaining("bar")
                .withMessageContaining("baz")
                .withMessageContaining("bing");
    }

    private static Properties propsWith(String propertyKey, String value) {
        final Properties properties = new Properties();
        properties.setProperty(propertyKey, value);
        return properties;
    }
}
