use mysql;

drop database if exists test_search_engine;

create database test_search_engine;

use test_search_engine;

drop table if exists page;

create table page (
	id serial,
	url text not null,
	crawled_time tinytext not null,
	indexed_time tinytext default null,
	title tinytext default null,
	description tinytext default null,
	page_rank float,
	primary key (id)
);

drop table if exists state;

create table state (
	id serial,
	url text not null,
	primary key (id)
);

drop table if exists page_connections;

create table page_connections (
	from_page_id bigint unsigned,
	to_page_id bigint unsigned,
	primary key (from_page_id, to_page_id),
	foreign key (from_page_id) references page(id) on delete cascade on update cascade,
	foreign key (to_page_id) references page(id) on delete cascade on update cascade
);

drop table if exists word;

create table word (
	id serial,
	word varchar(500) not null,
	pages_count int unsigned not null,
	primary key (id)
);

drop table if exists word_index;

create table word_index (
	word_id bigint unsigned,
	page_id bigint unsigned,
	count int unsigned not null,
	important bool not null,
	indices text not null,
	primary key (word_id, page_id),
	foreign key (page_id) references page(id) on delete cascade on update cascade,
	foreign key (word_id) references word(id) on delete cascade on update cascade
);