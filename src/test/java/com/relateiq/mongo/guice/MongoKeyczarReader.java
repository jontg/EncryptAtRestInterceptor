package com.relateiq.mongo.guice;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.relateiq.mongo.dao.ScopedKeyczarDAO;
import org.keyczar.DefaultKeyType;
import org.keyczar.enums.KeyPurpose;
import org.keyczar.exceptions.KeyczarException;
import org.keyczar.interfaces.KeyczarReader;

/**
 * Created by jontg on 3/24/14.
 */
public class MongoKeyczarReader implements KeyczarReader {
    private final String scope;
    private final KeyPurpose purpose;
    private final DefaultKeyType type;
    private final int size;
    private final ScopedKeyczarDAO scopedKeyczarDAO;

    @AssistedInject
    MongoKeyczarReader(
            ScopedKeyczarDAO scopedKeyczarDAO,
            @Assisted String scope,
            @Assisted KeyPurpose purpose,
            @Assisted DefaultKeyType type,
            @Assisted int size) {
        this.scopedKeyczarDAO = scopedKeyczarDAO;
        this.scope = scope;
        this.purpose = purpose;
        this.type = type;
        this.size = size;
    }

    @Override
    public String getKey(int version) throws KeyczarException {
        return scopedKeyczarDAO.getKey(version, scope, purpose, type, size);
    }

    @Override
    public String getKey() throws KeyczarException {
        return scopedKeyczarDAO.getKey(scope, purpose, type, size);
    }

    @Override
    public String getMetadata() throws KeyczarException {
        return scopedKeyczarDAO.getMetadata(scope, purpose, type, size);
    }
}
