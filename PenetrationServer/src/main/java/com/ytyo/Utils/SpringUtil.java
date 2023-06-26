package com.ytyo.Utils;

import org.springframework.context.ApplicationContext;


public class SpringUtil {
    public static ApplicationContext context;

    public static void initContext(ApplicationContext context) {
        SpringUtil.context = context;
    }

    public static <T> T getBean(Class<T> clazz) {
        return context.getBean(clazz);
    }
}
