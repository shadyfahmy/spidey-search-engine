package com.search.queryprocessor.controller;

import com.search.queryprocessor.entity.Query;
import com.search.queryprocessor.repository.QueryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

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

    /*
    @ResponseBody
    @RequestMapping(method = RequestMethod.GET, value = "/api/v1/paging")
    public List<Query> getPage() {
        return Arrays.asList(
                new Query("test1"),
                new Query("test2"),
                new Query("test3")
        );
    }
    */
}
