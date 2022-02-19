package io.github.gitflowincrementalbuilder.boundary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;

import io.github.gitflowincrementalbuilder.control.Property;

/**
 * Tests {@link UnchangedProjectsRemover} with Mockito mocks in context of BOMs (bill of material).
 *
 * @author famod
 */
public class UnchangedProjectsRemoverBOMSupportTest extends BaseUnchangedProjectsRemoverTest {

    @Test
    public void bomChanged_noImport() throws GitAPIException, IOException {
        MavenProject changedBOMModuleMock = addModuleMock(AID_MODULE_B, true, "pom");

        addModuleMock(AID_MODULE_C, false);

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Collections.singletonList(changedBOMModuleMock));
    }

    @Test
    public void bomChanged_noImport_otherBOMVersion() throws GitAPIException, IOException {
        MavenProject changedBOMModuleMock = addModuleMock(AID_MODULE_B, true, "pom");

        MavenProject unchangedModuleMock = addModuleMock(AID_MODULE_C, false);

        installOrigModelWithDepMgmt(unchangedModuleMock)
                .addDependency(buildBOMDependency(changedBOMModuleMock, "9.9.9"));

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Collections.singletonList(changedBOMModuleMock));
    }

    @Test
    public void bomChanged_noImport_nonBOMModuleChangedAsWell() throws GitAPIException, IOException {
        MavenProject changedBOMModuleMock = addModuleMock(AID_MODULE_B, true, "pom");

        MavenProject changedModuleMock= addModuleMock(AID_MODULE_C, true);

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedBOMModuleMock, changedModuleMock));
    }

    @Test
    public void bomChanged_oneImport() throws GitAPIException, IOException {
        MavenProject changedBOMModuleMock = addModuleMock(AID_MODULE_B, true, "pom");

        MavenProject unchangedModuleMock = addModuleMock(AID_MODULE_C, false);

        installOrigModelWithDepMgmt(unchangedModuleMock)
                .addDependency(buildBOMDependency(changedBOMModuleMock));

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedBOMModuleMock, unchangedModuleMock));
    }

    @Test
    public void bomChanged_oneImport_versionProperty() throws GitAPIException, IOException {
        MavenProject changedBOMModuleMock = addModuleMock(AID_MODULE_B, true, "pom");

        MavenProject unchangedModuleMock = addModuleMock(AID_MODULE_C, false);

        installOrigModelWithDepMgmt(unchangedModuleMock)
                .addDependency(buildBOMDependency(changedBOMModuleMock, "${project.version}"));

        // mock MavenSession.clone() (for LazyExpressionEvaluator) and have the clone delegate to the original session + mimic setCurrentProject()
        MavenSession sessionCloneMock = mock(MavenSession.class, AdditionalAnswers.delegatesTo(mavenSessionMock));
        MutableObject<MavenProject> currentProjectSetForClone = new MutableObject<>();
        doAnswer(invocation -> {
            currentProjectSetForClone.setValue(invocation.getArgument(0));
            return null;
        }).when(sessionCloneMock).setCurrentProject(any(MavenProject.class));
        doAnswer(i -> currentProjectSetForClone.getValue()).when(sessionCloneMock).getCurrentProject();
        when(mavenSessionMock.clone()).thenReturn(sessionCloneMock);

        // see constructor PluginParameterExpressionEvaluator
        when(mavenSessionMock.getSystemProperties()).thenReturn(new Properties());
        when(mavenSessionMock.getUserProperties()).thenReturn(new Properties());

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedBOMModuleMock, unchangedModuleMock));
        verify(sessionCloneMock).getCurrentProject();
    }

    @Test
    public void bomChanged_oneImport_oneWithout() throws GitAPIException, IOException {
        MavenProject changedBOMModuleMock = addModuleMock(AID_MODULE_B, true, "pom");

        addModuleMock(AID_MODULE_C, false);

        MavenProject unchangedModuleMock2 = addModuleMock(AID_MODULE_D, false);

        installOrigModelWithDepMgmt(unchangedModuleMock2)
                .addDependency(buildBOMDependency(changedBOMModuleMock));

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedBOMModuleMock, unchangedModuleMock2));
    }

    @Test
    public void bomChanged_oneImport_bomNotSelected() throws GitAPIException, IOException {
        MavenProject changedBOMModuleMock = addModuleMock(AID_MODULE_B, true, "pom");

        MavenProject unchangedModuleMock = addModuleMock(AID_MODULE_C, false);

        installOrigModelWithDepMgmt(unchangedModuleMock)
                .addDependency(buildBOMDependency(changedBOMModuleMock));

        overrideProjects(unchangedModuleMock);

        underTest.act(config());

        verify(mavenSessionMock, never()).setProjects(anyList());
    }

    @Test
    public void bomChanged_oneImport_bomNotSelected_transitive() throws GitAPIException, IOException {
        MavenProject changedBOMModuleMock = addModuleMock(AID_MODULE_B, true, "pom");

        MavenProject unchangedModuleMockC = addModuleMock(AID_MODULE_C, false);

        MavenProject unchangedModuleMockD = addModuleMock(AID_MODULE_D, false);

        installOrigModelWithDepMgmt(unchangedModuleMockC)
                .addDependency(buildBOMDependency(changedBOMModuleMock));

        setDownstreamProjects(unchangedModuleMockC, unchangedModuleMockD);
        setUpstreamProjects(unchangedModuleMockD, moduleA, unchangedModuleMockC);

        setProjectDeSelections(unchangedModuleMockD);
        overrideProjects(unchangedModuleMockD);

        underTest.act(config());

        verify(mavenSessionMock, never()).setProjects(anyList());
    }

    @Test
    public void bomChanged_oneImport_bomNotSelected_disableSelectedProjectsHandling() throws GitAPIException, IOException {
        MavenProject changedBOMModuleMock = addModuleMock(AID_MODULE_B, true, "pom");

        MavenProject unchangedModuleMock = addModuleMock(AID_MODULE_C, false);

        installOrigModelWithDepMgmt(unchangedModuleMock)
                .addDependency(buildBOMDependency(changedBOMModuleMock));

        setProjectDeSelections(unchangedModuleMock);
        overrideProjects(unchangedModuleMock);

        addGibProperty(Property.disableSelectedProjectsHandling, "true");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Collections.singletonList(unchangedModuleMock));
    }

    @Test
    public void bomChanged_oneImport_bomNotSelected_disableSelectedProjectsHandling_transitive() throws GitAPIException, IOException {
        MavenProject changedBOMModuleMock = addModuleMock(AID_MODULE_B, true, "pom");

        MavenProject unchangedModuleMockC = addModuleMock(AID_MODULE_C, false);

        MavenProject unchangedModuleMockD = addModuleMock(AID_MODULE_D, false);

        installOrigModelWithDepMgmt(unchangedModuleMockC)
                .addDependency(buildBOMDependency(changedBOMModuleMock));

        setDownstreamProjects(unchangedModuleMockC, unchangedModuleMockD);
        setUpstreamProjects(unchangedModuleMockD, moduleA, unchangedModuleMockC);

        setProjectDeSelections(unchangedModuleMockD);
        overrideProjects(unchangedModuleMockD);

        addGibProperty(Property.disableSelectedProjectsHandling, "true");

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Collections.singletonList(unchangedModuleMockD));
    }

    @Test
    public void bomChanged_twoImports() throws GitAPIException, IOException {
        MavenProject changedBOMModuleMock = addModuleMock(AID_MODULE_B, true, "pom");

        MavenProject unchangedModuleMock1 = addModuleMock(AID_MODULE_C, false);

        installOrigModelWithDepMgmt(unchangedModuleMock1)
                .addDependency(buildBOMDependency(changedBOMModuleMock));

        MavenProject unchangedModuleMock2 = addModuleMock(AID_MODULE_D, false);

        installOrigModelWithDepMgmt(unchangedModuleMock2)
                .addDependency(buildBOMDependency(changedBOMModuleMock));

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedBOMModuleMock, unchangedModuleMock1, unchangedModuleMock2));
    }

    @Test
    public void bomChanged_twoImports_twoBOMs() throws GitAPIException, IOException {
        MavenProject unchangedBOMModuleMock = addModuleMock(AID_MODULE_B, false, "pom");
        MavenProject changedBOMModuleMock = addModuleMock(AID_MODULE_C, true, "pom");

        MavenProject unchangedModuleMock1 = addModuleMock(AID_MODULE_D, false);

        installOrigModelWithDepMgmt(unchangedModuleMock1)
                .addDependency(buildBOMDependency(unchangedBOMModuleMock));

        MavenProject unchangedModuleMock2 = addModuleMock(AID_MODULE_E, false);

        installOrigModelWithDepMgmt(unchangedModuleMock2)
                .addDependency(buildBOMDependency(changedBOMModuleMock));

        underTest.act(config());

        verify(mavenSessionMock).setProjects(Arrays.asList(changedBOMModuleMock, unchangedModuleMock2));
    }

    private DependencyManagement installOrigModelWithDepMgmt(MavenProject moduleMock) {
        Model origModel = new Model();
        DependencyManagement depMgmt = new DependencyManagement();
        origModel.setDependencyManagement(depMgmt);
        Mockito.when(moduleMock.getOriginalModel()).thenReturn(origModel);
        return depMgmt;
    }

    private Dependency buildBOMDependency(MavenProject bomModuleMock) {
        return buildBOMDependency(bomModuleMock, bomModuleMock.getVersion());
    }

    private Dependency buildBOMDependency(MavenProject bomModuleMock, String version) {
        Dependency dep = new Dependency();
        dep.setGroupId(bomModuleMock.getGroupId());
        dep.setArtifactId(bomModuleMock.getArtifactId());
        dep.setVersion(version);
        dep.setScope(Artifact.SCOPE_IMPORT);
        dep.setType("pom");
        return dep;
    }
}
