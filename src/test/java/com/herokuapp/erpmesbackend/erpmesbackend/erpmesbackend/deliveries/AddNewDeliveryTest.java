package com.herokuapp.erpmesbackend.erpmesbackend.erpmesbackend.deliveries;

import com.herokuapp.erpmesbackend.erpmesbackend.erpmesbackend.FillBaseTemplate;
import com.herokuapp.erpmesbackend.erpmesbackend.shop.model.Delivery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AddNewDeliveryTest extends FillBaseTemplate {

    @Before
    public void init() {
        setupToken();
        addOneDeliveryRequest(false);
    }

    @Test
    public void checkIfResponseContainsAddedDelivery() {
        ResponseEntity<Delivery> deliveryResponseEntity = restTemplate.postForEntity("/deliveries",
                new HttpEntity<>(deliveryRequest, requestHeaders), Delivery.class);

        assertThat(deliveryResponseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Delivery delivery = deliveryResponseEntity.getBody();
        List<Delivery> deliveries = Arrays.asList(restTemplate.exchange("/deliveries",
                HttpMethod.GET, new HttpEntity<>(null, requestHeaders),
                Delivery[].class).getBody());
        assertTrue(deliveries.stream().anyMatch(d -> d.checkIfDataEquals(delivery)));
    }
}
