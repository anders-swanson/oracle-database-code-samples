package com.example.clientinfo;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

@SpringBootApplication
public class ClientInfoApplication implements BeanPostProcessor {
    public static void main(String[] args) {
        SpringApplication.run(ClientInfoApplication.class, args);
    }

    @Value("${spring.application.name}")
    private String appName;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        InetAddress address;

        try {
            address = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        if(bean instanceof DataSource ds) {
            Properties props = new Properties();
            String clientId = "%s@%s".formatted(appName, address.getHostName());
            props.setProperty("OCSID.CLIENTID", clientId);
            return new ClientInfoDataSource(ds, props);

        }
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }
}
