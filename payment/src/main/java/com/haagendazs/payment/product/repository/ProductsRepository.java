package com.haagendazs.payment.product.repository;

import com.haagendazs.payment.product.entity.Products;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductsRepository extends JpaRepository<Products, Long> {
}
