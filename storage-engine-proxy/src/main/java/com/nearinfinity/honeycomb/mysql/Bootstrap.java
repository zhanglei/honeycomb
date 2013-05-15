package com.nearinfinity.honeycomb.mysql;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.nearinfinity.honeycomb.config.AdapterType;
import com.nearinfinity.honeycomb.config.ConfigurationParser;
import com.nearinfinity.honeycomb.config.HoneycombConfiguration;
import com.nearinfinity.honeycomb.util.Verify;
import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Enumeration;
import java.util.Map;


public final class Bootstrap extends AbstractModule {
    private static final Logger logger = Logger.getLogger(Bootstrap.class);
    private final HoneycombConfiguration configuration;

    private Bootstrap(HoneycombConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * The initial function called by JNI to wire-up the required object graph dependencies
     *
     * @param configFilename The path to the configuration file, not null or empty
     * @param configSchema  The path to the schema used to validate the configuration file, not null or empty
     * @return {@link HandlerProxyFactory} with all dependencies setup
     */
    public static HandlerProxyFactory startup(String configFilename, String configSchema) {
        Verify.isNotNullOrEmpty(configFilename);
        Verify.isNotNullOrEmpty(configSchema);

        ensureLoggingPathsCorrect();
        HoneycombConfiguration configuration =
                ConfigurationParser.parseConfiguration(configFilename, configSchema);

        Bootstrap bootstrap = new Bootstrap(configuration);

        Injector injector = Guice.createInjector(bootstrap);
        return injector.getInstance(HandlerProxyFactory.class);
    }

    private static void ensureLoggingPathsCorrect() {
        Enumeration allAppenders = Logger.getRootLogger().getAllAppenders();
        while (allAppenders.hasMoreElements()) {
            Appender appender = (Appender) allAppenders.nextElement();
            if (appender instanceof FileAppender) {
                FileAppender fileAppender = (FileAppender) appender;
                File f = new File(fileAppender.getFile());
                System.err.println("Testing: " + f.getName());
                if (f.exists()) {
                    if (!f.canWrite())
                        System.err.println("Cannot write to " + f.getName());
                } else {
                    String createFailure = "Could not create logging file " + f.getName();
                    try {
                        if (!f.createNewFile()) {
                            System.err.println(createFailure);
                            throw new RuntimeException(createFailure);
                        }
                    } catch (IOException e) {
                        System.err.println(createFailure);
                        throw new RuntimeException(createFailure, e);
                    }
                }
            }
        }
    }

    @Override
    protected void configure() {
        bind(HoneycombConfiguration.class).toInstance(configuration);

        for (AdapterType adapter : AdapterType.values()) {
            if (configuration.isAdapterConfigured(adapter)) {
                try {
                    Class moduleClass = Class.forName(adapter.getModuleClass());
                    Constructor moduleCtor = moduleClass.getConstructor(Map.class);
                    Object module = moduleCtor.newInstance(configuration.getAdapterOptions(adapter));
                    install((Module) module);
                } catch (ClassNotFoundException e) {
                    logger.error("The " + adapter.getName() + " adaptor is" +
                            " configured, but could not be found on the classpath.");
                } catch (Exception e) {
                    logger.error("Exception while attempting to reflect on the "
                            + adapter.getName() + " adaptor.", e);
                }
            }
        }
    }
}