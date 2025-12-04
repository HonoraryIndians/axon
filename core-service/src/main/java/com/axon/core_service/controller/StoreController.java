package com.axon.core_service.controller;

import com.axon.core_service.domain.campaignactivity.CampaignActivity;
import com.axon.core_service.domain.dto.campaignactivity.filter.FilterDetail;
import com.axon.core_service.exception.CampaignActivityNotFoundException;
import com.axon.core_service.repository.CampaignActivityRepository;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@Slf4j
public class StoreController {

    private final CampaignActivityRepository campaignActivityRepository;

    @GetMapping("/mainshop")
    public String mainshop(Model model) {
        // Keep mock products for now as requested only for events
        model.addAttribute("rankings", MockDataGenerator.generateProducts(5, "Tech"));
        model.addAttribute("techDeals", MockDataGenerator.generateProducts(4, "Tech"));
        model.addAttribute("foodDeals", MockDataGenerator.generateProducts(4, "Food"));
        model.addAttribute("homeDeals", MockDataGenerator.generateProducts(4, "Home"));
        return "mainshop";
    }

    @GetMapping("/product/{id}")
    public String productDetail(@PathVariable Long id, Model model) {
        model.addAttribute("product", MockDataGenerator.generateSingleProduct(id));
        return "product/detail";
    }

    @GetMapping("/checkout")
    public String checkout(@RequestParam Long productId, Model model) {
        // Keep mock product for checkout for now
        ProductDisplayDto product = MockDataGenerator.generateSingleProduct(productId);
        model.addAttribute("product", product);
        return "checkout";
    }

