package com.relateiq;

import com.relateiq.annotations.KeyczarReaderFactory;
import org.keyczar.Crypter;
import org.keyczar.DefaultKeyType;
import org.keyczar.enums.KeyPurpose;
import org.keyczar.exceptions.KeyczarException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;


/**
 * Created by jontg on 3/25/14.
 */
@Singleton
public class CrypterFactory {
    private static final Logger log = LoggerFactory.getLogger(CrypterFactory.class);
    private final KeyczarReaderFactory readerFactory;

    @Inject
    public CrypterFactory(KeyczarReaderFactory readerFactory) {
        this.readerFactory = readerFactory;
    }

    public Crypter create(String scope, KeyPurpose purpose, DefaultKeyType type, int size) {
        try {
            return new Crypter(readerFactory.create(scope, purpose, type, size));
        } catch (KeyczarException e) {
            log.error("Critical failure loading crypter for " + scope, e);
            return null;
        }
    }

}
