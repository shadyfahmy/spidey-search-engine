</div>

<h3 align="center">:spider: Spidey :spider_web:</h3>

<div align="center">

[![GitHub issues](https://img.shields.io/github/contributors/shadyfahmy/SearchEngine)](https://github.com/shadyfahmy/SearchEngine/contributors)
[![GitHub issues](https://img.shields.io/github/issues/shadyfahmy/SearchEngine)](https://github.com/shadyfahmy/SearchEngine/issues)
[![GitHub forks](https://img.shields.io/github/forks/shadyfahmy/SearchEngine)](https://github.com/shadyfahmy/SearchEngine/network)
[![GitHub stars](https://img.shields.io/github/stars/shadyfahmy/SearchEngine)](https://github.com/shadyfahmy/SearchEngine/stargazers)
[![GitHub license](https://img.shields.io/github/license/shadyfahmy/SearchEngine)](https://github.com/shadyfahmy/SearchEngine/blob/master/LICENSE)

</div>

# :spider:Spidey:spider_web:
Fully working search engine which can search by text, image or voice , it also has its own crawler, indexer, ranker, query engine and UI.

---

## Dependencies
- java
- javac
- mysql-server

## 📝 Modules

* [Crawler](/src/crawler)
* [Indexer](/src/indexer)
* [Ranker](/src/ranker/PageRanker.java)
* [Query Engine](/query_processor)
* [Query Results Fetcher](/src/ranker/QueryResultsFetcher.java)
* [Performace Analysis](/src/performance_analysis)
* [DataBase Manager](/src/k2_algorithmic_warmup/database_manager)
* [UI](/front_end)

---

## How to Run

1. Clone using vesion control using any IDE ,e.g: [IntelliJ IDEA](https://www.jetbrains.com/help/idea/set-up-a-git-repository.html)
2. Install mysql server
```
sudo apt-get install mysql-server
```
2. Login to root user
```
sudo mysql -u root -p
```
3. Create new user named admin with admin password
```
CREATE USER 'admin'@'localhost' IDENTIFIED BY 'admin';
```
4. Grant all privileges to user **admin** on **test_search_egnine** database
```
GRANT ALL PRIVILEGES ON test_search_engine.* TO 'admin'@'localhost';
```
5. Run [Crawler](/src/crawler/Crawler.java) (preferred to wait 5 minutes before moving to the next step)
6. Run [Indexer](/src/indexer/Indexer.java)
7. Run [Query Processor](/query_processor/src/main/java/com/search/queryprocessor/QueryprocessorApplication.java) (wait until it lauches to move to next step)
8. Run [Performance Analysis](/src/performance_analysis/PerformanceAnalysis.java)

---

## Analysis
This analysis has been made with this parameters using [Performance Analysis](/src/performance_analysis/PerformanceAnalysis.java) module
```
public static final int MAX_WEBSITES = 500;
public final static int SECONDS_TO_SLEEP = 5;
```
### How many simultaneous search requests can your solution handle?
![Max Number Of Simultaneous Search Requet](/analysis_readme/max_num_sim_search_requests.png)
### How is the latency of your solution affected by the number of simultaneous search requests?
![Latency VS Simultaneous Search Requests](/analysis_readme/latency_vs_sim_num_requests.png)
### How is the search request latency of your solution affected by the number of web pages crawled?
![Latency VS Number Of Web pages Crawled](/analysis_readme/latency_vs_crawled_num.png)
### How is the search request latency of your solution affected by the size of the index table?
![Latency VS Size Of The Index Table](/analysis_readme/latency_vs_indexed_num.png)
### How is the search request latency of your solution affected by the ranking process?
![Latency VS Number Of KeyWords Of The Ranking Process](/analysis_readme/latency_vs_num_keywords.png)

---

## Screenshots

---
# Developers

<center>
  
| Name                                |              Email               |
| ----------------------------------- | :------------------------------: |
| Abdulrahman Khalid Hassan           | abdulrahman.elshafei98@gmail.com |
| Shady Fahmy Abd Elhafez             |       Shadyfahmy67@gmail.com     |
| AbdElRahman Muhammad Ahmad ElGamil  |     abdurrumanmohamed@gmail.com  |
| Yosry Mohammad Yosry                |         yosrym93@gmail.com       |

</center>
