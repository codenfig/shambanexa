package com.shambanexa.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class MarketDtos {
    private MarketDtos() {
    }

    public record Board(
            Stats stats,
            List<ProductView> products,
            List<ProductView> listings,
            List<ProductView> reviewListings,
            List<RequestView> requests,
            List<OfferView> offers,
            List<OrderView> orders,
            List<MessageView> messages,
            List<NoticeView> notices,
            SellerView ownSeller,
            List<SellerView> pendingSellers,
            boolean administrator
    ) {
    }

    public record Stats(long products, long buyers, long sellers, long openRequests, long pendingOffers, long completed) {
    }

    public record SellerView(
            UUID id, String name, String region, String district, String phone, double latitude, double longitude,
            String summary, String approval, boolean owned, Double rating, int reviews
    ) {
    }

    public record ProductView(
            UUID id, String name, String category, String imageKey, String description, BigDecimal quantity, String unit,
            BigDecimal priceAmount, String currency, BigDecimal minOrder, String grade, String region, String district,
            double latitude, double longitude, String fulfillment, String status, String sellerName, boolean verified,
            boolean owned, Double rating
    ) {
    }

    public record RequestView(
            UUID id, UUID productId, String productName, String category, BigDecimal quantity, String unit, String grade,
            BigDecimal targetPrice, String currency, String region, String district, Double latitude, Double longitude,
            LocalDate neededOn, String note, String status, String buyerName, boolean ownRequest, Instant createdAt
    ) {
    }

    public record OfferView(
            UUID id, UUID requestId, UUID sellerId, String sellerName, UUID productId, BigDecimal quantity, String unit,
            BigDecimal priceAmount, String currency, String terms, String status, boolean ownOffer, Instant createdAt
    ) {
    }

    public record OrderView(
            UUID id, UUID requestId, String productName, BigDecimal quantity, String unit, BigDecimal priceAmount,
            String currency, String fulfillment, String status, String buyerName, String sellerName, String sellerPhone,
            boolean ownPurchase, boolean ownSale, boolean reviewed, Instant createdAt
    ) {
    }

    public record MessageView(UUID id, UUID requestId, UUID orderId, String senderName, String body, boolean own, Instant createdAt) {
    }

    public record NoticeView(UUID id, String title, String detail, Instant createdAt) {
    }

    public record SellerBody(String name, String region, String district, String phone, Double latitude, Double longitude, String summary) {
    }

    public record ProductBody(
            String name, String category, String imageKey, String description, BigDecimal quantity, String unit,
            BigDecimal priceAmount, BigDecimal minOrder, String grade, String region, String district,
            Double latitude, Double longitude, String fulfillment
    ) {
    }

    public record DemandBody(
            UUID productId, String productName, String category, BigDecimal quantity, String unit, String grade,
            BigDecimal targetPrice, String region, String district, Double latitude, Double longitude,
            String neededOn, String note
    ) {
    }

    public record OfferBody(UUID productId, BigDecimal quantity, String unit, BigDecimal priceAmount, String terms) {
    }

    public record DecisionBody(Boolean accept, String status, String body, Integer score, String note, UUID requestId, UUID orderId) {
    }
}
