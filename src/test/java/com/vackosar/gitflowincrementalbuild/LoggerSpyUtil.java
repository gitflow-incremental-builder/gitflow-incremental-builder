package com.vackosar.gitflowincrementalbuild;

import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerSpyUtil {

    public static Logger buildSpiedLoggerFor(Class<?> owningClazz) {
        Logger logger = LoggerFactory.getLogger(owningClazz);
        // note: cannot directly use Mockito.spy(logger) because logback loggers are final
        Logger spiedLogger = Mockito.mock(Logger.class, AdditionalAnswers.delegatesTo(logger));
        return spiedLogger;
    }
}
