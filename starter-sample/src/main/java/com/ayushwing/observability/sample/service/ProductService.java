package com.ayushwing.observability.sample.service;

import com.ayushwing.observability.core.annotation.Counted;
import com.ayushwing.observability.core.annotation.Timed;
import com.ayushwing.observability.core.annotation.Traced;
import com.ayushwing.observability.sample.model.Product;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory product service that demonstrates {@code @Traced}, {@code @Timed},
 * and {@code @Counted} from the observability starter.
 *
 * <p>All operations are backed by a {@link ConcurrentHashMap} — no persistence,
 * this is purely a demo for observability instrumentation.
 */
@Service
public class ProductService {

    private final Map<String, Product> store = new ConcurrentHashMap<>();

    public ProductService() {
        // Seed some sample data
        save(new Product(null, "Laptop Pro 16", "electronics", 1499.99));
        save(new Product(null, "Wireless Headphones", "electronics", 89.99));
        save(new Product(null, "Standing Desk", "furniture", 649.00));
    }

    @Timed("products.list")
    public List<Product> findAll() {
        return new ArrayList<>(store.values());
    }

    @Traced("product.findById")
    @Timed("products.get")
    public Optional<Product> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Traced("product.create")
    @Counted("products.created")
    public Product save(Product product) {
        if (product.getId() == null || product.getId().isBlank()) {
            product.setId(UUID.randomUUID().toString());
        }
        store.put(product.getId(), product);
        return product;
    }

    @Traced("product.update")
    @Timed("products.update")
    public Optional<Product> update(String id, Product updated) {
        if (!store.containsKey(id)) {
            return Optional.empty();
        }
        updated.setId(id);
        store.put(id, updated);
        return Optional.of(updated);
    }

    @Counted("products.deleted")
    public boolean delete(String id) {
        return store.remove(id) != null;
    }
}
