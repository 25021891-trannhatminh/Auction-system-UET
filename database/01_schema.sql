use auctionsystem;
create table users(
	user_id int auto_increment primary key,
    username varchar(100) not null unique,
    email varchar(255) not null unique,
    role enum('BIDDER','SELLER','ADMIN') not null,
    is_active boolean default true,
    created_at timestamp default current_timestamp
);
use auctionsystem;
create table items(
	item_id int auto_increment primary key,
    seller_id int not null,
    category varchar(100)  not null,
    name varchar(255) not null,
    starting_price decimal(15,2) not null,
    created_at timestamp default current_timestamp,
    foreign key (seller_id) references users(user_id)
);
use auctionsystem;

create table auctions(
	auction_id int auto_increment primary key,
    item_id int not null unique,
    seller_id int not null,
    start_time datetime not null,
    end_time datetime not null,
    last_bid_time timestamp null,
    current_price decimal(15,2) not null,
    current_winner_id int,
    status enum('OPEN','RUNNING','FINISHED','PAID','CANCELED') default 'OPEN',
    created_at timestamp default current_timestamp,
    foreign key (current_winner_id) references users(user_id),
    foreign key (seller_id) references users(user_id),
    foreign key (item_id) references items(item_id)
    
);


use auctionsystem;
create table bids(
	bid_id int auto_increment primary key,
    auction_id int not null,
    bidder_id int not null,
    amount decimal(15,2) not null,
    bid_time timestamp default current_timestamp,
    is_auto_bid boolean default false,
    foreign key (auction_id) references auctions(auction_id),
    foreign key (bidder_id) references users(user_id)
);

create table auto_bids(
	auto_bid_id int auto_increment primary key,
    auction_id int not null,
    bidder_id int not null,
    max_bid decimal(15,2) not null,
    increment decimal(15,2) not null,
    status enum('ACTIVE','COMPLETED','CANCELED') default 'ACTIVE',
    created_at timestamp default current_timestamp,
    foreign key (auction_id) references auctions(auction_id),
    foreign key (bidder_id) references users(user_id)
    
);
