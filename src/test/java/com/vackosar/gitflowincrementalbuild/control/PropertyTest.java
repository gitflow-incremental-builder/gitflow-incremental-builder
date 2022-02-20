package com.vackosar.gitflowincrementalbuild.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.fail;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vackosar.gitflowincrementalbuild.SystemPropertiesResetExtension;

@ExtendWith(SystemPropertiesResetExtension.class)
public class PropertyTest {

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
        assertThat(Property.disable.getValue(NO_PROPS, NO_PROPS)).isEqualTo("false");
    }

    // prefixedName

    @Test
    public void getValue_prefixedName_projectProperties() {
        assertThat(Property.disable.getValue(NO_PROPS, propsWith(Property.disable.prefixedName(), "true"))).isEqualTo("true");
    }

    @Test
    public void getValue_prefixedName_systemProperties() {
        System.setProperty(Property.disable.prefixedName(), "true");

        assertThat(Property.disable.getValue(NO_PROPS, NO_PROPS)).isEqualTo("true");
    }

    @Test
    public void getValue_prefixedName_systemProperties_emptyValueMapsToTrue() {
        // need to use a property that is false per default
        System.setProperty(Property.buildAll.prefixedName(), "");

        assertThat(Property.buildAll.getValue(NO_PROPS, NO_PROPS)).isEqualTo("true");
    }

    @Test
    public void getValue_prefixedName_systemProperties_emptyValueNotMapped() {
        System.setProperty(Property.referenceBranch.prefixedName(), "");

        assertThat(Property.referenceBranch.getValue(NO_PROPS, NO_PROPS)).isEqualTo("");
    }

    @Test
    public void getValue_prefixedName_systemProperties_override() {
        System.setProperty(Property.disable.prefixedName(), "false");

        assertThat(Property.disable.getValue(NO_PROPS, propsWith(Property.disable.prefixedName(), "true"))).isEqualTo("false");
    }

    @Test
    public void getValue_prefixedName_systemProperties_override_prefixedShortName() {
        System.setProperty(Property.disable.prefixedShortName(), "false");

        assertThat(Property.disable.getValue(NO_PROPS, propsWith(Property.disable.prefixedName(), "true"))).isEqualTo("false");
    }

    // prefixedShortName

    @Test
    public void getValue_prefixedShortName_projectProperties() {
        // short name will only be resolved from system properties!
        assertThat(Property.disable.getValue(NO_PROPS, propsWith(Property.disable.prefixedShortName(), "true"))).isEqualTo("false");
    }

    @Test
    public void getValue_prefixedShortName_systemProperties() {
        System.setProperty(Property.disable.prefixedShortName(), "true");

        assertThat(Property.disable.getValue(NO_PROPS, NO_PROPS)).isEqualTo("true");
    }

    @Test
    public void getValue_prefixedShortName_systemProperties_override() {
        System.setProperty(Property.disable.prefixedShortName(), "false");

        assertThat(Property.disable.getValue(NO_PROPS, propsWith(Property.disable.prefixedShortName(), "true"))).isEqualTo("false");
    }

    @Test
    public void getValue_prefixedShortName_systemProperties_override_prefixedNameWins() {
        System.setProperty(Property.disable.prefixedName(), "true");
        System.setProperty(Property.disable.prefixedShortName(), "false");

        assertThat(Property.disable.getValue(NO_PROPS, NO_PROPS)).isEqualTo("true");
    }

    // name (plugin mode)

    @Test
    public void getValue_pluginProperties() {
        assertThat(Property.disable.getValue(propsWith(Property.disable.name(), "true"), NO_PROPS)).isEqualTo("true");
    }

    @Test
    public void getValue_pluginProperties_overridesProjectProperties() {
        assertThat(Property.disable.getValue(propsWith(Property.disable.name(), "true"), propsWith(Property.disable.name(), "false"))).isEqualTo("true");
    }

    @Test
    public void getValue_pluginProperties_overridesSystemProperties() {
        System.setProperty(Property.disable.prefixedName(), "false");

        assertThat(Property.disable.getValue(propsWith(Property.disable.name(), "true"), NO_PROPS)).isEqualTo("true");
    }

    @Test
    public void getValue_pluginProperties_overridesSystemPropertiesWithPrefixedShortName() {
        System.setProperty(Property.disable.prefixedShortName(), "false");

        assertThat(Property.disable.getValue(propsWith(Property.disable.name(), "true"), NO_PROPS)).isEqualTo("true");
    }

    // misc

    @Test
    public void getValueOpt_empty() {
        Optional<String> valueOpt = Property.disableIfBranchMatches.getValueOpt(NO_PROPS, NO_PROPS);

        assertThat(valueOpt).isEqualTo(Optional.empty());
    }

    @Test
    public void getValueOpt_notEmpty() {
        Optional<String> valueOpt = Property.disableIfBranchMatches.getValueOpt(NO_PROPS, propsWith(Property.disableIfBranchMatches.prefixedName(), "main"));

        assertThat(valueOpt).isEqualTo(Optional.of("main"));
    }

    @Test
    public void checkProperties_defaults() {
        Property.checkProperties(NO_PROPS, NO_PROPS);
        // no exception
    }

    @Test
    public void checkProperties_ok() {
        System.setProperty(Property.disable.prefixedName(), "");
        System.setProperty(Property.disableBranchComparison.prefixedShortName(), "true");

        Property.checkProperties(propsWith(Property.disableIfBranchMatches.name(), "main"),
                propsWith(Property.buildAllIfNoChanges.prefixedName(), "true"));
        // no exception
    }

    @Test
    public void checkProperties_fails() {
        System.setProperty(Property.PREFIX + "foo", "");
        System.setProperty(Property.PREFIX + "bar", "true");

        assertThatIllegalArgumentException().isThrownBy(() -> Property.checkProperties(
                propsWith("baz", "main"),
                propsWith(Property.PREFIX + "bing", "true")))
                .withMessageContaining("foo")
                .withMessageContaining("bar")
                .withMessageContaining("baz")
                .withMessageContaining("bing");
    }

    @Test
    public void getDefaultValue_sample() {
        assertThat(Property.disable.getDefaultValue()).isEqualTo("false");
    }

    @Test
    public void getDefaultValue_nonNull() {
        assertThat(Arrays.stream(Property.values()).map(Property::getDefaultValue)).doesNotContainNull();
    }

    @Test
    public void isBoolean_samples() {
        assertThat(Property.disable.isBoolean()).isTrue();
        assertThat(Property.referenceBranch.isBoolean()).isFalse();
    }

    private static Properties propsWith(String propertyKey, String value) {
        final Properties properties = new Properties();
        properties.setProperty(propertyKey, value);
        return properties;
    }
}
