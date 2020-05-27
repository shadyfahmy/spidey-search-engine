package com.search.queryprocessor.entity;

import lombok.ToString;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "queries", schema = "test_search_engine")
@ToString
public class Query {
    @Id
    String text;

    public Query(){}

    public Query(String txt) {
        this.text = txt;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
