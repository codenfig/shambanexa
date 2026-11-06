package com.shambanexa.implementation;

import com.shambanexa.common.ApiException;
import com.shambanexa.common.CurrentUser;
import com.shambanexa.dto.MarketDtos.Board;
import com.shambanexa.dto.MarketDtos.DecisionBody;
import com.shambanexa.dto.MarketDtos.MessageView;
import com.shambanexa.dto.MarketDtos.NoticeView;
import com.shambanexa.dto.MarketDtos.OfferBody;
import com.shambanexa.dto.MarketDtos.OfferView;
import com.shambanexa.dto.MarketDtos.OrderView;
import com.shambanexa.dto.MarketDtos.ProductBody;
import com.shambanexa.dto.MarketDtos.ProductView;
import com.shambanexa.dto.MarketDtos.DemandBody;
import com.shambanexa.dto.MarketDtos.RequestView;
import com.shambanexa.dto.MarketDtos.SellerBody;
import com.shambanexa.dto.MarketDtos.SellerView;
import com.shambanexa.dto.MarketDtos.Stats;
import com.shambanexa.entity.MarketMessage;
import com.shambanexa.entity.MarketNotice;
import com.shambanexa.entity.MarketOffer;
import com.shambanexa.entity.MarketOrder;
import com.shambanexa.entity.MarketProduct;
import com.shambanexa.entity.MarketRequest;
import com.shambanexa.entity.MarketReview;
import com.shambanexa.entity.MarketSeller;
import com.shambanexa.entity.UserAccount;
import com.shambanexa.repository.MarketMessageRepository;
import com.shambanexa.repository.MarketNoticeRepository;
import com.shambanexa.repository.MarketOfferRepository;
import com.shambanexa.repository.MarketOrderRepository;
import com.shambanexa.repository.MarketProductRepository;
import com.shambanexa.repository.MarketRequestRepository;
import com.shambanexa.repository.MarketReviewRepository;
import com.shambanexa.repository.MarketSellerRepository;
import com.shambanexa.repository.UserAccountRepository;
import com.shambanexa.security.AccessGuard;
import com.shambanexa.service.AuditTrail;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class MarketplaceService {

    private static final ZoneId DAR = ZoneId.of("Africa/Dar_es_Salaam");
    private static final Set<String> IMAGES = Set.of("MAIZE", "BEANS", "TOMATOES", "MILK", "POTATOES", "ONIONS", "CHICKENS", "RICE");
    private static final Set<String> CATEGORIES = Set.of("Cereals", "Legumes", "Vegetables", "Dairy", "Poultry", "Fruits", "Livestock", "Other");
    private static final Set<String> UNITS = Set.of("KG", "TONNE", "BAG", "LITRE", "PIECE");
    private static final Set<String> FULFILLMENT = Set.of("COLLECTION", "DELIVERY", "BOTH");

    private final AccessGuard guard;
    private final AuditTrail audit;
    private final MarketSellerRepository sellers;
    private final MarketProductRepository products;
    private final MarketRequestRepository requests;
    private final MarketOfferRepository offers;
    private final MarketOrderRepository orders;
    private final MarketMessageRepository messages;
    private final MarketReviewRepository reviews;
    private final MarketNoticeRepository notices;
    private final UserAccountRepository users;

    public MarketplaceService(AccessGuard guard, AuditTrail audit, MarketSellerRepository sellers,
                              MarketProductRepository products, MarketRequestRepository requests,
                              MarketOfferRepository offers, MarketOrderRepository orders,
                              MarketMessageRepository messages, MarketReviewRepository reviews,
                              MarketNoticeRepository notices, UserAccountRepository users) {
        this.guard = guard;
        this.audit = audit;
        this.sellers = sellers;
        this.products = products;
        this.requests = requests;
        this.offers = offers;
        this.orders = orders;
        this.messages = messages;
        this.reviews = reviews;
        this.notices = notices;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Board board(String q, String category, String region, String district) {
        CurrentUser user = guard.current();
        UUID tenantId = user.tenantId();
        List<MarketSeller> shops = sellers.findByTenantIdOrderByNameAsc(tenantId);
        Map<UUID, MarketSeller> shopById = shops.stream().collect(Collectors.toMap(MarketSeller::getId, Function.identity()));
        List<MarketProduct> rows = products.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<MarketRequest> demand = requests.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<MarketOffer> quotes = offers.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<MarketOrder> sales = orders.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<MarketReview> notes = reviews.findByTenantId(tenantId);
        Map<UUID, String> people = names();
        boolean admin = guard.canManageAccess();
        MarketSeller own = shops.stream().filter(shop -> shop.getOwnerId().equals(user.id())).findFirst().orElse(null);
        String query = text(q).toLowerCase(Locale.ROOT);
        String categoryFilter = text(category);
        String regionFilter = text(region);
        String districtFilter = text(district);
        List<ProductView> visible = rows.stream()
                .filter(row -> "ACTIVE".equals(row.getStatus()) && row.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .filter(row -> approved(shopById.get(row.getSellerId())))
                .filter(row -> query.isEmpty() || row.getName().toLowerCase(Locale.ROOT).contains(query)
                        || shopName(shopById, row.getSellerId()).toLowerCase(Locale.ROOT).contains(query))
                .filter(row -> categoryFilter.isEmpty() || row.getCategory().equalsIgnoreCase(categoryFilter))
                .filter(row -> regionFilter.isEmpty() || row.getRegion().equalsIgnoreCase(regionFilter))
                .filter(row -> districtFilter.isEmpty() || row.getDistrict().equalsIgnoreCase(districtFilter))
                .map(row -> productView(row, shopById, notes, user))
                .toList();
        Set<UUID> sellerIds = own == null ? Set.of() : Set.of(own.getId());
        List<RequestView> requestViews = demand.stream()
                .filter(row -> openDemand(row) || admin || row.getBuyerId().equals(user.id()) || involved(row.getId(), quotes, sellerIds))
                .map(row -> requestView(row, people, user))
                .toList();
        List<OfferView> offerViews = quotes.stream()
                .filter(row -> admin || (own != null && row.getSellerId().equals(own.getId())) || buyerOf(row.getRequestId(), demand, user.id()))
                .map(row -> offerView(row, shopById, user, own))
                .toList();
        Set<UUID> reviewed = notes.stream().map(MarketReview::getOrderId).collect(Collectors.toSet());
        List<OrderView> orderViews = sales.stream()
                .filter(row -> admin || row.getBuyerId().equals(user.id()) || (own != null && row.getSellerId().equals(own.getId())))
                .map(row -> orderView(row, shopById, people, user, own, reviewed))
                .toList();
        Set<UUID> requestIds = requestViews.stream().map(RequestView::id).collect(Collectors.toSet());
        Set<UUID> orderIds = orderViews.stream().map(OrderView::id).collect(Collectors.toSet());
        List<MessageView> messageViews = messages.findByTenantIdOrderByCreatedAtAsc(tenantId).stream()
                .filter(row -> (row.getRequestId() != null && requestIds.contains(row.getRequestId()))
                        || (row.getOrderId() != null && orderIds.contains(row.getOrderId())))
                .map(row -> new MessageView(row.getId(), row.getRequestId(), row.getOrderId(),
                        people.getOrDefault(row.getSenderId(), "Member"), row.getBody(), row.getSenderId().equals(user.id()), row.getCreatedAt()))
                .toList();
        List<NoticeView> noticeViews = notices.findByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, user.id()).stream()
                .limit(20)
                .map(row -> new NoticeView(row.getId(), row.getTitle(), row.getDetail(), row.getCreatedAt()))
                .toList();
        List<ProductView> listings = own == null ? List.of() : rows.stream()
                .filter(row -> row.getSellerId().equals(own.getId()))
                .map(row -> productView(row, shopById, notes, user))
                .toList();
        List<ProductView> reviewListings = admin
                ? rows.stream().map(row -> productView(row, shopById, notes, user)).toList()
                : List.of();
        SellerView ownView = own == null ? null : sellerView(own, notes, true, admin);
        List<SellerView> pending = admin
                ? shops.stream().filter(shop -> !"APPROVED".equals(shop.getApproval())).map(shop -> sellerView(shop, notes, false, true)).toList()
                : List.of();
        long buyers = demand.stream().filter(row -> !"CANCELLED".equals(row.getStatus())).map(MarketRequest::getBuyerId).distinct().count();
        Stats stats = new Stats(
                rows.stream().filter(row -> "ACTIVE".equals(row.getStatus()) && row.getQuantity().compareTo(BigDecimal.ZERO) > 0 && approved(shopById.get(row.getSellerId()))).count(),
                buyers,
                shops.stream().filter(shop -> "APPROVED".equals(shop.getApproval())).count(),
                demand.stream().filter(this::openDemand).count(),
                quotes.stream().filter(row -> "SENT".equals(row.getStatus())).count(),
                sales.stream().filter(row -> "COMPLETED".equals(row.getStatus())).count()
        );
        return new Board(stats, visible, listings, reviewListings, requestViews, offerViews, orderViews, messageViews, noticeViews, ownView, pending, admin);
    }

    public SellerView registerSeller(SellerBody body) {
        CurrentUser user = guard.current();
        if (sellers.findByTenantIdAndOwnerId(user.tenantId(), user.id()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "SELLER_EXISTS", "This account already has a seller profile. Update that registration.");
        }
        MarketSeller seller = new MarketSeller();
        seller.setTenantId(user.tenantId());
        seller.setOwnerId(user.id());
        seller.setApproval("PENDING");
        applySeller(seller, body);
        sellers.save(seller);
        audit.record("SELLER_REGISTERED", "MARKET_SELLER", seller.getId(), seller.getName());
        audit.activity("MARKET", "Seller registered", seller.getName() + " · " + seller.getRegion(), null);
        return sellerView(seller, List.of(), true, false);
    }

    public SellerView updateSeller(UUID id, SellerBody body) {
        MarketSeller seller = ownedSeller(id);
        applySeller(seller, body);
        if ("REJECTED".equals(seller.getApproval())) {
            seller.setApproval("PENDING");
        }
        sellers.save(seller);
        audit.record("SELLER_UPDATED", "MARKET_SELLER", seller.getId(), seller.getName());
        audit.activity("MARKET", "Seller updated", seller.getName(), null);
        return sellerView(seller, reviews.findByTenantId(seller.getTenantId()), true, guard.canManageAccess());
    }

    public SellerView verifySeller(UUID id, DecisionBody body) {
        if (!guard.canManageAccess()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "An administrator checks seller registrations");
        }
        MarketSeller seller = requireSeller(id);
        if (!"PENDING".equals(seller.getApproval())) {
            throw new ApiException(HttpStatus.CONFLICT, "SELLER_DECIDED", "This seller is already " + seller.getApproval().toLowerCase(Locale.ROOT));
        }
        boolean approve = body != null && Boolean.TRUE.equals(body.accept());
        seller.setApproval(approve ? "APPROVED" : "REJECTED");
        sellers.save(seller);
        notice(seller.getOwnerId(), approve ? "Seller profile approved" : "Seller profile rejected", seller.getName());
        audit.record(approve ? "SELLER_APPROVED" : "SELLER_REJECTED", "MARKET_SELLER", seller.getId(), seller.getName());
        audit.activity("MARKET", approve ? "Seller approved" : "Seller rejected", seller.getName(), null);
        return sellerView(seller, reviews.findByTenantId(seller.getTenantId()), false, true);
    }

    public ProductView addProduct(ProductBody body) {
        MarketSeller seller = requireApprovedOwner();
        MarketProduct product = new MarketProduct();
        product.setTenantId(seller.getTenantId());
        product.setSellerId(seller.getId());
        product.setStatus("ACTIVE");
        product.setCurrencyCode("TZS");
        applyProduct(product, body);
        products.save(product);
        audit.record("PRODUCT_LISTED", "MARKET_PRODUCT", product.getId(), product.getName());
        audit.activity("MARKET", "Product listed", product.getName() + " · " + product.getRegion(), null);
        return productView(product, Map.of(seller.getId(), seller), reviews.findByTenantId(seller.getTenantId()), guard.current());
    }

    public ProductView updateProduct(UUID id, ProductBody body) {
        MarketProduct product = ownedProduct(id);
        applyProduct(product, body);
        if ("REJECTED".equals(product.getStatus())) {
            product.setStatus("ACTIVE");
        }
        products.save(product);
        audit.record("PRODUCT_UPDATED", "MARKET_PRODUCT", product.getId(), product.getName());
        audit.activity("MARKET", "Product updated", product.getName(), null);
        return productView(product, Map.of(product.getSellerId(), requireSeller(product.getSellerId())), reviews.findByTenantId(product.getTenantId()), guard.current());
    }

    public ProductView moderate(UUID id, DecisionBody body) {
        if (!guard.canManageAccess()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "An administrator reviews listings");
        }
        MarketProduct product = requireProduct(id);
        boolean approve = body != null && Boolean.TRUE.equals(body.accept());
        product.setStatus(approve ? "ACTIVE" : "REJECTED");
        products.save(product);
        MarketSeller seller = requireSeller(product.getSellerId());
        notice(seller.getOwnerId(), approve ? "Listing approved" : "Listing rejected", product.getName());
        audit.record(approve ? "LISTING_APPROVED" : "LISTING_REJECTED", "MARKET_PRODUCT", product.getId(), product.getName());
        audit.activity("MARKET", approve ? "Listing approved" : "Listing rejected", product.getName(), null);
        return productView(product, Map.of(seller.getId(), seller), reviews.findByTenantId(product.getTenantId()), guard.current());
    }

    public RequestView postRequest(DemandBody body) {
        CurrentUser user = guard.current();
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST", "Enter the buying requirement");
        }
        MarketRequest request = new MarketRequest();
        request.setTenantId(user.tenantId());
        request.setBuyerId(user.id());
        request.setProductName(required(body.productName(), 2, 160, "Enter the product name"));
        request.setCategory(category(body.category()));
        request.setQuantity(positive(body.quantity(), "Enter the quantity"));
        request.setUnitLabel(unit(body.unit()));
        request.setGrade(required(body.grade(), 1, 80, "Enter the quality or grade"));
        request.setTargetPrice(money(body.targetPrice()));
        request.setCurrencyCode("TZS");
        request.setRegion(required(body.region(), 2, 80, "Choose the delivery region"));
        request.setDistrict(required(body.district(), 2, 80, "Enter the delivery district"));
        applyOptionalPoint(request, body.latitude(), body.longitude());
        request.setNeededOn(date(body.neededOn()));
        request.setNote(optional(body.note(), 400));
        request.setStatus("OPEN");
        request.setUpdatedAt(Instant.now());
        if (body.productId() != null) {
            MarketProduct product = requireProduct(body.productId());
            request.setProductId(product.getId());
            if (request.getProductName().isBlank()) {
                request.setProductName(product.getName());
            }
        }
        requests.save(request);
        notifySellers(request);
        audit.record("BUYING_REQUEST", "MARKET_REQUEST", request.getId(), request.getProductName());
        audit.activity("MARKET", "Buying request posted", request.getProductName() + " · " + request.getRegion(), null);
        return requestView(request, names(), user);
    }

    public OfferView sendOffer(UUID requestId, OfferBody body) {
        MarketSeller seller = requireApprovedOwner();
        MarketRequest request = requireRequest(requestId);
        if (request.getBuyerId().equals(seller.getOwnerId())) {
            throw new ApiException(HttpStatus.CONFLICT, "OWN_REQUEST", "You cannot offer on your own buying request");
        }
        if (!openDemand(request)) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_CLOSED", "This request is no longer open");
        }
        boolean already = offers.findByTenantIdOrderByCreatedAtDesc(seller.getTenantId()).stream()
                .anyMatch(row -> row.getRequestId().equals(requestId) && row.getSellerId().equals(seller.getId()) && "SENT".equals(row.getStatus()));
        if (already) {
            throw new ApiException(HttpStatus.CONFLICT, "OFFER_EXISTS", "You already sent an offer on this request");
        }
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OFFER", "Enter the offer");
        }
        MarketOffer offer = new MarketOffer();
        offer.setTenantId(seller.getTenantId());
        offer.setRequestId(request.getId());
        offer.setSellerId(seller.getId());
        offer.setQuantity(positive(body.quantity(), "Enter the quantity you can supply"));
        offer.setUnitLabel(unit(body.unit()));
        offer.setPriceAmount(money(body.priceAmount()));
        offer.setCurrencyCode("TZS");
        offer.setTerms(required(body.terms(), 2, 400, "Enter the delivery or collection terms"));
        offer.setStatus("SENT");
        if (body.productId() != null) {
            MarketProduct product = ownedProduct(body.productId());
            if (product.getQuantity().compareTo(offer.getQuantity()) < 0) {
                throw new ApiException(HttpStatus.CONFLICT, "STOCK_SHORT", "The listing does not have that quantity");
            }
            offer.setProductId(product.getId());
        }
        offers.save(offer);
        if ("OPEN".equals(request.getStatus())) {
            request.setStatus("OFFER_RECEIVED");
            request.setUpdatedAt(Instant.now());
            requests.save(request);
        }
        notice(request.getBuyerId(), "Offer received", seller.getName() + " offered " + request.getProductName());
        audit.record("OFFER_SENT", "MARKET_OFFER", offer.getId(), request.getProductName());
        audit.activity("MARKET", "Offer sent", seller.getName() + " · " + request.getProductName(), null);
        return offerView(offer, Map.of(seller.getId(), seller), guard.current(), seller);
    }

    public OrderView decideOffer(UUID offerId, DecisionBody body) {
        MarketOffer offer = requireOffer(offerId);
        MarketRequest request = requireRequest(offer.getRequestId());
        CurrentUser user = guard.current();
        if (!request.getBuyerId().equals(user.id()) && !guard.canManageAccess()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "The buyer decides this offer");
        }
        if (!"SENT".equals(offer.getStatus()) || !openDemand(request)) {
            throw new ApiException(HttpStatus.CONFLICT, "OFFER_CLOSED", "This offer is no longer open");
        }
        boolean accept = body != null && Boolean.TRUE.equals(body.accept());
        if (!accept) {
            offer.setStatus("REJECTED");
            offers.save(offer);
            notice(sellerOwner(offer.getSellerId()), "Offer declined", request.getProductName());
            return null;
        }
        offer.setStatus("ACCEPTED");
        offers.save(offer);
        request.setStatus("ACCEPTED");
        request.setUpdatedAt(Instant.now());
        requests.save(request);
        for (MarketOffer other : offers.findByTenantIdOrderByCreatedAtDesc(offer.getTenantId())) {
            if (other.getRequestId().equals(request.getId()) && "SENT".equals(other.getStatus())) {
                other.setStatus("REJECTED");
                offers.save(other);
                notice(sellerOwner(other.getSellerId()), "Another offer was accepted", request.getProductName());
            }
        }
        MarketSeller seller = requireSeller(offer.getSellerId());
        MarketOrder order = new MarketOrder();
        order.setTenantId(offer.getTenantId());
        order.setRequestId(request.getId());
        order.setOfferId(offer.getId());
        order.setBuyerId(request.getBuyerId());
        order.setSellerId(seller.getId());
        order.setProductId(offer.getProductId());
        order.setProductName(request.getProductName());
        order.setQuantity(offer.getQuantity());
        order.setUnitLabel(offer.getUnitLabel());
        order.setPriceAmount(offer.getPriceAmount());
        order.setCurrencyCode("TZS");
        order.setFulfillment(offer.getProductId() == null ? "BOTH" : requireProduct(offer.getProductId()).getFulfillment());
        order.setStatus("ACCEPTED");
        order.setStockDrawn(false);
        order.setUpdatedAt(Instant.now());
        draw(order);
        orders.save(order);
        notice(seller.getOwnerId(), "Offer accepted", request.getProductName());
        audit.record("MARKET_ORDER", "MARKET_ORDER", order.getId(), request.getProductName());
        audit.activity("MARKET", "Order accepted", request.getProductName(), null);
        return orderView(order, Map.of(seller.getId(), seller), names(), user, seller, Set.of());
    }

    public OrderView advance(UUID orderId, DecisionBody body) {
        MarketOrder order = requireOrder(orderId);
        CurrentUser user = guard.current();
        MarketSeller seller = requireSeller(order.getSellerId());
        boolean sale = seller.getOwnerId().equals(user.id());
        boolean purchase = order.getBuyerId().equals(user.id());
        if (!sale && !purchase && !guard.canManageAccess()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the buyer or seller can update this order");
        }
        String next = body == null ? "" : text(body.status()).toUpperCase(Locale.ROOT);
        if ("CANCELLED".equals(next)) {
            if ("COMPLETED".equals(order.getStatus()) || "CANCELLED".equals(order.getStatus())) {
                throw new ApiException(HttpStatus.CONFLICT, "ORDER_CLOSED", "This order is already " + order.getStatus().toLowerCase(Locale.ROOT));
            }
            restore(order);
            order.setStatus("CANCELLED");
            MarketRequest request = requireRequest(order.getRequestId());
            request.setStatus("CANCELLED");
            request.setUpdatedAt(Instant.now());
            requests.save(request);
        } else if ("COMPLETED".equals(next) && ("READY".equals(order.getStatus()) || "IN_TRANSIT".equals(order.getStatus()))) {
            order.setStatus("COMPLETED");
            MarketRequest request = requireRequest(order.getRequestId());
            request.setStatus("COMPLETED");
            request.setUpdatedAt(Instant.now());
            requests.save(request);
        } else if (sale || guard.canManageAccess()) {
            String expected = switch (next) {
                case "PROCESSING" -> "ACCEPTED";
                case "READY", "IN_TRANSIT" -> "PROCESSING";
                default -> "";
            };
            if (!expected.equals(order.getStatus())) {
                throw new ApiException(HttpStatus.CONFLICT, "ORDER_STEP", "This order is " + order.getStatus().toLowerCase(Locale.ROOT).replace('_', ' '));
            }
            if ("READY".equals(next) && "DELIVERY".equals(order.getFulfillment())) {
                throw new ApiException(HttpStatus.CONFLICT, "ORDER_STEP", "This order is for delivery, so mark it in transit");
            }
            if ("IN_TRANSIT".equals(next) && "COLLECTION".equals(order.getFulfillment())) {
                throw new ApiException(HttpStatus.CONFLICT, "ORDER_STEP", "This order is for collection, so mark it ready");
            }
            order.setStatus(next);
        } else {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "The seller updates preparation and delivery");
        }
        order.setUpdatedAt(Instant.now());
        orders.save(order);
        UUID other = sale ? order.getBuyerId() : seller.getOwnerId();
        notice(other, "Order updated", order.getProductName() + " is " + order.getStatus().toLowerCase(Locale.ROOT).replace('_', ' '));
        audit.record("MARKET_ORDER_" + order.getStatus(), "MARKET_ORDER", order.getId(), order.getProductName());
        audit.activity("MARKET", "Order updated", order.getProductName() + " · " + order.getStatus().toLowerCase(Locale.ROOT).replace('_', ' '), null);
        return orderView(order, Map.of(seller.getId(), seller), names(), user, sale ? seller : null,
                reviews.findByTenantId(order.getTenantId()).stream().map(MarketReview::getOrderId).collect(Collectors.toSet()));
    }

    public MessageView message(DecisionBody body) {
        CurrentUser user = guard.current();
        if (body == null || (body.requestId() == null && body.orderId() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MESSAGE", "Choose the request or order");
        }
        if (body.requestId() != null) {
            partyToRequest(requireRequest(body.requestId()), user);
        }
        if (body.orderId() != null) {
            partyToOrder(requireOrder(body.orderId()), user);
        }
        MarketMessage message = new MarketMessage();
        message.setTenantId(user.tenantId());
        message.setRequestId(body.requestId());
        message.setOrderId(body.orderId());
        message.setSenderId(user.id());
        message.setBody(required(body.body(), 1, 1000, "Write a message"));
        messages.save(message);
        return new MessageView(message.getId(), message.getRequestId(), message.getOrderId(), user.fullName(), message.getBody(), true, message.getCreatedAt());
    }

    public void review(UUID orderId, DecisionBody body) {
        MarketOrder order = requireOrder(orderId);
        CurrentUser user = guard.current();
        if (!order.getBuyerId().equals(user.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "The buyer reviews a completed order");
        }
        if (!"COMPLETED".equals(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_OPEN", "Review the order after it is completed");
        }
        if (reviews.findByTenantIdAndOrderId(order.getTenantId(), order.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_EXISTS", "This order already has a review");
        }
        int score = body == null || body.score() == null ? 0 : body.score();
        if (score < 1 || score > 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SCORE", "Choose a rating from 1 to 5");
        }
        MarketReview review = new MarketReview();
        review.setTenantId(order.getTenantId());
        review.setOrderId(order.getId());
        review.setReviewerId(user.id());
        review.setSellerId(order.getSellerId());
        review.setScore((short) score);
        review.setNote(optional(body.note(), 400));
        reviews.save(review);
        notice(sellerOwner(order.getSellerId()), "New review", order.getProductName());
        audit.record("MARKET_REVIEW", "MARKET_REVIEW", review.getId(), order.getProductName());
        audit.activity("MARKET", "Review recorded", order.getProductName() + " · " + score, null);
    }

    private void notifySellers(MarketRequest request) {
        List<MarketSeller> shops = sellers.findByTenantIdOrderByNameAsc(request.getTenantId());
        List<MarketProduct> rows = products.findByTenantIdOrderByCreatedAtDesc(request.getTenantId());
        Set<UUID> matching = rows.stream()
                .filter(row -> "ACTIVE".equals(row.getStatus()) && request.getCategory().equalsIgnoreCase(row.getCategory()))
                .map(MarketProduct::getSellerId)
                .collect(Collectors.toSet());
        List<MarketSeller> targets = shops.stream()
                .filter(shop -> "APPROVED".equals(shop.getApproval()))
                .filter(shop -> matching.isEmpty() || matching.contains(shop.getId()))
                .filter(shop -> !shop.getOwnerId().equals(request.getBuyerId()))
                .toList();
        for (MarketSeller seller : targets) {
            notice(seller.getOwnerId(), "New buying request", request.getProductName() + " · " + request.getRegion());
        }
    }

    private void draw(MarketOrder order) {
        if (order.getProductId() == null) {
            return;
        }
        MarketProduct product = requireProduct(order.getProductId());
        if (product.getQuantity().compareTo(order.getQuantity()) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "STOCK_SHORT", "Available quantity cannot go negative");
        }
        product.setQuantity(product.getQuantity().subtract(order.getQuantity()));
        products.save(product);
        order.setStockDrawn(true);
    }

    private void restore(MarketOrder order) {
        if (!order.isStockDrawn() || order.getProductId() == null) {
            return;
        }
        MarketProduct product = requireProduct(order.getProductId());
        product.setQuantity(product.getQuantity().add(order.getQuantity()));
        if (!"REJECTED".equals(product.getStatus())) {
            product.setStatus("ACTIVE");
        }
        products.save(product);
        order.setStockDrawn(false);
    }

    private void partyToRequest(MarketRequest request, CurrentUser user) {
        if (guard.canManageAccess() || request.getBuyerId().equals(user.id())) {
            return;
        }
        MarketSeller own = sellers.findByTenantIdAndOwnerId(user.tenantId(), user.id()).orElse(null);
        boolean offered = own != null && offers.findByTenantIdOrderByCreatedAtDesc(user.tenantId()).stream()
                .anyMatch(row -> row.getRequestId().equals(request.getId()) && row.getSellerId().equals(own.getId()));
        if (!offered) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Messages stay between the buyer and a seller who offered");
        }
    }

    private void partyToOrder(MarketOrder order, CurrentUser user) {
        MarketSeller own = sellers.findByTenantIdAndOwnerId(user.tenantId(), user.id()).orElse(null);
        if (!guard.canManageAccess() && !order.getBuyerId().equals(user.id()) && (own == null || !order.getSellerId().equals(own.getId()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Messages stay between the buyer and the seller");
        }
    }

    private boolean involved(UUID requestId, List<MarketOffer> quotes, Set<UUID> sellerIds) {
        return quotes.stream().anyMatch(row -> row.getRequestId().equals(requestId) && sellerIds.contains(row.getSellerId()));
    }

    private boolean buyerOf(UUID requestId, List<MarketRequest> demand, UUID userId) {
        return demand.stream().anyMatch(row -> row.getId().equals(requestId) && row.getBuyerId().equals(userId));
    }

    private boolean openDemand(MarketRequest request) {
        return "OPEN".equals(request.getStatus()) || "OFFER_RECEIVED".equals(request.getStatus());
    }

    private boolean approved(MarketSeller seller) {
        return seller != null && "APPROVED".equals(seller.getApproval());
    }

    private ProductView productView(MarketProduct row, Map<UUID, MarketSeller> shops, List<MarketReview> notes, CurrentUser user) {
        MarketSeller seller = shops.get(row.getSellerId());
        return new ProductView(row.getId(), row.getName(), row.getCategory(), row.getImageKey(), row.getDescription(),
                row.getQuantity(), row.getUnitLabel(), row.getPriceAmount(), row.getCurrencyCode(), row.getMinOrder(),
                row.getGrade(), row.getRegion(), row.getDistrict(), row.getLatitude().doubleValue(), row.getLongitude().doubleValue(),
                row.getFulfillment(), row.getStatus(), shopName(shops, row.getSellerId()), approved(seller),
                seller != null && seller.getOwnerId().equals(user.id()), average(notes, row.getSellerId()));
    }

    private RequestView requestView(MarketRequest row, Map<UUID, String> people, CurrentUser user) {
        return new RequestView(row.getId(), row.getProductId(), row.getProductName(), row.getCategory(), row.getQuantity(),
                row.getUnitLabel(), row.getGrade(), row.getTargetPrice(), row.getCurrencyCode(), row.getRegion(), row.getDistrict(),
                row.getLatitude() == null ? null : row.getLatitude().doubleValue(),
                row.getLongitude() == null ? null : row.getLongitude().doubleValue(),
                row.getNeededOn(), row.getNote() == null ? "" : row.getNote(), row.getStatus(),
                people.getOrDefault(row.getBuyerId(), "Buyer"), row.getBuyerId().equals(user.id()), row.getCreatedAt());
    }

    private OfferView offerView(MarketOffer row, Map<UUID, MarketSeller> shops, CurrentUser user, MarketSeller own) {
        MarketSeller seller = shops.get(row.getSellerId());
        return new OfferView(row.getId(), row.getRequestId(), row.getSellerId(), shopName(shops, row.getSellerId()),
                row.getProductId(), row.getQuantity(), row.getUnitLabel(), row.getPriceAmount(), row.getCurrencyCode(),
                row.getTerms(), row.getStatus(), own != null && row.getSellerId().equals(own.getId()) || (seller != null && seller.getOwnerId().equals(user.id())),
                row.getCreatedAt());
    }

    private OrderView orderView(MarketOrder row, Map<UUID, MarketSeller> shops, Map<UUID, String> people, CurrentUser user,
                                MarketSeller own, Set<UUID> reviewed) {
        boolean party = row.getBuyerId().equals(user.id()) || (own != null && row.getSellerId().equals(own.getId())) || guard.canManageAccess();
        MarketSeller seller = shops.get(row.getSellerId());
        return new OrderView(row.getId(), row.getRequestId(), row.getProductName(), row.getQuantity(), row.getUnitLabel(),
                row.getPriceAmount(), row.getCurrencyCode(), row.getFulfillment(), row.getStatus(),
                people.getOrDefault(row.getBuyerId(), "Buyer"), shopName(shops, row.getSellerId()),
                party && seller != null ? seller.getPhone() : "",
                row.getBuyerId().equals(user.id()), own != null && row.getSellerId().equals(own.getId()),
                reviewed.contains(row.getId()), row.getCreatedAt());
    }

    private SellerView sellerView(MarketSeller seller, List<MarketReview> notes, boolean owned, boolean showPhone) {
        return new SellerView(seller.getId(), seller.getName(), seller.getRegion(), seller.getDistrict(),
                owned || showPhone ? seller.getPhone() : "", seller.getLatitude().doubleValue(), seller.getLongitude().doubleValue(),
                seller.getSummary(), seller.getApproval(), owned, average(notes, seller.getId()),
                (int) notes.stream().filter(row -> row.getSellerId().equals(seller.getId())).count());
    }

    private Double average(List<MarketReview> notes, UUID sellerId) {
        List<MarketReview> rows = notes.stream().filter(row -> row.getSellerId().equals(sellerId)).toList();
        if (rows.isEmpty()) {
            return null;
        }
        return rows.stream().mapToInt(MarketReview::getScore).average().orElse(0);
    }

    private void applySeller(MarketSeller seller, SellerBody body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELLER", "Enter the seller details");
        }
        seller.setName(required(body.name(), 2, 160, "Enter the seller name"));
        seller.setRegion(required(body.region(), 2, 80, "Choose a region"));
        seller.setDistrict(required(body.district(), 2, 80, "Enter the district"));
        String phone = required(body.phone(), 7, 40, "Enter a phone number");
        if (phone.chars().noneMatch(Character::isDigit)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE", "Enter a phone number");
        }
        seller.setPhone(phone);
        point(body.latitude(), body.longitude());
        seller.setLatitude(BigDecimal.valueOf(body.latitude()).setScale(6, RoundingMode.HALF_UP));
        seller.setLongitude(BigDecimal.valueOf(body.longitude()).setScale(6, RoundingMode.HALF_UP));
        seller.setSummary(required(body.summary(), 2, 400, "Enter a short description"));
    }

    private void applyProduct(MarketProduct product, ProductBody body) {
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRODUCT", "Enter the product");
        }
        product.setName(required(body.name(), 2, 160, "Enter the product name"));
        product.setCategory(category(body.category()));
        String image = text(body.imageKey()).toUpperCase(Locale.ROOT);
        if (!IMAGES.contains(image)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE", "Choose one of the product photos");
        }
        product.setImageKey(image);
        product.setDescription(required(body.description(), 2, 800, "Enter a description"));
        product.setQuantity(nonNegative(body.quantity()));
        product.setUnitLabel(unit(body.unit()));
        product.setPriceAmount(money(body.priceAmount()));
        product.setCurrencyCode("TZS");
        BigDecimal min = body.minOrder() == null ? BigDecimal.ONE : body.minOrder();
        if (min.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MIN_ORDER", "Enter a minimum order greater than zero");
        }
        product.setMinOrder(min.setScale(2, RoundingMode.HALF_UP));
        product.setGrade(required(body.grade(), 1, 80, "Enter the quality or grade"));
        product.setRegion(required(body.region(), 2, 80, "Choose a region"));
        product.setDistrict(required(body.district(), 2, 80, "Enter the district"));
        point(body.latitude(), body.longitude());
        product.setLatitude(BigDecimal.valueOf(body.latitude()).setScale(6, RoundingMode.HALF_UP));
        product.setLongitude(BigDecimal.valueOf(body.longitude()).setScale(6, RoundingMode.HALF_UP));
        String fulfillment = text(body.fulfillment()).toUpperCase(Locale.ROOT);
        if (!FULFILLMENT.contains(fulfillment)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FULFILLMENT", "Choose collection, delivery, or both");
        }
        product.setFulfillment(fulfillment);
    }

    private void applyOptionalPoint(MarketRequest request, Double latitude, Double longitude) {
        if (latitude == null && longitude == null) {
            return;
        }
        point(latitude, longitude);
        request.setLatitude(BigDecimal.valueOf(latitude).setScale(6, RoundingMode.HALF_UP));
        request.setLongitude(BigDecimal.valueOf(longitude).setScale(6, RoundingMode.HALF_UP));
    }

    private void point(Double latitude, Double longitude) {
        if (latitude == null || longitude == null || latitude < -12.2 || latitude > -0.5 || longitude < 28.9 || longitude > 40.9) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION", "Place the pin inside Tanzania");
        }
    }

    private MarketSeller requireApprovedOwner() {
        MarketSeller seller = sellers.findByTenantIdAndOwnerId(guard.tenantId(), guard.current().id())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "SELLER_REQUIRED", "Register as a seller and wait for approval before listing or offering"));
        if (!"APPROVED".equals(seller.getApproval())) {
            throw new ApiException(HttpStatus.CONFLICT, "SELLER_PENDING", "An administrator still has to approve this seller profile");
        }
        return seller;
    }

    private MarketSeller ownedSeller(UUID id) {
        MarketSeller seller = requireSeller(id);
        if (!seller.getOwnerId().equals(guard.current().id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SELLER_FORBIDDEN", "Only this seller can update the profile");
        }
        return seller;
    }

    private MarketProduct ownedProduct(UUID id) {
        MarketProduct product = requireProduct(id);
        MarketSeller seller = requireSeller(product.getSellerId());
        if (!seller.getOwnerId().equals(guard.current().id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PRODUCT_FORBIDDEN", "Only this seller can update the listing");
        }
        return product;
    }

    private MarketSeller requireSeller(UUID id) {
        return sellers.findByTenantIdAndId(guard.tenantId(), id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SELLER_NOT_FOUND", "Seller not found"));
    }

    private MarketProduct requireProduct(UUID id) {
        return products.findByTenantIdAndId(guard.tenantId(), id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found"));
    }

    private MarketRequest requireRequest(UUID id) {
        return requests.findByTenantIdAndId(guard.tenantId(), id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "Request not found"));
    }

    private MarketOffer requireOffer(UUID id) {
        return offers.findByTenantIdAndId(guard.tenantId(), id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Offer not found"));
    }

    private MarketOrder requireOrder(UUID id) {
        return orders.findByTenantIdAndId(guard.tenantId(), id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"));
    }

    private UUID sellerOwner(UUID sellerId) {
        return requireSeller(sellerId).getOwnerId();
    }

    private String shopName(Map<UUID, MarketSeller> shops, UUID sellerId) {
        MarketSeller seller = shops.get(sellerId);
        return seller == null ? "Seller" : seller.getName();
    }

    private void notice(UUID userId, String title, String detail) {
        MarketNotice notice = new MarketNotice();
        notice.setTenantId(guard.tenantId());
        notice.setUserId(userId);
        notice.setTitle(title);
        notice.setDetail(detail.length() > 400 ? detail.substring(0, 400) : detail);
        notices.save(notice);
    }

    private Map<UUID, String> names() {
        return users.findByTenantIdOrderByFullNameAsc(guard.tenantId()).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getFullName, (left, right) -> left));
    }

    private String category(String value) {
        String found = CATEGORIES.stream().filter(item -> item.equalsIgnoreCase(text(value))).findFirst().orElse(null);
        if (found == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY", "Choose a product category");
        }
        return found;
    }

    private String unit(String value) {
        String found = text(value).toUpperCase(Locale.ROOT);
        if (!UNITS.contains(found)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNIT", "Use kg, tonnes, bags, litres, or pieces");
        }
        return found;
    }

    private LocalDate date(String value) {
        try {
            LocalDate day = LocalDate.parse(text(value));
            if (day.isBefore(LocalDate.now(DAR))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DATE", "The delivery date cannot be in the past");
            }
            return day;
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE", "Enter the delivery date");
        }
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICE", "Enter the price in TZS");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal positive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QUANTITY", message);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QUANTITY", "Enter a quantity of zero or more");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String required(String value, int min, int max, String message) {
        String trimmed = text(value);
        if (trimmed.length() < min || trimmed.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FIELD", message);
        }
        return trimmed;
    }

    private String optional(String value, int max) {
        String trimmed = text(value);
        if (trimmed.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOTE", "Keep the note to " + max + " characters");
        }
        return trimmed;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
