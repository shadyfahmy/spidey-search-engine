package com.search.queryprocessor.controller;

import com.search.queryprocessor.entity.Query;
import com.search.queryprocessor.repository.QueryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin("*")
public class QueryController {

    private final QueryRepository repository;

    QueryController(QueryRepository repository) {
        this.repository = repository;
    }

    @RequestMapping(method = RequestMethod.POST, value = "/api/v1/save-query")
    public void addQuery(@RequestBody Query query) {
        repository.save(query);
    }

}
