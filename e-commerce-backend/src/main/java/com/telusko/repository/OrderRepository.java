package com.telusko.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.telusko.enums.OrderStatus;
import com.telusko.model.Order;
import com.telusko.model.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUser(User user, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    long countByPlacedAtAfter(LocalDateTime since);

    @Query("select coalesce(sum(o.totalAmount), 0) from Order o "
            + "where o.placedAt >= :since and o.status not in :excluded")
    BigDecimal sumRevenueSince(@Param("since") LocalDateTime since,
                               @Param("excluded") List<OrderStatus> excluded);

    @Query("select o.status, count(o) from Order o group by o.status order by count(o) desc")
    List<Object[]> countGroupedByStatus();

    @Query("select oi.productName, sum(oi.quantity), sum(oi.lineTotal) "
            + "from OrderItem oi where oi.order.placedAt >= :since "
            + "group by oi.productName order by sum(oi.quantity) desc")
    List<Object[]> topSellingSince(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("select coalesce(c.name, 'Uncategorised'), sum(oi.lineTotal) "
            + "from OrderItem oi left join oi.product p left join p.category c "
            + "where oi.order.placedAt >= :since "
            + "group by c.name order by sum(oi.lineTotal) desc")
    List<Object[]> revenueByCategorySince(@Param("since") LocalDateTime since);

    // AVG is defined to return a Double even over a BigDecimal column, so the return type has to
    // match or Hibernate fails converting the result. Callers round it into a money value.
    @Query("select coalesce(avg(o.totalAmount), 0) from Order o where o.placedAt >= :since")
    Double averageOrderValueSince(@Param("since") LocalDateTime since);
}
