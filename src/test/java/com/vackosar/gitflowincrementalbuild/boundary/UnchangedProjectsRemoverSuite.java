package com.vackosar.gitflowincrementalbuild.boundary;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({UnchangedProjectsRemoverTest.class, UnchangedProjectsRemoverSelectedProjectsTest.class, UnchangedProjectsRemoverLogImpactedTest.class})
public class UnchangedProjectsRemoverSuite {

}
