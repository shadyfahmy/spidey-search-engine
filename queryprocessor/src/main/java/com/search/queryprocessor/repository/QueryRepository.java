package com.search.queryprocessor.repository;

import com.search.queryprocessor.entity.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin("*")
public interface QueryRepository extends JpaRepository<Query, String> {

    @RestResource(path = "suggestions")
    Page<Query> findByTextStartingWith(@Param("text") String text, Pageable pageable);

}
