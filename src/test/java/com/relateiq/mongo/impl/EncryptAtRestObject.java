package com.relateiq.mongo.impl;

import com.relateiq.annotations.EncryptAtRest;
import com.relateiq.annotations.EncryptionScope;
import org.bson.types.ObjectId;

/**
 * Created by jgretarsson on 11/17/14.
 */
public class EncryptAtRestObject {
    @EncryptionScope
    public ObjectId scope;

    @EncryptAtRest
    public String body;
}
