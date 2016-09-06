package com.relateiq.annotations;

import org.keyczar.DefaultKeyType;
import org.keyczar.enums.KeyPurpose;
import org.keyczar.interfaces.KeyczarReader;

public interface KeyczarReaderFactory {
    public KeyczarReader create(String scope, KeyPurpose purpose, DefaultKeyType type, int size);
}
