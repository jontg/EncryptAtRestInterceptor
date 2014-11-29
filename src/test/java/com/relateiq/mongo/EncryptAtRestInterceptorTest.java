package com.relateiq.mongo;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import com.relateiq.annotations.EncryptAtRest;
import com.relateiq.mongo.guice.MongoConfigModuleForTest;
import com.relateiq.mongo.guice.MongoModuleForTest;
import com.relateiq.mongo.dao.ScopedKeyczarDAO;
import com.relateiq.mongo.impl.*;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.keyczar.DefaultKeyType;
import org.keyczar.enums.KeyPurpose;
import org.mongodb.morphia.Datastore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.HashMap;

import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.IsNot.not;

public class EncryptAtRestInterceptorTest {
    Logger log = LoggerFactory.getLogger(EncryptAtRestInterceptorTest.class);

    private static String SCOPE = "scope";
    protected static Injector injector;

    @Inject private ScopedKeyczarDAO scopedKeyczarDAO;
    @Inject private Datastore ds;
    @Inject @Named("UnencryptedDataSource") private Datastore unencryptedDs;

    @BeforeClass
    public static void setUpBeforeClass() throws IOException {
        injector = Guice.createInjector(new MongoModuleForTest(), new MongoConfigModuleForTest());
    }

    @Before
    public void setup() throws Exception {
        injector.injectMembers(this);

        ds = injector.getInstance(Datastore.class);
    }

    @After
    public void teardown() throws Exception {
        MongoModuleForTest.resetDBs();
    }

    @Test
    public void encryptAtRestObjectEncodesWithScope() {
        EncryptAtRestObject obj = new EncryptAtRestObject();
        obj.scope = ObjectId.get();
        obj.body = "Something encrypted";
        ds.save(obj);
        final EncryptAtRestObject asRead = ds.createQuery(EncryptAtRestObject.class).field(SCOPE).equal(obj.scope).get();
        assertThat(asRead.scope, equalTo(obj.scope));
        assertThat(asRead.body, equalTo(obj.body));

        final EncryptAtRestObject actual = unencryptedDs.createQuery(EncryptAtRestObject.class).field(SCOPE).equal(obj.scope).get();
        assertThat(actual.scope, equalTo(obj.scope));
        assertThat(actual.body, not(obj.body));
    }

    @Test
    public void encryptWithStaticScopeWorks() {
        EncryptWithStaticScopeObject obj = new EncryptWithStaticScopeObject();
        obj.body = "Something encrypted";
        ds.save(obj);
        final EncryptWithStaticScopeObject asRead = ds.createQuery(EncryptWithStaticScopeObject.class).field("id").equal(obj.id).get();
        assertThat(asRead.id, equalTo(obj.id));
        assertThat(asRead.body, equalTo(obj.body));

        final EncryptWithStaticScopeObject actual = unencryptedDs.createQuery(EncryptWithStaticScopeObject.class).field("id").equal(obj.id).get();
        assertThat(asRead.id, equalTo(obj.id));
        assertThat(actual.body, not(obj.body));
    }

    @Test
    public void encryptAtRestObjectCanReadUnencryptedObjects() {
        EncryptAtRestObject obj = new EncryptAtRestObject();
        obj.scope = ObjectId.get();
        obj.body = "Something encrypted";
        unencryptedDs.save(obj);
        final EncryptAtRestObject asRead = ds.createQuery(EncryptAtRestObject.class).field(SCOPE).equal(obj.scope).get();
        assertThat(asRead.scope, equalTo(obj.scope));
        assertThat(asRead.body, equalTo(obj.body));

        final EncryptAtRestObject actual = unencryptedDs.createQuery(EncryptAtRestObject.class).field(SCOPE).equal(obj.scope).get();
        assertThat(actual.scope, equalTo(obj.scope));
        assertThat(actual.body, equalTo(obj.body));
    }

    @Test
    public void encryptedDataIsUnreadableWhenKeysAreDestroyed() {
        EncryptAtRestObject obj = new EncryptAtRestObject();
        obj.scope = ObjectId.get();
        obj.body = "Something encrypted";
        ds.save(obj);

        scopedKeyczarDAO.deleteByScope(obj.scope.toString());

        final EncryptAtRestObject asRead = ds.createQuery(EncryptAtRestObject.class).field(SCOPE).equal(obj.scope).get();
        assertThat(asRead.scope, equalTo(obj.scope));
        assertThat(asRead.body, not(obj.body));
    }

    @Test
    public void encryptedDataIsUnreadableWhenKeyIllegallyChanged() {
        EncryptAtRestObject obj = new EncryptAtRestObject();
        obj.scope = ObjectId.get();
        obj.body = "Something encrypted";
        ds.save(obj);

        ObjectId newId = ObjectId.get();
        ds.update(ds.createQuery(EncryptAtRestObject.class).field(SCOPE).equal(obj.scope),
                ds.createUpdateOperations(EncryptAtRestObject.class).set(SCOPE, newId));

        final EncryptAtRestObject asRead = ds.createQuery(EncryptAtRestObject.class).field(SCOPE).equal(newId).get();
        assertNotNull(asRead);
        assertThat(asRead.scope, equalTo(newId));
        assertThat(asRead.body, not(obj.body));
    }

