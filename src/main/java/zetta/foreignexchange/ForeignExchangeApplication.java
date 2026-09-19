package zetta.foreignexchange;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "zetta.foreignexchange.common.integrations")
public class ForeignExchangeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForeignExchangeApplication.class, args);
    }

}
