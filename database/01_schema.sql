create database if not exists auctionsystem;
use auctionsystem;

create table users (
    user_id int auto_increment primary key,
    username varchar(100) not null unique,
    password varchar(255) not null,
    email varchar(255) not null unique,
    full_name varchar(255),
    phone varchar(20),
    role enum('USER', 'ADMIN') not null default 'USER',
    is_active boolean not null default true,
    status enum('ACTIVE', 'SUSPENDED', 'BANNED') not null default 'ACTIVE',
    last_login timestamp null,
    created_at timestamp default current_timestamp
);

create table item_categories (
    category_id int auto_increment primary key,
    name varchar(100) not null,
    parent_id int null,
    foreign key (parent_id) references item_categories(category_id) on delete set null
);

create table items (
    item_id int auto_increment primary key,
    seller_id int not null,
    category_id int null,
    name varchar(255) not null,
    description text,
    starting_price decimal(15, 2) not null,
    status enum('DRAFT', 'PENDING_REVIEW', 'AVAILABLE', 'IN_AUCTION', 'SOLD', 'REMOVED') not null default 'DRAFT',
    created_at timestamp default current_timestamp,
    foreign key (seller_id) references users(user_id) on delete restrict,
    foreign key (category_id) references item_categories(category_id) on delete set null
);

create table item_images (
    image_id int auto_increment primary key,
    item_id int not null,
    url varchar(500) not null,
    is_primary boolean not null default false,
    sort_order int not null default 0,
    foreign key (item_id) references items(item_id) on delete cascade
);

create table item_attributes (
    attr_id int auto_increment primary key,
    item_id int not null,
    attr_key varchar(100) not null,
    attr_value varchar(500) not null,
    foreign key (item_id) references items(item_id) on delete cascade
);

create table auctions (
    auction_id int auto_increment primary key,
    item_id int not null unique,
    seller_id int not null,
    start_time datetime not null,
    end_time datetime not null,
    last_bid_time timestamp null,
    min_bid_increment decimal(15, 2) not null default 0.00,
    reserve_price decimal(15, 2) null,
    snipe_window_seconds smallint not null default 300,
    snipe_extension_seconds smallint not null default 60,
    current_price decimal(15, 2) not null,
    current_winner_id int null,
    status enum('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED') not null default 'OPEN',
    created_at timestamp default current_timestamp,
    foreign key (item_id) references items(item_id) on delete cascade,
    foreign key (seller_id) references users(user_id) on delete restrict,
    foreign key (current_winner_id) references users(user_id) on delete set null
);

create table bids (
    bid_id int auto_increment primary key,
    auction_id int not null,
    bidder_id int not null,
    amount decimal(15, 2) not null,
    bid_time timestamp default current_timestamp,
    is_auto_bid boolean not null default false,
    status enum('WINNING','OUTBID','WON','LOST') not null default 'WINNING',
    foreign key (auction_id) references auctions(auction_id) on delete cascade,
    foreign key (bidder_id) references users(user_id) on delete restrict
);

create table auto_bids (
    auto_bid_id int auto_increment primary key,
    auction_id int not null,
    bidder_id int not null,
    max_bid decimal(15, 2) not null,
    increment decimal(15, 2) not null,
    status enum('ACTIVE', 'COMPLETED', 'CANCELED') not null default 'ACTIVE',
    created_at timestamp default current_timestamp,
    updated_at timestamp default current_timestamp on update current_timestamp,
    foreign key (auction_id) references auctions(auction_id) on delete cascade,
    foreign key (bidder_id) references users(user_id) on delete restrict
);

create table wallets (
    wallet_id int auto_increment primary key,
    user_id int not null unique,
    balance decimal(15, 2) not null default 0.00,
    updated_at timestamp default current_timestamp on update current_timestamp,
    foreign key (user_id) references users(user_id) on delete restrict
);

create table wallet_transactions (
    tx_id int auto_increment primary key,
    wallet_id int not null,
    user_id int not null,
    type enum('DEPOSIT', 'WITHDRAW', 'HOLD', 'RELEASE','PAYMENT','REFUND') not null,
    amount decimal(15, 2) not null,
    ref_auction_id int null,
    note varchar(500),
    created_at timestamp default current_timestamp,
    foreign key (wallet_id) references wallets(wallet_id) on delete restrict,
    foreign key (user_id) references users(user_id) on delete restrict,
    foreign key (ref_auction_id) references auctions(auction_id) on delete set null
);

create table payments (
    payment_id int auto_increment primary key,
    auction_id int not null unique,
    buyer_id int not null,
    seller_id int not null,
    amount decimal(15, 2) not null,
    status enum('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED') not null default 'PENDING',
    paid_at timestamp null,
    created_at timestamp default current_timestamp,
    foreign key (auction_id) references auctions(auction_id) on delete restrict,
    foreign key (buyer_id) references users(user_id) on delete restrict,
    foreign key (seller_id) references users(user_id) on delete restrict
);

create table notifications (
    notif_id int auto_increment primary key,
    user_id int not null,
    type enum(
        'BID_PLACED',
        'OUTBID',
        'AUCTION_WON',
        'AUCTION_LOST',
        'AUCTION_STARTED',
        'AUCTION_ENDED',
        'PAYMENT_RECEIVED',
        'PAYMENT_DUE',
        'SYSTEM',
        'ITEM_APPROVED',
        'ITEM_REJECTED'
    ) not null,
    title varchar(255) not null,
    content text not null,
    is_read boolean not null default false,
    related_id int null,
    created_at timestamp default current_timestamp,
    foreign key (user_id) references users(user_id) on delete cascade
);