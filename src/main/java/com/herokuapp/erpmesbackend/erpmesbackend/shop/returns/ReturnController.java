package com.herokuapp.erpmesbackend.erpmesbackend.shop.returns;

import com.herokuapp.erpmesbackend.erpmesbackend.shop.orders.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin("*")
public class ReturnController {

    private final ReturnRepository returnRepository;
    private final ShopService shopService;

    public ReturnController(ReturnRepository returnRepository, ShopService shopService) {
        this.returnRepository = returnRepository;
        this.shopService = shopService;
    }

    @GetMapping("/returns")
    @ResponseStatus(HttpStatus.OK)
    public List<Return> getAllOrders() {
        return returnRepository.findAll();
    }

    @PostMapping("/returns")
    @ResponseStatus(HttpStatus.CREATED)
    public Return addOnReturn(@RequestBody ShopServiceRequest request) {
        return shopService.addNewReturn(request);
    }

    @GetMapping("/returns/{id}")
    @ResponseStatus(HttpStatus.OK)
    public Return getOneReturn(@PathVariable("id") Long id) {
        shopService.checkIfReturnExists(id);
        return returnRepository.findById(id).get();
    }

    @PatchMapping("/returns/{id}")
    public HttpStatus updateStatusReturn(@PathVariable("id") Long id, @RequestBody ReturnStatus status) {
        shopService.checkIfReturnExists(id);
        shopService.updateReturnStatus(id, status);
        return HttpStatus.NO_CONTENT;
    }
}
