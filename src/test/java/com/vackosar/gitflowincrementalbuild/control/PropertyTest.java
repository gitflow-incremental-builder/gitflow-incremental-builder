package com.vackosar.gitflowincrementalbuild.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.vackosar.gitflowincrementalbuild.SystemPropertiesResetExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

@ExtendWith(SystemPropertiesResetExtension.class)
public class PropertyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyTest.class);

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
        assertEquals("true", Property.enabled.getValue(new Properties(), new Properties()));
    }

    // prefixedName

    @Test
    public void getValue_prefixedName_properties() {
        assertEquals("false", Property.enabled.getValue(new Properties(), propertiesWith(Property.enabled.prefixedName(), "false")));
    }

    @Test
    public void getValue_prefixedName_systemProperties() {
        System.setProperty(Property.enabled.prefixedName(), "false");

        assertEquals("false", Property.enabled.getValue(new Properties(), new Properties()));
    }

    @Test
    public void getValue_prefixedName_systemProperties_emptyValueMapsToTrue() {
        // need to use a property that is false per default
        System.setProperty(Property.buildAll.prefixedName(), "");

        assertEquals("true", Property.buildAll.getValue(new Properties(), new Properties()));
    }

    @Test
    public void getValue_prefixedName_systemProperties_emptyValueNotMapped() {
        System.setProperty(Property.referenceBranch.prefixedName(), "");

        assertEquals("", Property.referenceBranch.getValue(new Properties(), new Properties()));
    }

    @Test
    public void getValue_prefixedName_systemProperties_override() {
        System.setProperty(Property.enabled.prefixedName(), "true");

        assertEquals("true", Property.enabled.getValue(new Properties(), propertiesWith(Property.enabled.prefixedName(), "false")));
    }

    @Test
    public void getValue_prefixedName_systemProperties_override_prefixedShortName() {
        System.setProperty(Property.enabled.prefixedShortName(), "true");

        assertEquals("true", Property.enabled.getValue(new Properties(), propertiesWith(Property.enabled.prefixedName(), "false")));
    }

    // prefixedShortName

    @Test
    public void getValue_prefixedShortName_properties() {
        // short name will only be resolved from system properties!
        assertEquals("true", Property.enabled.getValue(new Properties(), propertiesWith(Property.enabled.prefixedShortName(), "false")));
    }

    @Test
    public void getValue_prefixedShortName_systemProperties() {
        System.setProperty(Property.enabled.prefixedShortName(), "false");

        assertEquals("false", Property.enabled.getValue(new Properties(), new Properties()));
    }

    @Test
    public void getValue_prefixedShortName_systemProperties_override() {
        System.setProperty(Property.enabled.prefixedShortName(), "true");

        assertEquals("true", Property.enabled.getValue(new Properties(), propertiesWith(Property.enabled.prefixedShortName(), "false")));
    }

    @Test
    public void getValue_prefixedShortName_systemProperties_override_prefixedNameWins() {
        System.setProperty(Property.enabled.prefixedName(), "false");
        System.setProperty(Property.enabled.prefixedShortName(), "true");

        assertEquals("false", Property.enabled.getValue(new Properties(), new Properties()));
    }

    private static Properties propertiesWith(String propertyKey, String value) {
        final Properties properties = new Properties();
        properties.setProperty(propertyKey, value);
        return properties;
    }
}
