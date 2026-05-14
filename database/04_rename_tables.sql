USE auctionsystem;

RENAME TABLE users TO accounts;

RENAME TABLE bids TO bid_transactions;

RENAME TABLE auto_bids TO auto_bid_configs;