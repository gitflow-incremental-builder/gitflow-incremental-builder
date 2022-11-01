package io.github.gitflowincrementalbuilder;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import io.github.gitflowincrementalbuilder.mojo.FakeMojo;
import io.github.gitflowincrementalbuilder.mojo.MojoParametersGeneratingByteBuddyPlugin;

@AnalyzeClasses(packages = "io.github.gitflowincrementalbuilder", importOptions = DoNotIncludeTests.class)
public class ArchitectureTest {

    @ArchTest
    public static final ArchRule jGitMayOnlyBeUsedByJGitSubpackage = noClasses()
            .that().resideOutsideOfPackages("io.github.gitflowincrementalbuilder.jgit..")
            .should().dependOnClassesThat().resideInAPackage("org.eclipse.jgit..");

    @ArchTest
    public static final ArchRule classesOnlyAccessedWithinPackageMustNotBePulic = classes()
            .that().areNotAssignableTo(MavenLifecycleParticipant.class)
                    .and().areNotAssignableTo(FakeMojo.class)
                    .and().areNotAssignableTo(MojoParametersGeneratingByteBuddyPlugin.class)
                    .and(new DescribedPredicate<JavaClass>("are only called from classes that reside in same package") {
                            @Override
                            public boolean test(final JavaClass cls) {
                                return cls.getAccessesToSelf()
                                    .stream()
                                    .map(JavaAccess::getOriginOwner)
                                    .allMatch(callerClass -> callerClass.getPackageName().equals(cls.getPackageName()));
                            }
                        }
            ).should().notBePublic();
}
