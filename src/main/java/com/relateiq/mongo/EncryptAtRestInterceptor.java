package com.relateiq.mongo;

import com.esotericsoftware.reflectasm.FieldAccess;
import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import com.relateiq.CrypterFactory;
import com.relateiq.annotations.EncryptAtRest;
import com.relateiq.annotations.EncryptionScope;
import org.javatuples.Quartet;
import org.javatuples.Triplet;
import org.keyczar.Crypter;
import org.keyczar.DefaultKeyType;
import org.keyczar.enums.KeyPurpose;
import org.keyczar.exceptions.BadVersionException;
import org.keyczar.exceptions.KeyczarException;
import org.keyczar.util.Base64Coder;
import org.mongodb.morphia.EntityInterceptor;
import org.mongodb.morphia.annotations.Property;
import org.mongodb.morphia.mapping.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * This interceptor makes encrypting data at rest (in mongo) as easy as adding some annotations to the relevant fields.
 * {@link com.relateiq.annotations.EncryptAtRest} is used to annotate which fields should be encrypted at rest, and {@link com.relateiq.annotations.EncryptionScope} is
 * the <em>scope</em> within which the document belongs. For example, {@link dom.MessageInfo} encrypts message bodies at rest in the scope of the userid.
 * <p/>
 * The <em>scope</em> defines a unique Keyczar ring that is associated with it, and therefore verification that a user has access to said scope should be
 * enforced by their associated DAOs. For example, all methods utilizing MessageInfoDAO get the userid through the UserContext, rather than from a parameter
 * passed in from a client.
 * <p/>
 * All scopes should be usefully .toString()-able, and all at-rest encrypted documents should be Strings, primitive data structures, or JSON encodable objects
 * with well defined getters and setters to facilitate conversion.
 * <p/>
 * The scope of a document <em>should not be changed</em> unless the entire document is persisted!
 * <p/>
 * The expected behavior is as follows:
 * <ul>
 * <li>If a new document is saved and the scope is not null, all relevant fields are encrypted before being sent to mongo.</li>
 * <li>If an old, unencrypted document is read from disc, the document should be returned as expected.</li>
 * <li>If a document has fields marked as {@link com.relateiq.annotations.EncryptAtRest} but no scope is provided, nothing is encrypted.</li>
 * <li>If a document has a field marked as {@link com.relateiq.annotations.EncryptionScope} but no fields are marked as {@link com.relateiq.annotations.EncryptAtRest},
 * then nothing is encrypted.</li>
 * </ul>
 * <p/>
 * Created by jontg on 3/25/14.
 */
public class EncryptAtRestInterceptor implements EntityInterceptor {
    public static final Charset UTF8_CHARSET = Charset.forName("UTF-8");
    private final Logger log = LoggerFactory.getLogger(EncryptAtRestInterceptor.class);

    private final LoadingCache<Class<?>, Optional<Quartet<Class, FieldAccess, String, EncryptionScope>>> scopes;
    private final LoadingCache<Class<?>, List<Triplet<Class, FieldAccess, String>>> encryptedFields;

    private final CrypterFactory crypterFactory;

    @Inject
    public EncryptAtRestInterceptor(CrypterFactory crypterFactory) {
        this.crypterFactory = crypterFactory;

        scopes = CacheBuilder.newBuilder()
                .recordStats()
                .build(new CacheLoader<Class, Optional<Quartet<Class, FieldAccess, String, EncryptionScope>>>() {
                    @Override
                    public Optional<Quartet<Class, FieldAccess, String, EncryptionScope>> load(Class key) throws Exception {
                        FieldAccess fieldAccess = FieldAccess.get(key);
                        for (Field f : key.getDeclaredFields()) {
                            if (f.isAnnotationPresent(EncryptionScope.class)) {
                                return Optional.of(Quartet.with(key, fieldAccess, f.getName(), f.getAnnotation(EncryptionScope.class)));
                            }
                        }

                        return Optional.absent();
                    }
                });

        encryptedFields = CacheBuilder.newBuilder()
                .recordStats()
                .build(new CacheLoader<Class, List<Triplet<Class, FieldAccess, String>>>() {
                    @Override
                    public List<Triplet<Class, FieldAccess, String>> load(Class key) throws Exception {
                        List<Triplet<Class, FieldAccess, String>> fieldAccesses = Lists.newArrayList();
                        FieldAccess fieldAccess = FieldAccess.get(key);
                        for (Field f : key.getDeclaredFields()) {
                            if (f.isAnnotationPresent(EncryptAtRest.class)) {
                                String fieldName = f.getName();
                                fieldAccesses.add(Triplet.with(key, fieldAccess, fieldName));
                            }
                        }
                        return fieldAccesses;
                    }
                });
    }

    @Override
    public void preLoad(java.lang.Object o, DBObject dbObject, Mapper mapper) {
// if (o instanceof ScopedKeyczar) {
//     return;
// }

        final Crypter crypter = loadCrypter(o, dbObject);
        if (crypter == null) {
            return;
        }

        List<Triplet<Class, FieldAccess, String>> fieldLookups = encryptedFields.getUnchecked(o.getClass());
        for (Triplet<Class, FieldAccess, String> fieldLookup : fieldLookups) {
            final String fieldName = fieldLookup.getValue2();
            final String jsonName = getJsonName(fieldLookup.getValue0(), fieldName);
            try {
                dbObject.put(jsonName,
                        getDecryptedValue(mapper, crypter,
                                o.getClass().getDeclaredField(fieldName).getType(),
                                dbObject.removeField(jsonName)));
            } catch (NoSuchFieldException e) {
                // WTF edge case
                log.warn("unable to decode {} [{}] from {}",
                        fieldName, jsonName, fieldLookup.getValue0().getCanonicalName(), e);
            }
        }
    }

