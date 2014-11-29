package com.relateiq.mongo.impl;

import com.relateiq.annotations.EncryptionScope;
import org.bson.types.ObjectId;

/**
* Created by jgretarsson on 11/17/14.
*/
public class RestObject {
    @EncryptionScope
    public ObjectId scope;
    public String body;
}
