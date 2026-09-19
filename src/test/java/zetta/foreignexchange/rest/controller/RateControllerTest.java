package zetta.foreignexchange.rest.controller;

import static org.instancio.Select.field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import zetta.foreignexchange.core.model.ExchangeRate;
import zetta.foreignexchange.core.service.RateService;
import zetta.foreignexchange.rest.mapper.ExchangeRateResponseMapper;
import zetta.foreignexchange.rest.model.ExchangeRateResponse;

import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
public class RateControllerTest {

    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final BigDecimal SCALED_RATE = new BigDecimal("0.86984000");

    @Mock
    private RateService rateService;

    @Spy
    private ExchangeRateResponseMapper exchangeRateResponseMapper = Mappers.getMapper(ExchangeRateResponseMapper.class);

    @InjectMocks
    private RateController rateController;

    @Test
    void getExchangeRate_withCurrencyPair_returnExchangeRateResponse() {
        ExchangeRate exchangeRate = Instancio.of(ExchangeRate.class)
                .set(field(ExchangeRate::baseCurrency), USD)
                .set(field(ExchangeRate::quoteCurrency), EUR)
                .set(field(ExchangeRate::rate), SCALED_RATE)
                .create();

        when(rateService.getExchangeRate(USD, EUR))
                .thenReturn(exchangeRate);

        ResponseEntity<ExchangeRateResponse> response = rateController.getExchangeRate(USD, EUR);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        ExchangeRateResponse exchangeRateResponse = response.getBody();
        assertEquals(USD, exchangeRateResponse.baseCurrency());
        assertEquals(EUR, exchangeRateResponse.quoteCurrency());
        assertEquals(SCALED_RATE, exchangeRateResponse.rate());
        assertEquals(exchangeRate.date(), exchangeRateResponse.date());

        verify(rateService).getExchangeRate(USD, EUR);
        verify(exchangeRateResponseMapper).mapToExchangeRateResponse(exchangeRate);
    }

    @Test
    void getExchangeRate_withIdenticalCurrencyPair_returnExchangeRateResponse() {
        ExchangeRate exchangeRate = Instancio.of(ExchangeRate.class)
                .set(field(ExchangeRate::baseCurrency), USD)
                .set(field(ExchangeRate::quoteCurrency), USD)
                .set(field(ExchangeRate::rate), BigDecimal.ONE)
                .create();

        when(rateService.getExchangeRate(USD, USD))
                .thenReturn(exchangeRate);

        ResponseEntity<ExchangeRateResponse> response = rateController.getExchangeRate(USD, USD);

        assertNotNull(response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().rate().compareTo(BigDecimal.ONE));

        verify(rateService).getExchangeRate(USD, USD);
        verify(exchangeRateResponseMapper).mapToExchangeRateResponse(exchangeRate);
    }
}
