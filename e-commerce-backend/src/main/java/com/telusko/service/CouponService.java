package com.telusko.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import com.telusko.dto.CartItemResponseDto;
import com.telusko.dto.CartResponseDto;
import com.telusko.enums.DiscountType;
import com.telusko.model.Cart;
import com.telusko.model.Coupon;
import com.telusko.model.User;
import com.telusko.repository.CartRepository;
import com.telusko.repository.CouponRepository;
import com.telusko.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository coupons;
    private final CartRepository carts;
    private final UserRepository users;
    private final AppVectorStoreService appVectors;


    /**
     * Every coupon, including deactivated ones.
     * <p>
     * This is the back-office listing. Filtering to active only made deactivating a coupon remove
     * it from the very screen that has the Activate button, so the toggle only ever worked in one
     * direction and a soft-deleted coupon could never be recovered. Customers see a different,
     * genuinely filtered list via {@link #getAvailableCouponsForCustomer}.
     */
    public Page<Coupon> getAllCoupons(int page, int limit) {
        if (page < 1) page = 1;
        if (limit < 1) limit = 5;

        Pageable pageable = PageRequest.of(page - 1, limit,
                Sort.by("startDate").descending());

        return coupons.findAll(pageable);
    }

    public Coupon createCoupon(Coupon request) {
        Coupon coupon = Coupon.builder()
                .code(request.getCode())
                .discountAmount(request.getDiscountAmount())
                .minimumOrderAmount(request.getMinimumOrderAmount())
                .discountType(request.getDiscountType())
                .startDate(request.getStartDate())
                .expiryDate(request.getExpiryDate())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Coupon saved = coupons.save(coupon);
        appVectors.indexCoupon(saved);


        return saved;
    }

    public Coupon getCouponById(Long couponId) {
        return coupons.findByIdAndActiveTrue(couponId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Coupon not found with id: " + couponId));
    }

    public void deleteCoupon(Long couponId) {
        Coupon coupon = coupons.findById(couponId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Coupon not found with id: " + couponId));
        coupon.setActive(false);

        Coupon saved = coupons.save(coupon);
        appVectors.indexCoupon(saved);

    }

    public Coupon updateCoupon(Long couponId, Coupon request) {
        Coupon coupon = coupons.findById(couponId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Coupon not found with id: " + couponId));

        if (request.getCode() != null) coupon.setCode(request.getCode());
        if (request.getDiscountAmount() != null) coupon.setDiscountAmount(request.getDiscountAmount());
        if (request.getMinimumOrderAmount() != null) coupon.setMinimumOrderAmount(request.getMinimumOrderAmount());
        if (request.getDiscountType() != null) coupon.setDiscountType(request.getDiscountType());
        if (request.getStartDate() != null) coupon.setStartDate(request.getStartDate());
        if (request.getExpiryDate() != null) coupon.setExpiryDate(request.getExpiryDate());
        if (request.getActive() != null) coupon.setActive(request.getActive());

        Coupon saved = coupons.save(coupon);
        appVectors.indexCoupon(saved);


        return saved;
    }

    public Coupon updateCouponActiveStatus(Long couponId, boolean isActive) {
        Coupon coupon = coupons.findById(couponId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Coupon not found with id: " + couponId));
        coupon.setActive(isActive);

        Coupon saved = coupons.save(coupon);
        appVectors.indexCoupon(saved);


        return saved;
    }



    /**
     * The coupons a customer can currently see, which is every active coupon inside its date range.
     * <p>
     * Deliberately not filtered by what is in the cart. This is a browse screen: hiding a coupon
     * until the basket is already big enough meant an empty cart showed nothing at all, so
     * customers never learned an offer existed. The minimum spend is returned with each coupon and
     * shown on the card, and it is still enforced when the coupon is applied and again when the
     * order is placed.
     */
    public Page<Coupon> getAvailableCouponsForCustomer(
            String userEmail, int page, int limit) {

        if (page < 1) page = 1;
        if (limit < 1) limit = 5;

        LocalDate today = LocalDate.now();

        List<Coupon> allActive = coupons
                .findByActiveTrue(Pageable.unpaged())
                .getContent();

        List<Coupon> eligible = allActive.stream()
                .filter(c -> !today.isBefore(c.getStartDate())
                        && !today.isAfter(c.getExpiryDate()))
                .toList();

        int from = Math.min((page - 1) * limit, eligible.size());
        int to = Math.min(from + limit, eligible.size());

        List<Coupon> content = eligible.subList(from, to);

        return new PageImpl<>(
                content,
                PageRequest.of(page - 1, limit),
                eligible.size()
        );
    }


    public CartResponseDto applyCoupon(String userEmail, String couponCode) {

        BigDecimal cartTotal = getCartTotal(userEmail);
        Cart cart = getUserCart(userEmail);

        Coupon coupon = coupons
                .findByCodeIgnoreCaseAndActiveTrue(couponCode)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Coupon not found: " + couponCode));

        LocalDate today = LocalDate.now();
        if (today.isBefore(coupon.getStartDate()) ||
                today.isAfter(coupon.getExpiryDate())) {
            throw new IllegalArgumentException("Coupon is expired or not yet valid");
        }

        if (cartTotal.compareTo(coupon.getMinimumOrderAmount()) < 0) {
            throw new IllegalArgumentException(
                    "Cart total is less than minimum amount for this coupon");
        }

        BigDecimal discount;
        if (coupon.getDiscountType() == DiscountType.FLAT) {
            discount = coupon.getDiscountAmount();
        } else {
            discount = cartTotal
                    .multiply(coupon.getDiscountAmount())
                    .divide(BigDecimal.valueOf(100));
        }

        if (discount.compareTo(cartTotal) > 0) {
            discount = cartTotal;
        }

        BigDecimal payable = cartTotal.subtract(discount);

        return CartResponseDto.builder()
                .items(cart.getItems().stream()
                        .map(item -> CartItemResponseDto.builder()
                                .productId(item.getProduct().getId())
                                .productName(item.getProduct().getName())
                                .mainImage(item.getProduct().getMainImage())
                                .unitPrice(item.getUnitPrice())
                                .quantity(item.getQuantity())
                                .lineTotal(item.getUnitPrice()
                                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                                .build())
                        .toList())
                .totalAmount(cartTotal)
                .discountAmount(discount)
                .payableAmount(payable)
                .appliedCouponCode(coupon.getCode())
                .build();
    }

    public CartResponseDto removeCoupon(String userEmail) {
        Cart cart = getUserCart(userEmail);
        BigDecimal total = cart.getTotalAmount();

        return CartResponseDto.builder()
                .items(cart.getItems().stream()
                        .map(item -> CartItemResponseDto.builder()
                                .productId(item.getProduct().getId())
                                .productName(item.getProduct().getName())
                                .mainImage(item.getProduct().getMainImage())
                                .unitPrice(item.getUnitPrice())
                                .quantity(item.getQuantity())
                                .lineTotal(item.getUnitPrice()
                                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                                .build())
                        .toList())
                .totalAmount(total)
                .discountAmount(BigDecimal.ZERO)
                .payableAmount(total)
                .appliedCouponCode(null)
                .build();
    }

    /**
     * The cart total, or zero when the customer has no cart yet.
     * <p>
     * Carts are created lazily on first add, so anyone who opens the coupons page before putting
     * something in their basket has no cart row. Throwing there turned a browse into an error
     * page; an empty basket is worth zero, which is what the eligibility check needs anyway.
     */
    private BigDecimal getCartTotal(String userEmail) {
        User user = users.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userEmail));

        return carts.findByUser(user)
                .map(Cart::getTotalAmount)
                .orElse(BigDecimal.ZERO);
    }

    private Cart getUserCart(String userEmail) {
        User user = users.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found: " + userEmail));

        return carts.findByUser(user)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Cart not found for user: " + userEmail));
    }
}
