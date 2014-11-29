package com.relateiq.mongo.impl;

import com.relateiq.annotations.EncryptAtRest;
import com.relateiq.annotations.EncryptionScope;
import com.relateiq.mongo.EncryptAtRestInterceptorTest;
import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Property;

/**
 * Created by jgretarsson on 11/17/14.
 */
public class EncryptAtRestWithAnnotations {
    @Property("id")
    @EncryptionScope
    public ObjectId scope;

    @Property("value")
    @EncryptAtRest
    public SubObject subObject;

    @Property("scope")
    public boolean justToMessWithThings = false;
}
