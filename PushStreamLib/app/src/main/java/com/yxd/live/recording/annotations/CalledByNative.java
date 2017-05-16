/**
 * 
 */
package com.yxd.live.recording.annotations;

/**
 * is used by the JNI generator to create the necessary JNI
 * bindings and expose this method to native code.
 */
public @interface CalledByNative {
	/*
     * If present, tells which inner class the method belongs to.
     */
    String value() default "";
}
