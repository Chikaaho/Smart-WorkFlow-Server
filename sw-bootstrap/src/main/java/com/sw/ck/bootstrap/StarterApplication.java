package com.sw.ck.bootstrap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Smart-WorkFlow 启动入口。
 * <p>
 * scanBasePackages = "com.sw.ck" 确保所有模块的 @Service/@Component 均被扫描。
 * MapperScan 确保各模块的 @Mapper 接口均注册为 Bean。
 * </p>
 */
@SpringBootApplication(scanBasePackages = "com.sw.ck")
@MapperScan({"com.sw.ck.**.mapper", "com.sw.ck.bootstrap.verify"})
public class StarterApplication {

    public static void main(String[] args) {
        SpringApplication.run(StarterApplication.class, args);
    }

}
