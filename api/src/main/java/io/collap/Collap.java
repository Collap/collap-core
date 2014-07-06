package io.collap;

import io.collap.controller.Dispatcher;
import io.collap.resource.Plugin;
import io.collap.resource.PluginManager;
import io.collap.util.FileUtils;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;

import java.io.*;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Collap {

    private static final Logger logger = Logger.getLogger (Collap.class.getName ());

    private SessionFactory sessionFactory;
    private PluginManager pluginManager;
    private Dispatcher rootDispatcher;

    private CacheManager cacheManager;
    private Cache fragmentCache;

    private String basePath;

    public void initialize () {
        StandardDirectories.initialize ();

        /* Check whether collap has already been installed. */
        File installFile = new File (StandardDirectories.base, "install");
        if (!installFile.exists ()) {
            // TODO: Enable install file generation in production.
            /* try {
                installFile.createNewFile ();
            } catch (IOException e) {
                logger.severe ("Could not create installation notice file 'install'.");
                e.printStackTrace ();
            } */
            install ();
        }

        /* Read collap config. */
        Properties properties = new Properties ();
        try {
            properties.load (new FileInputStream (new File (StandardDirectories.config, "collap.properties")));
            basePath = properties.getProperty ("basePath");
            /* Put trailing slashes. */
            if (!basePath.startsWith ("/")) basePath = "/" + basePath;
            if (!basePath.endsWith ("/")) basePath = basePath + "/";
        } catch (IOException e) {
            logger.log (Level.SEVERE, "Error loading 'collap.properties': ", e);
        }

        /* Initialize cache manager and caches. */
        try {
            cacheManager = CacheManager.create (new FileInputStream (new File (StandardDirectories.config, "cache.xml")));
        } catch (FileNotFoundException e) {
            e.printStackTrace ();
            logger.severe ("Cache configuration could not be found! This is mandatory.");
            throw new RuntimeException ("Cache configuration not found!"); // Note: This stops the execution of collap in tomcat.
        }
        fragmentCache = cacheManager.getCache ("fragmentCache");

        /* Register plugins. */
        pluginManager = new PluginManager (this);
        pluginManager.registerDirectory (StandardDirectories.plugin);

        /* Miscellaneous initializations. */
        rootDispatcher = new Dispatcher (this);
        initializeSessionFactory ();

        /* Initialize plugins. */
        pluginManager.initializeAllPlugins ();
    }

    /*
     * Sets up the collap directory.
     */
    private void install () {
        StandardDirectories.install ();

        // TODO: Copy the 'default' directory contents from the WAR.

        /* Copy config files. */
        copyConfigFile ("collap.properties");
        copyConfigFile ("hibernate.properties");
        copyConfigFile ("cache.xml");
    }

    /**
     * Only copies the config file if it was not created yet.
     */
    private void copyConfigFile (String name) {
        File cpConfig = new File (StandardDirectories.config, name);
        if (!cpConfig.exists ()) {
            ClassLoader classLoader = Thread.currentThread ().getContextClassLoader (); /* getClass ()... does not work! */
            try {
                FileUtils.copy (classLoader.getResourceAsStream ("default/config/" + name),
                        new FileOutputStream (cpConfig));
            } catch (IOException e) {
                logger.severe ("Could not copy '" + name + "'!");
                e.printStackTrace ();
            }
        }
    }

    private void initializeSessionFactory () {
        try {
            File configFile = new File (StandardDirectories.config, "hibernate.properties");
            Properties properties = new Properties ();
            properties.load (new FileInputStream (configFile));

            Configuration cfg = new Configuration ();
            cfg.addProperties (properties);

            /* Let plugins configure Hibernate. */
            for (Plugin plugin : pluginManager.getPlugins ().values ()) {
                plugin.configureHibernate (cfg);
            }

            StandardServiceRegistryBuilder standardServiceRegistryBuilder = new StandardServiceRegistryBuilder ();
            standardServiceRegistryBuilder.applySettings (cfg.getProperties ());
            StandardServiceRegistry registry = standardServiceRegistryBuilder.build ();
            sessionFactory = cfg.buildSessionFactory (registry);
        }catch (Exception e) {
            logger.log (Level.SEVERE, "Error: ", e);
        }
    }

    public void destroy () {
        sessionFactory.close ();
    }

    public SessionFactory getSessionFactory () {
        return sessionFactory;
    }

    public PluginManager getPluginManager () {
        return pluginManager;
    }

    public Dispatcher getRootDispatcher () {
        return rootDispatcher;
    }

    public String getBasePath () {
        return basePath;
    }

    public CacheManager getCacheManager () {
        return cacheManager;
    }

    public Cache getFragmentCache () {
        return fragmentCache;
    }

}
