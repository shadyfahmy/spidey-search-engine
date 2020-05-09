package com.search.queryprocessor.repository;

import com.search.queryprocessor.entity.Result;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;

@CrossOrigin("*")
public interface ResultRepository extends JpaRepository<Result, Integer> {
}
