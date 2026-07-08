package com.haagendazs.payment.product.repository;

import com.haagendazs.payment.product.entity.Products;
import com.haagendazs.payment.product.enums.ProductType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductsRepository extends JpaRepository<Products, Long> {
    List<Products> findAllByProductType(ProductType productType);
}
