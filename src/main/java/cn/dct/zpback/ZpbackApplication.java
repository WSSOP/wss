package cn.dct.zpback;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("cn.dct.zpback.mapper")
public class ZpbackApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZpbackApplication.class, args);
    }

}
