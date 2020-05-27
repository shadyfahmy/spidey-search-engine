package com.search.queryprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

@SpringBootApplication
public class QueryprocessorApplication {

	public static void main(String[] args)  {
		SpringApplication.run(QueryprocessorApplication.class, args);

	}

}
