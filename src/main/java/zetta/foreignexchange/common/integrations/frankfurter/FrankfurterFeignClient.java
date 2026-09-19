package zetta.foreignexchange.common.integrations.frankfurter;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import zetta.foreignexchange.common.integrations.frankfurter.configuration.FrankfurterConfiguration;
import zetta.foreignexchange.common.integrations.frankfurter.response.FrankfurterRatePairResponse;

@FeignClient(name = "frankfurter", url = "${frankfurter.client.url}", configuration = FrankfurterConfiguration.class)
public interface FrankfurterFeignClient {

    @GetMapping(value = "/rate/{base}/{quote}", produces = MediaType.APPLICATION_JSON_VALUE)
    FrankfurterRatePairResponse fetchLatestExchangeRates(@PathVariable String base, @PathVariable String quote);
}
