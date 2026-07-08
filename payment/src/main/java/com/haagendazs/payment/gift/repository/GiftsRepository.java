package com.haagendazs.payment.gift.repository;

import com.haagendazs.payment.gift.entity.Gifts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GiftsRepository extends JpaRepository<Gifts, Long> {
}
