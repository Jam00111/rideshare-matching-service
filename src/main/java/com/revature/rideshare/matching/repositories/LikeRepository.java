package com.revature.rideshare.matching.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.revature.rideshare.matching.beans.Like;

@Repository
@Transactional
public interface LikeRepository extends JpaRepository<Like, Integer> {

	List<Like> findByUserId(int userId);
}
