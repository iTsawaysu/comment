package com.sun.comment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * @author sun
 */
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
