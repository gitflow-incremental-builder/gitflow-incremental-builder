package io.github.gitflowincrementalbuilder.integration;

import static java.util.function.Predicate.not;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
        // Use jar type instead of pom - even pom-packaged projects can have jar dependencies
        // and jar dependencies in the reactor don't trigger the same resolution issues
        dep.setType("jar");
        dep.setScope("compile");
        target.getModel().addDependency(dep);

        logger.info("Added synthetic upstream project {}:{} and injected dependency into target {}:{}",
                SYNTHETIC_GROUP_ID, SYNTHETIC_ARTIFACT_ID, target.getGroupId(), target.getArtifactId());
    }

    private MavenProject createSyntheticProject(MavenSession session) {
        try {
            
            // Create the Model programmatically  
            Model model = new Model();
            model.setModelVersion("4.0.0");
            model.setGroupId(SYNTHETIC_GROUP_ID);
            model.setArtifactId(SYNTHETIC_ARTIFACT_ID);
            model.setVersion(SYNTHETIC_VERSION);
            model.setPackaging("jar");

            // Prepare the directory and files
            File workDir = new File(session.getExecutionRootDirectory(), "target/it-synthetic-upstream");
            mkdirs(workDir);
            File pomFile = new File(workDir, "pom.xml");
            
            // Serialize the Model to pom.xml
            MavenXpp3Writer writer = new MavenXpp3Writer();
            writer.write(Files.newBufferedWriter(pomFile.toPath()), model);
            
            // Create an empty jar file so the artifact can be resolved
            File jarFile = new File(workDir, SYNTHETIC_ARTIFACT_ID + "-" + SYNTHETIC_VERSION + ".jar");
            createEmptyJarFile(jarFile);
            
            // Install to the local repository so Maven can resolve the dependency
            // This is critical - Maven's dependency resolution looks in the local repo
            installToLocalRepository(session, pomFile, jarFile);
            
            // Create and return the MavenProject
            MavenProject project = new MavenProject(model);
            project.setFile(pomFile);
            project.setGroupId(SYNTHETIC_GROUP_ID);
            project.setArtifactId(SYNTHETIC_ARTIFACT_ID);
            project.setVersion(SYNTHETIC_VERSION);
            project.setPackaging("jar");
            
            // Create and set a proper Artifact pointing to the jar file
            Artifact artifact = new DefaultArtifact(
                SYNTHETIC_GROUP_ID,
                SYNTHETIC_ARTIFACT_ID,
                SYNTHETIC_VERSION,
                "compile",
                "jar",
                null,
                new DefaultArtifactHandler("jar")
            );
            artifact.setFile(jarFile);
            artifact.setResolved(true);
            project.setArtifact(artifact);
            
            return project;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create synthetic project", e);
        }
    }
    
    /**
     * Create an empty jar file so Maven's dependency resolution can find an artifact.
     */
    private void createEmptyJarFile(File jarFile) throws IOException {
        // Create a minimal valid JAR file (which is a ZIP file with a MANIFEST.MF)
        try (java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(
                Files.newOutputStream(jarFile.toPath()), 
                new java.util.jar.Manifest())) {
            // Empty jar with just a manifest
        }
        logger.debug("Created empty jar file: {}", jarFile);
    }
    
    /**
     * Install the POM and JAR to the local repository used by integration tests.
     * This mimics what 'mvn install' does, making the artifact available for dependency resolution.
     */
    private void installToLocalRepository(MavenSession session, File pomFile, File jarFile) throws IOException {
        File localRepo = session.getRequest().getLocalRepositoryPath();

        // Calculate the local repository path structure: groupId/artifactId/version/
        String groupPath = SYNTHETIC_GROUP_ID.replace('.', '/');
        File artifactDir = new File(localRepo, groupPath + "/" + SYNTHETIC_ARTIFACT_ID + "/" + SYNTHETIC_VERSION);
        mkdirs(artifactDir);
        
        // Copy POM file
        File localRepoPom = new File(artifactDir, SYNTHETIC_ARTIFACT_ID + "-" + SYNTHETIC_VERSION + ".pom");
        Files.copy(pomFile.toPath(), localRepoPom.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        
        // Copy JAR file
        File localRepoJar = new File(artifactDir, SYNTHETIC_ARTIFACT_ID + "-" + SYNTHETIC_VERSION + ".jar");
        Files.copy(jarFile.toPath(), localRepoJar.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        
        logger.info("Installed synthetic artifact to local repository: {}", localRepoJar);
    }

    private static void mkdirs(File dir) {
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IllegalStateException("Failed to create directory: " + dir);
        }
    }
}
