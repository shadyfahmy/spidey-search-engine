package com.search.queryprocessor.controller;

import com.search.queryprocessor.entity.History;
import com.search.queryprocessor.entity.Query;
import com.search.queryprocessor.entity.User;
import com.search.queryprocessor.repository.QueryRepository;
import com.search.queryprocessor.utils.Stemmer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.tartarus.snowball.ext.EnglishStemmer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@CrossOrigin("*")
public class ApiController {

    private final QueryRepository repository;
    Stemmer stemmer = new Stemmer();

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
        String q = "INSERT IGNORE INTO search.history (user, url) VALUES ("+String.valueOf(history.getUser())+
                ","+String.valueOf(history.getUrl())+");";
        System.out.println(q);
        jdbcTemplate.execute(q);

        int times = jdbcTemplate.queryForObject("select times from search.history where (user ="+
                        String.valueOf(history.getUser())+") and (url =" +String.valueOf(history.getUrl())+")",
                (us, RN) -> us.getInt("times"));
        times++;

        String q1 =  "UPDATE search.history SET times = "+String.valueOf(times)+"  WHERE (user ="+String.valueOf(history.getUser())+
                ") and (url ="+String.valueOf(history.getUrl())+");";
        System.out.println(q1);
        jdbcTemplate.execute(q1);

    }

    @ResponseBody
    @RequestMapping(method = RequestMethod.GET, value = "/api/v1/get-results")
    public int getResults(@RequestParam(name = "text") String text, @RequestParam(name = "page") int page,
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

        return user;
    }

    @ResponseBody
    @RequestMapping(method = RequestMethod.GET, value = "/api/v1/get-images")
    public int getImages(@RequestParam(name = "text") String text, @RequestParam(name = "page") int page,
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

        return user;
    }

}
