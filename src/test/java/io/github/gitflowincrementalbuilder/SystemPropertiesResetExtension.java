package io.github.gitflowincrementalbuilder;

import java.util.Properties;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

/**
 * Resets system properties to the state they had before the test method execution.<p/>
 * Note: Could use https://github.com/junit-pioneer/junit-pioneer instead, but for a simple rule this seems a bit much.
 */
public class SystemPropertiesResetExtension implements AfterEachCallback, BeforeEachCallback {

    private static final Namespace NAMESPACE = Namespace.create(SystemPropertiesResetExtension.class);
    private static final String KEY = "backup";

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        context.getStore(NAMESPACE).put(KEY, System.getProperties().clone());
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        System.setProperties(context.getStore(NAMESPACE).get(KEY, Properties.class));
    }
}
