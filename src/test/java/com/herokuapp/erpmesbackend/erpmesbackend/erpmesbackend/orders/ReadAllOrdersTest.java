package com.herokuapp.erpmesbackend.erpmesbackend.erpmesbackend.orders;

import com.herokuapp.erpmesbackend.erpmesbackend.erpmesbackend.FillBaseTemplate;
import com.herokuapp.erpmesbackend.erpmesbackend.shop.orders.Order;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ReadAllOrdersTest extends FillBaseTemplate {

    @Before
    public void init() {
        addManyItemRequests(true);
        addManyOrderRequests(true);
    }

    @Test
    public void checkIfResponseContainsAllOrders() {
        ResponseEntity<Order[]> forEntity = restTemplate.getForEntity("/orders", Order[].class);
        assertThat(forEntity.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Order> orders = Arrays.asList(forEntity.getBody());
        assertThat(orders.size()).isEqualTo(3);
    }
}
