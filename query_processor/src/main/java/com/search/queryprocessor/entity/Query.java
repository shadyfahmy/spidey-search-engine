package com.search.queryprocessor.entity;

import lombok.ToString;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "queries", schema = "search")
@ToString
public class Query {
    @Id
    String text;

    public void setText(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
