package com.vackosar.gitflowincrementalbuild.mojo;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.assertj.core.api.Condition;
import org.assertj.core.api.ProxyableListAssert;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.vackosar.gitflowincrementalbuild.boundary.Configuration;
import com.vackosar.gitflowincrementalbuild.control.Property;

/**
 * Tests in the broader context of {@link FakeMojo}.
 *
 * Watch out in Eclipse IDE: Due to certain limitations (that are documented in pom.xml), this test will not pick up changes until built in shell.
 *
 * @see MojoParametersGeneratingByteBuddyPlugin
 */
public class FakeMojoTest implements WithAssertions {

    private static final String XPATH_ROOT = "/plugin/mojos/mojo[goal/text() = '" + MojoParametersGeneratingByteBuddyPlugin.FAKE_MOJO_NAME + "']/";

    private static Document pluginXmlDocument;
    private static Document pluginHelpXmlDocument;
    private static XPath xPath;

    @Test
    public void allFieldsPresent() {
        final List<String> expectedFieldNames = gatherExpectedPropertyNames();

        final List<String> actualFieldNames = Arrays.stream(FakeMojo.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())  // jacoco
                .map(Field::getName)
                .sorted()
                .collect(Collectors.toList());

        assertThat(actualFieldNames).isEqualTo(expectedFieldNames);
    }

    @Test
    public void descriptorsHaveValidMojoImpl() {
        ImmutablePair<List<String>, List<String>> nodeTexts = parseDescriptors(XPATH_ROOT + "implementation");

        check("check %s implementation class", nodeTexts, a -> a.isEqualTo(Arrays.asList((FakeMojo.class.getName()))));
    }

    @Test
    public void descriptorsHaveMojoDescription() {
        ImmutablePair<List<String>, List<String>> nodeTexts = parseDescriptors(XPATH_ROOT + "description");

        check("check %s description", nodeTexts, a -> a.isNotEmpty());
    }

    @Test
    public void descriptorsHaveAllParameters() {
        ImmutablePair<List<String>, List<String>> nodeTexts = parseDescriptors(XPATH_ROOT + "parameters/parameter/name");

        check("check %s parameter names", nodeTexts, a -> a.isEqualTo(gatherExpectedPropertyNames()));
    }

    @Test
    public void descriptorsParametersHaveValidTypes() {
        ImmutablePair<List<String>, List<String>> nodeTexts = parseDescriptors(XPATH_ROOT + "parameters/parameter/type");

        check("check %s parameter types", nodeTexts, a -> a.allMatch(s -> "boolean".equals(s) || "java.lang.String".equals(s)));
    }

    @Test
    public void descriptorsParametersHaveBooleanTypeAtLeastOnce() {
        ImmutablePair<List<String>, List<String>> nodeTexts = parseDescriptors(XPATH_ROOT + "parameters/parameter/type");

        check("check %s parameter types", nodeTexts, a -> a.haveAtLeastOne(new Condition<>("boolean"::equals, "boolean")));
    }

    @Test
    public void descriptorsParametersHaveDescriptions() {
        ImmutablePair<List<String>, List<String>> nodeTexts = parseDescriptors(XPATH_ROOT + "parameters/parameter/description");

        check("check %s parameters have descriptions", nodeTexts, a -> a.allMatch(StringUtils::isNotBlank));
    }

    @Test
    public void descriptorsConfigElementsHaveAllExpressions() {
        List<String> expected = gatherExpectedPropertyNames(Property::prefixedName).stream()
                .map(name -> String.format("${%s}", name))
                .sorted()
                .collect(Collectors.toList());

        ImmutablePair<List<String>, List<String>> nodeTexts = parseDescriptors(XPATH_ROOT + "configuration/*");

        check("check %s configuration params have expressions", nodeTexts, a -> a.isEqualTo(expected));
    }

    @Test
    public void descriptorsConfigElementsHaveValidTypes() {
        ImmutablePair<List<String>, List<String>> nodeTexts = parseDescriptors(XPATH_ROOT + "configuration/*/@implementation");

        check("check %s configuration param types", nodeTexts, a -> a.allMatch(s -> "boolean".equals(s) || "java.lang.String".equals(s)));
    }

