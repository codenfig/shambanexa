package com.shambanexa.controller;

import com.shambanexa.dto.MarketDtos.Board;
import com.shambanexa.dto.MarketDtos.DecisionBody;
import com.shambanexa.dto.MarketDtos.MessageView;
import com.shambanexa.dto.MarketDtos.OfferBody;
import com.shambanexa.dto.MarketDtos.OfferView;
import com.shambanexa.dto.MarketDtos.OrderView;
import com.shambanexa.dto.MarketDtos.ProductBody;
import com.shambanexa.dto.MarketDtos.ProductView;
import com.shambanexa.dto.MarketDtos.DemandBody;
import com.shambanexa.dto.MarketDtos.RequestView;
import com.shambanexa.dto.MarketDtos.SellerBody;
import com.shambanexa.dto.MarketDtos.SellerView;
import com.shambanexa.implementation.MarketplaceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/market")
public class MarketplaceController {

    private final MarketplaceService market;

    public MarketplaceController(MarketplaceService market) {
        this.market = market;
    }

    @GetMapping
    Board board(@RequestParam(required = false) String q,
                @RequestParam(required = false) String category,
                @RequestParam(required = false) String region,
                @RequestParam(required = false) String district) {
        return market.board(q, category, region, district);
    }

    @PostMapping("/sellers")
    @ResponseStatus(HttpStatus.CREATED)
    SellerView register(@RequestBody SellerBody body) {
        return market.registerSeller(body);
    }

    @PutMapping("/sellers/{id}")
    SellerView updateSeller(@PathVariable UUID id, @RequestBody SellerBody body) {
        return market.updateSeller(id, body);
    }

    @PostMapping("/sellers/{id}/verification")
    SellerView verify(@PathVariable UUID id, @RequestBody DecisionBody body) {
        return market.verifySeller(id, body);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    ProductView addProduct(@RequestBody ProductBody body) {
        return market.addProduct(body);
    }

    @PutMapping("/products/{id}")
    ProductView updateProduct(@PathVariable UUID id, @RequestBody ProductBody body) {
        return market.updateProduct(id, body);
    }

    @PostMapping("/products/{id}/moderation")
    ProductView moderate(@PathVariable UUID id, @RequestBody DecisionBody body) {
        return market.moderate(id, body);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    RequestView postRequest(@RequestBody DemandBody body) {
        return market.postRequest(body);
    }

    @PostMapping("/requests/{id}/offers")
    @ResponseStatus(HttpStatus.CREATED)
    OfferView offer(@PathVariable UUID id, @RequestBody OfferBody body) {
        return market.sendOffer(id, body);
    }

    @PostMapping("/offers/{id}/decision")
    OrderView decide(@PathVariable UUID id, @RequestBody DecisionBody body) {
        return market.decideOffer(id, body);
    }

    @PostMapping("/orders/{id}/status")
    OrderView advance(@PathVariable UUID id, @RequestBody DecisionBody body) {
        return market.advance(id, body);
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    MessageView message(@RequestBody DecisionBody body) {
        return market.message(body);
    }

    @PostMapping("/orders/{id}/review")
    @ResponseStatus(HttpStatus.CREATED)
    void review(@PathVariable UUID id, @RequestBody DecisionBody body) {
        market.review(id, body);
    }
}
