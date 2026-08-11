package io.github.gitflowincrementalbuilder.integration;

import static java.util.function.Predicate.not;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maven lifecycle participant that adds a synthetic upstream project to the reactor and injects a dependency into a target project.<br/>
 * To be used only in GIB's own integrations tests!
 * <p/>
 * This actually went quite a bit beyond adding a project <i>that is already part of the reactor</i> as a new dependency to another project in the reactor (as issue #1183 described).
 * Instead, this participant <i>creates a new synthetic project on the fly, adds it to the reactor</i>, and injects a dependency into the target project.
 */
@Named
@Singleton
public class AddUpstreamSyntheticProjectParticipant extends AbstractMavenLifecycleParticipant {

    // If unset/blank => no-op
    public static final String PROP_TARGET_ARTIFACT_ID = "it.synthetic.dep.targetArtifactId";

    public static final String SYNTHETIC_GROUP_ID = "it.synthetic";
    public static final String SYNTHETIC_ARTIFACT_ID = "synthetically-added-upstream";
    public static final String SYNTHETIC_VERSION = "1.0-SNAPSHOT";
    
    private Logger logger = LoggerFactory.getLogger(AddUpstreamSyntheticProjectParticipant.class);

    @Override
    public void afterProjectsRead(MavenSession session) {
        Properties userProps = session.getUserProperties();
        final String targetArtifactId = Optional.ofNullable(userProps.getProperty(PROP_TARGET_ARTIFACT_ID))
                .map(String::trim)
                .filter(not(String::isEmpty))
                .orElse(null);
        if (targetArtifactId == null) {
            return;
        }

        List<MavenProject> projects = session.getProjects();

        MavenProject target = projects.stream()
                .filter(p -> targetArtifactId.equals(p.getArtifactId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Target project with artifactId=" + targetArtifactId + " not found in reactor projects"));

        // create synthetic project from a programmatically built Model
        MavenProject synthetic = createSyntheticProject(session);

        // Add synthetic to session first, so it's in the reactor
        List<MavenProject> mutated = new ArrayList<>(projects);
        mutated.add(synthetic);
        session.setProjects(mutated);
        List<MavenProject> mutatedAll = new ArrayList<>(session.getAllProjects());
        mutatedAll.add(synthetic);
        session.setAllProjects(mutatedAll);

        // make target depend on synthetic => synthetic is upstream of target
        Dependency dep = new Dependency();
        dep.setGroupId(SYNTHETIC_GROUP_ID);
        dep.setArtifactId(SYNTHETIC_ARTIFACT_ID);
        dep.setVersion(SYNTHETIC_VERSION);
        dep.setType("pom");
        dep.setScope("compile");
        target.getModel().addDependency(dep);

        logger.info("Added synthetic upstream project {}:{} and injected dependency into target {}:{}",
                SYNTHETIC_GROUP_ID, SYNTHETIC_ARTIFACT_ID, target.getGroupId(), target.getArtifactId());
    }

    private MavenProject createSyntheticProject(MavenSession session) {
            
        // Create the Model
        Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId(SYNTHETIC_GROUP_ID);
        model.setArtifactId(SYNTHETIC_ARTIFACT_ID);
        model.setVersion(SYNTHETIC_VERSION);
        model.setPackaging("pom");

        // Prepare the directory and files
        Path workDir = Paths.get(session.getExecutionRootDirectory(), "target", "it-synthetic-upstream");
        mkdirs(workDir);
        Path pomFile = workDir.resolve("pom.xml");

        // Serialize the Model to pom.xml
        MavenXpp3Writer writer = new MavenXpp3Writer();

        try {
            writer.write(Files.newBufferedWriter(pomFile), model);
            
            // Install to the local repository so Maven can resolve the dependency
            // This is critical - Maven's dependency resolution looks in the local repo
            installToLocalRepository(session, pomFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create synthetic project", e);
        }

        // Create and return the MavenProject
        MavenProject project = new MavenProject(model);
        project.setFile(pomFile.toFile());
        project.setGroupId(SYNTHETIC_GROUP_ID);
        project.setArtifactId(SYNTHETIC_ARTIFACT_ID);
        project.setVersion(SYNTHETIC_VERSION);
        project.setPackaging("pom");

        // Create and set a proper Artifact pointing to the pom file
        Artifact artifact = new DefaultArtifact(
            SYNTHETIC_GROUP_ID,
            SYNTHETIC_ARTIFACT_ID,
            SYNTHETIC_VERSION,
            "compile",
            "pom",
            null,
            new DefaultArtifactHandler("pom")
        );
        artifact.setFile(pomFile.toFile());
        artifact.setResolved(true);
        project.setArtifact(artifact);

        return project;
    }
    
    /**
     * Install the POM to the local repository used by integration tests.
     * This mimics what 'mvn install' does, making the artifact available for dependency resolution.
     */
    private void installToLocalRepository(MavenSession session, Path pomFile) throws IOException {
        Path localRepo = session.getRequest().getLocalRepositoryPath().toPath();

        // Calculate the local repository path structure: groupId/artifactId/version/
        String groupPath = SYNTHETIC_GROUP_ID.replace('.', '/');
        Path artifactDir = localRepo.resolve(groupPath).resolve(SYNTHETIC_ARTIFACT_ID).resolve(SYNTHETIC_VERSION);
        mkdirs(artifactDir);
        
        // Copy POM file
        Path localRepoPom = artifactDir.resolve(SYNTHETIC_ARTIFACT_ID + "-" + SYNTHETIC_VERSION + ".pom");
        Files.copy(pomFile, localRepoPom, StandardCopyOption.REPLACE_EXISTING);
        
        logger.info("Installed synthetic artifact to local repository: {}", localRepoPom);
    }

    private static void mkdirs(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create directory: " + dir, e);
        }
    }
}
