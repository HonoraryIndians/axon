package com.axon.core_service.controller;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.coupon.UserCoupon;
import com.axon.core_service.domain.dto.campaignactivity.filter.FilterDetail;
import com.axon.core_service.domain.dto.coupon.ApplicableCouponDto;
import com.axon.core_service.domain.product.Product;
import com.axon.core_service.exception.CampaignActivityNotFoundException;
import com.axon.core_service.repository.CampaignActivityRepository;
import com.axon.core_service.repository.ProductRepository;
import com.axon.core_service.service.CouponService;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@Slf4j
public class StoreController {

    private final CampaignActivityRepository campaignActivityRepository;
    private final ProductRepository productRepository;
    private final com.axon.core_service.repository.UserCouponRepository userCouponRepository;
    private final com.axon.core_service.config.auth.JwtTokenProvider jwtTokenProvider;
    private final CouponService couponService;

    @org.springframework.beans.factory.annotation.Value("${axon.entry-service-url}")
    private String entryServiceUrl;

    @GetMapping("/mainshop")
    public String mainshop(@RequestParam(required = false) String category, Model model) {
        List<Product> allProducts = productRepository.findAll();
        List<ProductDisplayDto> allDtos = allProducts.stream()
                .map(this::convertToProductDto)
                .collect(Collectors.toList());

        // 메인 배너/랭킹용 (특정 상품 노출: 1, 16, 31, 46, 40)
        List<Long> rankingIds = List.of(1L, 16L, 31L, 46L, 40L);
        List<ProductDisplayDto> rankings = allDtos.stream()
                .filter(p -> rankingIds.contains(p.getId()))
                .sorted((p1, p2) -> {
                    // Maintain the order of rankingIds
                    return Integer.compare(rankingIds.indexOf(p1.getId()), rankingIds.indexOf(p2.getId()));
                })
                .collect(Collectors.toList());
        model.addAttribute("rankings", rankings);

        // 카테고리별 상품 리스트
        if (category != null && !category.isEmpty()) {
            // 카테고리 필터링 된 리스트
            List<ProductDisplayDto> filtered = allDtos.stream()
                    .filter(p -> category.equalsIgnoreCase(p.getCategory()))
                    .collect(Collectors.toList());
            model.addAttribute("categoryProducts", filtered);
            model.addAttribute("selectedCategory", category);
        } else {
            // 전체 보기 (섹션별 노출)
            model.addAttribute("techDeals", filterDtoByCategory(allDtos, "TECH"));
            model.addAttribute("foodDeals", filterDtoByCategory(allDtos, "FOOD"));
            model.addAttribute("homeDeals", filterDtoByCategory(allDtos, "HOME"));
            model.addAttribute("fashionDeals", filterDtoByCategory(allDtos, "FASHION"));
        }

        return "mainshop";
    }

    private List<ProductDisplayDto> filterDtoByCategory(List<ProductDisplayDto> list, String category) {
        return list.stream()
                .filter(p -> category.equalsIgnoreCase(p.getCategory()))
                .limit(4)
                .collect(Collectors.toList());
    }

    private ProductDisplayDto convertToProductDto(Product product) {
        NumberFormat currency = NumberFormat.getInstance(Locale.KOREA);

        BigDecimal originalPrice = product.getPrice();
        BigDecimal sellingPrice = originalPrice;

        if (product.getDiscountRate() != null && product.getDiscountRate() > 0) {
            BigDecimal discount = originalPrice.multiply(BigDecimal.valueOf(product.getDiscountRate()))
                    .divide(BigDecimal.valueOf(100));
            sellingPrice = originalPrice.subtract(discount);
        }

        String priceStr = currency.format(sellingPrice);
        String originalPriceStr = currency.format(originalPrice);

        // Handle image path: assume DB has filename, prepend static path
        String imagePath = product.getImageUrl();
        if (imagePath != null && !imagePath.startsWith("/image/product/")) {
            // If it's just a filename, add the prefix
            if (!imagePath.startsWith("/")) {
                imagePath = "/image/product/" + imagePath;
            }
        }

        return ProductDisplayDto.builder()
                .id(product.getId())
                .name(product.getProductName())
                .brand(product.getBrand() != null ? product.getBrand() : "SKU STORE") // Default brand
                .price(priceStr)
                .originalPrice(originalPriceStr)
                .discountRate(product.getDiscountRate() != null ? product.getDiscountRate() : 0)
                .imageUrl(imagePath)
                .category(product.getCategory())
                .reviewCount(100) // Mock review count
                .build();
    }

