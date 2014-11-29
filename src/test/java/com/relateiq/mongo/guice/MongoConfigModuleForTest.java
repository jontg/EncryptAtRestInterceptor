package com.relateiq.mongo.guice;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

/**
 * Created by jon.gretarsson on 8/4/14.
 */
public class MongoConfigModuleForTest extends AbstractModule {
    @Override
    protected void configure() {
        {
            Properties properties = new Properties();
            try {
                ClassLoader ldr = MongoConfigModuleForTest.class.getClassLoader();
                InputStream is = ldr.getResourceAsStream("app.properties");
                properties.load(new InputStreamReader(is));

                Names.bindProperties(binder(), properties);
            } catch (Exception ignored) {
            }
        }
    }
}
