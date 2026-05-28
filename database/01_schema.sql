CREATE DATABASE IF NOT EXISTS auctionsystem;
USE auctionsystem;

CREATE TABLE accounts (
    user_id    INT DEFAULT nextval(seq_accounts) PRIMARY KEY,
    username   VARCHAR(100)   NOT NULL UNIQUE,
    password   VARCHAR(255)   NOT NULL,
    email      VARCHAR(255)   NOT NULL UNIQUE,
    full_name  VARCHAR(255),
    phone      VARCHAR(20),
    rating     DECIMAL(3,2)   NOT NULL DEFAULT 0.00,
    role       ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    status     ENUM('ACTIVE', 'SUSPENDED', 'BANNED') NOT NULL DEFAULT 'ACTIVE',
    last_login TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE items (
    item_id        INT DEFAULT nextval(seq_items) PRIMARY KEY,
    seller_id      INT            NOT NULL,
    name           VARCHAR(255)   NOT NULL,
    description    TEXT,
    starting_price DECIMAL(15,2)  NOT NULL,
    category       ENUM('ART', 'VEHICLE', 'ELECTRONIC'),
    status         ENUM('DRAFT', 'PENDING_REVIEW', 'AVAILABLE', 'IN_AUCTION', 'SOLD', 'REMOVED') NOT NULL DEFAULT 'DRAFT',
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (seller_id) REFERENCES accounts(user_id) ON DELETE RESTRICT
);

CREATE TABLE item_images (
    image_id   INT DEFAULT nextval(seq_item_images) PRIMARY KEY,
    item_id    INT          NOT NULL,
    url        VARCHAR(500) NOT NULL,
    is_primary BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order INT          NOT NULL DEFAULT 0,
    FOREIGN KEY (item_id) REFERENCES items(item_id) ON DELETE CASCADE
);

CREATE TABLE item_attributes (
    attr_id    INT DEFAULT nextval(seq_item_attributes) PRIMARY KEY,
    item_id    INT          NOT NULL,
    attr_key   VARCHAR(100) NOT NULL,
    attr_value VARCHAR(500) NOT NULL,
    FOREIGN KEY (item_id) REFERENCES items(item_id) ON DELETE CASCADE
);

CREATE TABLE auctions (
    auction_id              INT DEFAULT nextval(seq_auctions) PRIMARY KEY,
    item_id                 INT           NOT NULL UNIQUE,
    seller_id               INT           NOT NULL,
    start_time              DATETIME      NOT NULL,
    end_time                DATETIME      NOT NULL,
    last_bid_time           TIMESTAMP     NULL,
    min_bid_increment       DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    reserve_price           DECIMAL(15,2) NULL,
    snipe_window_seconds    SMALLINT      NOT NULL DEFAULT 300,
    snipe_extension_seconds SMALLINT      NOT NULL DEFAULT 60,
    current_price           DECIMAL(15,2) NOT NULL,
    current_winner_id       INT           NULL,
    status                  ENUM('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELED') NOT NULL DEFAULT 'OPEN',
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (item_id)           REFERENCES items(item_id)    ON DELETE CASCADE,
    FOREIGN KEY (seller_id)         REFERENCES accounts(user_id) ON DELETE RESTRICT,
    FOREIGN KEY (current_winner_id) REFERENCES accounts(user_id) ON DELETE SET NULL
);

CREATE TABLE bid_transactions (
    bid_id     INT DEFAULT nextval(seq_bid_transactions) PRIMARY KEY,
    auction_id INT           NOT NULL,
    bidder_id  INT           NOT NULL,
    amount     DECIMAL(15,2) NOT NULL,
    bid_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_auto_bid BOOLEAN      NOT NULL DEFAULT FALSE,
    status     ENUM('WINNING', 'OUTBID', 'WON', 'LOST') NOT NULL DEFAULT 'WINNING',
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (bidder_id)  REFERENCES accounts(user_id)   ON DELETE RESTRICT
);

CREATE TABLE auto_bid_configs (
    auto_bid_id INT DEFAULT nextval(seq_auto_bid_configs) PRIMARY KEY,
    auction_id  INT           NOT NULL,
    bidder_id   INT           NOT NULL,
    max_bid     DECIMAL(15,2) NOT NULL,
    increment   DECIMAL(15,2) NOT NULL,
    status      ENUM('ACTIVE', 'COMPLETED', 'CANCELED') NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT unique_auction_bidder UNIQUE (auction_id, bidder_id),
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (bidder_id)  REFERENCES accounts(user_id)   ON DELETE RESTRICT
);

CREATE TABLE wallets (
    wallet_id  INT DEFAULT nextval(seq_wallets) PRIMARY KEY,
    user_id    INT           NOT NULL UNIQUE,
    balance    DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES accounts(user_id) ON DELETE RESTRICT
);

CREATE TABLE wallet_transactions (
    tx_id          INT DEFAULT nextval(seq_wallet_transactions) PRIMARY KEY,
    wallet_id      INT           NOT NULL,
    user_id        INT           NOT NULL,
    type           ENUM('DEPOSIT', 'WITHDRAW', 'HOLD', 'RELEASE', 'PAYMENT', 'REFUND') NOT NULL,
    amount         DECIMAL(15,2) NOT NULL,
    ref_auction_id INT           NULL,
    note           VARCHAR(500),
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (wallet_id)      REFERENCES wallets(wallet_id)   ON DELETE RESTRICT,
    FOREIGN KEY (user_id)        REFERENCES accounts(user_id)    ON DELETE RESTRICT,
    FOREIGN KEY (ref_auction_id) REFERENCES auctions(auction_id) ON DELETE SET NULL
);

CREATE TABLE payments (
    payment_id INT DEFAULT nextval(seq_payments) PRIMARY KEY,
    auction_id INT           NOT NULL UNIQUE,
    buyer_id   INT           NOT NULL,
    seller_id  INT           NOT NULL,
    amount     DECIMAL(15,2) NOT NULL,
    status     ENUM('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED') NOT NULL DEFAULT 'PENDING',
    paid_at    TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE RESTRICT,
    FOREIGN KEY (buyer_id)   REFERENCES accounts(user_id)    ON DELETE RESTRICT,
    FOREIGN KEY (seller_id)  REFERENCES accounts(user_id)    ON DELETE RESTRICT
);

CREATE TABLE notifications (
    notif_id   INT DEFAULT nextval(seq_notifications) PRIMARY KEY,
    user_id    INT          NOT NULL,
    type       ENUM(
        'BID_PLACED', 'OUTBID', 'AUCTION_WON', 'AUCTION_LOST',
        'AUCTION_STARTED', 'AUCTION_ENDED', 'PAYMENT_RECEIVED',
        'PAYMENT_DUE', 'SYSTEM', 'ITEM_APPROVED', 'ITEM_REJECTED',
        'TIME_EXTENDED'
        ) NOT NULL,
    title      VARCHAR(255) NOT NULL,
    content    TEXT         NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    related_id INT          NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES accounts(user_id) ON DELETE CASCADE
);