    @GetMapping("/events")
    public String getCampaignActivities(Model model) {
        // Fetch real campaign activities (skip broken data)
        List<CampaignActivityDisplayDto> realActivities = campaignActivityRepository
                .findAllByStatus(com.axon.core_service.domain.dto.campaignactivity.CampaignActivityStatus.ACTIVE)
                .stream()
                .map(activity -> {
                    try {
                        return convertToDto(activity);
                    } catch (Exception e) {
                        log.warn("Failed to convert CampaignActivity id={}: {}", activity.getId(), e.getMessage());
                        return null;  // Skip broken data
                    }
                })
                .filter(dto -> dto != null)  // Remove nulls
                .collect(Collectors.toList());

        // Separate FCFS and Raffle activities
        model.addAttribute("fcfsCampaignActivities", realActivities);

        // Keep mocks for other sections if empty or just empty them
        model.addAttribute("raffleCampaignActivities", new ArrayList<>());
        model.addAttribute("coupons", MockDataGenerator.generateCoupons(3)); // Keep mock coupons

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

    private CampaignActivityDisplayDto convertToDto(CampaignActivity activity) {
        NumberFormat currency = NumberFormat.getInstance(Locale.KOREA);

        // Calculate price strings
        String priceStr = currency.format(activity.getPrice());

        // Use Product's price as original price if available
        BigDecimal originalPrice = activity.getProduct() != null ? activity.getProduct().getPrice()
                : activity.getPrice();
        String originalPriceStr = currency.format(originalPrice);

        return CampaignActivityDisplayDto.builder()
                .id(activity.getId())
                .title(activity.getName())
                .description("선착순 한정 판매") // Generic description
                .price(priceStr)
                .originalPrice(originalPriceStr)
                .limitCount(activity.getLimitCount() != null ? activity.getLimitCount() : 0)
                .imageUrl(activity.getImageUrl())
                .startDate(activity.getStartDate())
                .endDate(activity.getEndDate())
                .filters(activity.getFilters())
                .activityType(activity.getActivityType())
                .productId(activity.getProductId())
                .build();
    }

    // --- Mock Data Support ---

    @Getter
    @Builder
    public static class ProductDisplayDto {
        private Long id;
        private String brand;
        private String name;
        private String price; // Formatted string
        private String originalPrice;
        private int discountRate;
        private String imageUrl;
        private String category;
        private boolean isSoldOut;
        private int reviewCount;
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
    }

    static class MockDataGenerator {
        private static final Random random = new Random();
        private static final NumberFormat currency = NumberFormat.getInstance(Locale.KOREA);

        // Generic, diverse brands
        private static final String[] BRANDS = { "Axon Essentials", "Urban Tech", "Fresh Farm", "Nordic Home",
                "Pure Beauty", "Daily Fit", "Green Life" };

        // Categories and their specific items
        private static final String[] TECH_ITEMS = { "Wireless Mouse", "Mechanical Keyboard", "4K Monitor",
                "Noise Cancelling Headphones", "Smart Watch", "Tablet Stand" };
        private static final String[] FOOD_ITEMS = { "Premium Steak Kit", "Organic Salad Mix", "Cold Brew Coffee",
                "Protein Bar Box", "Sparkling Water", "Fresh Strawberries" };
        private static final String[] HOME_ITEMS = { "Aroma Diffuser", "Soft Cotton Towel", "Mood Lamp",
                "Ceramic Mug Set", "Memory Foam Pillow" };
        private static final String[] FASHION_ITEMS = { "Basic T-Shirt", "Wide Slacks", "Oversized Hoodie",
                "Canvas Tote Bag", "Denim Jacket", "Running Shoes" };

        public static List<ProductDisplayDto> generateProducts(int count, String category) {
            return IntStream.range(0, count)
                    .mapToObj(i -> generateSingleProduct((long) i, category))
                    .collect(Collectors.toList());
        }

        public static ProductDisplayDto generateSingleProduct(Long id) {
            // Randomly pick a category for general
            String[] categories = { "Tech", "Food", "Home", "Fashion" };
            return generateSingleProduct(id, categories[random.nextInt(categories.length)]);
        }

        private static ProductDisplayDto generateSingleProduct(Long id, String category) {
            String name;
            String brand = BRANDS[random.nextInt(BRANDS.length)];

            switch (category) {
                case "Tech":
                    name = TECH_ITEMS[random.nextInt(TECH_ITEMS.length)];
                    brand = "Urban Tech"; // Force tech brand
                    break;
                case "Food":
                    name = FOOD_ITEMS[random.nextInt(FOOD_ITEMS.length)];
                    brand = "Fresh Farm";
                    break;
                case "Home":
                    name = HOME_ITEMS[random.nextInt(HOME_ITEMS.length)];
                    brand = "Nordic Home";
                    break;
                default: // Fashion
                    name = FASHION_ITEMS[random.nextInt(FASHION_ITEMS.length)];
                    brand = "Axon Essentials";
                    break;
            }

            int priceVal = (random.nextInt(90) + 5) * 1000; // 5,000 ~ 95,000
            int discount = random.nextInt(30);
            int originalPriceVal = (int) (priceVal * (1.0 + discount / 100.0));

            String fullName = String.format("[%s] %s", brand, name);

            // Placeholder images with category text
            String imageUrl = "https://placehold.co/400x533/333/FFF?text=" + name.replaceAll(" ", "+");

            return ProductDisplayDto.builder()
                    .id(id)
                    .brand(brand)
                    .name(fullName)
                    .price(currency.format(priceVal))
                    .originalPrice(currency.format(originalPriceVal))
                    .discountRate(discount)
                    .imageUrl(imageUrl)
                    .category(category)
                    .reviewCount(random.nextInt(500))
                    .isSoldOut(random.nextBoolean() && random.nextBoolean())
                    .build();
        }

        public static List<CampaignActivityDisplayDto> generateCoupons(int count) {
            return IntStream.range(0, count)
                    .mapToObj(i -> CampaignActivityDisplayDto.builder()
                            .id((long) (i + 100))
                            .title("Special Coupon " + (i + 1))
                            .description("Get " + ((i + 1) * 10) + "% off")
                            .imageUrl("ticket")
                            .build())
                    .collect(Collectors.toList());
        }
    }
}
