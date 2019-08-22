package com.ali.trace.spy.jetty.handler;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @auther hanlang@mallcai.com
 * @date 2019-08-21 23:32
 */
public interface ITraceHttpHandler {


    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface TracerPath {
        String value() default "";
        int order() default 0;
    }

    @Target({ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface TraceParam {
        String value() default "";
    }

}
