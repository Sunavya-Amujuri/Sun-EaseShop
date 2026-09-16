package com.telusko.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.telusko.model.Product;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Optional<Product> findByIdAndActiveTrue(Long id);

    Page<Product> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);

    /**
     * Keyword search over the visible catalog.
     * <p>
     * The category is joined and fetched in the same query so mapping results to DTOs does not fire
     * one extra select per row.
     */
    @Query("select p from Product p left join fetch p.category c "
            + "where p.active = true and ("
            + "  lower(p.name) like lower(concat('%', :q, '%')) "
            + "  or lower(p.description) like lower(concat('%', :q, '%')) "
            + "  or lower(c.name) like lower(concat('%', :q, '%'))) "
            + "order by p.name asc")
    List<Product> searchActive(@Param("q") String q);
    @Query("select p from Product p left join fetch p.category where p.id in :ids and p.active = true")
    List<Product> findActiveByIdWithCategory(@Param("ids") List<Long> ids);
    @Query("select p from Product p where p.active = true and p.stockQty <= :threshold "
            + "order by p.stockQty asc")
    List<Product> findLowStock(@Param("threshold") int threshold, Pageable pageable);
}