package com.relateiq.mongo.impl;

import com.relateiq.annotations.EncryptAtRest;
import com.relateiq.annotations.EncryptionScope;
import org.bson.types.ObjectId;

import java.util.Map;

/**
 * Created by jgretarsson on 11/17/14.
 */
public class EncryptAtRestMapObject {
    @EncryptionScope
    public ObjectId scope;

    @EncryptAtRest
    public Map<String, String> map;
}
