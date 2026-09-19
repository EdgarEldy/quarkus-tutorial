package com.edgareldy.quarkustutorial.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import com.edgareldy.quarkustutorial.service.event.OrderCreatedEvent;
import jakarta.enterprise.event.Event;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Isolated unit tests of OrderServiceImpl with its three repositories mocked.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Plain Mockito with the package-private injected fields assigned directly (same approach as the other
// service tests): the total computation and the refusal rules need no container.
class OrderServiceImplTest {

    private OrderRepository orderRepository;
    private CustomerRepository customerRepository;
    private ProductRepository productRepository;
    private OrderServiceImpl service;
    private Event<OrderCreatedEvent> orderCreatedEvent;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        customerRepository = mock(CustomerRepository.class);
        productRepository = mock(ProductRepository.class);
        orderCreatedEvent = mock(Event.class);
        service = new OrderServiceImpl();
        service.orderCreatedEvent = orderCreatedEvent;
        service.orderRepository = orderRepository;
        service.customerRepository = customerRepository;
        service.productRepository = productRepository;
    }

    private static Customer customer(Long id) {
        Customer c = new Customer();
        c.setId(id);
        return c;
    }

    private static Product product(Long id, String price) {
        Product p = new Product();
        p.setId(id);
        p.setUnitPrice(new BigDecimal(price));
        return p;
    }

    private static Order order(Long id, Customer c, Product p, int quantity, String total) {
        Order o = new Order();
        o.setId(id);
        o.setCustomer(c);
        o.setProduct(p);
        o.setQuantity(quantity);
        o.setTotal(new BigDecimal(total));
        return o;
    }

    @Test
    void createComputesTotalFromCurrentUnitPriceWithScale2() {
        when(customerRepository.findById(1L)).thenReturn(customer(1L));
        when(productRepository.findById(2L)).thenReturn(product(2L, "2.50"));

        OrderResponse response = service.create(new OrderRequest(1L, 2L, 3));

        assertEquals(new BigDecimal("7.50"), response.total());
        assertEquals(1L, response.customerId());
        assertEquals(2L, response.productId());
        assertEquals(3, response.quantity());
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).persist(captor.capture());
        assertEquals(new BigDecimal("7.50"), captor.getValue().getTotal());
        // The price is read fresh from the repository, never from the cached ProductService.
        verify(productRepository).findById(2L);
    }

    @Test
    void createWithUnknownCustomerIsRefused() {
        when(customerRepository.findById(1L)).thenReturn(null);

        assertThrows(BusinessRuleException.class, () -> service.create(new OrderRequest(1L, 2L, 1)));
        verify(orderRepository, never()).persist(any(Order.class));
    }

    @Test
    void createWithUnknownProductIsRefused() {
        when(customerRepository.findById(1L)).thenReturn(customer(1L));
        when(productRepository.findById(2L)).thenReturn(null);

        assertThrows(BusinessRuleException.class, () -> service.create(new OrderRequest(1L, 2L, 1)));
        verify(orderRepository, never()).persist(any(Order.class));
    }

    @Test
    void createWithTotalBeyondNumeric14IsRefused() {
        when(customerRepository.findById(1L)).thenReturn(customer(1L));
        when(productRepository.findById(2L)).thenReturn(product(2L, "9999999999.99"));

        assertThrows(BusinessRuleException.class, () -> service.create(new OrderRequest(1L, 2L, 101)));
        verify(orderRepository, never()).persist(any(Order.class));
    }

    @Test
    void createAcceptsTheLargestTotalThatFits() {
        when(customerRepository.findById(1L)).thenReturn(customer(1L));
        when(productRepository.findById(2L)).thenReturn(product(2L, "9999999999.99"));

        OrderResponse response = service.create(new OrderRequest(1L, 2L, 100));

        // 14 significant digits: the widest value NUMERIC(14, 2) accepts.
        assertEquals(new BigDecimal("999999999999.00"), response.total());
    }

    @Test
    void findByIdMapsAndMissingIsNotFound() {
        when(orderRepository.findById(5L)).thenReturn(order(5L, customer(1L), product(2L, "1.00"), 2, "2.00"));
        when(orderRepository.findById(9L)).thenReturn(null);

        OrderResponse response = service.findById(5L);

        assertEquals(5L, response.id());
        assertEquals(new BigDecimal("2.00"), response.total());
        assertThrows(ResourceNotFoundException.class, () -> service.findById(9L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listBuildsPageResponseFromFilteredQueryAndCount() {
        PanacheQuery<Order> query = mock(PanacheQuery.class);
        PanacheQuery<Order> paged = mock(PanacheQuery.class);
        when(orderRepository.search(1L, null)).thenReturn(query);
        when(query.page(any(Page.class))).thenReturn(paged);
        Customer c = customer(1L);
        Product p = product(2L, "1.00");
        when(paged.list()).thenReturn(List.of(order(1L, c, p, 1, "1.00"), order(2L, c, p, 2, "2.00")));
        when(orderRepository.countFiltered(1L, null)).thenReturn(5L);

        PageResponse<OrderResponse> page = service.list(1, 2, 1L, null);

        assertEquals(2, page.content().size());
        assertEquals(1, page.page());
        assertEquals(2, page.size());
        assertEquals(5L, page.totalElements());
        assertEquals(3, page.totalPages());
    }
}
