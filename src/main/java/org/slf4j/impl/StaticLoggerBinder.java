package org.slf4j.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.Marker;

import java.util.Arrays;

@Singleton
public class StaticLoggerBinder {

    private final org.codehaus.plexus.logging.Logger logger;
    private static StaticLoggerBinder staticLoggerBinder;

    @Inject
    public StaticLoggerBinder(org.codehaus.plexus.logging.Logger logger) {
        this.logger = logger;
        StaticLoggerBinder.staticLoggerBinder = this;
    }

    public static final StaticLoggerBinder getSingleton() {return staticLoggerBinder;}

    public String getLoggerFactoryClassStr() {return null;}

    public ILoggerFactory getLoggerFactory() {
        return s -> new Logger() {
            @Override public String getName() {return logger.getName();}

            @Override public boolean isTraceEnabled() {return false;}
            @Override public void trace(String s) {}
            @Override public void trace(String s, Object o) {}
            @Override public void trace(String s, Object o, Object o1) {}
            @Override public void trace(String s, Object... objects) {}
            @Override public void trace(String s, Throwable throwable) {}
            @Override public boolean isTraceEnabled(Marker marker) {return false;}
            @Override public void trace(Marker marker, String s) {}
            @Override public void trace(Marker marker, String s, Object o) {}
            @Override public void trace(Marker marker, String s, Object o, Object o1) {}
            @Override public void trace(Marker marker, String s, Object... objects) {}
            @Override public void trace(Marker marker, String s, Throwable throwable) {}

            @Override public boolean isDebugEnabled() {return false;}
            @Override public void debug(String s) {logger.debug(s);}
            @Override public void debug(String s, Object o) {debug(s, new Object[] {o});}
            @Override public void debug(String s, Object o, Object o1) {debug(s, new Object[] {o, o1});;}
            @Override public void debug(String s, Object... objects) {logger.debug(s + "-" + Arrays.deepToString(objects));}
            @Override public void debug(String s, Throwable throwable) {logger.debug(s, throwable);}
            @Override public boolean isDebugEnabled(Marker marker) {return logger.isDebugEnabled();}
            @Override public void debug(Marker marker, String s) {logger.debug(s);}
            @Override public void debug(Marker marker, String s, Object o) {debug(s,o);}
            @Override public void debug(Marker marker, String s, Object o, Object o1) {debug(s, new Object[]{o, o1});}
            @Override public void debug(Marker marker, String s, Object... objects) {debug(s, objects);}
            @Override public void debug(Marker marker, String s, Throwable throwable) {debug(s, throwable);}

            @Override public boolean isInfoEnabled() {return false;}
            @Override public void info(String s) {logger.info(s);}
            @Override public void info(String s, Object o) {info(s, new Object[] {o});}
            @Override public void info(String s, Object o, Object o1) {info(s, new Object[] {o, o1});;}
            @Override public void info(String s, Object... objects) {logger.info(s + "-" + Arrays.deepToString(objects));}
            @Override public void info(String s, Throwable throwable) {logger.info(s, throwable);}
            @Override public boolean isInfoEnabled(Marker marker) {return logger.isInfoEnabled();}
            @Override public void info(Marker marker, String s) {logger.info(s);}
            @Override public void info(Marker marker, String s, Object o) {info(s,o);}
            @Override public void info(Marker marker, String s, Object o, Object o1) {info(s, new Object[]{o, o1});}
            @Override public void info(Marker marker, String s, Object... objects) {info(s, objects);}
            @Override public void info(Marker marker, String s, Throwable throwable) {info(s, throwable);}

            @Override public boolean isWarnEnabled() {return false;}
            @Override public void warn(String s) {logger.warn(s);}
            @Override public void warn(String s, Object o) {warn(s, new Object[] {o});}
            @Override public void warn(String s, Object o, Object o1) {warn(s, new Object[] {o, o1});;}
            @Override public void warn(String s, Object... objects) {logger.warn(s + "-" + Arrays.deepToString(objects));}
            @Override public void warn(String s, Throwable throwable) {logger.warn(s, throwable);}
            @Override public boolean isWarnEnabled(Marker marker) {return logger.isWarnEnabled();}
            @Override public void warn(Marker marker, String s) {logger.warn(s);}
            @Override public void warn(Marker marker, String s, Object o) {warn(s,o);}
            @Override public void warn(Marker marker, String s, Object o, Object o1) {warn(s, new Object[]{o, o1});}
            @Override public void warn(Marker marker, String s, Object... objects) {warn(s, objects);}
            @Override public void warn(Marker marker, String s, Throwable throwable) {warn(s, throwable);}

            @Override public boolean isErrorEnabled() {return false;}
            @Override public void error(String s) {logger.error(s);}
            @Override public void error(String s, Object o) {error(s, new Object[] {o});}
            @Override public void error(String s, Object o, Object o1) {error(s, new Object[] {o, o1});;}
            @Override public void error(String s, Object... objects) {logger.error(s + "-" + Arrays.deepToString(objects));}
            @Override public void error(String s, Throwable throwable) {logger.error(s, throwable);}
            @Override public boolean isErrorEnabled(Marker marker) {return logger.isErrorEnabled();}
            @Override public void error(Marker marker, String s) {logger.error(s);}
            @Override public void error(Marker marker, String s, Object o) {error(s,o);}
            @Override public void error(Marker marker, String s, Object o, Object o1) {error(s, new Object[]{o, o1});}
            @Override public void error(Marker marker, String s, Object... objects) {error(s, objects);}
            @Override public void error(Marker marker, String s, Throwable throwable) {error(s, throwable);}
        };
    }

}
