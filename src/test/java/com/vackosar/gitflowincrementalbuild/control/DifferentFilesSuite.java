package com.vackosar.gitflowincrementalbuild.control;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({DifferentFilesTest.class, DifferentFilesHttpFetchTest.class, DifferentFilesHttpFetchBasicAuthTest.class,
    DifferentFilesSshFetchTest.class})
public class DifferentFilesSuite {

}