    @Test
    public void encryptAtRestObjectWithoutScopeDoesNotDie() {
        EncryptAtRestObjectWithoutScope obj = new EncryptAtRestObjectWithoutScope();
        obj.scope = ObjectId.get();
        obj.body = "Something encrypted";
        ds.save(obj);
        final EncryptAtRestObjectWithoutScope asRead = ds.createQuery(EncryptAtRestObjectWithoutScope.class)
                .field(SCOPE).equal(obj.scope).get();
        assertThat(asRead.scope, equalTo(obj.scope));
        assertThat(asRead.body, equalTo(obj.body));

        final EncryptAtRestObjectWithoutScope actual = unencryptedDs.createQuery(EncryptAtRestObjectWithoutScope.class)
                .field(SCOPE).equal(obj.scope).get();
        assertThat(actual.scope, equalTo(obj.scope));
        assertThat(actual.body, equalTo(obj.body));
    }

    @Test
    public void unencryptedObjectWithScopeDoesNotDie() {
        RestObject obj = new RestObject();
        obj.scope = ObjectId.get();
        obj.body = "Something plaintext";
        ds.save(obj);
        final RestObject asRead = ds.createQuery(RestObject.class).field(SCOPE).equal(obj.scope).get();
        assertThat(asRead.scope, equalTo(obj.scope));
        assertThat(asRead.body, equalTo(obj.body));

        final RestObject actual = unencryptedDs.createQuery(RestObject.class).field(SCOPE).equal(obj.scope).get();
        assertThat(actual.scope, equalTo(obj.scope));
        assertThat(actual.body, equalTo(obj.body));
    }

    @Test
    public void encryptAtRestObjectEncodesMapsWithScope() {
        EncryptAtRestMapObject obj = new EncryptAtRestMapObject();
        obj.scope = ObjectId.get();
        obj.map = new HashMap<>();
        obj.map.put("key1", "value");
        obj.map.put("key2", "value");
        ds.save(obj);
        final EncryptAtRestMapObject asRead = ds.createQuery(EncryptAtRestMapObject.class).field(SCOPE).equal(obj.scope).get();
        assertThat(asRead.scope, equalTo(obj.scope));
        assertThat(asRead.map, equalTo(obj.map));

        final EncryptAtRestMapObject actual = unencryptedDs.createQuery(EncryptAtRestMapObject.class).field(SCOPE).equal(obj.scope).get();
        assertThat(actual.scope, equalTo(obj.scope));
        assertThat(actual.map, not(obj.map));
    }

    @Test
    public void encryptAtRestAnnotatedObjectEncodesSubsWithScope() {
        EncryptAtRestWithAnnotations obj = new EncryptAtRestWithAnnotations();
        obj.scope = ObjectId.get();
        obj.subObject = new SubObject();
        obj.subObject.name = "Name";
        obj.subObject.test = 100;
        ds.save(obj);
        log.info("PostSave, PreLoad");
        final EncryptAtRestWithAnnotations asRead = ds.createQuery(EncryptAtRestWithAnnotations.class).field("id").equal(obj.scope).get();
        assertThat(asRead.scope, equalTo(obj.scope));
        assertThat(asRead.subObject.name, equalTo(obj.subObject.name));
        assertThat(asRead.subObject.test, equalTo(obj.subObject.test));

        /*
        final EncryptAtRestSubObject actual = unencryptedDs.createQuery(EncryptAtRestSubObject.class).field(ScopedKeyczarDAO.FIELD_SCOPE).equal(obj.scope).get();
        assertThat(actual.scope, equalTo(obj.scope));
        MatcherAssert.assertThat(actual.subObject.name, equalTo(obj.subObject.name));
        MatcherAssert.assertThat(actual.subObject.test, equalTo(obj.subObject.test));
        */
    }

    @Test
    public void fetchUnencryptedRestObjectEncodesSubsWithScope() {
        EncryptAtRestSubObject obj = new EncryptAtRestSubObject();
        obj.scope = ObjectId.get();
        obj.subObject = new SubObject();
        obj.subObject.name = "Name";
        obj.subObject.test = 100;
        unencryptedDs.save(obj);
        final EncryptAtRestSubObject asRead = ds.createQuery(EncryptAtRestSubObject.class).field("scope").equal(obj.scope).get();
        assertThat(asRead.scope, equalTo(obj.scope));
        assertThat(asRead.subObject.name, equalTo(obj.subObject.name));
        assertThat(asRead.subObject.test, equalTo(obj.subObject.test));

        /*
        final EncryptAtRestSubObject actual = unencryptedDs.createQuery(EncryptAtRestSubObject.class).field(ScopedKeyczarDAO.FIELD_SCOPE).equal(obj.scope).get();
        assertThat(actual.scope, equalTo(obj.scope));
        MatcherAssert.assertThat(actual.subObject.name, equalTo(obj.subObject.name));
        MatcherAssert.assertThat(actual.subObject.test, equalTo(obj.subObject.test));
        */
    }

    @Test
    public void fetchEncryptAtRestWithIdDoesntCrashAndBurn() {
        EncryptAtRestWithId obj = new EncryptAtRestWithId();
        obj.scope = ObjectId.get();
        obj.value = "Something encrypted";
        ds.save(obj);
        final EncryptAtRestWithId asRead = ds.createQuery(EncryptAtRestWithId.class).field("_id").equal(obj.scope).get();
        assertThat(asRead.scope, equalTo(obj.scope));
        assertThat(obj.value, equalTo(asRead.value));
    }


    private static class EncryptAtRestObjectWithoutScope {
        public ObjectId scope;

        @EncryptAtRest
        public String body;
    }

}
