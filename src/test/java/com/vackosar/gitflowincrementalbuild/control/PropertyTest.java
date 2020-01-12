package com.vackosar.gitflowincrementalbuild.control;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vackosar.gitflowincrementalbuild.SystemPropertiesResetRule;

public class PropertyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyTest.class);

    @Rule
    public final SystemPropertiesResetRule sysPropResetRule = new SystemPropertiesResetRule();

    @Test
    public void exemplifyAll() {
        LOGGER.info("exemplifyAll():\n{}", Property.exemplifyAll());
    }

    @Test
    public void uniqueShortNames() {
        Map<String, List<Property>> byShortName = Arrays.stream(Property.values())
                .collect(Collectors.groupingBy(Property::shortName));
        String nonUniquesString = byShortName.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(Object::toString)
                .collect(Collectors.joining("\n\t"));
        if (!nonUniquesString.isEmpty()) {
            fail("Property.shortName clashed found:\n\t" + nonUniquesString);
        }
    }

    @Test
    public void getValue_unset() {
        assertEquals("true", Property.enabled.getValue(new Properties()));
    }

    // fullName

    @Test
    public void getValue_fullName_properties() {
        assertEquals("false", Property.enabled.getValue(propertiesWith(Property.enabled.fullName(), "false")));
    }

    @Test
    public void getValue_fullName_systemProperties() {
        System.setProperty(Property.enabled.fullName(), "false");

        assertEquals("false", Property.enabled.getValue(new Properties()));
    }

    @Test
    public void getValue_fullName_systemProperties_override() {
        System.setProperty(Property.enabled.fullName(), "true");

        assertEquals("true", Property.enabled.getValue(propertiesWith(Property.enabled.fullName(), "false")));
    }

    @Test
    public void getValue_fullName_systemProperties_override_shortName() {
        System.setProperty(Property.enabled.shortName(), "true");

        assertEquals("true", Property.enabled.getValue(propertiesWith(Property.enabled.fullName(), "false")));
    }

    // shortName

    @Test
    public void getValue_shortName_properties() {
        assertEquals("false", Property.enabled.getValue(propertiesWith(Property.enabled.shortName(), "false")));
    }

    @Test
    public void getValue_shortName_systemProperties() {
        System.setProperty(Property.enabled.shortName(), "false");

        assertEquals("false", Property.enabled.getValue(new Properties()));
    }

    @Test
    public void getValue_shortName_systemProperties_override() {
        System.setProperty(Property.enabled.shortName(), "true");

        assertEquals("true", Property.enabled.getValue(propertiesWith(Property.enabled.shortName(), "false")));
    }

    @Test
    public void getValue_shortName_systemProperties_override_fullNameWins() {
        System.setProperty(Property.enabled.fullName(), "false");
        System.setProperty(Property.enabled.shortName(), "true");

        assertEquals("false", Property.enabled.getValue(new Properties()));
    }

    private static Properties propertiesWith(String propertyKey, String value) {
        final Properties properties = new Properties();
        properties.setProperty(propertyKey, value);
        return properties;
    }
}
