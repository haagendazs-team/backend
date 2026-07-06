package com.haagendazs.payment.Gift.repository;

import com.haagendazs.payment.Gift.entity.Gifts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GiftsRepository extends JpaRepository<Gifts, Long> {
}
