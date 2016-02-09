package com.vackosar.gitflowincrementalbuild;

import com.google.inject.Singleton;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class ModuleDirLister {

    public List<Path> act(Path pom) {
        return subPoms(pom)
                .map(Path::getParent)
                .collect(Collectors.toList());
    }

    private Stream<Path> subPoms(Path pom) {
        try {
            Path parent = pom.getParent();
            final Stream<Path> poms =
                    readModules(pom).stream()
                            .map(this::pathize)
                            .map(parent::resolve)
                            .map(this::standartize)
                            .flatMap(this::subPoms);
            return Stream.concat(Stream.of(pom), poms);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String pathize(String module) {
        if (module.endsWith(".xml")) {
            return module;
        } else {
            return module + "/pom.xml";
        }
    }

    private Path standartize(Path path) {
        try {
            return path.normalize().toAbsolutePath().toRealPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> readModules(Path pom) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(pom.toFile());
            doc.getDocumentElement().normalize();
            XPathExpression xpath = XPathFactory.newInstance().newXPath().compile("project/modules/module");
            final NodeList moduleNodes = (NodeList) xpath.evaluate(doc, XPathConstants.NODESET);
            final ArrayList<String> modules = new ArrayList<>();
            for (int i = 0; i < moduleNodes.getLength(); i++) {
                modules.add(moduleNodes.item(i).getTextContent());
            }
            return modules;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
