package com.haagendazs.payment.product.repository;

import com.haagendazs.payment.product.entity.Products;
import com.haagendazs.payment.product.enums.ProductType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductsRepository extends JpaRepository<Products, Long> {
    List<Products> findAllByProductType(ProductType productType);

    @Query("""
        select p
        from Products p
        where p.productType = :productType
          and p.product_detail_id = :productDetailId
    """)
    Optional<Products> findByProductTypeAndProductDetailId(
            @Param("productType") ProductType productType,
            @Param("productDetailId") Long productDetailId
    );
}
