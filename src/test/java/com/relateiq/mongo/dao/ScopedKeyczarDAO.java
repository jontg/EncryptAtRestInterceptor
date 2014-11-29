package com.relateiq.mongo.dao;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.relateiq.mongo.dom.ScopedKeyczar;
import org.bson.types.ObjectId;
import org.javatuples.Pair;
import org.keyczar.*;
import org.keyczar.enums.KeyPurpose;
import org.keyczar.enums.KeyStatus;
import org.keyczar.exceptions.KeyczarException;
import org.keyczar.interfaces.KeyType;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.dao.BasicDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Created by jontg on 3/24/14.
 */
@Singleton
public class ScopedKeyczarDAO extends BasicDAO<ScopedKeyczar, ObjectId> {
    private static final Logger log = LoggerFactory.getLogger(ScopedKeyczarDAO.class);

    public static final String FIELD_SCOPE = "scope";

    private final Crypter crypter;

    // private static final String FIELD_METADATA = "metadata";
    // private static final String FIELD_SECRETS = "secrets";

    @Inject
    public ScopedKeyczarDAO(
            Crypter crypter,
            Datastore ds) {
        super(ScopedKeyczar.class, ds);
        this.crypter = crypter;
    }

    public void deleteByScope(String scope) {
        deleteByQuery(createQuery().field(FIELD_SCOPE).equal(scope));
    }

    public String getKey(String scope, KeyPurpose purpose, DefaultKeyType type, int size) {
        ScopedKeyczar keyczar = fetchOrCreateKeyczar(scope, purpose, type, size);
        KeyMetadata metadata;
        try {
            metadata = KeyMetadata.read(crypter.decrypt(keyczar.getMetadata()));
        } catch (KeyczarException e) {
            metadata = KeyMetadata.read(keyczar.getMetadata());
        }

        try {
            return crypter.decrypt(keyczar.getSecrets().get(Integer.toString(metadata.getPrimaryVersion().getVersionNumber())));
        } catch (KeyczarException e) {
            try {
                return keyczar.getSecrets().get(Integer.toString(metadata.getPrimaryVersion().getVersionNumber()));

            } catch (KeyczarException e2) {
                log.warn("Exception while fetching keys for " + scope, e2);
                return null;
            }
        }
    }

    public String getKey(int version, String scope, KeyPurpose purpose, DefaultKeyType type, int size) {
        try {
            return crypter.decrypt(
                    fetchOrCreateKeyczar(scope, purpose, type, size).getSecrets().get(Integer.toString(version)));
        } catch (KeyczarException e) {
            return fetchOrCreateKeyczar(scope, purpose, type, size).getSecrets().get(Integer.toString(version));
        }
    }

    public String getMetadata(String scope, KeyPurpose purpose, DefaultKeyType type, int size) {
        try {
            return crypter.decrypt(
                    fetchOrCreateKeyczar(scope, purpose, type, size).getMetadata());
        } catch (KeyczarException e) {
            return fetchOrCreateKeyczar(scope, purpose, type, size).getMetadata();
        }
    }

    public Iterable<String> getAllMetadata() {
        log.warn("Fetching all metadata for all keys - don't do this!");
        return Iterables.transform(createQuery().retrievedFields(true, "metadata").fetch(), ScopedKeyczar::getMetadata);
    }

    private ScopedKeyczar fetchOrCreateKeyczar(String scope, KeyPurpose purpose, DefaultKeyType type, int size) {
        ScopedKeyczar keyczar = createQuery().field(FIELD_SCOPE).equal(scope).get();
        if (keyczar == null) {
            KeyMetadata metadata = new KeyMetadata(scope, purpose, type);
            Pair<KeyVersion, KeyczarKey> version = generateKeyVersion(
                    type.isAcceptableSize(size) ? size : type.defaultSize(), metadata, scope);

            try {
                keyczar = ScopedKeyczar.newBuilder()
                        .setScope(scope)
                        .setMetadata(crypter.encrypt(metadata.toString()))
                        .setSecrets(ImmutableMap.of(Integer.toString(version.getValue0().getVersionNumber()),
                                crypter.encrypt(version.getValue1().toString())))
                        .build();
                save(keyczar);
                return createQuery().field(FIELD_SCOPE).equal(scope).get();
            } catch (KeyczarException e) {
                keyczar = ScopedKeyczar.newBuilder()
                        .setScope(scope)
                        .setMetadata(metadata.toString())
                        .setSecrets(ImmutableMap.of(Integer.toString(version.getValue0().getVersionNumber()),
                                version.getValue1().toString()))
                        .build();
                save(keyczar);
                return createQuery().field(FIELD_SCOPE).equal(scope).get();
            }
        }

        return keyczar;
    }

    private Pair<KeyVersion, KeyczarKey> generateKeyVersion(int number, KeyMetadata metadata, String scope) {

        KeyType type = metadata.getType();
        KeyczarKey key;
        try {
            key = type.getBuilder().generate(number);
        } catch (KeyczarException e) {
            log.warn("Keyczar exception while generating new metadata for " + scope, e);
            return null;
        }

        KeyVersion version = new KeyVersion(metadata.getVersions().size(), KeyStatus.PRIMARY, false);
        metadata.addVersion(version);

        return Pair.with(version, key);
    }
}
