package com.oldbutgold.shop.modules.catalog.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.catalog.api.CatalogDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.condition.EnabledIf;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIf("isDatabaseConfigured")
class MultipleCategoriesPostgresTest {

    static boolean isDatabaseConfigured() {
        return (System.getenv("OGSHOP_CATEGORY_TEST_DB_URL") != null && !System.getenv("OGSHOP_CATEGORY_TEST_DB_URL").isBlank())
                || (System.getenv("OGSHOP_TEST_DB_URL") != null && !System.getenv("OGSHOP_TEST_DB_URL").isBlank());
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> {
            String url = System.getenv("OGSHOP_CATEGORY_TEST_DB_URL");
            return (url != null && !url.isBlank()) ? url : System.getenv("OGSHOP_TEST_DB_URL");
        });
        properties.add("spring.datasource.username", () -> {
            String u = System.getenv("OGSHOP_CATEGORY_TEST_DB_USER");
            if (u == null || u.isBlank()) u = System.getenv("OGSHOP_TEST_DB_USER");
            return (u != null && !u.isBlank()) ? u : "og_shop";
        });
        properties.add("spring.datasource.password", () -> {
            String p = System.getenv("OGSHOP_CATEGORY_TEST_DB_PASSWORD");
            if (p == null || p.isBlank()) p = System.getenv("OGSHOP_TEST_DB_PASSWORD");
            return (p != null && !p.isBlank()) ? p : "root";
        });
        properties.add("spring.flyway.enabled", () -> true);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO og_compat,public");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired SellerProductService sellerProducts;
    @Autowired CatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    private long sellerId;
    private long reviewerId;
    private long electronics;
    private long books;
    private long other;

    @BeforeEach
    void fixtures() {
        sellerId = jdbc.queryForObject("INSERT INTO users(email,password_hash,full_name) VALUES (?,?,?) RETURNING user_id",
                Long.class, "category-" + UUID.randomUUID() + "@example.test", "unused", "Category test seller");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,role_id FROM roles WHERE role_name='SELLER'", sellerId);

        reviewerId = jdbc.queryForObject("INSERT INTO users(email,password_hash,full_name) VALUES (?,?,?) RETURNING user_id",
                Long.class, "category-rev-" + UUID.randomUUID() + "@example.test", "unused", "Category test reviewer");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,role_id FROM roles WHERE role_name='KTV'", reviewerId);

        electronics = category("electronics"); books = category("books-stationery"); other = category("other");
    }

    private long category(String slug) {
        return jdbc.queryForObject("SELECT category_id FROM categories WHERE slug=?", Long.class, slug);
    }

    private CatalogDtos.CreateOrUpdateProductRequest request(List<Long> ids, Long legacy, Long version) {
        return new CatalogDtos.CreateOrUpdateProductRequest(legacy, "Category test " + sellerId, "Fixture for multiple categories",
                BigDecimal.valueOf(250000), "GOOD", null, null, null, null, null, false, version, ids);
    }

    private void activate(long productId) {
        jdbc.update("INSERT INTO product_media(product_id,media_type,media_url,display_order) VALUES (?,'IMAGE','https://example.test/image.jpg',0),(?,'VIDEO','https://example.test/video.mp4',1)", productId, productId);
        sellerProducts.submitProduct(sellerId, productId);
        long version = jdbc.queryForObject("SELECT version FROM products WHERE product_id=?", Long.class, productId);
        sellerProducts.approveProduct(reviewerId, productId, version, "ck-cat-" + productId + "-" + UUID.randomUUID());
    }

    @Test
    void commitsMultipleCategoriesReplacesSelectionAndFiltersWithoutDuplicatePages() {
        var first = sellerProducts.createProduct(sellerId, request(List.of(electronics, books, electronics), null, null));
        assertThat(sellerProducts.getSellerProduct(sellerId, first.productId()).categories())
                .extracting(CatalogDtos.CategoryResponse::categoryId).containsExactly(electronics, books);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_categories WHERE product_id=?", Integer.class, first.productId())).isEqualTo(2);
        var edited = sellerProducts.updateProduct(sellerId, first.productId(), request(List.of(books, other), null, first.version()));
        assertThat(edited.categories()).extracting(CatalogDtos.CategoryResponse::categoryId).containsExactly(books, other);
        var second = sellerProducts.createProduct(sellerId, request(List.of(electronics, books), null, null));
        activate(first.productId()); activate(second.productId());
        var page0 = catalog.searchProducts("Category test " + sellerId, books, null, null, null, 0, 1, "newest");
        var page1 = catalog.searchProducts("Category test " + sellerId, books, null, null, null, 1, 1, "newest");
        assertThat(page0.totalElements()).isEqualTo(2);
        assertThat(page1.totalElements()).isEqualTo(2);
        assertThat(page0.items().getFirst().productId()).isNotEqualTo(page1.items().getFirst().productId());
        assertThat(catalog.searchProducts("Category test " + sellerId, other, null, null, null, 0, 10, "newest").items())
                .extracting(CatalogDtos.ProductSummaryResponse::productId).containsExactly(first.productId());
        assertThat(catalog.getProductDetail(first.productId()).categories())
                .extracting(CatalogDtos.CategoryResponse::categoryId).containsExactly(books, other);
    }

    @Test
    void acceptsLegacyPayloadAndRejectsMissingEmptyNullOrNonpositiveIdsOverHttp() throws Exception {
        var authentication = jwt().jwt(token -> token.subject(Long.toString(sellerId)))
                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
        http.perform(post("/api/v1/seller/products").with(authentication).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request(null, electronics, null)))).andExpect(status().isCreated());
        for (Object selection : List.of(List.of(), List.of(0), List.of(-1), java.util.Arrays.asList(electronics, null))) {
            var body = Map.of("title", "Test", "description", "Test", "listedPrice", 250000, "condition", "GOOD", "categoryIds", selection);
            http.perform(post("/api/v1/seller/products").with(authentication).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        }
        http.perform(post("/api/v1/seller/products").with(authentication).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request(null, null, null)))).andExpect(status().isBadRequest());
    }
}
