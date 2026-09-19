package com.edgareldy.quarkustutorial.service.impl;

import com.edgareldy.quarkustutorial.dto.common.PageResponse;
import com.edgareldy.quarkustutorial.dto.ecommerce.OrderRequest;
import com.edgareldy.quarkustutorial.dto.ecommerce.OrderResponse;
import com.edgareldy.quarkustutorial.entity.Customer;
import com.edgareldy.quarkustutorial.entity.Order;
import com.edgareldy.quarkustutorial.entity.Product;
import com.edgareldy.quarkustutorial.exception.BusinessRuleException;
import com.edgareldy.quarkustutorial.exception.ResourceNotFoundException;
import com.edgareldy.quarkustutorial.repository.CustomerRepository;
import com.edgareldy.quarkustutorial.repository.OrderRepository;
import com.edgareldy.quarkustutorial.repository.ProductRepository;
import com.edgareldy.quarkustutorial.service.OrderService;
import com.edgareldy.quarkustutorial.service.event.OrderCreatedEvent;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;

/**
 * Default {@link OrderService} backed by Panache; the total is computed from the current product price.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@ApplicationScoped
public class OrderServiceImpl implements OrderService {

    // NUMERIC(14, 2) holds at most 14 significant digits.
    private static final int MAX_TOTAL_PRECISION = 14;

    @Inject
    OrderRepository orderRepository;

    @Inject
    CustomerRepository customerRepository;

    // The repository is used directly, not the cached ProductService.findById: an order must be priced
    // from the row as it is now, never from a cached copy.
    @Inject
    ProductRepository productRepository;

    // Synchronous CDI event, fired inside the transaction: observers declared with
    // TransactionPhase.AFTER_SUCCESS (see OrderEventBroadcaster) only run once it commits.
    @Inject
    Event<OrderCreatedEvent> orderCreatedEvent;

    @Override
    @Transactional
    public PageResponse<OrderResponse> list(int page, int size, Long customerId, Long productId) {
        List<OrderResponse> content = orderRepository.search(customerId, productId).page(Page.of(page, size))
                .list().stream().map(OrderServiceImpl::toResponse).toList();
        return PageResponse.of(content, page, size, orderRepository.countFiltered(customerId, productId));
    }

    @Override
    @Transactional
    public OrderResponse findById(Long id) {
        Order order = orderRepository.findById(id);
        if (order == null) {
            throw new ResourceNotFoundException("Order " + id + " not found");
        }
        return toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse create(OrderRequest request) {
        Customer customer = customerRepository.findById(request.customerId());
        if (customer == null) {
            throw new BusinessRuleException("Customer " + request.customerId() + " does not exist");
        }
        Product product = productRepository.findById(request.productId());
        if (product == null) {
            throw new BusinessRuleException("Product " + request.productId() + " does not exist");
        }
        // unitPrice has scale 2 and the quantity is an integer, so the product keeps scale 2.
        BigDecimal total = product.getUnitPrice().multiply(BigDecimal.valueOf(request.quantity())).setScale(2);
        if (total.precision() > MAX_TOTAL_PRECISION) {
            throw new BusinessRuleException("The order total is too large");
        }
        Order order = new Order();
        order.setCustomer(customer);
        order.setProduct(product);
        order.setQuantity(request.quantity());
        order.setTotal(total);
        orderRepository.persist(order);
        orderCreatedEvent.fire(new OrderCreatedEvent(order.getId()));
        return toResponse(order);
    }

    private static OrderResponse toResponse(Order order) {
        return new OrderResponse(order.getId(), order.getCustomer().getId(), order.getProduct().getId(),
                order.getQuantity(), order.getTotal());
    }
}
