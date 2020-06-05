package com.search.queryprocessor.controller;

import com.search.queryprocessor.entity.History;
import com.search.queryprocessor.entity.Query;
import com.search.queryprocessor.entity.User;
import com.search.queryprocessor.repository.QueryRepository;
import com.search.queryprocessor.utils.Stemmer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import ranker.QueryResultsFetcher;

@RestController
@CrossOrigin("*")
public class ApiController {

    private final QueryRepository repository;
    Stemmer stemmer = new Stemmer();
    QueryResultsFetcher fetcher = new QueryResultsFetcher();


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


    //Add new user to database and return the added user
    @ResponseBody
    @RequestMapping(method = RequestMethod.POST, value = "/api/v1/add-user")
    public List<User> addUser() {
        jdbcTemplate.execute("INSERT INTO test_search_engine.users VALUES (NULL)");

        List<User> user = this.jdbcTemplate.query(
                "select * from test_search_engine.users where " +
                        "id = (select count(id) from test_search_engine.users);",
                new RowMapper<User>() {
                    public User mapRow(ResultSet rs, int rowNum) throws SQLException {
                        User u = new User(rs.getInt("id"));
                        return u;
                    }
                });
        return user;
    }

    //Add to history of a user
    @RequestMapping(method = RequestMethod.POST, value = "/api/v1/history")
    public void addHistory(@RequestBody History history) {
        String q = "INSERT IGNORE INTO test_search_engine.history (user, page) VALUES ("+String.valueOf(history.getUser())+
                ","+String.valueOf(history.getPage())+");";
        System.out.println(q);
        jdbcTemplate.execute(q);

        int times = jdbcTemplate.queryForObject("select times from test_search_engine.history where (user ="+
                        String.valueOf(history.getUser())+") and (page =" +String.valueOf(history.getPage())+")",
                (us, RN) -> us.getInt("times"));
        times++;

        String q1 =  "UPDATE test_search_engine.history SET times = "+String.valueOf(times)+"  WHERE (user ="+String.valueOf(history.getUser())+
                ") and (page ="+String.valueOf(history.getPage())+");";
        System.out.println(q1);
        jdbcTemplate.execute(q1);

    }

    @ResponseBody
    @RequestMapping(method = RequestMethod.GET, value = "/api/v1/get-results")
    public List<QueryResultsFetcher.Page> getResults(@RequestParam(name = "text") String text, @RequestParam(name = "page") int page,
                                                     @RequestParam(name = "user") int user ) {

        text = text.replace("\"", " \" ");

        String words = "";
        List<String> impWords = new ArrayList<String>();
        List<String> phrases = new ArrayList<String>();
        List<List<String>> impPhraseWords = new ArrayList<List<String>>();

        String[] splits = text.split(" (?=([^\"]*\"[^\"]*\")*[^\"]*$)");
        for (int i = 0; i < splits.length; i++) {
            String split = splits[i].trim();
            if (split.length() != 0) {

                if (split.charAt(0) == '"' && split.charAt(split.length() - 1) == '"') {
                    phrases.add(split);
                }
                else {
                    words = words.concat(split + " ");
                }
            }
        }
        impWords = stemmer.getStemmedWords(words);

        for (int i = 0; i < phrases.size(); i++) {
            impPhraseWords.add(stemmer.getStemmedWords(phrases.get(i)));
        }

        int offset = (page - 1)*20;
        return fetcher.getQueryResults(user, impWords, impPhraseWords, offset, 20);
    }

    @ResponseBody
    @RequestMapping(method = RequestMethod.GET, value = "/api/v1/get-images")
    public List<String> getImages(@RequestParam(name = "text") String text, @RequestParam(name = "page") int page,
                         @RequestParam(name = "user") int user ) {

        List<String> impWords = new ArrayList<String>();
        impWords = stemmer.getStemmedWords(text);
        int offset = (page - 1)*20;
        return fetcher.getImageSearchResults(impWords, offset, 20);
    }

}
