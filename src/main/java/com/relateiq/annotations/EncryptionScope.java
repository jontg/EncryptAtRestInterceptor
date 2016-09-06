package com.relateiq.annotations;

import org.keyczar.DefaultKeyType;
import org.keyczar.enums.KeyPurpose;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface EncryptionScope {
	public String scope() default "";

	public KeyPurpose purpose() default KeyPurpose.DECRYPT_AND_ENCRYPT;

	public DefaultKeyType type() default DefaultKeyType.AES;

	public int size() default 128;
}
