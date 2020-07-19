package com.vackosar.gitflowincrementalbuild.mojo;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;

import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.vackosar.gitflowincrementalbuild.control.Property;

import net.bytebuddy.build.BuildLogger;
import net.bytebuddy.build.Plugin;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.dynamic.DynamicType.Builder.FieldDefinition.Optional.Valuable;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * Compensates the problem that {@link Property} cannot (due to its enum nature) be used 1:1 as a mojo to expose the parameters for Eclipse etc.
 * <p>
 * It does so by:
 * </p>
 * <ol>
 * <li>adding all enum instances from {@link Property} to {@link FakeMojo} as ordinary {@code private} members (with initial default values)</li>
 * <li>adding {@link Mojo} to {@link Property} and {@link Parameter} to each of its enum instances</li>
 * </ol>
 * <p>
 * The first transformation allows Eclipse to read the parameter types properly (and might also help other tools that don't rely entirely on the plugin
 * descriptor and/or need to instantiate the mojo for some reason).<br>
 * Those added members are <b>not</b> annotated with {@link Parameter} because the generation of the plugin descriptor has to operate on {@code Property}
 * due to fact that desciptions are generated from JavaDoc (which cannot be added via bytecode/bytebuddy). Without this limitation everything could be
 * generated {@link FakeMojo} and could then be taken to generate the plugin descriptor (without plugin descriptor post-processing, see further down).
 * </p>
 * <p>
 * The second transformation keeps the source of {@link Property} free from mojo-clutter and avoids c+p mistakes (DRY principle).
 * </p>
 * Because descriptor generation has to operate on {@code Property} (see above), this entire workaround also relies on a post-processing of the plugin
 * descriptor which can be found in {@code pom.xml}:
 * <ul>
 * <li>change all {@code type} elements etc. from {@code com.vackosar.gitflowincrementalbuild.control.Property} to {@code java.lang.String} or {@code boolean}
 * </li>
 * <li>change the mojo implementation from {@link Property} to {@link FakeMojo}</li>
 * <li>change/enrich the mojo description and parameter descriptions</li>
 * </ul>
 *
 * @see <a href="https://github.com/vackosar/gitflow-incremental-builder/issues/199">Issue "Plugin parameters don't show up in Eclipse editor"</a>
  */
public class MojoParametersGeneratingByteBuddyPlugin implements Plugin {

    public static final String FAKE_MOJO_NAME = "config-do-not-execute";

    @SuppressWarnings("unused")
    private final BuildLogger logger;

    public MojoParametersGeneratingByteBuddyPlugin(final BuildLogger logger) {
        this.logger = logger;
    }

    @Override
    public boolean matches(TypeDescription target) {
        String actualName = target.getActualName();
        return actualName.equals(FakeMojo.class.getName()) || actualName.equals(Property.class.getName());
    }

    @Override
    public Builder<?> apply(Builder<?> initialBuilder, TypeDescription typeDescription, ClassFileLocator classFileLocator) {
        Builder<?> builder = initialBuilder;
        if (typeDescription.getActualName().equals(FakeMojo.class.getName())) {
            builder = transformFakeMojo(builder);
        } else {
            builder = transformPropertyEnum(builder);
        }
        return builder;
    }

    @Override
    public void close() throws IOException {
        // nothing
    }

    private Builder<?> transformFakeMojo(Builder<?> builder) {
        for (Property property : Property.values()) {
            boolean booleanProperty = property.isBoolean();
            Valuable<?> definedField = builder.defineField(property.name(), booleanProperty ? boolean.class : String.class, Modifier.PRIVATE);
            builder = booleanProperty ? definedField.value(Boolean.parseBoolean(property.getDefaultValue())) : definedField.value(property.getDefaultValue());
        }
        return builder;
    }

    private Builder<?> transformPropertyEnum(Builder<?> builder) {
        builder = builder.annotateType(buildMojoAnnotation());
        for (Property property : Property.values()) {
            builder = builder.field(ElementMatchers.named(property.name())).annotateField(buildParameterAnnotation(property));
        }
        return builder;
    }

    private static Annotation buildMojoAnnotation() {
        return new Mojo() {

            @Override
            public String name() {
                return FAKE_MOJO_NAME;
            }

            @Override
            public boolean threadSafe() {
                return true;
            }

            // just defaults from here on

            @Override
            public Class<? extends Annotation> annotationType() {
                return Mojo.class;
            }

            @Override
            public boolean requiresReports() {
                return false;
            }

            @Override
            public boolean requiresProject() {
                return true;
            }

            @Override
            public boolean requiresOnline() {
                return false;
            }

            @Override
            public boolean requiresDirectInvocation() {
                return false;
            }

            @Override
            public ResolutionScope requiresDependencyResolution() {
                return ResolutionScope.NONE;
            }

            @Override
            public ResolutionScope requiresDependencyCollection() {
                return ResolutionScope.NONE;
            }

            @Override
            public InstantiationStrategy instantiationStrategy() {
                return InstantiationStrategy.PER_LOOKUP;
            }

            @Override
            public boolean inheritByDefault() {
                return true;
            }

            @Override
            public String executionStrategy() {
                return "once-per-session";
            }

            @Override
            public LifecyclePhase defaultPhase() {
                return LifecyclePhase.NONE;
            }

            @Override
            public String configurator() {
                return "";
            }

            @Override
            public boolean aggregator() {
                return false;
            }
        };
    }

    private static Annotation buildParameterAnnotation(Property property) {
        return new Parameter() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return Parameter.class;
            }

            @Override
            public boolean required() {
                return false;
            }

            @Override
            public boolean readonly() {
                return false;
            }

            @Override
            public String property() {
                return Property.PREFIX + property.name();
            }

            @Override
            public String name() {
                return property.name();
            }

            @Override
            public String defaultValue() {
                return property.getDefaultValue();
            }

            @Override
            public String alias() {
                // cannot be used for the short name since in Eclipse this would then be a valid option to select (but only -D is allowed)
                return "";
            }
        };
    }
}
