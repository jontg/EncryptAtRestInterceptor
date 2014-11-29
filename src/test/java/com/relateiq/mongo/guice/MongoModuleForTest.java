package com.relateiq.mongo.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.mongodb.DB;
import com.mongodb.Mongo;
import com.relateiq.CrypterFactory;
import com.relateiq.annotations.KeyczarReaderFactory;
import com.relateiq.mongo.EncryptAtRestInterceptor;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import org.keyczar.Crypter;
import org.keyczar.DefaultKeyType;
import org.keyczar.MockKeyczarReader;
import org.keyczar.enums.KeyPurpose;
import org.keyczar.exceptions.KeyczarException;
import org.keyczar.interfaces.KeyczarReader;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.DatastoreImpl;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.logging.MorphiaLoggerFactory;
import org.mongodb.morphia.logging.slf4j.SLF4JLoggerImplFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.UnknownHostException;

/**
 * Configures Mongo for unit tests.
 * <p/>
 * <b>Why was this class created?</b> A number of tests (RiqEx, schema-registry) live outside of LucidDAO but still need to configure Mongo. We have two options:
 * Either create this class, or put those tests in LucidDAO. Putting all our code in LucidDAO leads to code crap so I chose this option. The added advantage
 * of this class is that mongo related initialization is now nicely separated out from everyone else.
 * <p/>
 * <p/>
 * <b>Why is this a PrivateModule?</b> We again had two choices. Choice 1 was to make all classes that create an injector install MongoDAOModuleForTest in
 * addition to DAOModuleForTest like tis {@code Guice.createInjector(new DAOModuleForTest(), new MongoDAOModuleForTest());}. The other choice was to let
 * DAOModuleForTest install other modules. The latter seems like a cleaner option but it requires MongoDAOModuleForTest to be a PrivateModule which exposes some
 * of it's bindings so there is no clash between the root and MongoDAOModuleForTest.
 *
 * @author adilaijaz
 * @see com.google.inject.PrivateModule
 * @see com.google.inject.AbstractModule#install(com.google.inject.Module)
 */
@Singleton
public class MongoModuleForTest extends AbstractModule {

    private static final Logger log = LoggerFactory.getLogger(MongoModuleForTest.class);

    private static DB db;
    private static MongodStarter starter;
    private static MongodExecutable mongodExecutable;
    private static MongodProcess mongod;

    public MongoModuleForTest() throws IOException {
        starter = MongodStarter.getDefaultInstance();

        int port = 27017;
        IMongodConfig mongodConfig = new MongodConfigBuilder()
                .version(Version.Main.PRODUCTION)
                .net(new Net(port, Network.localhostIsIPv6()))
                .build();
        mongodExecutable = starter.prepare(mongodConfig);
        mongod = mongodExecutable.start();

        Runtime.getRuntime().addShutdownHook(
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            mongod.stop();
                        } catch (Exception ignored) {
                        }
                    }
                }
        );
    }

    @Override
    protected void configure() {
        bind(CrypterFactory.class);
        install(new FactoryModuleBuilder()
                .implement(KeyczarReader.class, MongoKeyczarReader.class)
                .build(KeyczarReaderFactory.class));

    }

    public static void resetDBs() {
        try {
            db.dropDatabase();
        } catch (Exception e) {
            log.error("error resetting mongo DB", e);
        }
    }

    @Provides
    @Singleton
    Morphia provideMorphia(CrypterFactory crypterFactory) {
        MorphiaLoggerFactory.reset();
        MorphiaLoggerFactory.registerLogger(SLF4JLoggerImplFactory.class);

        final Morphia morphia = new Morphia();
        morphia.mapPackage("dom");
        morphia.getMapper().getOptions().setStoreEmpties(true);

        morphia.getMapper().addInterceptor(new EncryptAtRestInterceptor(crypterFactory));
        return morphia;
    }

    @Provides
    @Singleton
    Datastore provideDatastore(Morphia morphia, Mongo mongo, @Named("MONGO_DATABASE") String db_name) {
        DatastoreImpl datastore = new DatastoreImpl(morphia, mongo, db_name);

        // Reset-able db
        db = mongo.getDB(db_name);

        return datastore;
    }

    @Provides
    @Singleton
    @Named("UnencryptedDataSource")
    Datastore provideUnencryptedDataStore(Mongo mongo, @Named("MONGO_DATABASE") String mongoDB,
                                          @SuppressWarnings({"unused"}) Datastore ds) throws UnknownHostException {
        // Inject the above datastore even though it is unused.  This is to force the reset mechanism to be initialized.

        final Morphia morphia = new Morphia();
        morphia.mapPackage("dom");
        morphia.getMapper().getOptions().setStoreEmpties(true);

        return new DatastoreImpl(morphia, mongo, mongoDB);
    }

    @Provides
    @Singleton
    Mongo provideMongo(@Named("MONGO_HOST") String mongoHost, @Named("MONGO_PORT") int mongoPort) throws UnknownHostException {
        return new Mongo(mongoHost, mongoPort);
    }

    @Provides
    @Singleton
    Crypter provideCrypter() throws KeyczarException {
        return new Crypter(new MockKeyczarReader("MOCK_KEYCZAR", KeyPurpose.DECRYPT_AND_ENCRYPT, DefaultKeyType.AES));
    }
}
