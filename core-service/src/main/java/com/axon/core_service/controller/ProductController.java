package com.axon.core_service.controller;

import com.axon.core_service.domain.product.Product;
import com.axon.core_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;

    @GetMapping("/search")
    public ResponseEntity<?> searchProducts(@RequestParam("keyword") String keyword) {
        List<Product> products = productRepository.findByProductNameContaining(keyword);

        List<Map<String, Object>> result = products.stream()
                .map(p -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", p.getId());
                    map.put("name", p.getProductName());
                    map.put("price", p.getPrice());
                    map.put("stock", p.getStock());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}
