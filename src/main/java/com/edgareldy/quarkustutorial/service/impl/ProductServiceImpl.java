package com.edgareldy.quarkustutorial.service.impl;

import com.edgareldy.quarkustutorial.dto.common.PageResponse;
import com.edgareldy.quarkustutorial.dto.ecommerce.ProductRequest;
import com.edgareldy.quarkustutorial.dto.ecommerce.ProductResponse;
import com.edgareldy.quarkustutorial.entity.Category;
import com.edgareldy.quarkustutorial.entity.Product;
import com.edgareldy.quarkustutorial.exception.BusinessRuleException;
import com.edgareldy.quarkustutorial.exception.ResourceNotFoundException;
import com.edgareldy.quarkustutorial.repository.CategoryRepository;
import com.edgareldy.quarkustutorial.repository.ProductRepository;
import com.edgareldy.quarkustutorial.service.ProductService;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheKey;
import io.quarkus.cache.CacheResult;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Default {@link ProductService} backed by Panache, with a per-product cache on reads.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@ApplicationScoped
public class ProductServiceImpl implements ProductService {

    @Inject
    ProductRepository productRepository;

    @Inject
    CategoryRepository categoryRepository;

    @Override
    @Transactional
    public PageResponse<ProductResponse> list(int page, int size, Long categoryId) {
        List<ProductResponse> content = productRepository.findByCategory(categoryId).page(Page.of(page, size))
                .list().stream().map(ProductServiceImpl::toResponse).toList();
        long total = categoryId == null ? productRepository.count() : productRepository.countByCategoryId(categoryId);
        return PageResponse.of(content, page, size, total);
    }

    // @CacheResult (quarkus-cache): the first call for a given id runs the method and stores the returned
    // DTO in "product-cache"; later calls with the same key return it without touching the database.
    // @CacheKey makes the key explicit (the product id), so the invalidations below agree on it.
    // Only the ProductResponse DTO is cached, never a Hibernate entity. Exceptions (404) are not cached.
    // Deviation from the README: the annotation sits on this implementation method, not on the
    // ProductService interface. Verified empirically: ArC does not apply interceptor bindings declared on
    // an interface method (the cache stayed empty), whereas on the class method the entry is stored.
    @Override
    @Transactional
    @CacheResult(cacheName = "product-cache")
    public ProductResponse findById(@CacheKey Long id) {
        return toResponse(find(id));
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        apply(product, request);
        productRepository.persist(product);
        return toResponse(product);
    }

    // @CacheInvalidate: once this write succeeds, the entry stored by findById under the same cache name
    // and the same key (the id) is evicted, so the next read reloads the fresh value.
    @Override
    @Transactional
    @CacheInvalidate(cacheName = "product-cache")
    public ProductResponse update(@CacheKey Long id, ProductRequest request) {
        Product product = find(id);
        apply(product, request);
        return toResponse(product);
    }

    @Override
    @Transactional
    @CacheInvalidate(cacheName = "product-cache")
    public void delete(@CacheKey Long id) {
        productRepository.delete(find(id));
    }

    private Product find(Long id) {
        Product product = productRepository.findById(id);
        if (product == null) {
            throw new ResourceNotFoundException("Product " + id + " not found");
        }
        return product;
    }

    private void apply(Product product, ProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId());
        if (category == null) {
            throw new BusinessRuleException("Category " + request.categoryId() + " does not exist");
        }
        product.setCategory(category);
        product.setProductName(request.productName());
        // Scale to 2 decimals like the NUMERIC(12, 2) column (ProductRequest allows at most 2), so the
        // response to a write shows the same value a later read returns from the database.
        product.setUnitPrice(request.unitPrice().setScale(2));
    }

    private static ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId(), product.getCategory().getId(), product.getProductName(),
                product.getUnitPrice());
    }
}
