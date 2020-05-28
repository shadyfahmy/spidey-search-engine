package com.search.queryprocessor.controller;

import com.search.queryprocessor.entity.History;
import com.search.queryprocessor.entity.Query;
import com.search.queryprocessor.entity.User;
import com.search.queryprocessor.repository.QueryRepository;
import com.search.queryprocessor.utils.Stemmer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

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
        jdbcTemplate.execute("INSERT INTO test_search_engine.users VALUES (NULL)");
        return jdbcTemplate.queryForObject("select * from test_search_engine.users where " +
                        "id = (select count(id) from test_search_engine.users);",
                (us, RN) -> new User(us.getInt("id")));
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
        System.out.println(text);
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
        System.out.println(words);
        impWords = stemmer.getStemmedWords(words);

        for (int i = 0; i < phrases.size(); i++) {
            impPhraseWords.add(stemmer.getStemmedWords(phrases.get(i)));
        }

        for (int i = 0; i < impWords.size(); i++) {
            System.out.println(impWords.get(i));
        }
        for (int i = 0; i < impPhraseWords.size(); i++) {
            for (int j = 0; j < impPhraseWords.get(i).size(); j++)
                System.out.println(impPhraseWords.get(i).get(j));
        }
        //return 5;
        List<QueryResultsFetcher.Page> r = fetcher.getQueryResults(user, impWords, impPhraseWords, 0, 20);
        System.out.println((r.get(0)).title);
        return r;
    }

    @ResponseBody
    @RequestMapping(method = RequestMethod.GET, value = "/api/v1/get-images")
    public int getImages(@RequestParam(name = "text") String text, @RequestParam(name = "page") int page,
                         @RequestParam(name = "user") int user ) {
        System.out.println(text);

        List<String> impWords = new ArrayList<String>();

        impWords = stemmer.getStemmedWords(text);

        for (int i = 0; i < impWords.size(); i++) {
            System.out.println(impWords.get(i));
        }
        return user;
    }

}
