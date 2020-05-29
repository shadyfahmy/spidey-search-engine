use mysql;

drop database if exists test_search_engine;

create database test_search_engine;
ALTER DATABASE test_search_engine CHARACTER SET utf8 COLLATE utf8_general_ci;
use test_search_engine;

drop table if exists page;

create table page (
	id serial,
	url text not null,
	crawled_time tinytext not null,
	indexed_time tinytext default null,
	title text default null,
	description text default null,
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
	primary key (word_id, page_id),
	foreign key (page_id) references page(id) on delete cascade on update cascade,
	foreign key (word_id) references word(id) on delete cascade on update cascade
);

drop table if exists users;

CREATE TABLE users (
   id int NOT NULL AUTO_INCREMENT,
   PRIMARY KEY (id)
);

drop table if exists history;

CREATE TABLE history (
   user int NOT NULL,
   page bigint unsigned,
   times int NOT NULL DEFAULT '0',
   PRIMARY KEY (user, page),
   FOREIGN KEY (page) REFERENCES page(id) ON DELETE CASCADE ON UPDATE CASCADE
);

drop table if exists queries;

CREATE TABLE queries (
   text varchar(500) NOT NULL,
   PRIMARY KEY (text)
);

drop table if exists word_positions;

create table word_positions (
	word_id bigint unsigned,
	page_id bigint unsigned,
	position bigint,
	primary key (word_id, page_id, position),
	foreign key (page_id) references page(id) on delete cascade on update cascade,
	foreign key (word_id) references word(id) on delete cascade on update cascade
);


drop table if exists word_image;

create table word_image (
	id serial,
	word varchar(500) not null,
	images_count int unsigned not null,
	primary key (id)
);

drop table if exists image;

create table image (
	id serial,
	url text not null,
	description text default null,
	primary key (id)
);
drop table if exists word_index_image;

create table word_index_image (
	word_id bigint unsigned,
	image_id bigint unsigned,
	primary key (word_id, image_id),
	foreign key (image_id) references image(id) on delete cascade on update cascade,
	foreign key (word_id) references word_image(id) on delete cascade on update cascade
);