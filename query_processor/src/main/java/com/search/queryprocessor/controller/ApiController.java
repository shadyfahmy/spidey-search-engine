package com.search.queryprocessor.controller;

import com.search.queryprocessor.entity.History;
import com.search.queryprocessor.entity.Query;
import com.search.queryprocessor.entity.User;
import com.search.queryprocessor.repository.QueryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@CrossOrigin("*")
public class ApiController {

    private final QueryRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    ApiController(QueryRepository repository) {
        this.repository = repository;
    }

    //Add query to data base to be used in suggestions
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

    //Add new user to database and return the added user
    @ResponseBody
    @RequestMapping(method = RequestMethod.POST, value = "/api/v1/add-user")
    public User addUser() {
        jdbcTemplate.execute("INSERT INTO search.users VALUES (NULL)");
        return jdbcTemplate.queryForObject("select * from search.users where id = (select count(id) from search.users);",
                (us, RN) -> new User(us.getInt("id")));
    }

    //Add to history of a user
    @RequestMapping(method = RequestMethod.POST, value = "/api/v1/history")
    public void addHistory(@RequestBody History history) {
        String q = "INSERT INTO search.history (user, url) VALUES ("+String.valueOf(history.getUser())+
                ","+String.valueOf(history.getUrl())+");";
        System.out.println(q);
        jdbcTemplate.execute(q);
    }

}
