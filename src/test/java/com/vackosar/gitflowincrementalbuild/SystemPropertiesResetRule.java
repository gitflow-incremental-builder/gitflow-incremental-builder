package com.vackosar.gitflowincrementalbuild;

import java.util.Properties;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Resets system properties to the state they had before the test method execution.<p/>
 * Note: Could use https://github.com/stefanbirkner/system-rules instead, but for a simple rule this seems a bit much.
 */
public class SystemPropertiesResetRule implements TestRule {

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                final Properties backup = (Properties) System.getProperties().clone();
                try {
                    base.evaluate();
                } finally {
                    System.setProperties(backup);
                }
            }
        };
    }
}