    @Test
    public void descriptorsConfigElementsHaveBooleanTypeAtLeastOnce() {
        ImmutablePair<List<String>, List<String>> nodeTexts = parseDescriptors(XPATH_ROOT + "configuration/*/@implementation");

        check("check %s configuration param types", nodeTexts, a -> a.haveAtLeastOne(new Condition<>("boolean"::equals, "boolean")));
    }

    @Test
    public void descriptorsConfigElementsHaveDefaultValues() {
        ImmutablePair<List<String>, List<String>> nodeTexts = parseDescriptors(XPATH_ROOT + "configuration/*/@default-value");

        check("check %s configuration param defaults", nodeTexts, a -> a.doesNotContainNull());
    }

    @Test
    public void descriptorsConfigElementsHaveNonEmptyDefaultAtLeastOnce() {
        ImmutablePair<List<String>, List<String>> nodeTexts = parseDescriptors(XPATH_ROOT + "configuration/*/@default-value");

        check("check %s configuration param defaults", nodeTexts, a -> a.haveAtLeastOne(new Condition<>(StringUtils::isNotEmpty, "not empty")));
    }

    private List<String> gatherExpectedPropertyNames() {
        return gatherExpectedPropertyNames(Property::name);
    }

    private List<String> gatherExpectedPropertyNames(Function<Property, String> mapper) {
        return Arrays.stream(Property.values())
                .map(mapper)
                .sorted()
                .collect(Collectors.toList());
    }

    private ImmutablePair<List<String>, List<String>> parseDescriptors(String expression) {
        // lazy init (not in @BeforeAll because it is offtopic for allFieldsPresent())
        if (pluginXmlDocument == null) {
            Path pluginXmlPath = Paths.get("target", "classes", "META-INF", "maven", "plugin.xml");
            assertThat(pluginXmlPath).exists();

            String[] pluginGA = Configuration.PLUGIN_KEY.split(":");
            Path pluginHelpXmlPath = pluginXmlPath.resolveSibling(pluginGA[0]).resolve(pluginGA[1]).resolve("plugin-help.xml");
            assertThat(pluginHelpXmlPath).exists();

            try {
                DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                pluginXmlDocument = docBuilder.parse(pluginXmlPath.toFile());
                pluginHelpXmlDocument = docBuilder.parse(pluginHelpXmlPath.toFile());
                xPath = XPathFactory.newInstance().newXPath();
            } catch (ParserConfigurationException | IOException | SAXException e) {
                throw new IllegalStateException("Failed to parse plugin.xml and/or plugin-help.xml", e);
            }
        }

        XPathExpression xPathExpr;
        try {
            xPathExpr = xPath.compile(expression);
        } catch (XPathExpressionException e) {
            throw new IllegalStateException("Failed to parse: " + expression, e);
        }
        return ImmutablePair.of(
                parseNodeTexts(xPathExpr, pluginXmlDocument),
                parseNodeTexts(xPathExpr, pluginHelpXmlDocument));
    }

    private static List<String> parseNodeTexts(XPathExpression xPathExpr, Document doc) {
        NodeList nodeList;
        try {
            nodeList = (NodeList) xPathExpr.evaluate(doc, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new IllegalStateException(e);
        }
        return IntStream.range(0, nodeList.getLength())
                .mapToObj(nodeList::item)
                .map(Node::getTextContent)
                .sorted()
                .collect(Collectors.toList());
    }

    private void check(String asTextFormat, ImmutablePair<List<String>, List<String>> nodeTexts, Consumer<ProxyableListAssert<String>> assertionConsumer) {
        SoftAssertions softly = new SoftAssertions();
        assertionConsumer.accept(softly.assertThat(nodeTexts.left) .as(asTextFormat, "plugin.xml"));
        assertionConsumer.accept(softly.assertThat(nodeTexts.right) .as(asTextFormat, "plugin-help.xml"));
        softly.assertAll();
    }
}