    Object getDecryptedValue(final Mapper mapper, final Crypter crypter, final Class expectedType, final Object value) {
        if (value == null) {
            return null;
        }

        if (!(value instanceof String)) {
            log.warn("Unencrypted at-rest object while processing {}", Objects.toString(value));
            return value;
        }

        try {
            byte[] bytes;
            try {
                bytes = Base64Coder.decodeWebSafe((String) value);
            } catch (Exception e) {
                bytes = ((String) value).getBytes(UTF8_CHARSET);
            }

            bytes = crypter.decrypt(bytes);
            if (expectedType.equals(String.class)) {
                return new String(bytes, UTF8_CHARSET);
            } else if (expectedType.equals(Map.class)) {
                return JSON.parse(new String(bytes, UTF8_CHARSET));
            } else {
                log.warn("EncryptAtRest has not been well-tested with objects - please be careful encrypting {}!", expectedType.getSimpleName());
                DBObject object = (DBObject) JSON.parse(new String(bytes, UTF8_CHARSET));
                try {
                    return mapper.fromDBObject(expectedType, object, null);
                } catch (ClassCastException e) {
                    return object;
                }
            }
        } catch (BadVersionException e) {
            log.warn("Unencrypted at-rest object while processing {}: {} - {}", expectedType.getCanonicalName(), e.getClass().getSimpleName(), e.getMessage());
            return value;
        } catch (KeyczarException e) {
            log.error("Encryption exception while processing {}: {} - {}", expectedType.getCanonicalName(), e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    @Override
    public void preSave(Object o, DBObject dbObject, Mapper mapper) {
        // if (o instanceof ScopedKeyczar) {
        //     return;
        // }

        final Crypter crypter = loadCrypter(o, dbObject);
        if (crypter == null) {
            return;
        }

        List<Triplet<Class, FieldAccess, String>> fieldLookups = encryptedFields.getUnchecked(o.getClass());
        for (Triplet<Class, FieldAccess, String> fieldLookup : fieldLookups) {
            final String jsonName = getJsonName(fieldLookup.getValue0(), fieldLookup.getValue2());
            try {
                dbObject.put(jsonName, getEncryptedValue(mapper, crypter,
                        o.getClass().getDeclaredField(fieldLookup.getValue2()).getType(),
                        dbObject.get(jsonName)));
            } catch (NoSuchFieldException e) {
                log.warn("unable to decode {} [{}] from {}",
                        fieldLookup.getValue2(), jsonName, fieldLookup.getValue0().getCanonicalName(), e);
            }
        }
    }

    /**
     * @param mapper  morphia mapper to convert objects to database objects
     * @param crypter crypter by which the value should be encrypted
     * @param type    the type of value to be encrypted
     * @param value   the object to be encrypted  @return If successfully encrypted, a String representing the encrypted, encoded value; otherwise, the original value.
     */
    /* package private */Object getEncryptedValue(final Mapper mapper, final Crypter crypter, Class type, final Object value) {
        try {
            byte[] bytes;
            if (value instanceof String) {
                bytes = ((String) value).getBytes(UTF8_CHARSET);
            } else if (type.equals(Map.class)) {
                // TODO(jon,henry) should List also be treaded separately?
                bytes = JSON.serialize(value).getBytes(UTF8_CHARSET);
            } else {
                bytes = JSON.serialize(mapper.toDBObject(value)).getBytes(UTF8_CHARSET);
            }

            return Base64Coder.encodeWebSafe(crypter.encrypt(bytes));
        } catch (Exception e) {
            return value;
        }
    }

    @Override
    public void postLoad(Object o, DBObject dbObject, Mapper mapper) {
    }

    @Override
    public void prePersist(Object o, DBObject dbObject, Mapper mapper) {
    }

    @Override
    public void postPersist(Object o, DBObject dbObject, Mapper mapper) {
    }

    private Crypter loadCrypter(Object o, DBObject dbObject) {
        Optional<Quartet<Class, FieldAccess, String, EncryptionScope>> scopeLookup = scopes.getUnchecked(o.getClass());
        String scope = null;
        KeyPurpose purpose = null;
        DefaultKeyType type = null;
        int size = 0;
        if (scopeLookup.isPresent()) {
            final Quartet<Class, FieldAccess, String, EncryptionScope> quartet = scopeLookup.get();
            scope = quartet.getValue3().scope();
            purpose = quartet.getValue3().purpose();
            type = quartet.getValue3().type();
            size = quartet.getValue3().size();

            if (scope == null || scope.isEmpty()) {
                Object scopeObj = dbObject.get(getJsonName(quartet.getValue0(), quartet.getValue2()));
                if (scopeObj == null) {
                    scopeObj = quartet.getValue1().get(o, quartet.getValue2());
                }
                scope = String.valueOf(scopeObj);
            }
        }

        if (scope == null) {
            return null;
        }

        return crypterFactory.create(scope, purpose, type, size);
    }

    private String getJsonName(Class clazz, String fieldName) {
        Field result;
        try {
            result = clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            result = null;
        }
        if (result == null) {
            return fieldName;
        } else if (result.isAnnotationPresent(org.mongodb.morphia.annotations.Id.class)) {
            return "_id";
        } else if (result.isAnnotationPresent(Property.class)) {
            return result.getAnnotation(Property.class).value();
        } else {
            return fieldName;
        }
    }
}
