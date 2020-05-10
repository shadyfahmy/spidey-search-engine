package com.search.queryprocessor.controller;

import com.search.queryprocessor.entity.Query;
import com.search.queryprocessor.entity.Result;
import com.search.queryprocessor.repository.QueryRepository;
import com.search.queryprocessor.repository.ResultRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin("*")
public class ResultController {

    private final QueryRepository repository;

    ResultController(QueryRepository repository) {
        this.repository = repository;
    }

}