    @GetMapping("/product/{id}")
    public String productDetail(@PathVariable Long id, Model model) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));

        model.addAttribute("product", convertToProductDto(product));
        return "product/detail";
    }

    @GetMapping("/checkout")
    public String checkout(@RequestParam Long productId,
                          @org.springframework.web.bind.annotation.CookieValue(value = "accessToken", required = false) String accessToken,
                          Model model) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        model.addAttribute("product", convertToProductDto(product));

        Long userId = null;

        // 쿠키에서 accessToken 가져와서 사용자 ID 추출 (mypage와 동일한 방식)
        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            try {
                org.springframework.security.core.Authentication auth = jwtTokenProvider.getAuthentication(accessToken);
                Object principal = auth.getPrincipal();

                if (principal instanceof com.axon.core_service.domain.user.CustomOAuth2User) {
                    com.axon.core_service.domain.user.CustomOAuth2User oauthUser = (com.axon.core_service.domain.user.CustomOAuth2User) principal;
                    userId = oauthUser.getUserId();
                } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                    String username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
                    userId = Long.parseLong(username);
                }
                log.info("Extracted userId from token: {}", userId);
            } catch (Exception e) {
                log.error("Error extracting userId from token", e);
            }
        } else {
            log.info("No valid accessToken found in cookies");
        }

        // 사용자가 로그인되어 있으면 적용 가능한 쿠폰 조회
        if (userId != null) {
            try {
                List<ApplicableCouponDto> applicableCoupons = couponService.getApplicableCoupons(userId, product.getPrice(), product.getCategory());
                model.addAttribute("coupons", applicableCoupons);
                log.info("Found {} applicable coupons for user {} and product {}", applicableCoupons.size(), userId, productId);
            } catch (Exception e) {
                log.error("Error loading coupons for checkout", e);
                model.addAttribute("coupons", java.util.Collections.emptyList());
            }
        } else {
            log.info("userId is null - user not authenticated");
            model.addAttribute("coupons", java.util.Collections.emptyList());
        }

        return "checkout";
    }

    @GetMapping("/events")
    public String getCampaignActivities(
            @org.springframework.web.bind.annotation.CookieValue(value = "accessToken", required = false) String accessToken,
            Model model) {
        // Fetch real campaign activities (skip broken data)
        List<CampaignActivityDisplayDto> allActivities = campaignActivityRepository
                .findAllByStatus(com.axon.core_service.domain.dto.campaignactivity.CampaignActivityStatus.ACTIVE)
                .stream()
                .map(activity -> {
                    try {
                        return convertToDto(activity);
                    } catch (Exception e) {
                        log.warn("Failed to convert CampaignActivity id={}: {}", activity.getId(), e.getMessage());
                        return null; // Skip broken data
                    }
                })
                .filter(dto -> dto != null) // Remove nulls
                .collect(Collectors.toList());

        // Separate FCFS and Coupon activities
        List<CampaignActivityDisplayDto> fcfsActivities = allActivities.stream()
                .filter(a -> a.getActivityType() != com.axon.messaging.CampaignActivityType.COUPON)
                .collect(Collectors.toList());

        List<CampaignActivityDisplayDto> couponActivities = allActivities.stream()
                .filter(a -> a.getActivityType() == com.axon.messaging.CampaignActivityType.COUPON)
                .collect(Collectors.toList());

        // Check for issued coupons (if user is logged in)
        List<Long> issuedCouponIds = new ArrayList<>();
        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            try {
                org.springframework.security.core.Authentication auth = jwtTokenProvider.getAuthentication(accessToken);
                Object principal = auth.getPrincipal();

                Long userId = null;
                if (principal instanceof com.axon.core_service.domain.user.CustomOAuth2User) {
                    userId = ((com.axon.core_service.domain.user.CustomOAuth2User) principal).getUserId();
                } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                    // Fallback if typical UserDetails
                    userId = Long.parseLong(
                            ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername());
                }

                if (userId != null) {
                    issuedCouponIds = userCouponRepository.findAllCouponIdsByUserId(userId);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch issued coupons from access token", e);
            }
        }

        model.addAttribute("fcfsCampaignActivities", fcfsActivities);
        model.addAttribute("raffleCampaignActivities", new ArrayList<>()); // Placeholder for now
        model.addAttribute("coupons", couponActivities);
        model.addAttribute("entryServiceUrl", entryServiceUrl);
        model.addAttribute("issuedCouponIds", issuedCouponIds);

        return "campaign-activities";
    }

    @GetMapping("/campaignActivity/{id}")
    public String getCampaignActivityDetail(@PathVariable Long id, Model model) {
        // Fetch real campaign activity by ID
        CampaignActivity campaignActivity = campaignActivityRepository.findById(id)
                .orElseThrow(() -> new CampaignActivityNotFoundException(id));

        if (campaignActivity
                .getStatus() != com.axon.core_service.domain.dto.campaignactivity.CampaignActivityStatus.ACTIVE) {
            model.addAttribute("message", "현재 진행 중인 캠페인이 아닙니다.");
            return "alert_back";
        }

        model.addAttribute("campaignActivity", convertToDto(campaignActivity));
        return "entry";
    }

    @GetMapping("/cart")
    public String cart() {
        return "cart";
    }

    @GetMapping("/cart/checkout")
    public String payment() {
        return "payment";
    }

    @GetMapping("/payment/success")
    public String paymentSuccess() {
        return "payment-success";
    }

    @GetMapping("/mypage")
    public String mypage(
            @org.springframework.web.bind.annotation.CookieValue(value = "accessToken", required = false) String accessToken,
            Model model) {
        String username = "Guest";
        Long userId = null;
        List<UserCoupon> userCoupons = new ArrayList<>();

        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            try {
                org.springframework.security.core.Authentication auth = jwtTokenProvider.getAuthentication(accessToken);
                Object principal = auth.getPrincipal();

                if (principal instanceof com.axon.core_service.domain.user.CustomOAuth2User) {
                    com.axon.core_service.domain.user.CustomOAuth2User oauthUser = (com.axon.core_service.domain.user.CustomOAuth2User) principal;
                    username = oauthUser.getDisplayName(); // Use real name
                    userId = oauthUser.getUserId();
                } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                    username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
                    userId = Long.parseLong(username); // Fallback for test users
                }

                if (userId != null) {
                    userCoupons = userCouponRepository.findAllByUserId(userId);
                }
            } catch (Exception e) {
                log.warn("Failed to extract user data from token", e);
            }
        }

        model.addAttribute("username", username);
        model.addAttribute("userCoupons", userCoupons);
        return "mypage";
    }

    private CampaignActivityDisplayDto convertToDto(CampaignActivity activity) {
        NumberFormat currency = NumberFormat.getInstance(Locale.KOREA);

        // Calculate price strings
        String priceStr = currency.format(activity.getPrice());

        // Use Product's price as original price if available
        BigDecimal originalPrice = activity.getProduct() != null ? activity.getProduct().getPrice()
                : activity.getPrice();
        String originalPriceStr = currency.format(originalPrice);

        // Coupon Mapping
        String couponName = null;
        BigDecimal couponDiscountAmount = null;
        Integer couponDiscountRate = null;

        if (activity.getActivityType() == com.axon.messaging.CampaignActivityType.COUPON
                && activity.getCoupon() != null) {
            couponName = activity.getCoupon().getCouponName();
            couponDiscountAmount = activity.getCoupon().getDiscountAmount();
            couponDiscountRate = activity.getCoupon().getDiscountRate();
        }

        return CampaignActivityDisplayDto.builder()
                .id(activity.getId())
                .title(activity.getName())
                .description(activity.getActivityType() == com.axon.messaging.CampaignActivityType.COUPON
                        ? "쿠폰 다운로드 이벤트"
                        : "선착순 한정 판매")
                .price(priceStr)
                .originalPrice(originalPriceStr)
                .limitCount(activity.getLimitCount() != null ? activity.getLimitCount() : 0)
                .imageUrl(activity.getImageUrl())
                .startDate(activity.getStartDate())
                .endDate(activity.getEndDate())
                .filters(activity.getFilters())
                .activityType(activity.getActivityType())
                .productId(activity.getProductId())
                .couponId(activity.getCoupon() != null ? activity.getCoupon().getId() : null)
                .couponName(couponName)
                .couponDiscountAmount(couponDiscountAmount)
                .couponDiscountRate(couponDiscountRate)
                .build();
    }

    @Getter
    @Builder
    public static class CampaignActivityDisplayDto {
        private Long id;
        private String title;
        private String description;
        private String price;
        private String originalPrice;
        private int limitCount;
        private String imageUrl;
        private java.time.LocalDateTime startDate;
        private java.time.LocalDateTime endDate;
        private List<FilterDetail> filters;
        private com.axon.messaging.CampaignActivityType activityType;
        private Long productId;
        private Long couponId;

        // Coupon specific fields
        private String couponName;
        private BigDecimal couponDiscountAmount;
        private Integer couponDiscountRate;
    }

    @Getter
    @Builder
    public static class ProductDisplayDto {
        private Long id;
        private String name;
        private String brand;
        private String price; // Selling price
        private String originalPrice; // Original price (if discounted)
        private int discountRate;
        private String imageUrl;
        private String category;
        private int reviewCount;
    }
}
